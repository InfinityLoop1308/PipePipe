package project.pipepipe.app.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.screens.PreferenceScreen

@Composable
fun PlayerSettingScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var musicModeEnabled by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean("auto_background_play_key", false))
    }

    var autoQueueEnabled by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean("auto_queue_key", false))
    }

    val resolutionAuto = stringResource(MR.strings.auto)
    val resolutionBest = stringResource(MR.strings.settings_player_resolution_best)
    val resolutionDataSaver = stringResource(MR.strings.settings_player_resolution_datasaver)

    val resolutionEntries = remember(resolutionAuto, resolutionBest, resolutionDataSaver) {
        listOf(
            resolutionAuto,
            resolutionBest,
            "1080P",
            "720P",
            "480P",
            "360P",
            resolutionDataSaver
        )
    }
    val resolutionValues = remember {
        listOf(
            "auto",
            "best",
            "1080p",
            "720p",
            "480p",
            "360p",
            "lowest"
        )
    }

    val randomSummary = if (musicModeEnabled) {
        stringResource(MR.strings.settings_player_music_random_summary)
    } else {
        stringResource(MR.strings.settings_player_music_random_disabled_summary)
    }

    val autoplayAlways = stringResource(MR.strings.always)
    val autoplayWifi = stringResource(MR.strings.wifi_only)
    val autoplayNever = stringResource(MR.strings.never)

    val autoplayEntries = remember(autoplayAlways, autoplayWifi, autoplayNever) {
        listOf(
            autoplayAlways,
            autoplayWifi,
            autoplayNever
        )
    }
    val autoplayValues = remember {
        listOf(
            "autoplay_always_key",
            "autoplay_wifi_key",
            "autoplay_never_key"
        )
    }

    var autoplayValue by remember {
        mutableStateOf(SharedContext.settingsManager.getString("autoplay_key", "autoplay_never_key"))
    }

    val autoplaySummary = remember(autoplayValue, autoplayAlways, autoplayWifi, autoplayNever) {
        when (autoplayValue) {
            "autoplay_always_key" -> autoplayAlways
            "autoplay_wifi_key" -> autoplayWifi
            "autoplay_never_key" -> autoplayNever
            else -> autoplayWifi
        }
    }

    val minimizeNone = stringResource(MR.strings.minimize_on_exit_none_description)
    val minimizeBackground = stringResource(MR.strings.minimize_on_exit_background_description)
    val minimizePopup = stringResource(MR.strings.minimize_on_exit_popup_description)

    val minimizeEntries = remember(minimizeNone, minimizeBackground, minimizePopup) {
        listOf(
            minimizeNone,
            minimizeBackground,
            minimizePopup
        )
    }
    val minimizeValues = remember {
        listOf(
            "minimize_on_exit_none_key",
            "minimize_on_exit_background_key",
            "minimize_on_exit_popup_key"
        )
    }

    var minimizeValue by remember {
        mutableStateOf(SharedContext.settingsManager.getString("minimize_on_exit_key", "minimize_on_exit_none_key"))
    }

    val minimizeSummary = remember(minimizeValue, minimizeNone, minimizeBackground, minimizePopup) {
        when (minimizeValue) {
            "minimize_on_exit_none_key" -> minimizeNone
            "minimize_on_exit_background_key" -> minimizeBackground
            "minimize_on_exit_popup_key" -> minimizePopup
            else -> minimizeNone
        }
    }

    val zoomFit = stringResource(MR.strings.zoom_fit)
    val zoomFill = stringResource(MR.strings.zoom_fill)
    val zoomZoom = stringResource(MR.strings.zoom_zoom)

    val zoomEntries = remember(zoomFit, zoomFill, zoomZoom) {
        listOf(
            zoomFit,
            zoomFill,
            zoomZoom
        )
    }
    val zoomValues = remember {
        listOf(
            0,
            3,
            4
        )
    }

    var zoomValue by remember {
        mutableStateOf(SharedContext.settingsManager.getInt("last_resize_mode", 0))
    }

    val zoomSummary = remember(zoomValue, zoomFit, zoomFill, zoomZoom) {
        when (zoomValue) {
            0 -> zoomFit
            3 -> zoomFill
            4 -> zoomZoom
            else -> zoomFit
        }
    }

    val advancedFormatsEntries = remember {
        listOf("VP9", "AV01", "HEVC", "EC-3")
    }
    val advancedFormatsValues = remember {
        listOf("VP9", "AV01", "HEVC", "EC-3")
    }
    val advancedFormatsDefaultValues = remember {
        setOf("VP9", "HEVC")
    }

    val preferenceItems = listOf<PreferenceItem>(
        PreferenceItem.ListPref(
            key = "default_resolution",
            title = stringResource(MR.strings.default_resolution_title),
            summary = null,
            entries = resolutionEntries,
            entryValues = resolutionValues,
            defaultValue = "auto"
        ),
        PreferenceItem.MultiSelectPref(
            key = "advanced_formats_key",
            title = stringResource(MR.strings.advanced_formats_title),
            summary = stringResource(MR.strings.advanced_formats_summary),
            entries = advancedFormatsEntries,
            entryValues = advancedFormatsValues,
            defaultValues = advancedFormatsDefaultValues
        ),
        PreferenceItem.IntListPref(
            key = "last_resize_mode",
            title = stringResource(MR.strings.video_zoom_mode_title),
            summary = stringResource(MR.strings.video_zoom_mode_summary).replace("%s", zoomSummary),
            entries = zoomEntries,
            entryValues = zoomValues,
            defaultValue = 0,
            onValueChange = { value ->
                zoomValue = value
            }
        ),
        PreferenceItem.ListPref(
            key = "autoplay_key",
            title = stringResource(MR.strings.autoplay_title),
            summary = stringResource(MR.strings.autoplay_summary).replace("%s", autoplaySummary),
            entries = autoplayEntries,
            entryValues = autoplayValues,
            defaultValue = "autoplay_never_key",
            onValueChange = { value ->
                autoplayValue = value
            }
        ),
        PreferenceItem.ListPref(
            key = "minimize_on_exit_key",
            title = stringResource(MR.strings.minimize_on_exit_title),
            summary = stringResource(MR.strings.minimize_on_exit_summary).replace("%s", minimizeSummary),
            entries = minimizeEntries,
            entryValues = minimizeValues,
            defaultValue = "minimize_on_exit_none_key",
            onValueChange = { value ->
                minimizeValue = value
            }
        ),
        PreferenceItem.SwitchPref(
            key = "auto_background_play_key",
            title = stringResource(MR.strings.auto_background_play_title),
            summary = stringResource(MR.strings.auto_background_play_summary),
            defaultValue = false,
            onValueChange = { enabled ->
                musicModeEnabled = enabled
            }
        ),
        PreferenceItem.SwitchPref(
            key = "random_music_play_mode_key",
            title = stringResource(MR.strings.settings_player_music_random_title),
            summary = randomSummary,
            enabled = musicModeEnabled,
            defaultValue = false
        ),
        PreferenceItem.SwitchPref(
            key = "playback_skip_silence_key",
            title = stringResource(MR.strings.playback_skip_silence_title),
            summary = stringResource(MR.strings.playback_skip_silence_summary),
            defaultValue = false
        ),
        PreferenceItem.SwitchPref(
            key = "auto_queue_key",
            title = stringResource(MR.strings.auto_queue_title),
            summary = stringResource(MR.strings.auto_queue_summary),
            defaultValue = false,
            onValueChange = { enabled ->
                autoQueueEnabled = enabled
            }
        ),
        PreferenceItem.SwitchPref(
            key = "dont_auto_queue_long_key",
            title = stringResource(MR.strings.dont_auto_queue_long_title),
            summary = stringResource(MR.strings.dont_auto_queue_long_description),
            enabled = autoQueueEnabled,
            defaultValue = true
        ),
    )

    PreferenceScreen(
        title = stringResource(MR.strings.settings_category_player_title),
        items = preferenceItems,
        modifier = modifier
    )
}
