package com.example.thoughtvault.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "群晖 NAS 连接",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // WebDAV 地址
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("WebDAV 地址") },
                placeholder = { Text("https://192.168.1.100:5005/thoughts/") },
                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            // 用户名
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用户名") },
                placeholder = { Text("群晖 DSM 账号") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )

            // 密码
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
            )

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 测试连接
                OutlinedButton(
                    onClick = viewModel::onTestConnection,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isTesting && state.baseUrl.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("测试连接")
                }

                // 保存配置
                Button(
                    onClick = viewModel::onSave,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (state.hasExistingConfig) "更新配置" else "保存配置")
                }
            }

            // 测试结果
            state.testResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (result) {
                            is TestResult.Success -> MaterialTheme.colorScheme.primaryContainer
                            is TestResult.Error -> MaterialTheme.colorScheme.errorContainer
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when (result) {
                                is TestResult.Success -> Icons.Default.CheckCircle
                                is TestResult.Error -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = when (result) {
                                is TestResult.Success -> MaterialTheme.colorScheme.primary
                                is TestResult.Error -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            when (result) {
                                is TestResult.Success -> result.message
                                is TestResult.Error -> result.message
                            }
                        )
                    }
                }
            }

            // 使用说明
            if (!state.hasExistingConfig) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "如何配置？",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            """1. 在群晖 DSM 套件中心安装「WebDAV Server」
2. 启用 HTTP 或 HTTPS 端口（默认 5005/5006）
3. 在 File Station 中创建 shared folder: thoughts/
4. 按计划文档创建子目录结构（raw/, daily/ 等）
5. 在此填入 NAS 地址、DSM 账号密码即可""",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 清除配置（仅当已配置时显示）
            if (state.hasExistingConfig) {
                Spacer(Modifier.height(32.dp))
                TextButton(
                    onClick = viewModel::onClearConfig,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("清除所有配置")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
