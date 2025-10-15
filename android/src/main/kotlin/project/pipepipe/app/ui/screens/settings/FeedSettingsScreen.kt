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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.ListPreference

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
        )
    )

    Column {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.settings_category_feed_title),
            defaultNavigationOnClick = {
                navController.popBackStack()
            }
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
                    else -> Unit
                }
            }
        }
    }
}
