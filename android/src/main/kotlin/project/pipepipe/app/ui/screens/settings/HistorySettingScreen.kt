package project.pipepipe.app.ui.screens.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.ui.screens.PreferenceScreen

@Composable
fun HistorySettingScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Dialog states
    var showClearWatchHistoryDialog by remember { mutableStateOf(false) }
    var showClearPlaybackStatesDialog by remember { mutableStateOf(false) }
    var showClearSearchHistoryDialog by remember { mutableStateOf(false) }

    // State for watch_history_mode to control enable_playback_resume
    var watchHistoryMode by remember { mutableStateOf("on_play") }

    val preferenceItems = remember(watchHistoryMode) {
        listOf(
            // History settings
            PreferenceItem.ListPref(
                key = "watch_history_mode",
                title = MR.strings.enable_watch_history_title.desc().toString(context),
                entries = listOf(
                    MR.strings.watch_history_on_play.desc().toString(context),
                    MR.strings.watch_history_on_click.desc().toString(context),
                    MR.strings.watch_history_disabled.desc().toString(context)
                ),
                entryValues = listOf("on_play", "on_click", "disabled"),
                defaultValue = "on_play",
                onValueChange = { value ->
                    watchHistoryMode = value
                }
            ),
            PreferenceItem.SwitchPref(
                key = "enable_playback_resume",
                title = MR.strings.enable_playback_resume_title.desc().toString(context),
                summary = MR.strings.enable_playback_resume_summary.desc().toString(context),
                defaultValue = true,
                enabled = watchHistoryMode != "disabled"
            ),
            PreferenceItem.SwitchPref(
                key = "enable_search_history",
                title = MR.strings.enable_search_history_title.desc().toString(context),
                summary = MR.strings.enable_search_history_summary.desc().toString(context),
                defaultValue = true
            ),
            // Clear data category
            PreferenceItem.CategoryPref(
                key = "clear_data_category",
                title = MR.strings.settings_category_clear_data_title.desc().toString(context)
            ),
            PreferenceItem.ClickablePref(
                key = "clear_play_history",
                title = MR.strings.clear_views_history_title.desc().toString(context),
                summary = MR.strings.clear_views_history_summary.desc().toString(context),
                onClick = {
                    showClearWatchHistoryDialog = true
                }
            ),
            PreferenceItem.ClickablePref(
                key = "clear_playback_states",
                title = MR.strings.clear_playback_states_title.desc().toString(context),
                summary = MR.strings.clear_playback_states_summary.desc().toString(context),
                onClick = {
                    showClearPlaybackStatesDialog = true
                }
            ),
            PreferenceItem.ClickablePref(
                key = "clear_search_history",
                title = MR.strings.clear_search_history_title.desc().toString(context),
                summary = MR.strings.clear_search_history_summary.desc().toString(context),
                onClick = {
                    showClearSearchHistoryDialog = true
                }
            )
        )
    }

    PreferenceScreen(
        title = stringResource(MR.strings.title_activity_history),
        items = preferenceItems,
        modifier = modifier
    )

    // Clear watch history dialog
    if (showClearWatchHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearWatchHistoryDialog = false },
            title = { Text(stringResource(MR.strings.clear_views_history_title)) },
            text = { Text(stringResource(MR.strings.delete_view_history_alert)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            DatabaseOperations.clearAllStreamHistory()
                            ToastManager.show(MR.strings.watch_history_deleted.desc().toString(context))
                        }
                        showClearWatchHistoryDialog = false
                    }
                ) {
                    Text(stringResource(MR.strings.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearWatchHistoryDialog = false }) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }

    // Clear playback states dialog
    if (showClearPlaybackStatesDialog) {
        AlertDialog(
            onDismissRequest = { showClearPlaybackStatesDialog = false },
            title = { Text(stringResource(MR.strings.clear_playback_states_title)) },
            text = { Text(stringResource(MR.strings.delete_playback_states_alert)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            DatabaseOperations.clearAllPlaybackStates()
                            ToastManager.show(MR.strings.watch_history_states_deleted.desc().toString(context))
                        }
                        showClearPlaybackStatesDialog = false
                    }
                ) {
                    Text(stringResource(MR.strings.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPlaybackStatesDialog = false }) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }

    // Clear search history dialog
    if (showClearSearchHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearSearchHistoryDialog = false },
            title = { Text(stringResource(MR.strings.clear_search_history_title)) },
            text = { Text(stringResource(MR.strings.delete_search_history_alert_all)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            DatabaseOperations.clearAllSearchHistory()
                            ToastManager.show(MR.strings.search_history_deleted.desc().toString(context))
                        }
                        showClearSearchHistoryDialog = false
                    }
                ) {
                    Text(stringResource(MR.strings.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSearchHistoryDialog = false }) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }
}
