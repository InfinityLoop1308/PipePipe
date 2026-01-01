package project.pipepipe.app.platform

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import dev.icerock.moko.resources.desc.desc
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import project.pipepipe.app.*
import project.pipepipe.app.global.MediaControllerHolder
import project.pipepipe.app.helper.ToastManager
import project.pipepipe.app.mediasource.toMediaItem
import project.pipepipe.app.service.*
import project.pipepipe.app.uistate.VideoDetailPageState
import project.pipepipe.shared.infoitem.StreamInfo

class AndroidActions(
    private val context: Context,
    private val drawerLayout: DrawerLayout?,
    override val feedWorkState: StateFlow<FeedWorkState>,
    private val onStartFeedUpdate: (Long) -> Unit,
    private val onResetFeedState: () -> Unit,
) : PlatformActions {


    private val _screenOrientation = MutableStateFlow(getCurrentOrientation())
    override val screenOrientation: StateFlow<ScreenOrientation> = _screenOrientation.asStateFlow()

    private fun getCurrentOrientation(): ScreenOrientation {
        val activity = context as? Activity ?: return ScreenOrientation.PORTRAIT
        return if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ScreenOrientation.LANDSCAPE
        } else {
            ScreenOrientation.PORTRAIT
        }
    }

    fun updateScreenOrientation() {
        _screenOrientation.value = getCurrentOrientation()
    }

    override suspend fun backgroundPlay(streamInfo: StreamInfo) {
        val controller = MediaControllerHolder.getInstance(context)
        MainScope().launch{
            controller.setPlaybackMode(PlaybackMode.AUDIO_ONLY)
            controller.playFromStreamInfo(streamInfo)
        }
    }

    override suspend fun enqueue(streamInfo: StreamInfo) {
        val controller = MediaControllerHolder.getInstance(context)
        MainScope().launch {
            controller.addMediaItem(streamInfo.toMediaItem())
            if (controller.mediaItemCount == 1) {
                controller.play()
            }
        }
    }

    override fun enterPictureInPicture(streamInfo: StreamInfo) {
        val activity = context as? MainActivity ?: return
        MainScope().launch {
            val controller = MediaControllerHolder.getInstance(context)
            SharedContext.sharedVideoDetailViewModel.loadVideoDetails(streamInfo.url, streamInfo.serviceId)
            SharedContext.enterPipmode()
            controller.setPlaybackMode(PlaybackMode.VIDEO_AUDIO)
            controller.playFromStreamInfo(streamInfo)
            SharedContext.sharedVideoDetailViewModel.setPageState(VideoDetailPageState.FULLSCREEN_PLAYER)
            activity.enterPipMode(streamInfo.isPortrait)
        }
    }

    override fun share(url: String, title: String?) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }

    override fun downloadImage(url: String, filename: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val request = DownloadManager.Request(url.toUri()).apply {
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "PipePipe/$filename")
            }
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        }
    }

    override fun copyToClipboard(text: String, label: String?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        ToastManager.show(MR.strings.msg_copied.desc().toString(context = context))
    }

    override fun sendEmail(to: String, subject: String, body: String) {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        val activities = context.packageManager.queryIntentActivities(emailIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (activities.isNotEmpty()) {
            context.startActivity(emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            ToastManager.show(MR.strings.no_email_app_available.desc().toString(context = context))
        }
    }

    override fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "${packageInfo.versionName} ($versionCode)"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    override fun getDeviceInfo(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})"
    }

    override fun getOsInfo(): String {
        return (System.getProperty("os.name") ?: "Linux") +
                " " + Build.VERSION.BASE_OS.ifEmpty { "Android" } +
                " " + Build.VERSION.RELEASE +
                " - " + Build.VERSION.SDK_INT
    }

    override fun startFeedUpdate(groupId: Long) {
        onStartFeedUpdate(groupId)
    }

    override fun resetFeedState() {
        onResetFeedState()
    }

    override fun playAll(items: List<StreamInfo>, startIndex: Int, shuffle: Boolean) {
        MainScope().launch {
            val controller = MediaControllerHolder.getInstance(context)
            controller.setPlaybackMode(PlaybackMode.AUDIO_ONLY)

            // Save items to database
            items.forEach { item ->
                project.pipepipe.app.database.DatabaseOperations.insertOrUpdateStream(item)
            }

            val mediaItems = items.map { it.toMediaItem() }
            controller.setShuffleModeEnabled(shuffle)
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.prepare()
            controller.play()

            if (SharedContext.sharedVideoDetailViewModel.uiState.value.pageState == VideoDetailPageState.HIDDEN) {
                kotlinx.coroutines.delay(500)
                SharedContext.sharedVideoDetailViewModel.showAsBottomPlayer()
            }
        }
    }

    override fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    override fun scheduleNotificationsWork() {
        StreamsNotificationManager.schedulePeriodicWork(context)
    }

    override fun checkForUpdate(isManual: Boolean) {
        UpdateCheckWorker.enqueueUpdateCheck(context, isManual)
    }

    override fun enterImmersiveVideoMode(isPortraitVideo: Boolean) {
        val activity = context as? Activity ?: return
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Hide system bars with swipe-to-show behavior
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Set screen orientation based on video aspect ratio if auto-rotate is disabled
        val isAutoRotateDisabled = Settings.System.getInt(
            activity.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        ) == 0

        if (isAutoRotateDisabled) {
            activity.requestedOrientation = if (isPortraitVideo) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    override fun exitImmersiveVideoMode() {
        val activity = context as? Activity ?: return

        // Check if auto-rotate is disabled
        val isAutoRotateDisabled = Settings.System.getInt(
            activity.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        ) == 0

        if (isAutoRotateDisabled) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    override fun startSleepTimer(minutes: Int) {
        if (minutes > 0) {
            SleepTimerService.startTimer(context, minutes)
        }
    }

    @androidx.media3.common.util.UnstableApi
    override fun stopPlaybackService() {
        MainScope().launch {
            val controller = MediaControllerHolder.getInstance(context)
            controller.sendCustomCommand(
                PlaybackService.CustomCommands.STOP_SERVICE_COMMAND,
                android.os.Bundle.EMPTY
            )
        }
    }

    override fun openDrawer() {
        drawerLayout?.openDrawer(androidx.core.view.GravityCompat.START)
    }

    override fun readScreenBrightness(): Float {
        val activity = context as? Activity
        val windowValue = activity?.window?.attributes?.screenBrightness
        if (windowValue != null && windowValue >= 0f) {
            return windowValue.coerceIn(0f, 1f)
        }
        val system = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        }.getOrDefault(128)
        return (system / 255f).coerceIn(0f, 1f)
    }

    override fun setScreenBrightness(brightness: Float) {
        val activity = context as? Activity ?: return
        activity.window?.let { window ->
            val lp = window.attributes
            lp.screenBrightness = brightness
            window.attributes = lp
        }
    }

    override fun saveScreenBrightness(brightness: Float) {
        SharedContext.settingsManager.putFloat("player_brightness", brightness)
    }

    override fun getSavedScreenBrightness(): Float {
        return SharedContext.settingsManager.getFloat("player_brightness", -1f)
    }

    // ========== Volume Control ==========

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun getMaxVolume(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    override fun getCurrentVolume(): Float {
        val maxVolume = getMaxVolume()
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()
    }

    override fun setVolume(volume: Float) {
        val maxVolume = getMaxVolume()
        val newVolume = (volume * maxVolume).toInt().coerceIn(0, maxVolume)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (newVolume != currentVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        }
    }

    // ========== Screen State ==========

    override fun setKeepScreenOn(enabled: Boolean) {
        val activity = context as? Activity ?: return
        if (enabled) {
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ========== System UI Control ==========

    override fun setSystemBarsVisible(visible: Boolean, isFullscreen: Boolean, colorScheme: ColorScheme, isSystemDark: Boolean) {
        val activity = context as? Activity ?: return
        val window = activity.window ?: return
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (isFullscreen) {
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            if (visible) {
                insetsController.show(
                    WindowInsetsCompat.Type.statusBars() or
                            WindowInsetsCompat.Type.navigationBars()
                )
            } else {
                insetsController.hide(
                    WindowInsetsCompat.Type.statusBars() or
                            WindowInsetsCompat.Type.navigationBars()
                )
            }
        } else {
            applySystemBarColors(colorScheme, isSystemDark)
            insetsController.show(
                WindowInsetsCompat.Type.statusBars() or
                        WindowInsetsCompat.Type.navigationBars()
            )
        }
    }

    override fun applySystemBarColors(colorScheme: ColorScheme, isSystemDark: Boolean) {
        val activity = context as? Activity ?: return
        val window = activity.window ?: return
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        val pureBlackEnabled = isPureBlackEnabled()
        val materialYouEnabled = isMaterialYouEnabled()
        val customPrimaryColor = getCustomPrimaryColor()
        val isDark = isDarkTheme(isSystemDark)

        val topBarColor = if (pureBlackEnabled && isDark) {
            Color.Black
        } else if (materialYouEnabled || customPrimaryColor == Color.White) {
            colorScheme.surface
        } else {
            if (isDark) {
                getCustomDarkColor(customPrimaryColor)
            } else {
                customPrimaryColor
            }
        }
        val useLightIcons = topBarColor.luminance() < 0.5f
        insetsController.isAppearanceLightStatusBars = !useLightIcons
        insetsController.isAppearanceLightNavigationBars = !isDark
    }
}
