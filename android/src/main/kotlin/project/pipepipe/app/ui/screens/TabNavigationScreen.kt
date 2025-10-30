package project.pipepipe.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.MainScreenTabConfig
import project.pipepipe.app.helper.MainScreenTabConfigDefaults
import project.pipepipe.app.helper.MainScreenTabHelper
import project.pipepipe.app.ui.screens.playlistdetail.PlaylistDetailScreen
import project.pipepipe.app.ui.theme.customTopBarColor
import project.pipepipe.app.ui.theme.onCustomTopBarColor


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabNavigationScreen(navController: NavController) {
    val settingsManager = SharedContext.settingsManager

    // Load saved tabs or use defaults
    val tabConfigs by remember {
        mutableStateOf(
            try {
                val jsonString = settingsManager.getString("custom_tabs_config_key")
                if (jsonString.isNotEmpty()) {
                    Json.decodeFromString<List<MainScreenTabConfig>>(jsonString)
                } else {
                    MainScreenTabConfigDefaults.getDefaultTabs()
                }
            } catch (e: Exception) {
                MainScreenTabConfigDefaults.getDefaultTabs()
            }
        )
    }

    val pagerState = rememberPagerState(pageCount = { tabConfigs.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(customTopBarColor())
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(customTopBarColor()),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { navController.navigate(Screen.Settings.route) },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(MR.strings.settings),
                    tint = onCustomTopBarColor()
                )
            }

            Text(
                text = MainScreenTabHelper.getTabDisplayName(tabConfigs[pagerState.currentPage].route),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = onCustomTopBarColor()
            )

            IconButton(onClick = { navController.navigate(Screen.Search.createRoute()) }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(MR.strings.search),
                    tint = onCustomTopBarColor()
                )
            }
        }

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth(),
            containerColor = customTopBarColor()
        ) {
            tabConfigs.forEachIndexed { index, tabConfig ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = MainScreenTabHelper.getTabIcon(tabConfig.route),
                            contentDescription = MainScreenTabHelper.getTabDisplayName(tabConfig.route),
                            modifier = Modifier.size(22.dp),
                            tint = onCustomTopBarColor()
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 3
        ) { page ->
            val route = tabConfigs[page].route
            val baseRoute = route.substringBefore('?')
            when {
                route == "dashboard" -> DashboardScreen(navController = navController)
                route == "subscriptions" -> SubscriptionsScreen(navController = navController)
                route == "bookmarked_playlists" -> BookmarkedPlaylistScreen(navController = navController)
                route == "blank" -> BlankPageScreen()
                route == "history" -> PlaylistDetailScreen(
                    url = "local://history",
                    useAsTab = true,
                    navController = navController
                )
                baseRoute.startsWith("feed/") -> {
                    val feedId = baseRoute.substringAfter("feed/")
                    val params = route.substringAfter('?', "")
                    val nameParam = if (params.isNotEmpty()) {
                        params.split('&').find { it.startsWith("name=") }?.substringAfter("name=")
                    } else null
                    val encodedUrl = if (nameParam != null) {
                        "local://feed/$feedId?name=$nameParam"
                    } else {
                        "local://feed/$feedId"
                    }
                    PlaylistDetailScreen(
                        url = encodedUrl,
                        useAsTab = true,
                        navController = navController
                    )
                }
                baseRoute == "playlist" ||  route.startsWith("trending://")  -> {
                    val params = route.substringAfter('?', "")
                    val urlParam = params.split('&').find { it.startsWith("url=") }?.substringAfter("url=")
                    val serviceIdParam = params.split('&').find { it.startsWith("serviceId=") }?.substringAfter("serviceId=")
                    if (urlParam != null) {
                        PlaylistDetailScreen(
                            url = java.net.URLDecoder.decode(urlParam, "UTF-8"),
                            useAsTab = true,
                            navController = navController,
                            serviceId = serviceIdParam
                        )
                    }
                }
                baseRoute == "channel" -> {
                    val params = route.substringAfter('?', "")
                    val urlParam = params.split('&').find { it.startsWith("url=") }?.substringAfter("url=")
                    val serviceIdParam = params.split('&').find { it.startsWith("serviceId=") }?.substringAfter("serviceId=")
                    if (urlParam != null && serviceIdParam != null) {
                        ChannelScreen(
                            navController = navController,
                            channelUrl = java.net.URLDecoder.decode(urlParam, "UTF-8"),
                            serviceId = serviceIdParam,
                            useAsTab = true
                        )
                    }
                }
            }
        }
    }
}