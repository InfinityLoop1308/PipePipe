package project.pipepipe.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import project.pipepipe.app.MR
import project.pipepipe.app.dataStore
import project.pipepipe.app.global.StringResourceHelper
import project.pipepipe.app.SharedContext
import project.pipepipe.app.Utils.generateQueryUrl
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.shared.infoitem.SupportedServiceInfo
import project.pipepipe.shared.infoitem.helper.SearchFilterItem
import project.pipepipe.shared.infoitem.helper.SearchType
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.ErrorComponent
import project.pipepipe.app.ui.component.player.SponsorBlockUtils
import project.pipepipe.app.ui.item.MediaListItem
import project.pipepipe.app.ui.theme.getContrastingColor
import project.pipepipe.app.ui.viewmodel.SearchViewModel
import project.pipepipe.shared.infoitem.url
import project.pipepipe.shared.infoitem.serviceId

private val SELECTED_SERVICE_KEY = stringPreferencesKey("selected_service")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController) {
    var showFilterDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val viewModel: SearchViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Local UI state for search suggestions
    var isSearchFieldFocused by remember { mutableStateOf(false) }

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    val uniqueItems = remember(uiState.list.itemList) {
        uiState.list.itemList.distinctBy { it.url }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.searchQuery) {
        if (uiState.searchQuery != textFieldValue.text) {
            textFieldValue = TextFieldValue(
                text = uiState.searchQuery,
                selection = TextRange(uiState.searchQuery.length) // 保证光标在末尾
            )
        }
    }


    val context = LocalContext.current
    val serviceInfoList = runBlocking {
        Json.decodeFromString<List<SupportedServiceInfo>>(context.dataStore.data.first()[stringPreferencesKey("supported_services")]!!)
    }

    LaunchedEffect(Unit) {
        if (uiState.selectedService == null && serviceInfoList.isNotEmpty()) { //todo: a page to show when serviceInfoList is empty
            val savedServiceId = context.dataStore.data.first()[SELECTED_SERVICE_KEY]
            val savedService = savedServiceId?.let { serviceId ->
                serviceInfoList.find { it.serviceId == serviceId }
            }
            val serviceToUse = savedService ?: serviceInfoList.first()
            viewModel.updateSelectedService(serviceToUse)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.updateSearchQuery("")
    }


    fun performSearch(query: String? = null) {
        val searchText = query ?: textFieldValue.text
        if (searchText.isNotEmpty()) {
            val searchUrl = generateQueryUrl(searchText, uiState.selectedSearchType!!)
            viewModel.search(searchUrl, listState)
            focusManager.clearFocus()
            GlobalScope.launch {
                DatabaseOperations.insertOrUpdateSearchHistory(searchText)
            }
        }
    }


    LaunchedEffect(listState, uiState.list.nextPageUrl, uiState.common.isLoading) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible to layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalCount) ->
                val hasNextPage = uiState.list.nextPageUrl != null
                val canLoadMore = !uiState.common.isLoading && hasNextPage
                val atBottom = totalCount > 0 && lastVisible == totalCount - 1

                if (canLoadMore && atBottom) {
                    val serviceId = uniqueItems.firstOrNull()?.serviceId ?: return@collect
                    viewModel.loadMoreResults(serviceId)
                }
            }
    }

    val themeSearchBarColor = SponsorBlockUtils.parseHexColor(uiState.selectedService?.themeColor)
    val onThemeSearchBarColor = getContrastingColor(themeSearchBarColor)
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isSearchFieldFocused) {
            BackHandler {
                focusManager.clearFocus()
            }
        }
        CustomTopBar(
            title = {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        viewModel.updateSearchQuery(newValue.text)
                    },
                    placeholder = { Text(text = stringResource(MR.strings.search), style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    ), fontSize = 16.sp, color = onThemeSearchBarColor) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isSearchFieldFocused = focusState.isFocused
                        },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        focusedTextColor = onThemeSearchBarColor,
                        unfocusedTextColor = onThemeSearchBarColor
                    ),
                    textStyle = TextStyle(fontSize = 16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        performSearch()
                    })
                )
            },
            titlePadding = 0.dp,
            defaultNavigationOnClick = { navController.popBackStack() },
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(MR.strings.clear), tint = onThemeSearchBarColor)
                        }
                    }
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(MR.strings.filter), tint = onThemeSearchBarColor)
                    }
                    IconButton(onClick = {
                        performSearch()
                    }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(MR.strings.search), tint = onThemeSearchBarColor)
                    }
                }
            },
            backgroundColor = themeSearchBarColor,
            onBackgroundColor = onThemeSearchBarColor
        )


        Box(modifier = Modifier.fillMaxWidth()) {
            if (uiState.common.error != null) {
                ErrorComponent(
                    error = uiState.common.error!!,
                    onRetry = {
                        val searchText = textFieldValue.text
                        if (searchText.isNotEmpty()) {
                            val searchUrl = generateQueryUrl(searchText, uiState.selectedSearchType!!)
                            viewModel.search(searchUrl, listState)
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    state = listState
                ) {
                    if (uiState.common.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    itemsIndexed(
                        items = uniqueItems,
                        key = {_, item -> item.url }
                    ) { index, item ->
                        MediaListItem(
                            item = item,
                            onClick = {
                                SharedContext.sharedVideoDetailViewModel.loadVideoDetails(item.url, item.serviceId)
                            }
                        )
                        if (index < uniqueItems.lastIndex) {
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    if (uiState.common.isLoading && uiState.list.nextPageUrl != null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                }
            }
            if (isSearchFieldFocused) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    items(uiState.searchSuggestionList) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clickable {
                                    viewModel.updateSearchQuery(suggestion.text)
                                    performSearch(suggestion.text)
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (suggestion.isLocal)Icons.Default.History else Icons.Default.Search,
                                contentDescription = if (suggestion.isLocal) stringResource(MR.strings.title_activity_history) else stringResource(MR.strings.search),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = suggestion.text,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                                fontSize = 13.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                Icons.Default.NorthWest,
                                contentDescription = stringResource(MR.strings.fill_search),
                                modifier = Modifier
                                    .size(21.dp)
                                    .clickable {
                                        viewModel.updateSearchQuery(suggestion.text)
                                        focusRequester.requestFocus()
                                    },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        FilterCard(
            serviceInfoList = serviceInfoList,
            selectedServiceId = uiState.selectedService!!.serviceId,
            selectedSearchType = uiState.selectedSearchType,
            searchQuery = uiState.searchQuery,
            onServiceChange = { serviceId ->
                val service = serviceInfoList.find { it.serviceId == serviceId }
                service?.let {
                    viewModel.updateSelectedService(it)
                    GlobalScope.launch {
                        context.dataStore.edit { preferences ->
                            preferences[SELECTED_SERVICE_KEY] = serviceId
                        }
                    }
                }
            },
            onSearchTypeChange = { searchType ->
                viewModel.updateSelectedSearchType(searchType)
            },
            onFilterToggle = { groupName, filter ->
                viewModel.toggleSearchFilter(groupName, filter)
            },
            onReset = {
                viewModel.resetFilters()
            },
            onSearch = {
                performSearch()
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FilterCard(
    serviceInfoList: List<SupportedServiceInfo>,
    selectedServiceId: String,
    selectedSearchType: SearchType?,
    searchQuery: String,
    onServiceChange: (String) -> Unit,
    onSearchTypeChange: (SearchType) -> Unit,
    onFilterToggle: (String, SearchFilterItem) -> Unit,
    onReset: () -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                IntegratedServiceHeader(
                    serviceInfoList = serviceInfoList,
                    selectedServiceId = selectedServiceId,
                    onServiceChange = onServiceChange
                )

                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    AnimatedContent(
                        targetState = selectedServiceId,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith
                                    fadeOut(animationSpec = tween(300))
                        },
                        label = "filter_content"
                    ) { serviceId ->
                        val selectedService = serviceInfoList.find { it.serviceId == serviceId }
                        selectedService?.let { service ->
                            FilterContent(
                                service = service,
                                selectedSearchType = selectedSearchType,
                                onSearchTypeChange = onSearchTypeChange,
                                onFilterToggle = onFilterToggle
                            )
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = onReset) {
                        Text(stringResource(MR.strings.playback_reset))
                    }
                    FilledTonalButton(onClick = {
                        if (searchQuery.isNotEmpty()) {
                            onSearch()
                        }
                        onDismiss()
                    }) {
                        Text(if (searchQuery.isEmpty()) stringResource(MR.strings.recaptcha_done_button) else stringResource(MR.strings.perform_search))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegratedServiceHeader(
    serviceInfoList: List<SupportedServiceInfo>,
    selectedServiceId: String,
    onServiceChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedService = serviceInfoList.find { it.serviceId == selectedServiceId }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = (if (serviceInfoList.size > 1)28 else 0).dp)
                    ) {
                        Text(
                            text = selectedService?.serviceId ?: stringResource(MR.strings.select_service),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (serviceInfoList.size > 1) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .rotate(if (expanded) 180f else 0f)
                                    .width(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                serviceInfoList.forEach { service ->
                    DropdownMenuItem(
                        text = {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(service.serviceId)
                            }
                        },
                        onClick = {
                            onServiceChange(service.serviceId)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun FilterContent(
    service: SupportedServiceInfo,
    selectedSearchType: SearchType?,
    onSearchTypeChange: (SearchType) -> Unit,
    onFilterToggle: (String, SearchFilterItem) -> Unit
) {
    val initialExpandedGroups = remember(selectedSearchType) {
        selectedSearchType?.availableSearchFilterGroups
            ?.filter { group ->
                group.selectedSearchFilters.any { selectedFilter ->
                    selectedFilter.parameter != group.defaultFilter?.parameter
                }
            }
            ?.map { it.groupName }
            ?.toSet() ?: emptySet()
    }

    var expandedGroups by remember(selectedSearchType) {
        mutableStateOf(initialExpandedGroups)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Search Type Selection
        service.availableSearchTypes?.let { searchTypes ->
            Text(
                text = stringResource(MR.strings.search_type),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            searchTypes.forEach { searchType ->
                val isSelected = selectedSearchType?.name == searchType.name

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSearchTypeChange(searchType) },
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSearchTypeChange(searchType) },
                            modifier = Modifier.padding(0.dp)
                        )
                        Text(
                            text = StringResourceHelper.getTranslatedFilterString(searchType.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        selectedSearchType?.availableSearchFilterGroups?.forEach { filterGroup ->
            val isExpanded = expandedGroups.contains(filterGroup.groupName)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    expandedGroups = if (isExpanded) {
                        expandedGroups - filterGroup.groupName
                    } else {
                        expandedGroups + filterGroup.groupName
                    }
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = StringResourceHelper.getTranslatedFilterString(filterGroup.groupName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (isExpanded) stringResource(MR.strings.collapse) else stringResource(MR.strings.expand),
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    val itemsPerRow = 2
                    val chunkedItems = filterGroup.availableSearchFilterItems.chunked(itemsPerRow)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        chunkedItems.forEach { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { filterItem ->
                                    val isSelected = filterGroup.selectedSearchFilters.any {
                                        it.parameter == filterItem.parameter
                                    }

                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            onFilterToggle(filterGroup.groupName, filterItem)
                                        },
                                        label = {
                                            Text(
                                                text = StringResourceHelper.getTranslatedFilterString(filterItem.name),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Add spacers for incomplete rows
                                repeat(itemsPerRow - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}