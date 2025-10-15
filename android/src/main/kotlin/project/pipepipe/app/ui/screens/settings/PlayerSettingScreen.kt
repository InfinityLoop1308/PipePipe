package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.ListPreference
import project.pipepipe.app.ui.component.SwitchPreference

@Composable
fun PlayerSettingScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var musicModeEnabled by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean("auto_background_play_key", false))
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

    val preferenceItems = listOf<PreferenceItem>(
        PreferenceItem.ListPref(
            key = "default_resolution_key",
            title = stringResource(MR.strings.default_resolution_title),
            summary = null,
            entries = resolutionEntries,
            entryValues = resolutionValues,
            defaultValue = "auto"
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
    )

    Column {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.settings_category_player_title),
            defaultNavigationOnClick = {
                navController.popBackStack()
            }
        )

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = preferenceItems,
                key = { it.key }
            ) { item ->
                when (item) {
                    is PreferenceItem.SwitchPref -> SwitchPreference(item = item)
                    is PreferenceItem.ListPref -> ListPreference(item = item)
                    else -> Unit
                }
            }
        }
    }
}
