package project.pipepipe.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import project.pipepipe.shared.SharedContext
import project.pipepipe.shared.helper.ToastManager
import project.pipepipe.shared.uistate.VideoDetailPageState
import project.pipepipe.app.ui.component.BottomSheetMenu
import project.pipepipe.app.ui.component.Toast
import project.pipepipe.app.ui.navigation.NavGraph
import project.pipepipe.app.ui.screens.PlayQueueScreen
import project.pipepipe.app.ui.screens.videodetail.VideoDetailScreen
import project.pipepipe.app.ui.theme.PipePipeTheme


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkIntentForPlayQueue(intent)

        setContent {
            val navController = rememberNavController()
            val toastMessage by ToastManager.message.collectAsState()
            PipePipeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val videoDetailUiState by SharedContext.sharedVideoDetailViewModel.uiState.collectAsState()
                    val bottomMenuContent by SharedContext.bottomSheetMenuViewModel.menuContent.collectAsState()
                    val showPlayQueue by SharedContext.playQueueVisibility.collectAsState()
                    val scope = rememberCoroutineScope()

                    val bottomPlayerExtraHeight = 64.dp
                    val animatedExtraPadding by animateDpAsState(
                        targetValue = when (videoDetailUiState.pageState) {
                            VideoDetailPageState.HIDDEN -> 0.dp
                            else -> bottomPlayerExtraHeight
                        },
                        label = "bottomPlayerExtraPaddingAnimation"
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavGraph(
                            navController = navController,
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .padding(bottom = animatedExtraPadding)
                        )

                        VideoDetailScreen(modifier = Modifier.navigationBarsPadding(), navController = navController)
                        if (showPlayQueue) {
                            PlayQueueScreen()
                        }

                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        if (bottomMenuContent != null) {
                            ModalBottomSheet(
                                onDismissRequest = { SharedContext.bottomSheetMenuViewModel.dismiss() },
                                sheetState = sheetState
                            ) {
                                BottomSheetMenu(
                                    navController = navController,
                                    content = bottomMenuContent!!,
                                    onDismiss = {
                                        scope.launch {
                                            sheetState.hide()
                                        }.invokeOnCompletion {
                                            if (!sheetState.isVisible) {
                                                SharedContext.bottomSheetMenuViewModel.dismiss()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        toastMessage?.let { message ->
                            Toast(message = message)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntentForPlayQueue(intent)
    }

    private fun checkIntentForPlayQueue(intent: Intent) {
        if (intent.getBooleanExtra("open_play_queue", false) && !SharedContext.playQueueVisibility.value) {
            SharedContext.toggleShowPlayQueueVisibility()
        }
    }
}

