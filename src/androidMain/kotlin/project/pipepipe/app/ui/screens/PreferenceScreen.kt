package project.pipepipe.app.ui.screens

// PreferenceScreen.kt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import project.pipepipe.app.settings.PreferenceItem
import project.pipepipe.shared.helper.SettingsManager
import project.pipepipe.app.ui.component.*

@Composable
fun PreferenceScreen(
    items: List<PreferenceItem>,
    settingsManager: SettingsManager = remember { SettingsManager() },
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(items) { item ->
            when (item) {
                is PreferenceItem.SwitchPref -> SwitchPreference(item)
                is PreferenceItem.ListPref -> ListPreference(item)
                is PreferenceItem.EditTextPref -> EditTextPreference(item)
                is PreferenceItem.SliderPref -> SliderPreference(item)
                is PreferenceItem.ClickablePref -> ClickablePreference(item)
                is PreferenceItem.CategoryPref -> CategoryPreference(item)
                is PreferenceItem.MultiSelectPref -> MultiSelectPreference(item)
                is PreferenceItem.ColorPref -> ColorPreference(item)
            }
        }
    }
}
