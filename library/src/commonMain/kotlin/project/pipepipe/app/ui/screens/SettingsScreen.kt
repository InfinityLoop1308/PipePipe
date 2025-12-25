package project.pipepipe.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.ui.component.CustomTopBar

@Composable
fun SettingsScreen(
    navController: NavController
) {
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
            item {
                SettingsItem(
                    icon = Icons.Default.PlayCircleFilled,
                    title = stringResource(MR.strings.settings_category_player_title),
                    onClick = { navController.navigate(Screen.PlayerSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.TouchApp,
                    title = stringResource(MR.strings.settings_section_gesture),
                    onClick = { navController.navigate(Screen.GestureSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = stringResource(MR.strings.settings_section_appearance),
                    onClick = { navController.navigate(Screen.AppearanceSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.AccountCircle,
                    title = stringResource(MR.strings.settings_section_account),
                    onClick = { navController.navigate(Screen.AccountSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.History,
                    title = stringResource(MR.strings.title_activity_history),
                    onClick = { navController.navigate(Screen.HistorySettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(MR.strings.settings_category_feed_title),
                    onClick = { navController.navigate(Screen.FeedSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.CloudDownload,
                    title = stringResource(MR.strings.settings_section_update),
                    onClick = { navController.navigate(Screen.UpdateSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Save,
                    title = stringResource(MR.strings.settings_section_import_export),
                    onClick = { navController.navigate(Screen.ImportExportSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.FilterAlt,
                    title = stringResource(MR.strings.settings_section_filter),
                    onClick = { navController.navigate(Screen.FilterSettings.route) }
                )
            }
            item {
                SettingsItem(
                    icon = Icons.Default.Shield,
                    title = stringResource(MR.strings.sponsor_block),
                    onClick = { navController.navigate(Screen.SponsorBlockSettings.route) }
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(26.dp))
        Text(
            text = title,
            fontSize = 15.sp,
        )
    }
}