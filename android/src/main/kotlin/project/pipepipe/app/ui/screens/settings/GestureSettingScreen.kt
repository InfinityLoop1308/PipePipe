package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.MR
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.ListPreference
import project.pipepipe.app.ui.component.SwitchPreference

@Composable
fun GestureSettingScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val secondsLabel = MR.strings.common_seconds.desc().toString(context)
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

    val preferenceItems = remember(secondsLabel, seekDurationEntries) {
        listOf(
            PreferenceItem.SwitchPref(
                key = "volume_gesture_control_key",
                title = MR.strings.volume_gesture_control_title.desc().toString(context),
                summary = MR.strings.settings_gesture_volume_summary.desc().toString(context),
                defaultValue = true
            ),
            PreferenceItem.SwitchPref(
                key = "brightness_gesture_control_key",
                title = MR.strings.brightness_gesture_control_title.desc().toString(context),
                summary = MR.strings.settings_gesture_brightness_summary.desc().toString(context),
                defaultValue = true
            ),
            PreferenceItem.SwitchPref(
                key = "fullscreen_gesture_control_key",
                title = MR.strings.fullscreen_gesture_control_title.desc().toString(context),
                summary = MR.strings.settings_gesture_fullscreen_summary.desc().toString(context),
                defaultValue = true
            ),
            PreferenceItem.SwitchPref(
                key = "swipe_seek_gesture_control_key",
                title = MR.strings.settings_gesture_swipe_seek_title.desc().toString(context),
                summary = MR.strings.settings_gesture_swipe_seek_summary.desc().toString(context),
                defaultValue = true
            ),
            PreferenceItem.ListPref(
                key = "seek_duration_key",
                title = MR.strings.settings_gesture_seek_duration_title.desc().toString(context),
                summary = null,
                entries = seekDurationEntries,
                entryValues = seekDurationValues,
                defaultValue = "15000"
            ),
            PreferenceItem.ListPref(
                key = "speeding_playback_key",
                title = MR.strings.settings_gesture_playback_speed_title.desc().toString(context),
                summary = null,
                entries = speedingPlaybackEntries,
                entryValues = speedingPlaybackValues,
                defaultValue = "1"
            )
        )
    }
    Column {
        CustomTopBar(
            defaultTitleText = MR.strings.settings_section_gesture.desc().toString(context)
        )

        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = preferenceItems,
                key = { it.key }
            ) { item ->
                when (item) {
                    is PreferenceItem.SwitchPref -> {
                        SwitchPreference(
                            item = item,
                        )
                    }

                    is PreferenceItem.ListPref -> {
                        ListPreference(
                            item = item,
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}
