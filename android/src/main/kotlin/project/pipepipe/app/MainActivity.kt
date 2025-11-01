package project.pipepipe.app

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.launch
import project.pipepipe.app.global.PipHelper
import project.pipepipe.app.helper.ExternalUrlPatternHelper
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.service.PlaybackService
import project.pipepipe.app.service.UpdateCheckWorker
import project.pipepipe.app.service.setPlaybackMode
import project.pipepipe.app.ui.component.BottomSheetMenu
import project.pipepipe.app.ui.component.DataMigrationDialog
import project.pipepipe.app.ui.component.ErrorHandlingDialog
import project.pipepipe.app.ui.component.FirstRunDialog
import project.pipepipe.app.ui.component.ImageViewer
import project.pipepipe.app.ui.component.Toast
import project.pipepipe.app.ui.component.WelcomeDialog
import project.pipepipe.app.ui.navigation.NavGraph
import project.pipepipe.app.ui.screens.PlayQueueScreen
import project.pipepipe.app.ui.screens.Screen
import project.pipepipe.app.ui.screens.videodetail.VideoDetailScreen
import project.pipepipe.app.ui.theme.PipePipeTheme
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.shared.infoitem.PlaylistInfo
import project.pipepipe.shared.infoitem.StreamInfo


class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    lateinit var navController: NavHostController
    private var wasInPipMode = false

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
        checkIntentForFeedFailures(intent)
        checkIntentForStreamsFailures(intent)
        checkIntentForChannelNavigation(intent)
        handleDeepLink(intent)

        setContent {
            navController = rememberNavController()
            SharedContext.navController = navController
            val toastMessage by ToastManager.message.collectAsState()

            // Dialog state management
            // TODO: Update welcome dialog text after the official release
            var showWelcomeDialog by remember { mutableStateOf(false) }
            var showDataMigrationDialog by remember { mutableStateOf(false) }
            var showErrorHandlingDialog by remember { mutableStateOf(false) }
            var showFirstRunDialog by remember { mutableStateOf(false) }

            // Check if this is the first run and show dialogs in sequence
            // Dialog order: Welcome -> Data Migration -> Error Handling -> Update Checker
            LaunchedEffect(Unit) {
                val isFirstRun = SharedContext.settingsManager.getBoolean("is_first_run", true)
                if (isFirstRun) {
                    // Show welcome dialog first
                    showWelcomeDialog = true
                }
            }

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

                        VideoDetailScreen(modifier = Modifier.navigationBarsPadding().statusBarsPadding(), navController = navController)
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

                        // Welcome dialog - shown first on first run
                        if (showWelcomeDialog) {
                            WelcomeDialog(
                                onDismiss = {
                                    showWelcomeDialog = false
                                    // After welcome dialog, show data migration dialog
                                    showDataMigrationDialog = true
                                }
                            )
                        }

                        // Data migration dialog - shown after welcome dialog
                        if (showDataMigrationDialog) {
                            DataMigrationDialog(
                                onDismiss = {
                                    showDataMigrationDialog = false
                                    // After data migration dialog, show error handling dialog
                                    showErrorHandlingDialog = true
                                }
                            )
                        }

                        // Error handling dialog - shown after data migration dialog
                        if (showErrorHandlingDialog) {
                            ErrorHandlingDialog(
                                onDismiss = {
                                    showErrorHandlingDialog = false
                                    // After error handling dialog, show update checker dialog
                                    showFirstRunDialog = true
                                }
                            )
                        }

                        // Update checker dialog - shown after error handling dialog
                        if (showFirstRunDialog) {
                            FirstRunDialog(
                                onDismiss = {
                                    showFirstRunDialog = false
                                    SharedContext.settingsManager.putBoolean("is_first_run", false)
                                },
                                onEnableUpdateChecker = {
                                    SharedContext.settingsManager.putBoolean(UpdateCheckWorker.UPDATE_ENABLED_KEY, true)
                                    ToastManager.show(MR.strings.update_checking.desc().toString(context = this@MainActivity))
                                    UpdateCheckWorker.enqueueUpdateCheck(this@MainActivity, isManual = true)
                                    showFirstRunDialog = false
                                    SharedContext.settingsManager.putBoolean("is_first_run", false)
                                }
                            )
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
        checkIntentForFeedFailures(intent)
        checkIntentForStreamsFailures(intent)
        checkIntentForChannelNavigation(intent)
        handleDeepLink(intent)
    }

    private fun checkIntentForChannelNavigation(intent: Intent) {
        if (intent.getBooleanExtra("navigate_to_channel", false)) {
            val channelUrl = intent.getStringExtra("channel_url")
            val serviceId = intent.getStringExtra("service_id")
            if (channelUrl != null && serviceId != null) {
                navController.navigate(Screen.Channel.createRoute(channelUrl, serviceId))
                intent.removeExtra("navigate_to_channel")
                intent.removeExtra("channel_url")
                intent.removeExtra("service_id")
            }
        }
    }


    private fun checkIntentForPlayQueue(intent: Intent) {
        if (intent.getBooleanExtra("open_play_queue", false) && !SharedContext.playQueueVisibility.value) {
            SharedContext.toggleShowPlayQueueVisibility()
        }
    }

    private fun checkIntentForFeedFailures(intent: Intent) {
        if (intent.getBooleanExtra("show_feed_failures", false)) {
            val failedChannels = intent.getStringArrayListExtra("failed_channels") ?: return

            val message = buildString {
                append(MR.strings.feed_update_failed_channels.desc().toString(this@MainActivity))
                append("\n\n")
                failedChannels.forEachIndexed { index, channel ->
                    append("${index + 1}. $channel\n")
                }
            }

            AlertDialog.Builder(this)
                .setTitle(MR.strings.feed_load_error.desc().toString(this))
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()

            // Clear the flag to avoid showing the dialog again
            intent.removeExtra("show_feed_failures")
            intent.removeExtra("failed_channels")
        }
    }

    private fun checkIntentForStreamsFailures(intent: Intent) {
        if (intent.getBooleanExtra("show_streams_failures", false)) {
            val failedChannels = intent.getStringArrayListExtra("failed_channels") ?: return

            val message = buildString {
                append(MR.strings.feed_update_failed_channels.desc().toString(this@MainActivity))
                append("\n\n")
                failedChannels.forEachIndexed { index, channel ->
                    append("${index + 1}. $channel\n")
                }
            }

            AlertDialog.Builder(this)
                .setTitle(MR.strings.streams_notification_error.desc().toString(this))
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()

            // Clear the flag to avoid showing the dialog again
            intent.removeExtra("show_streams_failures")
            intent.removeExtra("failed_channels")
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val url = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                } else null
            }
            else -> null
        } ?: return

        // Try to handle the URL using the unified helper
        if (!ExternalUrlPatternHelper.tryHandleUrl(url)) {
            showUnrecognizedUrlDialog(url)
        }

        // Clear intent data/extras to prevent reprocessing
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data = null
            Intent.ACTION_SEND -> intent.removeExtra(Intent.EXTRA_TEXT)
        }
    }

    private fun showUnrecognizedUrlDialog(url: String) {
        AlertDialog.Builder(this)
            .setTitle(MR.strings.url_not_recognized_title.desc().toString(this))
            .setMessage(MR.strings.url_not_recognized_message.desc().toString(this).format(url))
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

    private fun handleExitPip() {
        SharedContext.exitPipMode()
        if (SharedContext.sharedVideoDetailViewModel.uiState.value.currentStreamInfo?.url == mediaController?.currentMediaItem?.mediaId) {
            SharedContext.sharedVideoDetailViewModel.showAsDetailPage()
        } else {
            SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
        }
    }


    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            wasInPipMode = true
        } else if (wasInPipMode) {
            // User returned to app by clicking fullscreen button
            handleExitPip()
            wasInPipMode = false
        }
    }

    override fun onStop() {
        super.onStop()
        // If in PiP mode and Activity is stopping, user closed the PiP window
        if (wasInPipMode && isInPictureInPictureMode) {
            mediaController?.pause()
            handleExitPip()
            wasInPipMode = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (SharedContext.isInPipMode.value && !isInPictureInPictureMode) {
            handleExitPip() // for unknown reason, onPictureInPictureModeChanged not triggered on some devices.
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
