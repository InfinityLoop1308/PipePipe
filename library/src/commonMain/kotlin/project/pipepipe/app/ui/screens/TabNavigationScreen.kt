package project.pipepipe.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.customTopBarColor
import project.pipepipe.app.helper.MainScreenTabDefaults
import project.pipepipe.app.helper.MainScreenTabHelper
import project.pipepipe.app.onCustomTopBarColor
import project.pipepipe.app.ui.component.ResponsiveTabs
import project.pipepipe.app.ui.screens.playlistdetail.PlaylistDetailScreen
import java.net.URLDecoder


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabNavigationScreen(navController: NavController) {
    val settingsManager = SharedContext.settingsManager
    val platformActions = SharedContext.platformActions

    // Load saved tabs or use defaults
    val tabConfigs by remember {
        mutableStateOf(
            try {
                val jsonString = settingsManager.getString("custom_tabs_config_key")
                if (jsonString.isEmpty()) {
                    MainScreenTabDefaults.getDefaultTabs()
                } else {
                    // Try new format first
                    val tabs = try {
                        Json.decodeFromString<List<String>>(jsonString)
                    } catch (e: Exception) {
                        // Try old format (List<{route: String, isDefault: Boolean}>)
                        // TODO: remove this fallback once goto stable. Also TabCustomizationScreen.kt
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
                    val migratedTabs = tabs.map { route ->
                        route.replace("serviceId=YOUTUBE", "serviceId=0")
                            .replace("serviceId=BILIBILI", "serviceId=5")
                            .replace("serviceId=NICONICO", "serviceId=6")
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

    val pagerState = rememberPagerState(pageCount = { tabConfigs.size })
    val scope = rememberCoroutineScope()

    // Automatically adjust currentPage if it goes out of bounds
    LaunchedEffect(tabConfigs.size) {
        if (tabConfigs.isNotEmpty() && pagerState.currentPage >= tabConfigs.size) {
            pagerState.scrollToPage(tabConfigs.size - 1)
        }
    }

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
                onClick = { platformActions.openDrawer() },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = onCustomTopBarColor()
                )
            }

            // Safe access to tab config with boundary check
            if (tabConfigs.isNotEmpty()) {
                val safeIndex = pagerState.currentPage.coerceIn(0, tabConfigs.size - 1)
                Text(
                    text = MainScreenTabHelper.getTabDisplayName(tabConfigs[safeIndex]),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = onCustomTopBarColor()
                )
            }

            IconButton(onClick = { navController.navigate(Screen.Search.createRoute()) }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(MR.strings.search),
                    tint = onCustomTopBarColor()
                )
            }
        }

        ResponsiveTabs(
            titles = List(tabConfigs.size) { "" },
            selectedIndex = pagerState.currentPage,
            modifier = Modifier.fillMaxWidth(),
            icons = tabConfigs.map { MainScreenTabHelper.getTabIcon(it) },
            containerColor = customTopBarColor()
        ) { index ->
            scope.launch {
                pagerState.animateScrollToPage(index)
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 3
        ) { page ->
            val route = tabConfigs[page]
            val baseRoute = route.substringBefore('?')
            when {
                route == "subscriptions" -> SubscriptionsScreen(navController = navController, useAsTab = true)
                route == "bookmarked_playlists" -> BookmarkedPlaylistScreen(navController = navController, useAsTab = true)
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
                            url = URLDecoder.decode(urlParam, "UTF-8"),
                            useAsTab = true,
                            navController = navController,
                            serviceId = serviceIdParam?.toInt()
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
                            channelUrl = URLDecoder.decode(urlParam, "UTF-8"),
                            serviceId = serviceIdParam.toInt(),
                            useAsTab = true
                        )
                    }
                }
            }
        }
    }
}