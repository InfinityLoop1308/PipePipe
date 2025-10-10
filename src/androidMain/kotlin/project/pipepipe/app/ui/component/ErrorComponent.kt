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
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.ui.screens.settings.copyLogToClipboard
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.database.DatabaseOperations
import project.pipepipe.shared.uistate.ErrorInfo
import java.text.SimpleDateFormat
import java.util.*

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
    val formattedTime = remember {
        SimpleDateFormat("HH:mm", SharedContext.appLocale).format(Date())
    }

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

    val titleText = stringResource(MR.strings.error_title_generic)
    val messageText = stringResource(MR.strings.error_message_generic)
    val retryText = stringResource(MR.strings.error_retry)
    val feedbackText = stringResource(MR.strings.error_feedback)
    val showMoreText = stringResource(MR.strings.error_show_more)
    val showLessText = stringResource(MR.strings.error_show_less)
    val detailsTitle = stringResource(MR.strings.error_details_title)
    val statusIconCd = stringResource(MR.strings.error_status_icon_cd)
    val retryIconCd = stringResource(MR.strings.error_retry_button_cd)
    val feedbackIconCd = stringResource(MR.strings.error_feedback_button_cd)

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
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(48.dp),
                color = getErrorColor(errorState.type).copy(alpha = 0.08f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getErrorIcon(errorState.type),
                        contentDescription = statusIconCd,
                        modifier = Modifier.size(56.dp),
                        tint = getErrorColor(errorState.type)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = messageText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            errorState.errorCode?.let { code ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(MR.strings.error_reference_code, code),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        isRetrying = true
                        onRetry()
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
                        contentDescription = retryIconCd,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(retryText, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            DatabaseOperations.getErrorLogById(error.errorId)?.let {
                                copyLogToClipboard(context, it)
                            }
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
                        contentDescription = feedbackIconCd,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(feedbackText, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { showDetails = !showDetails },
                enabled = !isRetrying
            ) {
                Text(
                    text = if (showDetails) showLessText else showMoreText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

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
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = detailsTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = buildFriendlyDetails(
                                errorState = errorState,
                                formattedTime = formattedTime
                            ),
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

@Composable
private fun buildFriendlyDetails(
    errorState: ErrorState,
    formattedTime: String
): String {
    val lines = when (errorState.type) {
        ErrorType.NETWORK -> listOf(
            stringResource(MR.strings.error_details_network_line_1),
            stringResource(MR.strings.error_details_network_line_2),
            stringResource(MR.strings.error_details_network_line_3)
        )
        ErrorType.SERVER -> listOf(
            stringResource(MR.strings.error_details_server_line_1),
            stringResource(MR.strings.error_details_server_line_2),
            stringResource(MR.strings.error_details_server_line_3)
        )
        ErrorType.PERMISSION -> listOf(
            stringResource(MR.strings.error_details_permission_line_1),
            stringResource(MR.strings.error_details_permission_line_2),
            stringResource(MR.strings.error_details_permission_line_3)
        )
        ErrorType.UNKNOWN -> listOf(
            stringResource(MR.strings.error_details_unknown_line_1),
            stringResource(MR.strings.error_details_unknown_line_2),
            stringResource(MR.strings.error_details_unknown_line_3)
        )
    }.toMutableList()

    errorState.errorCode?.let {
        lines += stringResource(MR.strings.error_reference_code, it)
    }

    lines += stringResource(MR.strings.error_occurred_at, formattedTime)

    return lines.joinToString("\n")
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
