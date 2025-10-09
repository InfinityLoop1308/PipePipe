package project.pipepipe.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import project.pipepipe.shared.database.DatabaseOperations
import project.pipepipe.shared.infoitem.ChannelInfo

@Composable
fun FeedGroupSelectionDialog(
    channelInfo: ChannelInfo,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit
) {
    var feedGroups by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var selectedGroups by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(channelInfo.url) {
        scope.launch {
            val groups = DatabaseOperations.getAllFeedGroups()
                .map { it.uid to (it.name ?: "Unnamed Group") }
            feedGroups = groups

            // Load current selections
            val subscription = DatabaseOperations.getSubscriptionByUrl(channelInfo.url)
            if (subscription != null) {
                selectedGroups = DatabaseOperations.getFeedGroupsBySubscription(subscription.uid).toSet()
            }
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Feed Groups") },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(feedGroups) { (groupId, groupName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = groupId in selectedGroups,
                                onCheckedChange = { checked ->
                                    selectedGroups = if (checked) {
                                        selectedGroups + groupId
                                    } else {
                                        selectedGroups - groupId
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = groupName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedGroups)
                    onDismiss()
                },
                enabled = !isLoading
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
