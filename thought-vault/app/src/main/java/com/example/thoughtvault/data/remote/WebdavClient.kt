package com.example.thoughtvault.data.remote

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebDAV 客户端 — 所有 NAS 通信的唯一入口。
 * 封装了 HTTP Basic Auth、PUT/GET/MKCOL/PROPFIND/DELETE 操作。
 */
@Singleton
class WebdavClient @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                Timber.d("${request.method} ${request.url} -> ${response.code}")
                response
            }
            .build()
    }

    /**
     * 指数退避重试：遇到网络瞬时错误时自动重试。
     * 延迟序列：500ms → 1s → 2s（最多 3 次尝试）
     */
    private suspend fun <T> retryWithBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500,
        block: suspend () -> Result<T>,
    ): Result<T> {
        var attempt = 0
        var lastResult: Result<T>? = null

        while (attempt < maxAttempts) {
            val result = block()
            if (result.isSuccess) return result

            val error = result.exceptionOrNull()
            // 仅对可重试的错误（网络瞬时故障）进行重试
            if (error is ConnectException || error is SocketTimeoutException) {
                attempt++
                if (attempt < maxAttempts) {
                    val delay = initialDelayMs * (1L shl (attempt - 1))  // 500, 1000, 2000
                    Timber.d("重试 $attempt/$maxAttempts，等待 ${delay}ms...")
                    kotlinx.coroutines.delay(delay)
                    lastResult = result
                    continue
                }
            }
            // 不可重试的错误（4xx, 5xx），直接返回
            return result
        }
        return lastResult ?: Result.failure(IOException("重试耗尽"))
    }

    /** 构建带 Basic Auth 的请求头 */
    private fun authHeaders(username: String, password: String): Map<String, String> {
        val credentials = "$username:$password"
        val encoded = Base64.encodeToString(
            credentials.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP
        )
        return mapOf("Authorization" to "Basic $encoded")
    }

    /**
     * PUT — 创建或覆盖文件（含指数退避重试）。
     */
    suspend fun put(
        url: String,
        content: String,
        username: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        retryWithBackoff {
            try {
                val body = content.toRequestBody("text/markdown; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .apply { authHeaders(username, password).forEach { (k, v) -> addHeader(k, v) } }
                    .put(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("PUT 失败: HTTP ${response.code}"))
                }
            } catch (e: ConnectException) {
                Result.failure(ConnectionException("无法连接到 NAS", e))
            } catch (e: SocketTimeoutException) {
                Result.failure(ConnectionException("连接超时", e))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * GET — 读取文件内容。
     * 返回 null 表示文件不存在（404）。
     */
    suspend fun get(
        url: String,
        username: String,
        password: String,
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .apply { authHeaders(username, password).forEach { (k, v) -> addHeader(k, v) } }
                .get()
                .build()

            val response = client.newCall(request).execute()
            when {
                response.isSuccessful -> Result.success(response.body?.string() ?: "")
                response.code == 404 -> Result.success(null)
                else -> Result.failure(IOException("GET failed: ${response.code}"))
            }
        } catch (e: ConnectException) {
            Result.failure(ConnectionException("无法连接到 NAS", e))
        } catch (e: SocketTimeoutException) {
            Result.failure(ConnectionException("连接超时", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * PUT — 创建空文件（用于锁文件）。
     */
    suspend fun putEmpty(
        url: String,
        username: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .apply { authHeaders(username, password).forEach { (k, v) -> addHeader(k, v) } }
                .put("".toRequestBody(null))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("PUT empty failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * DELETE — 删除文件（用于释放锁）。
     */
    suspend fun delete(
        url: String,
        username: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .apply { authHeaders(username, password).forEach { (k, v) -> addHeader(k, v) } }
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("DELETE failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * MKCOL — 创建目录（按需创建年/月子目录）。
     */
    suspend fun mkcol(
        url: String,
        username: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .apply { authHeaders(username, password).forEach { (k, v) -> addHeader(k, v) } }
                .method("MKCOL", null)
                .build()

            val response = client.newCall(request).execute()
            // 201 Created 或 405 Method Not Allowed（目录已存在）都视为成功
            if (response.isSuccessful || response.code == 405) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("MKCOL failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * PROPFIND — 列出目录内容（用于验证连接和浏览历史）。
     */
    suspend fun propfind(
        url: String,
        username: String,
        password: String,
        depth: Int = 1,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = """<?xml version="1.0" encoding="utf-8"?>
                <D:propfind xmlns:D="DAV:">
                  <D:prop>
                    <D:displayname/>
                    <D:getlastmodified/>
                    <D:getcontentlength/>
                  </D:prop>
                </D:propfind>""".trimIndent()
                .toRequestBody("application/xml; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .apply { authHeaders(username, password).forEach { (k, v) -> addHeader(k, v) } }
                .header("Depth", depth.toString())
                .method("PROPFIND", body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(response.body?.string() ?: "")
            } else {
                Result.failure(IOException("PROPFIND failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 测试 NAS 连接是否正常。
     * 向根目录发送 PROPFIND Depth:0 请求。
     */
    suspend fun testConnection(baseUrl: String, username: String, password: String): Result<Boolean> {
        return propfind(baseUrl, username, password, depth = 0).map { true }
    }
}

/** 连接异常 */
class ConnectionException(message: String, cause: Throwable? = null) : IOException(message, cause)
