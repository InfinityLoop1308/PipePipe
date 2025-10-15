package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import project.pipepipe.app.MR
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.database.Subscriptions

@Composable
fun ChannelNotificationSelectionScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var subscriptions by remember { mutableStateOf<List<Subscriptions>>(emptyList()) }
    var selectedChannels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load subscriptions from database
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val subs = DatabaseOperations.getAllSubscriptions()
                subscriptions = subs
                // Get channels with notification_mode == 1
                selectedChannels = subs
                    .filter { it.notification_mode == 1L }
                    .mapNotNull { it.url }
                    .toSet()
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.channels),
            defaultNavigationOnClick = {
                navController.popBackStack()
            },
            actions = {
                IconButton(
                    onClick = {
                        val allSelected = subscriptions.all {
                            it.url?.let { url -> selectedChannels.contains(url) } ?: false
                        }

                        if (allSelected) {
                            // Unselect all
                            selectedChannels = emptySet()
                            coroutineScope.launch {
                                subscriptions.forEach { subscription ->
                                    subscription.url?.let { url ->
                                        DatabaseOperations.updateSubscriptionNotificationMode(
                                            url = url,
                                            notificationMode = 0L
                                        )
                                    }
                                }
                            }
                        } else {
                            // Select all
                            selectedChannels = subscriptions.mapNotNull { it.url }.toSet()
                            coroutineScope.launch {
                                subscriptions.forEach { subscription ->
                                    subscription.url?.let { url ->
                                        DatabaseOperations.updateSubscriptionNotificationMode(
                                            url = url,
                                            notificationMode = 1L
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    val allSelected = subscriptions.all {
                        it.url?.let { url -> selectedChannels.contains(url) } ?: false
                    }
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = "Select all"
                    )
                }
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (subscriptions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(MR.strings.no_subscriptions_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    items = subscriptions,
                    key = { it.uid }
                ) { subscription ->
                    val isSelected = subscription.url?.let { selectedChannels.contains(it) } ?: false

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                subscription.url?.let { url ->
                                    val newNotificationMode = if (isSelected) 0L else 1L

                                    // Update local state
                                    selectedChannels = if (isSelected) {
                                        selectedChannels - url
                                    } else {
                                        selectedChannels + url
                                    }

                                    // Update database
                                    coroutineScope.launch {
                                        DatabaseOperations.updateSubscriptionNotificationMode(
                                            url = url,
                                            notificationMode = newNotificationMode
                                        )
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subscription.name ?: stringResource(MR.strings.bottom_sheet_unknown_channel),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null
                        )
                    }
                }
            }
        }
    }
}
