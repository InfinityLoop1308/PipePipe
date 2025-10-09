package project.pipepipe.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import project.pipepipe.app.database.DatabaseImporter
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.MR
import project.pipepipe.app.ui.component.CategoryPreference
import project.pipepipe.app.ui.component.ClickablePreference
import project.pipepipe.app.ui.component.CustomTopBar
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
    var importDatabaseSelected by remember { mutableStateOf(true) }
    var importSettingsSelected by remember { mutableStateOf(true) }

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
            showImportDialog = true
        }
    }

    val preferenceItems = remember(exportLauncher, importLauncher, dateFormat) {
        listOf(
            PreferenceItem.CategoryPref(
                key = "backup_category",
                title = MR.strings.settings_import_export_backup_category.desc().toString(context)
            ),
            PreferenceItem.ClickablePref(
                key = "export_backup_pref",
                title = MR.strings.settings_import_export_export_title.desc().toString(context),
                summary = MR.strings.settings_import_export_export_summary.desc().toString(context),
                onClick = {
                    val fileName = "PipePipeBackup-${dateFormat.format(Date())}.zip"
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
            )
        )
    }

    Column {
        CustomTopBar(
            defaultTitleText = MR.strings.settings_section_import_export.desc().toString(context),
            defaultNavigationOnClick = { navController.popBackStack() }
        )

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(
                items = preferenceItems,
                key = { item -> item.key.ifEmpty { item.title } }
            ) { item ->
                when (item) {
                    is PreferenceItem.CategoryPref -> CategoryPreference(item)
                    is PreferenceItem.ClickablePref -> ClickablePreference(item)
                    else -> Unit
                }
            }
        }
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
                        label = MR.strings.settings_import_export_choice_settings.desc().toString(context),
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
                                importSettings = importSettingsSelected
                            )
                        }
                        showImportDialog = false
                        pendingImportUri = null
                    },
                    enabled = importDatabaseSelected || importSettingsSelected
                ) {
                    Text(MR.strings.settings_import_export_dialog_confirm.desc().toString(context))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        pendingImportUri = null
                    }
                ) {
                    Text(MR.strings.common_cancel.desc().toString(context))
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
