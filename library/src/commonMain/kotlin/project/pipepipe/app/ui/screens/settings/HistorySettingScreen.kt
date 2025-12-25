package project.pipepipe.app.ui.screens.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.ui.screens.PreferenceScreen

@Composable
fun HistorySettingScreen(
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // Dialog states
    var showClearWatchHistoryDialog by remember { mutableStateOf(false) }
    var showClearPlaybackStatesDialog by remember { mutableStateOf(false) }
    var showClearSearchHistoryDialog by remember { mutableStateOf(false) }

    // State for watch_history_mode to control enable_playback_resume
    var watchHistoryMode by remember { mutableStateOf("on_play") }

    val enableWatchHistoryTitle = stringResource(MR.strings.enable_watch_history_title)
    val watchHistoryOnPlay = stringResource(MR.strings.watch_history_on_play)
    val watchHistoryOnClick = stringResource(MR.strings.watch_history_on_click)
    val watchHistoryDisabled = stringResource(MR.strings.watch_history_disabled)
    val enablePlaybackResumeTitle = stringResource(MR.strings.enable_playback_resume_title)
    val enablePlaybackResumeSummary = stringResource(MR.strings.enable_playback_resume_summary)
    val enableSearchHistoryTitle = stringResource(MR.strings.enable_search_history_title)
    val enableSearchHistorySummary = stringResource(MR.strings.enable_search_history_summary)
    val clearDataCategoryTitle = stringResource(MR.strings.settings_category_clear_data_title)
    val clearViewsHistoryTitle = stringResource(MR.strings.clear_views_history_title)
    val clearViewsHistorySummary = stringResource(MR.strings.clear_views_history_summary)
    val clearPlaybackStatesTitle = stringResource(MR.strings.clear_playback_states_title)
    val clearPlaybackStatesSummary = stringResource(MR.strings.clear_playback_states_summary)
    val clearSearchHistoryTitle = stringResource(MR.strings.clear_search_history_title)
    val clearSearchHistorySummary = stringResource(MR.strings.clear_search_history_summary)

    val preferenceItems = remember(watchHistoryMode) {
        listOf(
            // History settings
            PreferenceItem.ListPref(
                key = "watch_history_mode",
                title = enableWatchHistoryTitle,
                entries = listOf(
                    watchHistoryOnPlay,
                    watchHistoryOnClick,
                    watchHistoryDisabled
                ),
                entryValues = listOf("on_play", "on_click", "disabled"),
                defaultValue = "on_play",
                onValueChange = { value ->
                    watchHistoryMode = value
                }
            ),
            PreferenceItem.SwitchPref(
                key = "enable_playback_resume",
                title = enablePlaybackResumeTitle,
                summary = enablePlaybackResumeSummary,
                defaultValue = true,
                enabled = watchHistoryMode != "disabled"
            ),
            PreferenceItem.SwitchPref(
                key = "enable_search_history",
                title = enableSearchHistoryTitle,
                summary = enableSearchHistorySummary,
                defaultValue = true
            ),
            // Clear data category
            PreferenceItem.CategoryPref(
                key = "clear_data_category",
                title = clearDataCategoryTitle
            ),
            PreferenceItem.ClickablePref(
                key = "clear_play_history",
                title = clearViewsHistoryTitle,
                summary = clearViewsHistorySummary,
                onClick = {
                    showClearWatchHistoryDialog = true
                }
            ),
            PreferenceItem.ClickablePref(
                key = "clear_playback_states",
                title = clearPlaybackStatesTitle,
                summary = clearPlaybackStatesSummary,
                onClick = {
                    showClearPlaybackStatesDialog = true
                }
            ),
            PreferenceItem.ClickablePref(
                key = "clear_search_history",
                title = clearSearchHistoryTitle,
                summary = clearSearchHistorySummary,
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

    val watchHistoryDeletedText = stringResource(MR.strings.watch_history_deleted)

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
                            ToastManager.show(watchHistoryDeletedText)
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

    val watchHistoryStatesDeletedText = stringResource(MR.strings.watch_history_states_deleted)

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
                            ToastManager.show(watchHistoryStatesDeletedText)
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

    val searchHistoryDeletedText = stringResource(MR.strings.search_history_deleted)

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
                            ToastManager.show(searchHistoryDeletedText)
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
