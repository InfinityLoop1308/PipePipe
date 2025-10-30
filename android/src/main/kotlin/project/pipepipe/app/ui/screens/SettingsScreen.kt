package project.pipepipe.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import dev.icerock.moko.resources.desc.desc
import project.pipepipe.app.MR
import project.pipepipe.app.ui.component.CustomTopBar
import project.pipepipe.app.ui.screens.settings.GestureSettingScreen

@Composable
fun SettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val sections = remember {
        listOf(
            SettingsSection(Icons.Default.PlayCircleFilled, MR.strings.settings_category_player_title.desc().toString(context = context), Screen.PlayerSettings),
            SettingsSection(Icons.Default.TouchApp, MR.strings.settings_section_gesture.desc().toString(context = context), Screen.GestureSettings),
//            SettingsSection(Icons.Default.Download, "Download"),
            SettingsSection(Icons.Default.Palette, MR.strings.settings_section_appearance.desc().toString(context = context), Screen.AppearanceSettings),
//            SettingsSection(Icons.Default.History, "History and cache"),
//            SettingsSection(Icons.Default.Public, "Content"),
//            SettingsSection(Icons.Default.Person, "Account"),
            SettingsSection(Icons.Default.Notifications, MR.strings.settings_category_feed_title.desc().toString(context = context), Screen.FeedSettings),
            SettingsSection(Icons.Default.SystemUpdateAlt, MR.strings.settings_section_update.desc().toString(context = context), Screen.UpdateSettings),
            SettingsSection(Icons.Default.Save, MR.strings.settings_section_import_export.desc().toString(context = context), Screen.ImportExportSettings),
            SettingsSection(Icons.Default.FilterAlt, MR.strings.settings_section_filter.desc().toString(context = context), Screen.FilterSettings),
            SettingsSection(Icons.Default.Shield, MR.strings.sponsor_block.desc().toString(context = context), Screen.SponsorBlockSettings),
            SettingsSection(Icons.Default.BugReport, MR.strings.log.desc().toString(context = context), Screen.LogSettings)
        )
    }

    Scaffold(
        topBar = {
            CustomTopBar(
                defaultTitleText = stringResource(MR.strings.settings)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
        ) {
            items(sections) { section ->
                SettingsItem(section) { navController.navigate(section.screen.route) }
            }
        }
    }
}

@Composable
private fun SettingsItem(section: SettingsSection, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = section.title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(26.dp))
        Text(
            text = section.title,
            fontSize = 15.sp,
        )
    }
}

data class SettingsSection(
    val icon: ImageVector,
    val title: String,
    val screen: Screen
)