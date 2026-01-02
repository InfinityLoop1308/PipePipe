package project.pipepipe.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.helper.ColorHelper
import project.pipepipe.app.ui.component.*
import project.pipepipe.app.ui.screens.Screen
import project.pipepipe.app.supportingTextColor

// Preset theme colors
private val PRESET_COLORS = listOf(
    "#e53935", // Red
    "#f57c00", // Orange
    "#9e9e9e", // Grey
    "#FB7299", // Pink
    "#000000",
    "#FFFFFF",  // White
)

@Composable
fun AppearanceSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val settingsManager = SharedContext.settingsManager

    // Check if Material You is supported
    val materialYouSupported = SharedContext.androidVersion >= 31

    // State for Material You
    val materialYouEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("material_you_enabled_key", false))
    }

    // State for Pure Black
    val pureBlackEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("pure_black_key", false))
    }

    // State for Grid Layout
    val gridLayoutEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("grid_layout_enabled_key", false))
    }

    // State for dynamic color for search
    val dynamicColorForSearchEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("dynamic_color_for_search_enabled_key", false))
    }

    // State for theme color
    val themeColorState = remember {
        mutableStateOf(settingsManager.getString("theme_color_key", PRESET_COLORS[0]))
    }

    // Strings for theme mode
    val themeModeTitle = stringResource(MR.strings.settings_appearance_theme_mode_title)
    val themeModeSystem = stringResource(MR.strings.settings_appearance_theme_mode_system)
    val themeModeLight = stringResource(MR.strings.settings_appearance_theme_mode_light)
    val themeModeDark = stringResource(MR.strings.settings_appearance_theme_mode_dark)

    val themeModeEntries = listOf(themeModeSystem, themeModeLight, themeModeDark)
    val themeModeValues = listOf("system", "light", "dark")

    val themeColorTitle = stringResource(MR.strings.settings_appearance_theme_color_title)

    val materialYouTitle = stringResource(MR.strings.settings_appearance_material_you_title)
    val materialYouSummary = if (materialYouSupported) {
        stringResource(MR.strings.settings_appearance_material_you_summary)
    } else {
        stringResource(MR.strings.settings_appearance_material_you_disabled_summary)
    }

    val pureBlackTitle = stringResource(MR.strings.settings_appearance_pure_black_title)
    val pureBlackSummary = stringResource(MR.strings.settings_appearance_pure_black_summary)

    val gridLayoutTitle = stringResource(MR.strings.settings_appearance_grid_layout_title)
    val gridLayoutSummary = stringResource(MR.strings.settings_appearance_grid_layout_summary)

    val gridColumnsTitle = stringResource(MR.strings.settings_appearance_grid_columns_title)
    val gridColumnsSummary = stringResource(MR.strings.settings_appearance_grid_columns_summary)

    val customizeTabsTitle = stringResource(MR.strings.customize_tabs)
    val customizeTabsSummary = stringResource(MR.strings.customize_tabs_summary)

    val videoTabsTitle = stringResource(MR.strings.video_tabs_title)
    val videoTabsSummary = stringResource(MR.strings.video_tabs_summary)

    val dynamicColorForSearchTitle = stringResource(MR.strings.settings_appearance_dynamic_color_for_search_title)
    val dynamicColorForSearchSummary = stringResource(MR.strings.settings_appearance_dynamic_color_for_search_summary)

    val themeCategory = stringResource(MR.strings.theme)
    val gridCategory = stringResource(MR.strings.grid)
    val tabCategory = stringResource(MR.strings.tab)
    val searchCategory = stringResource(MR.strings.search)

    // Video tabs entries
    val videoTabsEntries = listOf(
        stringResource(MR.strings.comments_tab_description),
        stringResource(MR.strings.related_items_tab_description),
        stringResource(MR.strings.sponsor_block),
        stringResource(MR.strings.description_tab)
    )
    val videoTabsValues = listOf("comments", "related", "sponsorblock", "description")
    val videoTabsDefaultValues = videoTabsValues.toSet()

    val preferenceItems = listOf(
        PreferenceItem.CategoryPref(
            key = "theme_category",
            title = themeCategory
        ),
        PreferenceItem.ListPref(
            key = "theme_mode_key",
            title = themeModeTitle,
            entries = themeModeEntries,
            entryValues = themeModeValues,
            defaultValue = "system"
        ),
        PreferenceItem.SwitchPref(
            key = "material_you_enabled_key",
            title = materialYouTitle,
            summary = materialYouSummary,
            enabled = materialYouSupported,
            defaultValue = false,
            onValueChange = {
                materialYouEnabledState.value = it
            }
        ),
        PreferenceItem.SwitchPref(
            key = "pure_black_key",
            title = pureBlackTitle,
            summary = pureBlackSummary,
            defaultValue = false,
            onValueChange = {
                pureBlackEnabledState.value = it
            }
        ),
        PreferenceItem.CategoryPref(
            key = "search_category",
            title = searchCategory
        ),
        PreferenceItem.SwitchPref(
            key = "dynamic_color_for_search_enabled_key",
            title = dynamicColorForSearchTitle,
            summary = dynamicColorForSearchSummary,
            defaultValue = false,
            onValueChange = {
                dynamicColorForSearchEnabledState.value = it
            }
        ),
        PreferenceItem.CategoryPref(
            key = "tab_category",
            title = tabCategory
        ),
        PreferenceItem.ClickablePref(
            title = customizeTabsTitle,
            summary = customizeTabsSummary,
            onClick = {
                navController.navigate(Screen.TabCustomization.route)
            }
        ),
        PreferenceItem.MultiSelectPref(
            key = "video_tabs_key",
            title = videoTabsTitle,
            summary = videoTabsSummary,
            entries = videoTabsEntries,
            entryValues = videoTabsValues,
            defaultValues = videoTabsDefaultValues
        ),
        PreferenceItem.CategoryPref(
            key = "grid_category",
            title = gridCategory
        ),
        PreferenceItem.SwitchPref(
            key = "grid_layout_enabled_key",
            title = gridLayoutTitle,
            summary = gridLayoutSummary,
            defaultValue = false,
            onValueChange = {
                gridLayoutEnabledState.value = it
            }
        ),
        PreferenceItem.ListPref(
            key = "grid_columns_key",
            title = gridColumnsTitle,
            summary = gridColumnsSummary,
            entries = listOf("1", "2", "3", "4", "5", "6", "7", "8"),
            entryValues = listOf("1", "2", "3", "4", "5", "6", "7", "8"),
            defaultValue = "4",
            enabled = gridLayoutEnabledState.value
        ),

    )

    Column {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.settings_section_appearance)
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
                    is PreferenceItem.CategoryPref -> CategoryPreference(item = item)
                    is PreferenceItem.ListPref -> ListPreference(item = item)
                    is PreferenceItem.SwitchPref -> {
                        SwitchPreference(item = item)
                        // Add color palette after pure_black_key (last item in theme category)
                        if (item.key == "pure_black_key") {
                            InlineColorPalette(
                                title = themeColorTitle,
                                currentColor = themeColorState.value,
                                enabled = !materialYouEnabledState.value,
                                onColorSelected = { color ->
                                    themeColorState.value = color
                                    settingsManager.putString("theme_color_key", color)
                                }
                            )
                        }
                    }
                    is PreferenceItem.ClickablePref -> ClickablePreference(item = item)
                    is PreferenceItem.MultiSelectPref -> MultiSelectPreference(item = item)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun InlineColorPalette(
    title: String,
    currentColor: String,
    enabled: Boolean,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PRESET_COLORS.forEach { colorHex ->
                        val color = ColorHelper.parseHexColor(colorHex, Color.Gray)
                        val isSelected = currentColor.equals(colorHex, ignoreCase = true)

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(colorHex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (ColorHelper.isLightColor(color)) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(MR.strings.disabled),
                    fontSize = 13.sp,
                    color = supportingTextColor().copy(alpha = 0.38f),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
            }
        }
    }
}
