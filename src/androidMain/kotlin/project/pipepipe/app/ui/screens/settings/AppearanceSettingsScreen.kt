package project.pipepipe.app.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.component.ListPreference
import project.pipepipe.app.ui.component.SwitchPreference
import project.pipepipe.shared.SharedContext

// Preset theme colors
private val PRESET_COLORS = listOf(
    "#e53935", // Red
    "#f57c00", // Orange
    "#ff6f00", // Deep Orange
    "#9e9e9e", // Grey
    "#17a0c4", // Blue
    "#FB7299", // Pink
    "#e2c9a3", // Tan
    "#6e0e3c"  // Maroon
)

@Composable
fun AppearanceSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val settingsManager = SharedContext.settingsManager

    // Check if Material You is supported
    val materialYouSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // State for Material You
    val materialYouEnabledState = remember {
        mutableStateOf(settingsManager.getBoolean("material_you_enabled_key", false))
    }

    // State for theme color
    val themeColorState = remember {
        mutableStateOf(settingsManager.getString("theme_color_key", PRESET_COLORS[0]))
    }

    var showColorDialog by remember { mutableStateOf(false) }

    // Strings for theme mode
    val themeModeTitle = stringResource(MR.strings.settings_appearance_theme_mode_title)
    val themeModeSystem = stringResource(MR.strings.settings_appearance_theme_mode_system)
    val themeModeLight = stringResource(MR.strings.settings_appearance_theme_mode_light)
    val themeModeDark = stringResource(MR.strings.settings_appearance_theme_mode_dark)

    val themeModeEntries = listOf(themeModeSystem, themeModeLight, themeModeDark)
    val themeModeValues = listOf("system", "light", "dark")

    val themeColorTitle = stringResource(MR.strings.settings_appearance_theme_color_title)
    val themeColorSummary = if (materialYouEnabledState.value) {
        stringResource(MR.strings.settings_appearance_theme_color_disabled_summary)
    } else {
        stringResource(MR.strings.settings_appearance_theme_color_summary)
    }

    val materialYouTitle = stringResource(MR.strings.settings_appearance_material_you_title)
    val materialYouSummary = if (materialYouSupported) {
        stringResource(MR.strings.settings_appearance_material_you_summary)
    } else {
        stringResource(MR.strings.settings_appearance_material_you_disabled_summary)
    }

    val preferenceItems = listOf(
        PreferenceItem.ListPref(
            key = "theme_mode_key",
            title = themeModeTitle,
            entries = themeModeEntries,
            entryValues = themeModeValues,
            defaultValue = "system"
        ),
        PreferenceItem.ClickablePref(
            key = "theme_color_key",
            title = themeColorTitle,
            summary = themeColorSummary,
            enabled = !materialYouEnabledState.value,
            onClick = { showColorDialog = true }
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
        )
    )

    val switchStateMap: Map<String, MutableState<Boolean>> = mapOf(
        "material_you_enabled_key" to materialYouEnabledState
    )

    Column {
        CustomTopBar(
            defaultTitleText = stringResource(MR.strings.settings_section_appearance),
            defaultNavigationOnClick = { navController.popBackStack() }
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
                    is PreferenceItem.ClickablePref -> ThemeColorPreference(
                        item = item,
                        currentColor = themeColorState.value
                    )
                    is PreferenceItem.SwitchPref -> {
                        val state = switchStateMap[item.key]
                        if (state != null) {
                            key("${item.key}_${state.value}") {
                                SwitchPreference(item = item)
                            }
                        } else {
                            SwitchPreference(item = item)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    // Color picker dialog
    if (showColorDialog) {
        ColorPickerDialog(
            currentColor = themeColorState.value,
            onDismiss = { showColorDialog = false },
            onColorSelected = { color ->
                themeColorState.value = color
                settingsManager.putString("theme_color_key", color)
                showColorDialog = false
            }
        )
    }
}

@Composable
private fun ThemeColorPreference(
    item: PreferenceItem.ClickablePref,
    currentColor: String,
    modifier: Modifier = Modifier
) {
    val color = parseColorOrNull(currentColor) ?: MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled, onClick = { if (item.enabled) item.onClick() }),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item.icon?.let {
                Box(modifier = Modifier.padding(end = 16.dp)) {
                    it()
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    color = if (item.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                item.summary?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = if (item.enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }
            }

            Box(modifier = Modifier.padding(start = 16.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(
    currentColor: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.settings_appearance_theme_color_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(PRESET_COLORS) { colorHex ->
                    val color = parseColorOrNull(colorHex) ?: Color.Gray
                    val isSelected = currentColor.equals(colorHex, ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                                tint = if (isLightColor(color)) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.ok))
            }
        }
    )
}

private fun parseColorOrNull(hex: String): Color? {
    return try {
        val sanitized = hex.trim().removePrefix("#")
        if (sanitized.length != 6 && sanitized.length != 8) return null

        val argb = when (sanitized.length) {
            6 -> 0xFF000000L or sanitized.toLong(16)
            else -> sanitized.toLong(16)
        }.toInt()

        Color(argb)
    } catch (e: Exception) {
        null
    }
}

private fun isLightColor(color: Color): Boolean {
    // Calculate relative luminance
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance > 0.5f
}
