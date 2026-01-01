package project.pipepipe.app.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.screens.PreferenceScreen

@Composable
fun DanmakuSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    // State for enable_max_rows_customization
    var enableMaxRowsCustomization by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean("enable_max_rows_customization_key", false))
    }

    val preferenceItems = listOf(
        PreferenceItem.SliderPref(
            key = "regular_bullet_comments_duration_key",
            title = stringResource(MR.strings.regular_bullet_comments_duration_title),
            summary = stringResource(MR.strings.regular_bullet_comments_duration_summary),
            defaultValue = 8,
            valueRange = 5..15,
            steps = 10
        ),
        PreferenceItem.SliderPref(
            key = "top_bottom_bullet_comments_duration_key",
            title = stringResource(MR.strings.top_bottom_bullet_comments_duration_title),
            summary = stringResource(MR.strings.top_bottom_bullet_comments_duration_summary),
            defaultValue = 8,
            valueRange = 5..15,
            steps = 10
        ),
        PreferenceItem.SliderPref(
            key = "bullet_comments_outline_radius_key",
            title = stringResource(MR.strings.bullet_comments_outline_radius_title),
            defaultValue = 2,
            valueRange = 0..8,
            steps = 8
        ),
        PreferenceItem.SliderPref(
            key = "bullet_comments_opacity_key",
            title = stringResource(MR.strings.bullet_comments_opacity_title),
            defaultValue = 255,
            valueRange = 0..255,
            steps = 255
        ),
        PreferenceItem.SwitchPref(
            key = "enable_max_rows_customization_key",
            title = stringResource(MR.strings.enable_max_rows_customization_title),
            summary = stringResource(MR.strings.enable_max_rows_customization_summary),
            defaultValue = false,
            onValueChange = { enabled ->
                enableMaxRowsCustomization = enabled
            }
        ),
        PreferenceItem.SliderPref(
            key = "max_bullet_comments_rows_top_key",
            title = stringResource(MR.strings.max_bullet_comments_rows_top_summary),
            summary = "%s",
            defaultValue = 15,
            valueRange = 0..15,
            steps = 15,
            enabled = enableMaxRowsCustomization
        ),
        PreferenceItem.SliderPref(
            key = "max_bullet_comments_rows_bottom_key",
            title = stringResource(MR.strings.max_bullet_comments_rows_bottom_summary),
            summary = "%s",
            defaultValue = 15,
            valueRange = 0..15,
            steps = 15,
            enabled = enableMaxRowsCustomization
        ),
        PreferenceItem.SliderPref(
            key = "max_bullet_comments_rows_regular_key",
            title = stringResource(MR.strings.max_bullet_comments_rows_regular_summary),
            summary = "%s",
            defaultValue = 15,
            valueRange = 0..15,
            steps = 15,
            enabled = enableMaxRowsCustomization
        ),
    )

    PreferenceScreen(
        title = stringResource(MR.strings.danmaku_settings_title),
        items = preferenceItems,
        modifier = modifier
    )
}
