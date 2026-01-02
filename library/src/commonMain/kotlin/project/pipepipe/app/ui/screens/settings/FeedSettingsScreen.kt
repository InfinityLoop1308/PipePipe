package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.ui.component.ClickablePreference
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.ListPreference
import project.pipepipe.app.ui.component.MultiSelectPreference
import project.pipepipe.app.ui.component.SwitchPreference
import project.pipepipe.app.ui.screens.Screen

@Composable
fun FeedSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val alwaysUpdate = stringResource(MR.strings.feed_update_threshold_option_always_update)

    val feedUpdateThresholdEntries = remember(alwaysUpdate) {
        listOf(
            alwaysUpdate,
            "5 minutes",
            "15 minutes",
            "1 hour",
            "6 hours",
            "12 hours",
            "1 day"
        )
    }

    val feedUpdateThresholdValues = remember {
        listOf(
            "0",
            "300",
            "900",
            "3600",
            "21600",
            "43200",
            "86400"
        )
    }

    var feedUpdateThresholdValue by remember {
        mutableStateOf(SharedContext.settingsManager.getString("feed_update_threshold_key", "300"))
    }

    val feedUpdateThresholdSummary = remember(feedUpdateThresholdValue, alwaysUpdate) {
        val index = feedUpdateThresholdValues.indexOf(feedUpdateThresholdValue)
        if (index >= 0 && index < feedUpdateThresholdEntries.size) {
            feedUpdateThresholdEntries[index]
        } else {
            "5 minutes"
        }
    }

    // Channel tabs to fetch when updating the feed
    val feedFetchChannelTabsEntries = listOf(
        stringResource(MR.strings.channel_tab_videos),
        stringResource(MR.strings.channel_tab_tracks),
        stringResource(MR.strings.channel_tab_shorts),
        stringResource(MR.strings.channel_tab_livestreams)
    )

    val feedFetchChannelTabsValues = listOf(
        "fetch_channel_tabs_videos",
        "fetch_channel_tabs_tracks",
        "fetch_channel_tabs_shorts",
        "fetch_channel_tabs_live"
    )

    var feedFetchChannelTabsValue by remember {
        mutableStateOf(
            SharedContext.settingsManager.getStringSet("feed_fetch_channel_tabs_key", feedFetchChannelTabsValues.toSet())
        )
    }

    val feedFetchChannelTabsSummary = remember(feedFetchChannelTabsValue, feedFetchChannelTabsEntries, feedFetchChannelTabsValues) {
        val selectedEntries = feedFetchChannelTabsValues.mapIndexedNotNull { index, value ->
            if (feedFetchChannelTabsValue.contains(value)) feedFetchChannelTabsEntries.getOrNull(index) else null
        }
        selectedEntries.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    // New streams notifications settings
    var enableStreamsNotifications by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean("enable_streams_notifications", false))
    }

    val streamsNotificationsIntervalEntries = remember {
        listOf(
            "15 minutes",
            "30 minutes",
            "1 hour",
            "2 hours",
            "4 hours",
            "12 hours",
            "1 day"
        )
    }

    val streamsNotificationsIntervalValues = remember {
        listOf(
            "900",
            "1800",
            "3600",
            "7200",
            "14400",
            "43200",
            "86400"
        )
    }

    var streamsNotificationsIntervalValue by remember {
        mutableStateOf(SharedContext.settingsManager.getString("streams_notifications_interval_key", "14400"))
    }

    val streamsNotificationsIntervalSummary = remember(streamsNotificationsIntervalValue) {
        val index = streamsNotificationsIntervalValues.indexOf(streamsNotificationsIntervalValue)
        if (index >= 0 && index < streamsNotificationsIntervalEntries.size) {
            streamsNotificationsIntervalEntries[index]
        } else {
            "4 hours"
        }
    }

    // Channel count for notification channels preference
    var totalChannels by remember { mutableStateOf(0) }
    var notificationChannels by remember { mutableStateOf(0) }

    // Function to load channel counts
    fun loadChannelCounts() {
        GlobalScope.launch {
            val subscriptions = DatabaseOperations.getAllSubscriptions()
            totalChannels = subscriptions.size
            notificationChannels = subscriptions.count { it.notification_mode == 1L }
        }
    }

    // Load channel counts on initial composition
    LaunchedEffect(Unit) {
        loadChannelCounts()
    }

    val channelsSummary = remember(notificationChannels, totalChannels) {
        "$notificationChannels / $totalChannels"
    }

    val preferenceItems = listOf(
        PreferenceItem.ListPref(
            key = "feed_update_threshold_key",
            title = stringResource(MR.strings.feed_update_threshold_title),
            summary = stringResource(MR.strings.feed_update_threshold_summary).replace("%s", feedUpdateThresholdSummary),
            entries = feedUpdateThresholdEntries,
            entryValues = feedUpdateThresholdValues,
            defaultValue = "300",
            onValueChange = { value ->
                feedUpdateThresholdValue = value
            }
        ),
        PreferenceItem.MultiSelectPref(
            key = "feed_fetch_channel_tabs_key",
            title = stringResource(MR.strings.feed_fetch_channel_tabs),
            summary = stringResource(MR.strings.feed_fetch_channel_tabs_summary),
            entries = feedFetchChannelTabsEntries,
            entryValues = feedFetchChannelTabsValues,
            defaultValues = feedFetchChannelTabsValues.toSet(),
            onValuesChange = { values ->
                feedFetchChannelTabsValue = values
            }
        ),
        PreferenceItem.SwitchPref(
            key = "enable_streams_notifications",
            title = stringResource(MR.strings.enable_streams_notifications_title),
            summary = stringResource(MR.strings.enable_streams_notifications_summary),
            defaultValue = false,
            onValueChange = { value ->
                enableStreamsNotifications = value
                // Reschedule periodic work when setting changes
                SharedContext.platformActions.scheduleNotificationsWork()
            }
        ),
        PreferenceItem.ListPref(
            key = "streams_notifications_interval_key",
            title = stringResource(MR.strings.streams_notifications_interval_title),
            summary = streamsNotificationsIntervalSummary,
            entries = streamsNotificationsIntervalEntries,
            entryValues = streamsNotificationsIntervalValues,
            defaultValue = "14400",
            enabled = enableStreamsNotifications,
            onValueChange = { value ->
                streamsNotificationsIntervalValue = value
                // Reschedule periodic work when interval changes
                SharedContext.platformActions.scheduleNotificationsWork()
            }
        ),
        PreferenceItem.ClickablePref(
            key = "streams_notifications_channels_key",
            title = stringResource(MR.strings.channels),
            summary = channelsSummary,
            enabled = enableStreamsNotifications,
            onClick = {
                navController.navigate(Screen.ChannelNotificationSelection.route)
            }
        )
    )

    Column {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.settings_category_feed_title)
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
                    is PreferenceItem.ListPref -> ListPreference(item = item)
                    is PreferenceItem.MultiSelectPref -> MultiSelectPreference(item = item)
                    is PreferenceItem.SwitchPref -> SwitchPreference(item = item)
                    is PreferenceItem.ClickablePref -> ClickablePreference(item = item)
                    else -> Unit
                }
            }
        }
    }
}
