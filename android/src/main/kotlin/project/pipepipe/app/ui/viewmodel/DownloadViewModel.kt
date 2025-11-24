package project.pipepipe.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import project.pipepipe.app.uistate.DownloadItemState
import project.pipepipe.app.uistate.DownloadStatus
import project.pipepipe.app.uistate.DownloadUiState
import project.pipepipe.app.viewmodel.DownloadViewModel as SharedDownloadViewModel

/**
 * Android wrapper ViewModel for downloads
 * Delegates to shared ViewModel and provides Android-specific lifecycle
 */
class DownloadViewModel : ViewModel() {
    private val sharedViewModel = SharedDownloadViewModel()
    val uiState: StateFlow<DownloadUiState> = sharedViewModel.uiState

    /**
     * Refresh downloads list
     */
    fun refreshDownloads() {
        viewModelScope.launch {
            sharedViewModel.refreshDownloads()
        }
    }

    /**
     * Get downloads by status
     */
    fun getDownloadsByStatus(status: DownloadStatus, onResult: (List<DownloadItemState>) -> Unit) {
        viewModelScope.launch {
            val result = sharedViewModel.getDownloadsByStatus(status)
            onResult(result)
        }
    }

    /**
     * Get active downloads
     */
    fun getActiveDownloads(onResult: (List<DownloadItemState>) -> Unit) {
        viewModelScope.launch {
            val result = sharedViewModel.getActiveDownloads()
            onResult(result)
        }
    }
}
