package project.pipepipe.app.platform

import androidx.compose.runtime.Composable

interface PlatformMenuItems {
    @Composable
    fun localPlaylistMenuItems()
    @Composable
    fun localPlaylistDialogs(playlistId: Long?)
}