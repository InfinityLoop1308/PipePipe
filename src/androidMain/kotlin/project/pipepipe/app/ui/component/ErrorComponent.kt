package project.pipepipe.app.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.ui.screens.settings.copyLogToClipboard
import project.pipepipe.shared.database.DatabaseOperations
import project.pipepipe.shared.uistate.ErrorInfo

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ErrorComponent(
    error: ErrorInfo,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    shouldStartFromTop: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDetailsDialog by remember { mutableStateOf(false) }
    var stackTrace by remember { mutableStateOf<String>("") }

    val titleText = stringResource(MR.strings.error_snackbar_message)
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
                    scope.launch {
                        DatabaseOperations.getErrorLogById(error.errorId)?.let {
                            copyLogToClipboard(context, it)
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
                    scope.launch {
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
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
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
                    onClick = { showDetailsDialog = false }
                ) {
                    Text(stringResource(MR.strings.close))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            DatabaseOperations.getErrorLogById(error.errorId)?.let {
                                copyLogToClipboard(context, it)
                            }
                        }
                    }
                ) {
                    Text(stringResource(MR.strings.copy))
                }
            }
        )
    }
}
