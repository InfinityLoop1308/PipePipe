package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import project.pipepipe.app.database.DatabaseOperations
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.StacktraceDialog
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogSettingScreen(
    modifier: Modifier = Modifier
) {
    val platformActions = SharedContext.platformActions
    var errorLogs by remember { mutableStateOf(emptyList<project.pipepipe.database.Error_log>()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        GlobalScope.launch {
            errorLogs = DatabaseOperations.getAllErrorLogs()
            isLoading = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.log)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (errorLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(MR.strings.no_error_logs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(errorLogs, key = { it.id }) { log ->
                    ErrorLogItem(
                        log = log,
                        onDelete = {
                            GlobalScope.launch {
                                DatabaseOperations.deleteErrorLog(log.id)
                                errorLogs = errorLogs.filter { it.id != log.id }
                            }
                        },
                        onCopy = {
                            platformActions.copyToClipboard(buildLogJson(log), "Error Log")
                        },
                        onEmail = {
                            platformActions.sendEmail(
                                to = "feedback@pipepipe.dev",
                                subject = "Exception in PipePipe ${platformActions.getAppVersion()}",
                                body = buildLogJson(log)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorLogItem(
    log: project.pipepipe.database.Error_log,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onEmail: () -> Unit
) {
    val platformActions = SharedContext.platformActions
    var showDetailsDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedDate = remember(log.timestamp) {
        dateFormat.format(Date(log.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.task,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamp
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stacktrace preview
            Text(
                text = log.stacktrace.replace("\n", ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    )
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View Details button on the left
                TextButton(
                    onClick = { showDetailsDialog = true },
                    modifier = Modifier.offset(x = (-8).dp)
                ) {
                    Text(stringResource(MR.strings.view_details))
                }

                // Email, Copy and Delete icons on the right
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onEmail) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = stringResource(MR.strings.email),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(MR.strings.copy),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(MR.strings.delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showDetailsDialog) {
        StacktraceDialog(
            stackTrace = log.stacktrace,
            onDismiss = { showDetailsDialog = false },
            onCopy = { platformActions.copyToClipboard(buildLogJson(log), "Error Log") }
        )
    }
}

private fun getSystemLanguage(): String {
    return Locale.getDefault().toString() // 例如: "en_US", "zh_CN"
}

private fun getSystemTimezone(): String {
    val timeZone = TimeZone.getDefault()
    return "${timeZone.id} (UTC${getTimezoneOffset(timeZone)})" // 例如: "Asia/Shanghai (UTC+8:00)"
}

private fun getTimezoneOffset(timeZone: TimeZone): String {
    val offset = timeZone.rawOffset / (1000 * 60 * 60) // 转换为小时
    val minutes = (timeZone.rawOffset % (1000 * 60 * 60)) / (1000 * 60)
    return if (offset >= 0) {
        if (minutes != 0) "+$offset:${String.format("%02d", minutes)}" else "+$offset:00"
    } else {
        if (minutes != 0) "$offset:${String.format("%02d", -minutes)}" else "$offset:00"
    }
}

fun buildLogJson(log: project.pipepipe.database.Error_log): String {
    return buildJsonObject {
        put("os", SharedContext.platformActions.getOsInfo())
        put("device", SharedContext.platformActions.getDeviceInfo())
        put("app_version", SharedContext.platformActions.getAppVersion())
        put("timestamp", log.timestamp)
        put("language", getSystemLanguage())
        put("timezone", getSystemTimezone())
        put("task", log.task)
        put("request", log.request)
        put("stacktrace", log.stacktrace)
    }.toString()
}

