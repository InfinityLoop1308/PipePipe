package project.pipepipe.app.helper

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import project.pipepipe.app.MR



actual object DanmakuHelper {
    @Composable
    actual fun rememberPlatformTypeface(): Any? {
        val context = LocalContext.current
        return remember {
            MR.fonts.lxgw_wenkai.getTypeface(context)
        }
    }

    actual fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

