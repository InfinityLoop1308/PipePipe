package project.pipepipe.app.ui.screens

// PreferenceScreen.kt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import project.pipepipe.app.ui.screens.settings.PreferenceItem
import project.pipepipe.app.ui.component.*

@Composable
fun PreferenceScreen(
    title: String,
    items: List<PreferenceItem>,
    modifier: Modifier = Modifier,
    verticalSpacing: Int = 4,
    contentPadding: PaddingValues = PaddingValues(bottom = 16.dp)
) {
    Column {
        CustomTopBar(
            defaultTitleText = title
        )

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing.dp),
            contentPadding = contentPadding
        ) {
            items(
                items = items,
                key = { item -> item.key.ifEmpty { item.title } }
            ) { item ->
                when (item) {
                    is PreferenceItem.SwitchPref -> SwitchPreference(item)
                    is PreferenceItem.ListPref -> ListPreference(item)
                    is PreferenceItem.IntListPref -> IntListPreference(item)
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
}
