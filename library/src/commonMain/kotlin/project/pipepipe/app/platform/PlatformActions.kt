package project.pipepipe.app.platform

import androidx.compose.material3.ColorScheme
import kotlinx.coroutines.flow.StateFlow
import project.pipepipe.shared.infoitem.StreamInfo

/**
 * Platform-specific media actions interface.
 * Abstracts Android-specific operations like MediaController, PIP, and sharing
 * to allow commonMain code to trigger these actions.
 */
interface PlatformActions {
    /**
     * Feed update work state flow.
     */
    val feedWorkState: StateFlow<FeedWorkState>

    /**
     * Current screen orientation.
     */
    val screenOrientation: StateFlow<ScreenOrientation>

    /**
     * Enter Picture-in-Picture mode with the given stream.
     */
    fun enterPictureInPicture(streamInfo: StreamInfo)

    /**
     * Share a URL using the platform's share sheet.
     */
    fun share(url: String, title: String?)

    /**
     * Download an image to device storage.
     */
    fun downloadImage(url: String, filename: String)

    /**
     * Copy text to clipboard.
     */
    fun copyToClipboard(text: String, label: String? = null)

    /**
     * Send email using the platform's email client.
     */
    fun sendEmail(to: String, subject: String, body: String)

    /**
     * Get app version string.
     */
    fun getAppVersion(): String

    /**
     * Get device info string.
     */
    fun getDeviceInfo(): String

    /**
     * Get OS info string.
     */
    fun getOsInfo(): String

    /**
     * Get system country code (ISO 3166-1 alpha-2).
     * @return Two-letter country code, e.g., "US", "CN", "JP"
     */
    fun getSystemCountry(): String

    /**
     * Start feed update for a specific group.
     * @param groupId The feed group ID, -1 for all feeds
     */
    fun startFeedUpdate(groupId: Long = -1)

    /**
     * Reset feed update state to idle.
     */
    fun resetFeedState()

    /**
     * Open a URL in the system's default browser.
     * @param url The URL to open
     */
    fun openUrl(url: String)

    /**
     * Schedule notifications work for streams.
     */
    fun scheduleNotificationsWork()

    /**
     * Check for app update.
     * @param isManual True if triggered by user manually, false if automatic
     */
    fun checkForUpdate(isManual: Boolean)

    /**
     * Enter immersive video playback mode.
     * Hides system bars and locks screen orientation based on video aspect ratio.
     * @param isPortraitVideo True if the video is portrait orientation
     */
    fun enterImmersiveVideoMode(isPortraitVideo: Boolean)

    /**
     * Exit immersive video playback mode.
     * Restores system bars and unlocks screen orientation.
     */
    fun exitImmersiveVideoMode()

    /**
     * Start sleep timer.
     * @param minutes Duration in minutes, 0 to cancel
     */
    fun startSleepTimer(minutes: Int)

    /**
     * Stop the playback service completely.
     */
    fun stopPlaybackService()

    /**
     * Open the navigation drawer.
     */
    fun openDrawer()

    /**
     * Read the current system screen brightness.
     * @return Current brightness value between 0.0 and 1.0
     */
    fun readScreenBrightness(): Float

    /**
     * Set the screen brightness for the current window.
     * @param brightness Brightness value between 0.0 and 1.0, or -1 for system default
     */
    fun setScreenBrightness(brightness: Float)

    /**
     * Save the screen brightness to preferences for persistence.
     * @param brightness Brightness value between 0.0 and 1.0
     */
    fun saveScreenBrightness(brightness: Float)

    /**
     * Get the saved screen brightness from preferences.
     * @return Saved brightness value, or -1 if not set
     */
    fun getSavedScreenBrightness(): Float

    // ========== Volume Control ==========

    /**
     * Get the maximum system volume level.
     * @return Maximum volume level (platform-specific scale)
     */
    fun getMaxVolume(): Int

    /**
     * Get the current system volume as a fraction.
     * @return Current volume between 0.0 and 1.0
     */
    fun getCurrentVolume(): Float

    /**
     * Set the system volume.
     * @param volume Volume level between 0.0 and 1.0
     */
    fun setVolume(volume: Float)

    // ========== Screen State ==========

    /**
     * Keep the screen on or allow it to turn off.
     * @param enabled True to keep screen on, false to allow screen off
     */
    fun setKeepScreenOn(enabled: Boolean)

    // ========== System UI Control ==========

    fun setSystemBarsVisible(visible: Boolean, isFullscreen: Boolean, colorScheme: ColorScheme, isSystemDark: Boolean)
    fun applySystemBarColors(colorScheme: ColorScheme, isSystemDark: Boolean)
}

/**
 * Screen orientation enum, platform-independent.
 */
enum class ScreenOrientation {
    PORTRAIT,
    LANDSCAPE
}
