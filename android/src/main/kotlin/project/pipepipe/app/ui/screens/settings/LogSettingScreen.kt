package project.pipepipe.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.ToastManager
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.GlobalScope
import project.pipepipe.app.MR
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.StacktraceDialog
import java.text.SimpleDateFormat
import java.util.*
import android.os.Build

@Composable
fun LogSettingScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
                            copyLogToClipboard(context, log)
                        },
                        onEmail = {
                            sendLogEmail(context, log)
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
    val context = LocalContext.current
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
            onCopy = { copyLogToClipboard(context, log) }
        )
    }
}

private fun getOsString(): String {
    return (System.getProperty("os.name")?: "Linux") +
            " " + Build.VERSION.BASE_OS.ifEmpty { "Android" } +
            " " + Build.VERSION.RELEASE +
            " - " + Build.VERSION.SDK_INT
}

private fun getDeviceString(): String {
    return "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
}

private fun getAppVersionString(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        "${packageInfo.versionName} ($versionCode)"
    } catch (e: Exception) {
        "Unknown"
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

fun copyLogToClipboard(context: Context, log: project.pipepipe.database.Error_log) {
    val json = buildJsonObject {
        put("os", getOsString())
        put("device", getDeviceString())
        put("app_version", getAppVersionString(context))
        put("timestamp", log.timestamp)
        put("language", getSystemLanguage())
        put("timezone", getSystemTimezone())
        put("task", log.task)
        put("request", log.request)
        put("stacktrace", log.stacktrace)
    }.toString()

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Error Log", json)
    clipboard.setPrimaryClip(clip)

    ToastManager.show(MR.strings.msg_copied.desc().toString(context = context))
}

fun sendLogEmail(context: Context, log: project.pipepipe.database.Error_log) {
    val json = buildJsonObject {
        put("os", getOsString())
        put("device", getDeviceString())
        put("app_version", getAppVersionString(context))
        put("timestamp", log.timestamp)
        put("language", getSystemLanguage())
        put("timezone", getSystemTimezone())
        put("task", log.task)
        put("request", log.request)
        put("stacktrace", log.stacktrace)
    }.toString()

    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf("feedback@pipepipe.dev"))
        putExtra(Intent.EXTRA_SUBJECT, "Exception in PipePipe $versionName")
        putExtra(Intent.EXTRA_TEXT, json)
    }

    // Check if there's an email app available
    val packageManager = context.packageManager
    val activities = packageManager.queryIntentActivities(emailIntent, PackageManager.MATCH_DEFAULT_ONLY)

    if (activities.isNotEmpty()) {
        context.startActivity(emailIntent)
    } else {
        ToastManager.show(MR.strings.no_email_app_available.desc().toString(context = context))
    }
}
