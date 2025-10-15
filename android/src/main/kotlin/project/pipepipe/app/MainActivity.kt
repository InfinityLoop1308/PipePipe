package project.pipepipe.app

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
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
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import project.pipepipe.app.global.PipHelper
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.service.PlaybackService
import project.pipepipe.app.service.setPlaybackMode
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.app.ui.component.BottomSheetMenu
import project.pipepipe.app.ui.component.ImageViewer
import project.pipepipe.app.ui.component.Toast
import project.pipepipe.app.ui.navigation.NavGraph
import project.pipepipe.app.ui.screens.PlayQueueScreen
import project.pipepipe.app.ui.screens.videodetail.VideoDetailScreen
import project.pipepipe.app.ui.theme.PipePipeTheme


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize MediaController
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())

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

                        ImageViewer()

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

    /**
     * Enter Picture-in-Picture mode with the specified aspect ratio
     */
    fun enterPipMode(isPortrait: Boolean = false): Boolean {
        val aspectRatio: Rational = if (!isPortrait)Rational(16, 9) else Rational(9, 16)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .build()
        return enterPictureInPictureMode(params)
    }



    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            SharedContext.exitPipMode()
            SharedContext.sharedVideoDetailViewModel.showAsDetailPage()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        val uiState = SharedContext.sharedVideoDetailViewModel.uiState.value
        val pageState = uiState.pageState

        // Only trigger when in DETAIL_PAGE or FULLSCREEN_PLAYER and player is playing
        if ((pageState == VideoDetailPageState.DETAIL_PAGE || pageState == VideoDetailPageState.FULLSCREEN_PLAYER)
            && mediaController?.isPlaying == true) {

            val minimizeSetting = SharedContext.settingsManager.getString("minimize_on_exit_key", "minimize_on_exit_none_key")
            val streamInfo = uiState.currentStreamInfo

            when (minimizeSetting) {
                "minimize_on_exit_background_key" -> {
                    // Minimize to background player
                    mediaController?.setPlaybackMode(PlaybackMode.AUDIO_ONLY)
                }
                "minimize_on_exit_popup_key" -> {
                    // Minimize to popup player (PiP)
                    if (streamInfo != null && mediaController != null) {
                        PipHelper.enterPipMode(mediaController!!, streamInfo, this)
                    }
                }
                // "minimize_on_exit_none_key" or default - do nothing
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
    }
}

