package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.service.UpdateCheckWorker
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.ui.component.ClickablePreference
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.SwitchPreference

@Composable
fun UpdateSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var updateEnabled by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean(UpdateCheckWorker.UPDATE_ENABLED_KEY, true))
    }

    var showPreRelease by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean(UpdateCheckWorker.SHOW_PRERELEASE_KEY, false))
    }

    val preferenceItems = listOf(
        PreferenceItem.SwitchPref(
            key = UpdateCheckWorker.UPDATE_ENABLED_KEY,
            title = stringResource(MR.strings.update_app_enabled_title),
            summary = stringResource(MR.strings.update_app_enabled_summary),
            defaultValue = true,
            onValueChange = { value ->
                updateEnabled = value
                if (value) {
                    // 启用更新时立即检查一次
                    ToastManager.show(MR.strings.update_checking.desc().toString(context = context))
                    UpdateCheckWorker.enqueueUpdateCheck(context, isManual = true)
                }
            }
        ),
        PreferenceItem.SwitchPref(
            key = UpdateCheckWorker.SHOW_PRERELEASE_KEY,
            title = stringResource(MR.strings.update_show_prerelease_title),
            summary = stringResource(MR.strings.update_show_prerelease_summary),
            defaultValue = false,
            enabled = updateEnabled,
            onValueChange = { value ->
                showPreRelease = value
            }
        ),
        PreferenceItem.ClickablePref(
            key = "manual_update_check",
            title = stringResource(MR.strings.update_check_manual_title),
            summary = stringResource(MR.strings.update_check_manual_summary),
            onClick = {
                ToastManager.show(MR.strings.update_checking.desc().toString(context = context))
                UpdateCheckWorker.enqueueUpdateCheck(context, isManual = true)
            }
        )
    )

    Column {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.settings_section_update)
        )

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = preferenceItems,
                key = PreferenceItem::key
            ) { item ->
                when (item) {
                    is PreferenceItem.SwitchPref -> SwitchPreference(item = item)
                    is PreferenceItem.ClickablePref -> ClickablePreference(item = item)
                    else -> Unit
                }
            }
        }
    }
}
