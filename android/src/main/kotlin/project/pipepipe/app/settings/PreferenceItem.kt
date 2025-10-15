package project.pipepipe.app.settings

import androidx.compose.runtime.Composable

sealed class PreferenceItem {
    abstract val key: String
    abstract val title: String
    abstract val summary: String?
    abstract val icon: (@Composable () -> Unit)?
    abstract val enabled: Boolean

    data class SwitchPref(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val icon: (@Composable () -> Unit)? = null,
        override val enabled: Boolean = true,
        val defaultValue: Boolean = false,
        val onValueChange: ((Boolean) -> Unit)? = null
    ) : PreferenceItem()

    data class ListPref(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val icon: (@Composable () -> Unit)? = null,
        override val enabled: Boolean = true,
        val entries: List<String>,
        val entryValues: List<String>,
        val defaultValue: String = "",
        val onValueChange: ((String) -> Unit)? = null
    ) : PreferenceItem()

    data class EditTextPref(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val icon: (@Composable () -> Unit)? = null,
        override val enabled: Boolean = true,
        val defaultValue: String = "",
        val dialogTitle: String? = null,
        val placeholder: String? = null,
        val onValueChange: ((String) -> Unit)? = null
    ) : PreferenceItem()

    data class SliderPref(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val icon: (@Composable () -> Unit)? = null,
        override val enabled: Boolean = true,
        val defaultValue: Float = 0f,
        val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        val steps: Int = 0,
        val showValue: Boolean = true,
        val onValueChange: ((Float) -> Unit)? = null
    ) : PreferenceItem()

    data class ClickablePref(
        override val key: String = "",
        override val title: String,
        override val summary: String? = null,
        override val icon: (@Composable () -> Unit)? = null,
        override val enabled: Boolean = true,
        val onClick: () -> Unit
    ) : PreferenceItem()

    data class CategoryPref(
        override val key: String = "",
        override val title: String,
        override val summary: String? = null,
        override val icon: (@Composable () -> Unit)? = null,
        override val enabled: Boolean = true
    ) : PreferenceItem()

    data class MultiSelectPref(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val icon: (@Composable () -> Unit)? = null,
        override val enabled: Boolean = true,
        val entries: List<String>,
        val entryValues: List<String>,
        val defaultValues: Set<String> = emptySet(),
        val onValuesChange: ((Set<String>) -> Unit)? = null
    ) : PreferenceItem()

    data class ColorPref(
        override val key: String,
        override val title: String,
        override val summary: String? = null,
        override val icon: (@Composable () -> Unit)? = null,
        override val enabled: Boolean = true,
        val currentColor: String,
        val defaultColor: String = "#FFFFFF",
        val onColorChange: ((String) -> Unit)? = null
    ) : PreferenceItem()
}