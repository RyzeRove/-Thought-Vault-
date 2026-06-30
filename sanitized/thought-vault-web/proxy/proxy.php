<?php
/**
 * 思维札记 WebDAV 代理
 *
 * 部署: 将此文件放到 Web Station 站点根目录
 * 作用: 浏览器 -> proxy.php -> WebDAV (localhost端口)
 * 原因: 解决浏览器直接访问 WebDAV 的 CORS 限制
 *
 * 部署前修改: $webdavBase 中的 <USER> 替换为实际 NAS 用户名
 */

// ====== 配置 ======
$webdavBase = 'http://127.0.0.1:<your-nas-webdav-http-port>/homes/<your-nas-username>/thoughts';

// CORS 头（同域一般用不到，保留以便跨域调试）
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, PUT, DELETE, MKCOL, PROPFIND, OPTIONS, PATCH');
header('Access-Control-Allow-Headers: Authorization, Content-Type, Depth, X-Http-Method-Override');
header('Access-Control-Expose-Headers: DAV, Content-Length, Last-Modified, ETag');

// 预检请求直接返回
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

// 获取 WebDAV 路径
$path = $_GET['path'] ?? '';
if ($path === '') {
    // 测试连接：访问根目录
    $url = $webdavBase;
} else {
    // 安全校验：禁止路径穿越
    if (strpos($path, '..') !== false) {
        http_response_code(403);
        exit('Invalid path');
    }
    $url = $webdavBase . '/' . ltrim($path, '/');
}

// 获取请求方法
$method = $_SERVER['HTTP_X_HTTP_METHOD_OVERRIDE'] ?? $_SERVER['REQUEST_METHOD'];

// 获取请求体
$body = file_get_contents('php://input');

// 构建转发请求头
$forwardHeaders = [];
foreach ($_SERVER as $key => $value) {
    if (strpos($key, 'HTTP_') === 0) {
        $headerName = str_replace(' ', '-', ucwords(str_replace('_', ' ', strtolower(substr($key, 5)))));
        // 跳过不需要转发的头
        if (in_array(strtolower($headerName), ['host', 'x-http-method-override', 'origin', 'referer'])) {
            continue;
        }
        $forwardHeaders[] = "$headerName: $value";
    }
}

// 特殊处理 Depth 头（PROPFIND 需要）
if (isset($_SERVER['HTTP_DEPTH'])) {
    $forwardHeaders[] = 'Depth: ' . $_SERVER['HTTP_DEPTH'];
}

// 执行 cURL 请求
$ch = curl_init($url);
curl_setopt_array($ch, [
    CURLOPT_CUSTOMREQUEST  => $method,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_HEADER         => false,
    CURLOPT_HTTPHEADER     => $forwardHeaders,
    CURLOPT_TIMEOUT        => 30,
    CURLOPT_CONNECTTIMEOUT => 10,
    CURLOPT_FOLLOWLOCATION => false,
]);

// 有请求体时传递
if (strlen($body) > 0) {
    curl_setopt($ch, CURLOPT_POSTFIELDS, $body);
}

// WebDAV 可能返回 207 Multi-Status（不要当错误处理）
curl_setopt($ch, CURLOPT_FAILONERROR, false);

$response = curl_exec($ch);
$httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
$contentType = curl_getinfo($ch, CURLINFO_CONTENT_TYPE);
$error = curl_error($ch);
curl_close($ch);

if ($error) {
    http_response_code(502);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => 'Proxy error: ' . $error]);
    exit;
}

// 返回原始响应
http_response_code($httpCode);
if ($contentType) {
    header('Content-Type: ' . $contentType . '; charset=utf-8');
}
echo $response;
