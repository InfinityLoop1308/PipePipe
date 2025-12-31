package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.helper.StringResourceHelper
import project.pipepipe.app.helper.MainScreenTabDefaults
import project.pipepipe.app.helper.MainScreenTabHelper
import project.pipepipe.app.helper.MainScreenTabHelper.categoryIconFor
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.items.CommonItem
import project.pipepipe.app.ui.screens.SubscriptionRow
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.net.URLEncoder

enum class TabType {
    SELECT, FEED, PLAYLIST, CHANNEL, TRENDING, HISTORY, BLANK
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabCustomizationScreen(
    modifier: Modifier = Modifier
) {
    val settingsManager = SharedContext.settingsManager
    val selectItemText = stringResource(MR.strings.select_item)
    val alreadyExistsText = stringResource(MR.strings.tab_already_exists)
    val cannotRemoveTabText = stringResource(MR.strings.cannot_remove_tab)

    var tabs by remember {
        mutableStateOf(
            try {
                val jsonString = settingsManager.getString("custom_tabs_config_key")
                if (jsonString.isEmpty()) {
                    MainScreenTabDefaults.getDefaultTabs()
                } else {
                    // Try new format first
                    val parsedTabs = try {
                        Json.decodeFromString<List<String>>(jsonString)
                    } catch (e: Exception) {
                        // Try old format (List<{route: String, isDefault: Boolean}>)
                        val jsonElement = Json.parseToJsonElement(jsonString)
                        if (jsonElement is JsonArray && jsonElement.firstOrNull() is JsonObject) {
                            jsonElement.mapNotNull { element ->
                                (element as? JsonObject)?.get("route")?.jsonPrimitive?.content
                            }
                        } else {
                            throw e // Neither format worked, fall through to default
                        }
                    }
                    // Migrate old serviceId format (string -> int)
                    val migratedTabs = parsedTabs.map { route ->
                        route.replace("serviceId=YOUTUBE", "serviceId=0")
                            .replace("serviceId=BILIBILI", "serviceId=5")
                            .replace("serviceId=NICONICO", "serviceId=6")
                    }.let { list ->
                        // Replace legacy "dashboard" with "feed/-1" if feed/-1 doesn't exist
                        if (list.contains("dashboard") && !list.any { it.startsWith("feed/-1") }) {
                            list.map { if (it == "dashboard") "feed/-1" else it }
                        } else {
                            list.filter { it != "dashboard" }
                        }
                    }
                    // Save if migrated
                    val migratedJson = Json.encodeToString(migratedTabs)
                    if (migratedJson != jsonString) {
                        settingsManager.putString("custom_tabs_config_key", migratedJson)
                    }
                    migratedTabs
                }
            } catch (e: Exception) {
                MainScreenTabDefaults.getDefaultTabs()
            }
        )
    }

    // Only need one state
    var showDialog by remember { mutableStateOf<TabType?>(null) }

    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        tabs = tabs.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        saveTabs(tabs, settingsManager)
    }

    Column(modifier = modifier.fillMaxSize()) {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.customize_tabs) // You'll need to add this string resource
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            itemsIndexed(
                items = tabs,
                key = { _, route -> route }
            ) { index, route ->
                ReorderableItem(
                    reorderableLazyListState,
                    key = route
                ) { isDragging ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val dismissState = remember(route) {
                        SwipeToDismissBoxState(
                            initialValue = SwipeToDismissBoxValue.Settled,
                            density = androidx.compose.ui.unit.Density(1f),
                            confirmValueChange = { _ ->
                                if (tabs.size <= 1) {
                                    ToastManager.show(cannotRemoveTabText)
                                    false
                                } else {
                                    tabs = tabs.toMutableList().apply { removeAll { it == route } }
                                    saveTabs(tabs, settingsManager)
                                    true
                                }
                            },
                            positionalThreshold = { it * 0.5f }
                        )
                    }
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    text = stringResource(MR.strings.delete),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true
                    ) {
                        TabItemRow(
                            route = route,
                            isDragging = isDragging,
                            dragHandleModifier = Modifier.draggableHandle(
                                onDragStarted = {},
                                onDragStopped = {},
                                interactionSource = interactionSource
                            )
                        )
                    }
                }
            }
        }

        Button(
            onClick = { showDialog = TabType.SELECT },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(MR.strings.add_tab))
        }
    }

    // Unified dialog handling
    when (showDialog) {
        TabType.SELECT -> SelectTypeDialog(
            onDismiss = { showDialog = null },
            onSelect = { showDialog = it }
        )
        TabType.FEED -> SelectItemDialog(
            title = selectItemText,
            onDismiss = { showDialog = null }
        ) {
            val groups = runBlocking{ DatabaseOperations.getAllFeedGroups() }
            item {
                FeedGroupRow(
                    name = stringResource(MR.strings.all),
                    iconId = 0,
                    onClick = {
                        addTab("feed/-1?iconId=0", tabs, settingsManager, { tabs = it }, alreadyExistsText)
                        showDialog = null
                    }
                )
            }
            items(groups) { g ->
                FeedGroupRow(
                    name = g.name,
                    iconId = g.icon_id.toInt(),
                    onClick = {
                        val encodedName = java.net.URLEncoder.encode(g.name, "UTF-8")
                        addTab("feed/${g.uid}?name=$encodedName&iconId=${g.icon_id.toInt()}", tabs, settingsManager, { tabs = it }, alreadyExistsText)
                        showDialog = null
                    }
                )
            }
        }
        TabType.PLAYLIST -> SelectItemDialog(
            title = selectItemText,
            onDismiss = { showDialog = null }
        ) {
            val playlists = runBlocking{ DatabaseOperations.getAllPlaylistsCombined() }
            items(playlists) { p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    CommonItem(
                        item = p,
                        onClick = {
                            val encodedUrl = URLEncoder.encode(p.url, "UTF-8")
                            val encodedName = URLEncoder.encode(p.name, "UTF-8")
                            var route = "playlist?url=$encodedUrl&name=$encodedName"
                            p.serviceId?.let {
                                route += "&serviceId=${it}"
                            }
                            addTab(route, tabs, settingsManager, { tabs = it }, alreadyExistsText)
                            showDialog = null
                        },
                        shouldUseSecondaryColor = false
                    )
                }
            }
        }
        TabType.CHANNEL -> SelectItemDialog(
            title = selectItemText,
            onDismiss = { showDialog = null }
        ) {
            val channels = runBlocking{ DatabaseOperations.getAllSubscriptions() }
            items(channels) { subscription ->
                SubscriptionRow(
                    subscription = subscription,
                    useHorizontalPadding = false,
                    onClick = {
                        val encodedUrl = URLEncoder.encode(subscription.url ?: "", "UTF-8")
                        val encodedName = URLEncoder.encode(subscription.name ?: "", "UTF-8")
                        val route = "channel?url=${encodedUrl}&serviceId=${subscription.service_id}&name=${encodedName}"
                        addTab(route, tabs, settingsManager, { tabs = it }, alreadyExistsText)
                        showDialog = null
                    }
                )
            }
        }
        TabType.TRENDING -> SelectItemDialog(
            title = selectItemText,
            onDismiss = { showDialog = null }
        ) {
            val trendingItems = try {
                val jsonString = SharedContext.settingsManager.getString("supported_services")
                val serviceInfoList = Json.decodeFromString<List<SupportedServiceInfo>>(jsonString)
                serviceInfoList.flatMap { it.trendingList }.map {
                    Triple(it.serviceId, it.name, it.url)
                }
            } catch (e: Exception) {
                emptyList()
            }
            items(trendingItems) { (serviceId, name, url) ->
                val translatedName = StringResourceHelper.getTranslatedTrendingName(name)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                    color = Color.Transparent
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "$serviceId: $translatedName",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        modifier = Modifier.clickable {
                            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                            val route = "playlist?url=$encodedUrl&name=$name&serviceId=${serviceId}"
                            addTab(route, tabs, settingsManager, { tabs = it }, alreadyExistsText)
                            showDialog = null
                        }
                    )
                }
            }
        }
        TabType.HISTORY -> {
            addTab("history", tabs, settingsManager, { tabs = it }, alreadyExistsText)
            showDialog = null
        }
        TabType.BLANK -> {
            addTab("blank", tabs, settingsManager, { tabs = it }, alreadyExistsText)
            showDialog = null
        }
        null -> {}
    }
}

@Composable
private fun TabItemRow(
    route: String,
    isDragging: Boolean,
    dragHandleModifier: Modifier
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isDragging) 4.dp else 1.dp
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = MainScreenTabHelper.getTabDisplayName(route),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            leadingContent = {
                Icon(
                    imageVector = MainScreenTabHelper.getTabIcon(route),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = dragHandleModifier
                    )
                }
            }
        )
    }
}

@Composable
private fun SelectTypeDialog(onDismiss: () -> Unit, onSelect: (TabType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.add_tab)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TabTypeOption(stringResource(MR.strings.settings_category_feed_title), Icons.Default.Subscriptions) { onSelect(TabType.FEED) }
                TabTypeOption(stringResource(MR.strings.playlists), Icons.Default.Bookmark) { onSelect(TabType.PLAYLIST) }
                TabTypeOption(stringResource(MR.strings.channel), Icons.Default.Person) { onSelect(TabType.CHANNEL) }
                TabTypeOption(stringResource(MR.strings.trending), Icons.Default.Whatshot) { onSelect(TabType.TRENDING) }
                TabTypeOption(stringResource(MR.strings.title_activity_history), Icons.Default.History) { onSelect(TabType.HISTORY) }
                TabTypeOption(stringResource(MR.strings.blank_page), Icons.Default.Tab) { onSelect(TabType.BLANK) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(stringResource(MR.strings.cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectItemDialog(
    title: String,
    onDismiss: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(stringResource(MR.strings.cancel)) } }
    )
}

@Composable
private fun TabTypeOption(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}

private fun addTab(
    route: String,
    tabs: List<String>,
    manager: project.pipepipe.app.helper.SettingsManager,
    updateTabs: (List<String>) -> Unit,
    alreadyExistsText: String
) {
    // Check if route already exists
    if (tabs.contains(route)) {
        ToastManager.show(alreadyExistsText)
        return
    }

    val newTabs = tabs + route
    saveTabs(newTabs, manager)
    updateTabs(newTabs)
}

private fun saveTabs(tabs: List<String>, settingsManager: project.pipepipe.app.helper.SettingsManager) {
    val jsonString = Json.encodeToString(tabs)
    settingsManager.putString("custom_tabs_config_key", jsonString)
}

@Composable
private fun FeedGroupRow(
    name: String,
    iconId: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = Color.Transparent
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingContent = {
                Icon(
                    imageVector = categoryIconFor(iconId),
                    contentDescription = name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}
