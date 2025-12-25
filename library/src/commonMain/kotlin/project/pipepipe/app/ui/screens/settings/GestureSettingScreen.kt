package project.pipepipe.app.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.ui.screens.PreferenceScreen

@Composable
fun GestureSettingScreen(
    modifier: Modifier = Modifier
) {
    val secondsLabel = stringResource(MR.strings.common_seconds)
    val seekDurationEntries = remember(secondsLabel) {
        listOf(
            "5 $secondsLabel",
            "10 $secondsLabel",
            "15 $secondsLabel",
            "20 $secondsLabel",
            "25 $secondsLabel",
            "30 $secondsLabel"
        )
    }
    val seekDurationValues = remember {
        listOf(
            "5000",
            "10000",
            "15000",
            "20000",
            "25000",
            "30000"
        )
    }
    val disableLabel = stringResource(MR.strings.disabled)
    val speedingPlaybackEntries = remember(disableLabel) {
        listOf(
            "0.1x",
            "0.3x",
            "0.5x",
            "0.75x",
            disableLabel,
            "1.25x",
            "1.5x",
            "1.75x",
            "2x",
            "2.25x",
            "2.5x",
            "3x",
            "5x"
        )
    }
    val speedingPlaybackValues = remember {
        listOf(
            "0.1",
            "0.3",
            "0.5",
            "0.75",
            "1",
            "1.25",
            "1.5",
            "1.75",
            "2",
            "2.25",
            "2.5",
            "3",
            "5"
        )
    }

    val volumeGestureTitle = stringResource(MR.strings.volume_gesture_control_title)
    val volumeGestureSummary = stringResource(MR.strings.settings_gesture_volume_summary)
    val brightnessGestureTitle = stringResource(MR.strings.brightness_gesture_control_title)
    val brightnessGestureSummary = stringResource(MR.strings.settings_gesture_brightness_summary)
    val fullscreenGestureTitle = stringResource(MR.strings.fullscreen_gesture_control_title)
    val fullscreenGestureSummary = stringResource(MR.strings.settings_gesture_fullscreen_summary)
    val swipeSeekTitle = stringResource(MR.strings.settings_gesture_swipe_seek_title)
    val swipeSeekSummary = stringResource(MR.strings.settings_gesture_swipe_seek_summary)
    val seekDurationTitle = stringResource(MR.strings.settings_gesture_seek_duration_title)
    val playbackSpeedTitle = stringResource(MR.strings.settings_gesture_playback_speed_title)

    val preferenceItems = remember(secondsLabel, seekDurationEntries) {
        listOf(
            PreferenceItem.SwitchPref(
                key = "volume_gesture_control_key",
                title = volumeGestureTitle,
                summary = volumeGestureSummary,
                defaultValue = true
            ),
            PreferenceItem.SwitchPref(
                key = "brightness_gesture_control_key",
                title = brightnessGestureTitle,
                summary = brightnessGestureSummary,
                defaultValue = true
            ),
            PreferenceItem.SwitchPref(
                key = "fullscreen_gesture_control_key",
                title = fullscreenGestureTitle,
                summary = fullscreenGestureSummary,
                defaultValue = true
            ),
            PreferenceItem.SwitchPref(
                key = "swipe_seek_gesture_control_key",
                title = swipeSeekTitle,
                summary = swipeSeekSummary,
                defaultValue = true
            ),
            PreferenceItem.ListPref(
                key = "seek_duration_key",
                title = seekDurationTitle,
                summary = null,
                entries = seekDurationEntries,
                entryValues = seekDurationValues,
                defaultValue = "15000"
            ),
            PreferenceItem.ListPref(
                key = "speeding_playback_key",
                title = playbackSpeedTitle,
                summary = null,
                entries = speedingPlaybackEntries,
                entryValues = speedingPlaybackValues,
                defaultValue = "1"
            )
        )
    }

    PreferenceScreen(
        title = stringResource(MR.strings.settings_section_gesture),
        items = preferenceItems,
        modifier = modifier
    )
}
