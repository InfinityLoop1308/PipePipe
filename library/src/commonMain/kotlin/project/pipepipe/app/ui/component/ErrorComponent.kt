package project.pipepipe.app.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.ui.screens.settings.buildLogJson
import project.pipepipe.app.uistate.ErrorInfo
import project.pipepipe.shared.infoitem.SupportedServiceInfo

@Composable
fun ErrorComponent(
    error: ErrorInfo,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    shouldStartFromTop: Boolean = false,
) {
    var showDetailsDialog by remember { mutableStateOf(false) }
    var stackTrace by remember { mutableStateOf<String>("") }
    val serviceList = remember {
        val jsonString = SharedContext.settingsManager.getString("supported_services", "[]")
        runCatching{ Json.decodeFromString<List<SupportedServiceInfo>>(jsonString) }.getOrDefault(emptyList())
    }

    // Map error codes to specific messages (for GEO_001 onwards)
    val titleText = when (error.errorCode) {
        "GEO_001" -> stringResource(MR.strings.error_code_geo_001)
        "UNAV_001" -> stringResource(MR.strings.error_code_block_001)
        "PRIV_001" -> stringResource(MR.strings.error_code_priv_001)
        "PAID_001" -> stringResource(MR.strings.error_code_paid_001)
        "PAID_002" -> stringResource(MR.strings.error_code_paid_002)
        "PAID_003" -> stringResource(MR.strings.error_code_paid_003)
        "TIME_001" -> stringResource(MR.strings.error_code_time_001)
        "TIME_002" -> stringResource(MR.strings.error_code_time_002)
        else -> {
            if (error.errorCode.startsWith("RISK")) {
                val serviceName = serviceList.first { it.serviceId == error.serviceId }.serviceName
                stringResource(MR.strings.error_code_risk).format(serviceName)
            } else {
                stringResource(MR.strings.error_snackbar_message)
            }
        }// Generic message for other codes
    }
    val retryText = stringResource(MR.strings.retry)
    val feedbackText = stringResource(MR.strings.error_snackbar_action)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if(shouldStartFromTop) Arrangement.Top else Arrangement.Center
    ) {
        Text(
            text = titleText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        error.errorCode.let { code ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.width(144.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(retryText)
            }

            OutlinedButton(
                onClick = {
                    GlobalScope.launch {
                        DatabaseOperations.getErrorLogById(error.errorId)?.let {
                            SharedContext.platformActions.copyToClipboard(buildLogJson(it), "Error Log")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(feedbackText)
            }

            OutlinedButton(
                onClick = {
                    GlobalScope.launch {
                        DatabaseOperations.getErrorLogById(error.errorId)?.let {
                            stackTrace = it.stacktrace
                            showDetailsDialog = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(MR.strings.view_details))
            }
        }
    }

    if (showDetailsDialog) {
        StacktraceDialog(
            stackTrace = stackTrace,
            onDismiss = { showDetailsDialog = false },
            onCopy = {
                GlobalScope.launch {
                    DatabaseOperations.getErrorLogById(error.errorId)?.let {
                        SharedContext.platformActions.copyToClipboard(buildLogJson(it), "Error Log")
                    }
                }
            }
        )
    }
}

@Composable
fun StacktraceDialog(
    stackTrace: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(MR.strings.stacktrace))
        },
        text = {
            Text(
                text = stackTrace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss
            ) {
                Text(stringResource(MR.strings.close))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onCopy()
                    isCopied = true
                    scope.launch {
                        delay(2000) // 2秒后重置图标
                        isCopied = false
                    }
                },
                enabled = !isCopied
            ) {
                AnimatedContent(
                    targetState = isCopied,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.8f))
                            .togetherWith(fadeOut() + scaleOut(targetScale = 0.8f))
                    },
                    label = "copyButtonAnimation"
                ) { copied ->
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(24.dp)
                    ) {
                        if (copied) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(MR.strings.msg_copied))
                        } else {
                            Text(stringResource(MR.strings.copy))
                        }
                    }
                }
            }
        },
        modifier = modifier
    )
}