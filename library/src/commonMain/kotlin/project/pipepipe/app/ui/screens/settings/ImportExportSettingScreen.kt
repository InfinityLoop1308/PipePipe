package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.ImportExportHelper.checkBackupVersion
import project.pipepipe.app.helper.ImportExportHelper.createBackupBytes
import project.pipepipe.app.helper.ImportExportHelper.exportSubscriptionsToJson
import project.pipepipe.app.helper.ImportExportHelper.importBackupFromBytes
import project.pipepipe.app.helper.ImportExportHelper.importSubscriptionsFromJson
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.ui.screens.Screen
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun ImportExportSettingScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var pendingImportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showNewPipeWarningDialog by remember { mutableStateOf(false) }
    var importDatabaseSelected by remember { mutableStateOf(true) }
    var importSettingsSelected by remember { mutableStateOf(true) }

    var pendingJsonImportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var showJsonImportDialog by remember { mutableStateOf(false) }

    // Strings
    val backupCategoryTitle = stringResource(MR.strings.settings_category_backup_title)
    val exportTitle = stringResource(MR.strings.settings_import_export_export_title)
    val exportSummary = stringResource(MR.strings.settings_import_export_export_summary)
    val importTitle = stringResource(MR.strings.settings_import_export_import_title)
    val importSummary = stringResource(MR.strings.settings_import_export_import_summary)
    val subscriptionsTitle = stringResource(MR.strings.tab_subscriptions)
    val exportJsonTitle = stringResource(MR.strings.subscription_export_json_title)
    val exportJsonSummary = stringResource(MR.strings.subscription_export_json_summary)
    val importJsonTitle = stringResource(MR.strings.subscription_import_json_title)
    val importJsonSummary = stringResource(MR.strings.subscription_import_json_summary)
    val screenTitle = stringResource(MR.strings.settings_section_import_export)
    val exportSuccessMsg = stringResource(MR.strings.subscription_export_success)
    val exportFailedMsg = stringResource(MR.strings.subscription_export_failed)

    // Import messages strings
    val importMessages = project.pipepipe.app.helper.ImportExportHelper.ImportMessages(
        nothingSelected = stringResource(MR.strings.backup_nothing_selected_import),
        databaseNotFound = stringResource(MR.strings.backup_database_not_found),
        settingsNotFound = stringResource(MR.strings.backup_settings_not_found),
        successBoth = stringResource(MR.strings.backup_import_both_success),
        successDatabase = stringResource(MR.strings.backup_import_database_success),
        successSettings = stringResource(MR.strings.backup_import_settings_success),
        successCompleted = stringResource(MR.strings.backup_import_completed),
        failed = stringResource(MR.strings.backup_import_failed)
    )

    // FileKit: Export backup launcher
    val exportLauncher = rememberFileSaverLauncher { file: PlatformFile? ->
        file?.let {
            scope.launch {
                try {
                    val bytes = createBackupBytes(
                        includeDatabase = true,
                        includeSettings = true
                    )
                    it.write(bytes)
                    ToastManager.show(exportSuccessMsg)
                } catch (e: Exception) {
                    GlobalScope.launch { DatabaseOperations.insertErrorLog(e.stackTraceToString(), "EXPORT", "UNKNOWN_999") }
                    ToastManager.show(exportFailedMsg.format(e.message ?: "Unknown error"))
                }
            }
        }
    }

    // FileKit: Import backup launcher
    val importLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("zip"))
    ) { file: PlatformFile? ->
        file?.let {
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.Default) { it.readBytes() }
                    pendingImportBytes = bytes
                    importDatabaseSelected = true
                    importSettingsSelected = true

                    // Check if this is a NewPipe backup (versions 7, 8, 9)
                    val version = checkBackupVersion(bytes)
                    if (version in listOf(7, 8, 9)) {
                        showNewPipeWarningDialog = true
                    } else {
                        showImportDialog = true
                    }
                } catch (e: Exception) {
                    GlobalScope.launch { DatabaseOperations.insertErrorLog(e.stackTraceToString(), "IMPORT", "UNKNOWN_999") }
                    ToastManager.show(e.message ?: "Failed to read file")
                }
            }
        }
    }

    // FileKit: Export subscriptions JSON launcher
    val exportJsonLauncher = rememberFileSaverLauncher { file: PlatformFile? ->
        file?.let {
            scope.launch {
                try {
                    val bytes = exportSubscriptionsToJson()
                    it.write(bytes)
//                    ToastManager.show(exportSuccessMsg)
                } catch (e: Exception) {
                    GlobalScope.launch { DatabaseOperations.insertErrorLog(e.stackTraceToString(), "EXPORT", "UNKNOWN_999") }
//                    ToastManager.show(exportFailedMsg.format(e.message ?: "Unknown error"))
                }
            }
        }
    }

    // FileKit: Import subscriptions JSON launcher
    val importJsonLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = listOf("json"))
    ) { file: PlatformFile? ->
        file?.let {
            scope.launch {
                try {
                    val bytes = withContext(Dispatchers.Default) { it.readBytes() }
                    pendingJsonImportBytes = bytes
                    showJsonImportDialog = true
                } catch (e: Exception) {
                    GlobalScope.launch { DatabaseOperations.insertErrorLog(e.stackTraceToString(), "IMPORT", "UNKNOWN_999") }
                    ToastManager.show(e.message ?: "Failed to read file")
                }
            }
        }
    }



    val preferenceItems = remember(backupCategoryTitle, exportTitle, exportSummary, importTitle, importSummary, subscriptionsTitle, exportJsonTitle, exportJsonSummary, importJsonTitle, importJsonSummary) {
        listOf(
            PreferenceItem.CategoryPref(
                key = "backup_category",
                title = backupCategoryTitle
            ),
            PreferenceItem.ClickablePref(
                key = "export_backup_pref",
                title = exportTitle,
                summary = exportSummary,
                onClick = {
                    val fileName = "PipePipe5-Backup-${formatDateTime()}"
                    exportLauncher.launch(
                        suggestedName = fileName,
                        extension = "zip"
                    )
                }
            ),
            PreferenceItem.ClickablePref(
                key = "import_backup_pref",
                title = importTitle,
                summary = importSummary,
                onClick = {
                    importLauncher.launch()
                }
            ),
            PreferenceItem.CategoryPref(
                key = "subscriptions_category",
                title = subscriptionsTitle
            ),
            PreferenceItem.ClickablePref(
                key = "export_subscriptions_json_pref",
                title = exportJsonTitle,
                summary = exportJsonSummary,
                onClick = {
                    val fileName = "PipePipe5-Subscriptions-${formatDateTime()}"
                    exportJsonLauncher.launch(
                        suggestedName = fileName,
                        extension = "json"
                    )
                }
            ),
            PreferenceItem.ClickablePref(
                key = "import_subscriptions_json_pref",
                title = importJsonTitle,
                summary = importJsonSummary,
                onClick = {
                    importJsonLauncher.launch()
                }
            )
        )
    }

    PreferenceScreen(
        title = screenTitle,
        items = preferenceItems,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp)
    )

    // NewPipe Warning Dialog
    if (showNewPipeWarningDialog && pendingImportBytes != null) {
        AlertDialog(
            onDismissRequest = {
                showNewPipeWarningDialog = false
                pendingImportBytes = null
            },
            title = { Text(stringResource(MR.strings.newpipe_backup_warning_title)) },
            text = {
                Text(stringResource(MR.strings.newpipe_backup_warning_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewPipeWarningDialog = false
                        showImportDialog = true
                    }
                ) {
                    Text(stringResource(MR.strings.hint_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewPipeWarningDialog = false
                        pendingImportBytes = null
                    }
                ) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }

    // Import Dialog
    if (showImportDialog && pendingImportBytes != null) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportBytes = null
            },
            title = { Text(stringResource(MR.strings.settings_import_export_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = stringResource(MR.strings.settings_import_export_dialog_message))
                    ImportChoiceRow(
                        label = stringResource(MR.strings.settings_import_export_choice_database),
                        checked = importDatabaseSelected,
                        onCheckedChange = { importDatabaseSelected = it }
                    )
                    ImportChoiceRow(
                        label = stringResource(MR.strings.settings),
                        checked = importSettingsSelected,
                        onCheckedChange = { importSettingsSelected = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetBytes = pendingImportBytes
                        if (targetBytes != null) {
                            scope.launch {
                                importBackupFromBytes(
                                    bytes = targetBytes,
                                    importDatabase = importDatabaseSelected,
                                    importSettings = importSettingsSelected,
                                    messages = importMessages,
                                    onSuccess = {
                                        // Navigate to Main screen and trigger dialog checks
                                        navController.navigate(Screen.Main.route) {
                                            popUpTo(Screen.Main.route) { inclusive = true }
                                        }
                                        // Trigger dialog check after a short delay to ensure navigation completes
                                        scope.launch {
                                            delay(300)
                                            SharedContext.triggerDialogCheck()
                                        }
                                    }
                                )
                            }
                        }
                        showImportDialog = false
                        pendingImportBytes = null
                    },
                    enabled = importDatabaseSelected || importSettingsSelected
                ) {
                    Text(stringResource(MR.strings.import_title))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        pendingImportBytes = null
                    }
                ) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }

    // JSON Import Dialog
    if (showJsonImportDialog && pendingJsonImportBytes != null) {
        val importSuccessMsg = stringResource(MR.strings.subscription_import_success)
        val importFailedMsg = stringResource(MR.strings.subscription_import_failed)

        AlertDialog(
            onDismissRequest = {
                showJsonImportDialog = false
                pendingJsonImportBytes = null
            },
            title = { Text(stringResource(MR.strings.subscription_import_json_title)) },
            text = {
                Text(text = stringResource(MR.strings.subscription_import_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetBytes = pendingJsonImportBytes
                        if (targetBytes != null) {
                            scope.launch {
                                try {
                                    val count = importSubscriptionsFromJson(targetBytes)
                                    ToastManager.show(importSuccessMsg.format(count))
                                } catch (e: Exception) {
                                    GlobalScope.launch { DatabaseOperations.insertErrorLog(e.stackTraceToString(), "IMPORT", "UNKNOWN_999") }
                                    ToastManager.show(importFailedMsg.format(e.message ?: "Unknown error"))
                                }
                            }
                        }
                        showJsonImportDialog = false
                        pendingJsonImportBytes = null
                    }
                ) {
                    Text(stringResource(MR.strings.import_title))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showJsonImportDialog = false
                        pendingJsonImportBytes = null
                    }
                ) {
                    Text(stringResource(MR.strings.cancel))
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

@OptIn(ExperimentalTime::class)
private fun formatDateTime(): String {
    val now = Clock.System.now()
    val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.year}${localDateTime.month.number.toString().padStart(2, '0')}${localDateTime.day.toString().padStart(2, '0')}_${localDateTime.hour.toString().padStart(2, '0')}${localDateTime.minute.toString().padStart(2, '0')}${localDateTime.second.toString().padStart(2, '0')}"
}
