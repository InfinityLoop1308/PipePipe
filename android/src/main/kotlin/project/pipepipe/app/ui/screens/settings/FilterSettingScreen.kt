package project.pipepipe.app.ui.screens.settings

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.component.ClickablePreference
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.MultiSelectPreference
import project.pipepipe.app.ui.component.SwitchPreference
import project.pipepipe.app.ui.screens.Screen

@Composable
fun FilterSettingScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val filterTitle = stringResource(MR.strings.settings_category_filter_title)

    val filterByKeywordKey = "filter_by_keyword_key"
    val filterByKeywordTitle = stringResource(MR.strings.filter_by_keyword_title)
    val filterByKeywordSummary = stringResource(MR.strings.filter_by_keyword_summary)

    val filterByChannelKey = "filter_by_channel_key"
    val filterByChannelTitle = stringResource(MR.strings.filter_by_channel_title)
    val filterByChannelSummary = stringResource(MR.strings.filter_by_channel_summary)

    val filterShortsKey = "filter_shorts_key"
    val filterShortsTitle = stringResource(MR.strings.filter_shorts_title)
    val filterShortsSummary = stringResource(MR.strings.filter_shorts_summary)

    val filterPaidKey = "filter_paid_contents_key"
    val filterPaidTitle = stringResource(MR.strings.filter_paid_contents_title)
    val filterPaidSummary = stringResource(MR.strings.filter_paid_contents_summary)

    val filterTypesTitle = stringResource(MR.strings.filter_field_summary)
    val filterTypeEntries = listOf(
        stringResource(MR.strings.search_result),
        stringResource(MR.strings.recommended_videos),
        stringResource(MR.strings.related_items_tab_description),
        stringResource(MR.strings.channel_videos)
    )
    val filterTypeValues = remember {
        listOf(
            "search_result",
            "recommendations",
            "related_item",
            "channels"
        )
    }

    val preferenceItems = remember(
        filterByKeywordKey,
        filterByKeywordTitle,
        filterByKeywordSummary,
        filterByChannelKey,
        filterByChannelTitle,
        filterByChannelSummary,
        filterShortsKey,
        filterShortsTitle,
        filterShortsSummary,
        filterPaidKey,
        filterPaidTitle,
        filterPaidSummary,
        filterTypesTitle,
        filterTypeEntries
    ) {
        listOf(
            PreferenceItem.ClickablePref(
                key = filterByKeywordKey,
                title = filterByKeywordTitle,
                summary = filterByKeywordSummary,
                onClick = {
                    navController.navigate(Screen.FilterKeywordSettings.route)
                }
            ),
            PreferenceItem.ClickablePref(
                key = filterByChannelKey,
                title = filterByChannelTitle,
                summary = filterByChannelSummary,
                onClick = {
                    navController.navigate(Screen.FilterChannelSettings.route)
                }
            ),
            PreferenceItem.SwitchPref(
                key = filterShortsKey,
                title = filterShortsTitle,
                summary = filterShortsSummary,
                defaultValue = false
            ),
            PreferenceItem.SwitchPref(
                key = filterPaidKey,
                title = filterPaidTitle,
                summary = filterPaidSummary,
                defaultValue = false
            ),
            PreferenceItem.MultiSelectPref(
                key = "filter_type_key",
                title = filterTypesTitle,
                entries = filterTypeEntries,
                entryValues = filterTypeValues,
                defaultValues = filterTypeValues.toSet()
            )
        )
    }

    Column {
        CustomTopBar(
            defaultTitleText = filterTitle
        )

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = preferenceItems,
                key = { item -> item.key.ifEmpty { item.title } }
            ) { item ->
                when (item) {
                    is PreferenceItem.ClickablePref -> ClickablePreference(item = item)
                    is PreferenceItem.SwitchPref -> SwitchPreference(item = item)
                    is PreferenceItem.MultiSelectPref -> MultiSelectPreference(item = item)
                    else -> Unit
                }
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterByKeywordsScreen(
    navController: NavController,
    isChannelScreen: Boolean = false
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val key = if (!isChannelScreen)"filter_by_keyword_key_set" else "filter_by_channel_key_set"

    var showAddDialog by remember { mutableStateOf(false) }
    var keywords by remember {
        mutableStateOf(
            SharedContext.settingsManager.getStringSet(key, emptySet()).toList()
        )
    }

    // 搜索相关状态
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // 筛选后的关键词列表
    val filteredKeywords = remember(keywords, searchQuery) {
        if (searchQuery.isBlank()) {
            keywords
        } else {
            keywords.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    // 当进入搜索模式时请求焦点
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    // 返回键处理
    if (isSearchActive) {
        BackHandler {
            focusManager.clearFocus()
            isSearchActive = false
            searchQuery = ""
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!isSearchActive && !isChannelScreen) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(MR.strings.add_filter),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isSearchActive) {
                    if (isSearchActive) {
                        detectTapGestures {
                            focusManager.clearFocus()
                        }
                    }
                }
        ) {
            CustomTopBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = stringResource(MR.strings.search),
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(
                                            includeFontPadding = false
                                        )
                                    ),
                                    fontSize = 16.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent
                            ),
                            textStyle = TextStyle(fontSize = 16.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                        )
                    } else {
                        Text(
                            stringResource(MR.strings.filter_by_keyword_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                },
                titlePadding = 0.dp,
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                if (searchQuery.isEmpty()) {
                                    isSearchActive = false
                                } else {
                                    searchQuery = ""
                                }
                            }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(MR.strings.clear)
                                )
                            }
                        }

                        if (!isSearchActive) {
                            IconButton(
                                onClick = { isSearchActive = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(MR.strings.search)
                                )
                            }
                        }
                    }
                }
            )

            when {
                keywords.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(MR.strings.no_items),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                filteredKeywords.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(MR.strings.playlist_empty_search_result)
                                .format(searchQuery),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = filteredKeywords,
                            key = { it }
                        ) { keyword ->
                            SwipeToDeleteItem(
                                keyword = keyword,
                                onDelete = {
                                    keywords = keywords.filter { it != keyword }
                                    SharedContext.settingsManager.putStringSet(
                                        key,
                                        keywords.toSet()
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 添加关键词对话框
    if (showAddDialog) {
        AddKeywordDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { newKeyword ->
                if (newKeyword.isNotBlank() && !keywords.contains(newKeyword)) {
                    keywords = keywords + newKeyword
                    SharedContext.settingsManager.putStringSet(
                        key,
                        keywords.toSet()
                    )
                }
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeToDeleteItem(
    keyword: String,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(MR.strings.delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Text(
                text = keyword,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .padding(horizontal = 16.dp)
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun AddKeywordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var keywordText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.add_keyword)) },
        text = {
            OutlinedTextField(
                value = keywordText,
                onValueChange = { keywordText = it },
                placeholder = { Text(stringResource(MR.strings.enter_keyword)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(keywordText) }) {
                Text(stringResource(MR.strings.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
            }
        }
    )
}