package project.pipepipe.app.global

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import project.pipepipe.app.service.PlaybackService

object MediaControllerHolder {
    private const val TAG = "MediaControllerRepo"

    @Volatile
    private var controller: MediaController? = null
    private val mutex = Mutex()

    @OptIn(UnstableApi::class)
    suspend fun getInstance(context: Context): MediaController {
        controller?.let {
            if (it.isConnected) return it
        }

        return mutex.withLock {
            controller?.let {
                if (it.isConnected) return@withLock it
            }

            runCatching { controller?.release() }
            controller = null

            val appContext = context.applicationContext
            val sessionToken = SessionToken(
                appContext,
                ComponentName(appContext, PlaybackService::class.java)
            )

            val newController = MediaController.Builder(appContext, sessionToken)
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(ctrl: MediaController) {
                        Log.w(TAG, "Disconnected from service, will reconnect on next call")
                        if (controller === ctrl) {
                            controller = null
                        }
                    }
                })
                .buildAsync()
                .await()

            controller = newController
            newController
        }
    }

    suspend fun release() {
        mutex.withLock {
            controller?.release()
            controller = null
        }
    }
}