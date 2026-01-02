package project.pipepipe.app.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ToastManager

@Composable
fun UpdateSettingsScreen(
    modifier: Modifier = Modifier
) {
    val UPDATE_ENABLED_KEY = "update_app_enabled"
    val SHOW_PRERELEASE_KEY = "show_prerelease"
    val checkMsg = stringResource(MR.strings.update_checking)
    var updateEnabled by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean(UPDATE_ENABLED_KEY, true))
    }

    var showPreRelease by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean(SHOW_PRERELEASE_KEY, false))
    }

    val preferenceItems = listOf(
        PreferenceItem.SwitchPref(
            key = UPDATE_ENABLED_KEY,
            title = stringResource(MR.strings.update_app_enabled_title),
            summary = stringResource(MR.strings.update_app_enabled_summary),
            defaultValue = true,
            onValueChange = { value ->
                updateEnabled = value
                if (value) {
                    ToastManager.show(checkMsg)
                    SharedContext.platformActions.checkForUpdate(isManual = true)
                }
            }
        ),
        PreferenceItem.SwitchPref(
            key = SHOW_PRERELEASE_KEY,
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
                ToastManager.show(checkMsg)
                SharedContext.platformActions.checkForUpdate(isManual = true)
            }
        )
    )

    PreferenceScreen(
        title = stringResource(MR.strings.settings_section_update),
        items = preferenceItems,
        modifier = modifier
    )
}
