package project.pipepipe.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import project.pipepipe.shared.database.DatabaseOperations
import project.pipepipe.shared.job.ErrorDetail
import project.pipepipe.shared.uistate.ErrorInfo
import project.pipepipe.app.ui.screens.settings.copyLogToClipboard

/**
 * 错误类型枚举
 */
enum class ErrorType {
    NETWORK,
    SERVER,
    UNKNOWN,
    PERMISSION
}

/**
 * 错误数据类
 */
data class ErrorState(
    val type: ErrorType = ErrorType.UNKNOWN,
    val title: String = "Oops!",
    val message: String = "看起来出了点小问题",
    val errorCode: String? = null,
    val showDetails: Boolean = false
)

/**
 * 错误组件
 *
 * @param errorState 错误状态
 * @param onRetry 重试回调
 * @param modifier 修饰符
 */
@Composable
fun ErrorComponent(
    error: ErrorInfo,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorState = ErrorState()
    var showDetails by remember { mutableStateOf(errorState.showDetails) }
    var isRetrying by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 动画效果
    val alpha by animateFloatAsState(
        targetValue = if (isRetrying) 0.5f else 1f,
        animationSpec = tween(300),
        label = "alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isRetrying) 0.95f else 1f,
        animationSpec = tween(300),
        label = "scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            )
            .alpha(alpha)
            .scale(scale)
            .offset(y = (-24).dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 友好的图标
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(48.dp),
                color = getErrorColor(errorState.type).copy(alpha = 0.08f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getErrorIcon(errorState.type),
                        contentDescription = "Status Icon",
                        modifier = Modifier.size(56.dp),
                        tint = getErrorColor(errorState.type)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 友好的标题
            Text(
                text = errorState.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 友好的消息
            Text(
                text = errorState.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            // 错误代码（如果有）- 更低调的样式
            errorState.errorCode?.let { code ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "参考代码: $code",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

// 操作按钮 - 改为竖排
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 重试按钮 - 使用柔和的颜色
                Button(
                    onClick = {
                        isRetrying = true
                        onRetry()
                        // 模拟重试动画
                        kotlinx.coroutines.GlobalScope.launch {
                            kotlinx.coroutines.delay(500)
                            isRetrying = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getErrorColor(errorState.type).copy(alpha = 0.9f)
                    ),
                    enabled = !isRetrying,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Retry",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("再试一次", fontWeight = FontWeight.Medium)
                }

                // 反馈按钮
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val row = DatabaseOperations.getErrorLogById(error.errorId)
                            copyLogToClipboard(context, row!!)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isRetrying,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = "Report",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("反馈问题", fontWeight = FontWeight.Medium)
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            // 详情切换按钮
            TextButton(
                onClick = { showDetails = !showDetails },
                enabled = !isRetrying
            ) {
                Text(
                    text = if (showDetails) "收起详情" else "了解更多",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            // 详情内容
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "💡 可能的原因",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = buildFriendlyDetails(errorState),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 根据错误类型获取友好的图标
 */
@Composable
private fun getErrorIcon(type: ErrorType): ImageVector {
    return when (type) {
        ErrorType.NETWORK -> Icons.Outlined.CloudOff
        ErrorType.SERVER -> Icons.Outlined.SentimentDissatisfied
        ErrorType.PERMISSION -> Icons.Outlined.Lock
        ErrorType.UNKNOWN -> Icons.Outlined.ErrorOutline
    }
}

/**
 * 根据错误类型获取柔和的颜色
 */
@Composable
private fun getErrorColor(type: ErrorType): Color {
    return when (type) {
        ErrorType.NETWORK -> Color(0xFF42A5F5) // 柔和的蓝色
        ErrorType.SERVER -> Color(0xFFEF5350) // 柔和的红色
        ErrorType.PERMISSION -> Color(0xFFAB47BC) // 柔和的紫色
        ErrorType.UNKNOWN -> Color(0xFF78909C) // 柔和的灰蓝色
    }
}

/**
 * 构建友好的错误详情文本
 */
private fun buildFriendlyDetails(errorState: ErrorState): String {
    return buildString {
        when (errorState.type) {
            ErrorType.NETWORK -> {
                appendLine("• 网络连接可能不稳定")
                appendLine("• 可以尝试切换到其他网络")
                appendLine("• 检查一下路由器或WiFi设置")
            }
            ErrorType.SERVER -> {
                appendLine("• 服务器可能正在维护中")
                appendLine("• 稍等几分钟后再试试")
                appendLine("• 如果问题持续，我们会尽快修复")
            }
            ErrorType.PERMISSION -> {
                appendLine("• 需要开启相关权限才能使用")
                appendLine("• 可以在设置中重新授权")
                appendLine("• 我们会保护您的隐私安全")
            }
            ErrorType.UNKNOWN -> {
                appendLine("• 这可能是临时的小故障")
                appendLine("• 重启应用通常能解决问题")
                appendLine("• 如果还是不行，欢迎联系我们")
            }
        }
        errorState.errorCode?.let {
            appendLine("\n参考代码: $it")
        }
        appendLine("发生时间: ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date())}")
    }
}
