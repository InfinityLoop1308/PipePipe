package project.pipepipe.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext
import project.pipepipe.app.ui.theme.supportingTextColor
import project.pipepipe.app.helper.ColorHelper
import project.pipepipe.app.helper.ColorHelper.parseHexColorOrNull

@Composable
fun SwitchPreference(
    item: PreferenceItem.SwitchPref,
    modifier: Modifier = Modifier
) {
    var checked by remember {
        mutableStateOf(SharedContext.settingsManager.getBoolean(item.key, item.defaultValue))
    }

    PreferenceTemplate(
        title = item.title,
        summary = item.summary,
        icon = item.icon,
        enabled = item.enabled,
        modifier = modifier,
        onClick = {
            if (item.enabled) {
                val newValue = !checked
                checked = newValue
                SharedContext.settingsManager.putBoolean(item.key, newValue)
                item.onValueChange?.invoke(newValue)
            }
        },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = item.enabled,
                modifier = Modifier.scale(0.8f)
            )
        }
    )
}

@Composable
fun ListPreference(
    item: PreferenceItem.ListPref,
    modifier: Modifier = Modifier
) {
    var selectedValue by remember {
        mutableStateOf(SharedContext.settingsManager.getString(item.key, item.defaultValue))
    }
    var showDialog by remember { mutableStateOf(false) }

    val selectedEntry = remember(selectedValue) {
        val index = item.entryValues.indexOf(selectedValue)
        if (index >= 0 && index < item.entries.size) item.entries[index] else ""
    }

    PreferenceTemplate(
        title = item.title,
        summary = item.summary ?: selectedEntry,
        icon = item.icon,
        enabled = item.enabled,
        modifier = modifier,
        onClick = { if (item.enabled) showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(item.title) },
            text = {
                Column {
                    item.entries.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedValue = item.entryValues[index]
                                    SharedContext.settingsManager.putString(item.key, selectedValue)
                                    item.onValueChange?.invoke(selectedValue)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedValue == item.entryValues[index],
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(entry)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }
}

@Composable
fun EditTextPreference(
    item: PreferenceItem.EditTextPref,
    modifier: Modifier = Modifier
) {
    var value by remember {
        mutableStateOf(SharedContext.settingsManager.getString(item.key, item.defaultValue))
    }
    var showDialog by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(value) }

    PreferenceTemplate(
        title = item.title,
        summary = item.summary ?: value,
        icon = item.icon,
        enabled = item.enabled,
        modifier = modifier,
        onClick = {
            if (item.enabled) {
                textFieldValue = value
                showDialog = true
            }
        }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(item.dialogTitle ?: item.title) },
            text = {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    placeholder = item.placeholder?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        value = textFieldValue
                        SharedContext.settingsManager.putString(item.key, value)
                        item.onValueChange?.invoke(value)
                        showDialog = false
                    }
                ) {
                    Text(stringResource(MR.strings.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }
}

@Composable
fun SliderPreference(
    item: PreferenceItem.SliderPref,
    modifier: Modifier = Modifier
) {
    var value by remember {
        mutableStateOf(SharedContext.settingsManager.getFloat(item.key, item.defaultValue))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                item.icon?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        it()
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } ?: Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge
                )

                item.summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.showValue) {
                Text(
                    text = "%.2f".format(value),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Slider(
            value = value,
            onValueChange = {
                value = it
                SharedContext.settingsManager.putFloat(item.key, it)
                item.onValueChange?.invoke(it)
            },
            valueRange = item.valueRange,
            steps = item.steps,
            enabled = item.enabled
        )
    }
}

@Composable
fun ClickablePreference(
    item: PreferenceItem.ClickablePref,
    modifier: Modifier = Modifier
) {
    PreferenceTemplate(
        title = item.title,
        summary = item.summary,
        icon = item.icon,
        enabled = item.enabled,
        modifier = modifier,
        onClick = { if (item.enabled) item.onClick() }
    )
}

@Composable
fun CategoryPreference(
    item: PreferenceItem.CategoryPref,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        item.summary?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = supportingTextColor()
            )
        }
    }
}

@Composable
fun MultiSelectPreference(
    item: PreferenceItem.MultiSelectPref,
    modifier: Modifier = Modifier
) {
    var selectedValues by remember {
        mutableStateOf(
            SharedContext.settingsManager
                .getStringSet(item.key, item.defaultValues)
                .toSet()
        )
    }
    var dialogSelection by remember { mutableStateOf(selectedValues) }
    var showDialog by remember { mutableStateOf(false) }

    val summaryText = remember(selectedValues, item.entries, item.entryValues, item.summary) {
        item.summary ?: run {
            val selectedEntries = item.entryValues.mapIndexedNotNull { index, value ->
                if (selectedValues.contains(value)) item.entries.getOrNull(index) else null
            }
            selectedEntries.takeIf { it.isNotEmpty() }?.joinToString(", ")
        }
    }

    PreferenceTemplate(
        title = item.title,
        summary = summaryText,
        icon = item.icon,
        enabled = item.enabled,
        modifier = modifier,
        onClick = {
            if (item.enabled) {
                dialogSelection = selectedValues
                showDialog = true
            }
        }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(item.title) },
            text = {
                Column {
                    item.entries.forEachIndexed { index, entry ->
                        val value = item.entryValues.getOrNull(index) ?: return@forEachIndexed
                        val isChecked = dialogSelection.contains(value)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    dialogSelection = dialogSelection.toMutableSet().also { set ->
                                        if (!set.add(value)) {
                                            set.remove(value)
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                                enabled = item.enabled
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(entry)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedValues = dialogSelection
                        SharedContext.settingsManager.putStringSet(item.key, selectedValues)
                        item.onValuesChange?.invoke(selectedValues)
                        showDialog = false
                    }
                ) {
                    Text(stringResource(MR.strings.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }
}

@Composable
fun ColorPreference(
    item: PreferenceItem.ColorPref,
    modifier: Modifier = Modifier
) {
    val normalizedCurrentColor = remember(item.currentColor, item.defaultColor) {
        ColorHelper.sanitizeHexColorInput(item.currentColor)
            ?: ColorHelper.sanitizeHexColorInput(item.defaultColor)
            ?: "#FFFFFF"
    }
    var showDialog by remember { mutableStateOf(false) }
    var dialogText by remember(normalizedCurrentColor) { mutableStateOf(normalizedCurrentColor) }

    val summaryText = item.summary ?: normalizedCurrentColor
    val swatchColor = parseHexColorOrNull(normalizedCurrentColor) ?: MaterialTheme.colorScheme.primary

    PreferenceTemplate(
        title = item.title,
        summary = summaryText,
        icon = item.icon,
        enabled = item.enabled,
        modifier = modifier,
        onClick = {
            if (item.enabled) {
                dialogText = normalizedCurrentColor
                showDialog = true
            }
        },
        trailing = {
            ColorSwatch(color = swatchColor)
        }
    )

    if (showDialog) {
        val sanitizedInput = ColorHelper.sanitizeHexColorInput(dialogText)
        val dialogPreviewColor = parseHexColorOrNull(sanitizedInput ?: dialogText) ?: swatchColor

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(item.title) },
            text = {
                Column {
                    OutlinedTextField(
                        value = dialogText,
                        onValueChange = { dialogText = it.uppercase() },
                        label = { Text("#RRGGBB or #AARRGGBB") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        trailingIcon = {
                            ColorSwatch(
                                color = dialogPreviewColor,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val color = sanitizedInput ?: return@TextButton
                        SharedContext.settingsManager.putString(item.key, color)
                        item.onColorChange?.invoke(color)
                        showDialog = false
                    },
                    enabled = sanitizedInput != null
                ) {
                    Text(stringResource(MR.strings.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(MR.strings.cancel))
                }
            }
        )
    }
}


@Composable
private fun ColorSwatch(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

@Composable
private fun PreferenceTemplate(
    title: String,
    summary: String?,
    icon: (@Composable () -> Unit)?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Box(modifier = Modifier.padding(end = 16.dp)) {
                    it()
                }
            }

            Column(
                modifier = Modifier.weight(1f)
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
                Spacer(modifier = Modifier.height(2.dp))
                summary?.let {
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = if (enabled) {
                            supportingTextColor()
                        } else {
                            supportingTextColor().copy(alpha = 0.38f)
                        },
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false
                            )
                        )
                    )
                }
            }

            trailing?.let {
                Box(modifier = Modifier.padding(start = 16.dp)) {
                    it()
                }
            }
        }
    }
}