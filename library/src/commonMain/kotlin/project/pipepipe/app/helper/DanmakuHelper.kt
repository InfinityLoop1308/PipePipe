package project.pipepipe.app.helper

import androidx.compose.runtime.Composable


expect object DanmakuHelper {
    /**
     * Android: SystemClock.elapsedRealtime()
     * iOS: CACurrentMediaTime() * 1000
     * Desktop: System.nanoTime() / 1_000_000
     */
    fun elapsedRealtimeMillis(): Long
    @Composable
    fun rememberPlatformTypeface(): Any?
}

