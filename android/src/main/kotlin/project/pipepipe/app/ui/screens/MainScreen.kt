
package project.pipepipe.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import project.pipepipe.app.database.DatabaseImporter
import dev.icerock.moko.resources.compose.stringResource
import project.pipepipe.app.MR
import project.pipepipe.app.SharedContext

import java.net.URLEncoder

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val importDatabaseLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            DatabaseImporter(context).importDatabase(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = {
            SharedContext.sharedVideoDetailViewModel.loadVideoDetails("https://www.bilibili.com/video/BV1SB4y1y7GQ?p=1", 5)
        }) {
            Text(stringResource(MR.strings.go_to_video_detail))
        }
        
        Button(
            onClick = {
                importDatabaseLauncher.launch("*/*")
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(MR.strings.import_database))
        }
        
        Button(
            onClick = {
                navController.navigate(Screen.BookmarkedPlaylist.route)
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(MR.strings.tab_bookmarks))
        }
        
        Button(
            onClick = {
                SharedContext.toggleShowPlayQueueVisibility()
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(MR.strings.play_queue))
        }

        Button(
            onClick = {
                navController.navigate(Screen.History.route)
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(MR.strings.title_activity_history))
        }
        Button(
            onClick = {
                navController.navigate(
                    "channel?url=" + URLEncoder.encode("https://www.nicovideo.jp/user/68979207", "UTF-8") +
                        "&serviceId=NICONICO"
                )
            },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(MR.strings.channel))
        }
    }
}
