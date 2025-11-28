package project.pipepipe.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import project.pipepipe.app.database.DatabaseImporter
import project.pipepipe.app.database.SubscriptionJsonHelper
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.ui.screens.PreferenceScreen
import project.pipepipe.app.ui.screens.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImportExportSettingScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val databaseImporter = remember { DatabaseImporter(context) }
    val dateFormat = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()) }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showNewPipeWarningDialog by remember { mutableStateOf(false) }
    var importDatabaseSelected by remember { mutableStateOf(true) }
    var importSettingsSelected by remember { mutableStateOf(true) }

    var pendingJsonImportUri by remember { mutableStateOf<Uri?>(null) }
    var showJsonImportDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            databaseImporter.exportBackup(it)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            pendingImportUri = it
            importDatabaseSelected = true
            importSettingsSelected = true

            // Check if this is a NewPipe backup (versions 7, 8, 9)
            CoroutineScope(Dispatchers.IO).launch {
                val version = databaseImporter.checkBackupVersion(it)
                CoroutineScope(Dispatchers.Main).launch {
                    if (version in listOf(7, 8, 9)) {
                        showNewPipeWarningDialog = true
                    } else {
                        showImportDialog = true
                    }
                }
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val count = SubscriptionJsonHelper.exportToJson(context, it)
                    ToastManager.show(
                        MR.strings.subscription_export_success.desc().toString(context)
                    )
                } catch (e: Exception) {
                    GlobalScope.launch{ DatabaseOperations.insertErrorLog(e.stackTraceToString(), "EXPORT", "UNKNOWN_999") }
                    ToastManager.show(
                        MR.strings.subscription_export_failed.desc().toString(context)
                            .format(e.message ?: "Unknown error")
                    )
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flags)
            }
            pendingJsonImportUri = it
            showJsonImportDialog = true
        }
    }

    val preferenceItems = remember(exportLauncher, importLauncher, exportJsonLauncher, importJsonLauncher, dateFormat) {
        listOf(
            PreferenceItem.CategoryPref(
                key = "backup_category",
                title = MR.strings.settings_category_backup_title.desc().toString(context)
            ),
            PreferenceItem.ClickablePref(
                key = "export_backup_pref",
                title = MR.strings.settings_import_export_export_title.desc().toString(context),
                summary = MR.strings.settings_import_export_export_summary.desc().toString(context),
                onClick = {
                    val fileName = "PipePipe5-Backup-${dateFormat.format(Date())}.zip"
                    exportLauncher.launch(fileName)
                }
            ),
            PreferenceItem.ClickablePref(
                key = "import_backup_pref",
                title = MR.strings.settings_import_export_import_title.desc().toString(context),
                summary = MR.strings.settings_import_export_import_summary.desc().toString(context),
                onClick = {
                    importLauncher.launch(arrayOf("application/zip"))
                }
            ),
            PreferenceItem.CategoryPref(
                key = "subscriptions_category",
                title = MR.strings.tab_subscriptions.desc().toString(context)
            ),
            PreferenceItem.ClickablePref(
                key = "export_subscriptions_json_pref",
                title = MR.strings.subscription_export_json_title.desc().toString(context),
                summary = MR.strings.subscription_export_json_summary.desc().toString(context),
                onClick = {
                    val fileName = "PipePipe5-Subscriptions-${dateFormat.format(Date())}.json"
                    exportJsonLauncher.launch(fileName)
                }
            ),
            PreferenceItem.ClickablePref(
                key = "import_subscriptions_json_pref",
                title = MR.strings.subscription_import_json_title.desc().toString(context),
                summary = MR.strings.subscription_import_json_summary.desc().toString(context),
                onClick = {
                    importJsonLauncher.launch(arrayOf("application/json"))
                }
            )
        )
    }

    PreferenceScreen(
        title = MR.strings.settings_section_import_export.desc().toString(context),
        items = preferenceItems,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp)
    )

    if (showNewPipeWarningDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showNewPipeWarningDialog = false
                pendingImportUri = null
            },
            title = { Text(MR.strings.newpipe_backup_warning_title.desc().toString(context)) },
            text = {
                Text(MR.strings.newpipe_backup_warning_message.desc().toString(context))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewPipeWarningDialog = false
                        showImportDialog = true
                    }
                ) {
                    Text(MR.strings.hint_continue.desc().toString(context))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewPipeWarningDialog = false
                        pendingImportUri = null
                    }
                ) {
                    Text(MR.strings.cancel.desc().toString(context))
                }
            }
        )
    }

    if (showImportDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportUri = null
            },
            title = { Text(MR.strings.settings_import_export_import_title.desc().toString(context)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = MR.strings.settings_import_export_dialog_message.desc().toString(context))
                    ImportChoiceRow(
                        label = MR.strings.settings_import_export_choice_database.desc().toString(context),
                        checked = importDatabaseSelected,
                        onCheckedChange = { importDatabaseSelected = it }
                    )
                    ImportChoiceRow(
                        label = MR.strings.settings.desc().toString(context),
                        checked = importSettingsSelected,
                        onCheckedChange = { importSettingsSelected = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetUri = pendingImportUri
                        if (targetUri != null) {
                            databaseImporter.importBackup(
                                targetUri,
                                importDatabase = importDatabaseSelected,
                                importSettings = importSettingsSelected,
                                onSuccess = {
                                    // Navigate to Main screen and trigger dialog checks
                                    navController.navigate(Screen.Main.route) {
                                        popUpTo(Screen.Main.route) { inclusive = true }
                                    }
                                    // Trigger dialog check after a short delay to ensure navigation completes
                                    CoroutineScope(Dispatchers.Main).launch {
                                        kotlinx.coroutines.delay(300)
                                        project.pipepipe.app.SharedContext.triggerDialogCheck()
                                    }
                                }
                            )
                        }
                        showImportDialog = false
                        pendingImportUri = null
                    },
                    enabled = importDatabaseSelected || importSettingsSelected
                ) {
                    Text(MR.strings.import_title.desc().toString(context))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        pendingImportUri = null
                    }
                ) {
                    Text(MR.strings.cancel.desc().toString(context))
                }
            }
        )
    }

    if (showJsonImportDialog && pendingJsonImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showJsonImportDialog = false
                pendingJsonImportUri = null
            },
            title = { Text(MR.strings.subscription_import_json_title.desc().toString(context)) },
            text = {
                Text(text = MR.strings.subscription_import_confirm_message.desc().toString(context))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetUri = pendingJsonImportUri
                        if (targetUri != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val count = SubscriptionJsonHelper.importFromJson(context, targetUri)
                                    ToastManager.show(
                                        MR.strings.subscription_import_success.desc().toString(context)
                                            .format(count)
                                    )
                                } catch (e: Exception) {
                                    GlobalScope.launch{ DatabaseOperations.insertErrorLog(e.stackTraceToString(), "IMPORT", "UNKNOWN_999") }
                                    ToastManager.show(
                                        MR.strings.subscription_import_failed.desc().toString(context)
                                            .format(e.message ?: "Unknown error")
                                    )
                                }
                            }
                        }
                        showJsonImportDialog = false
                        pendingJsonImportUri = null
                    }
                ) {
                    Text(MR.strings.import_title.desc().toString(context))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showJsonImportDialog = false
                        pendingJsonImportUri = null
                    }
                ) {
                    Text(MR.strings.cancel.desc().toString(context))
                }
            }
        )
    }
}

@Composable
private fun ImportChoiceRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
