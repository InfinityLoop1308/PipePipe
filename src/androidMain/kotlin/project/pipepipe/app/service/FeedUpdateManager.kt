package project.pipepipe.app.service

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

object FeedUpdateManager {
    private val _workState = MutableStateFlow<FeedWorkState>(FeedWorkState.Idle)
    val workState: StateFlow<FeedWorkState> = _workState

    private var currentWorkId: UUID? = null

    fun startFeedUpdate(context: Context, groupId: Long = -1) {
        val workRequest = OneTimeWorkRequestBuilder<FeedWorker>()
            .setInputData(workDataOf("groupId" to groupId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        currentWorkId = workRequest.id

        WorkManager.getInstance(context).apply {
            enqueueUniqueWork(
                "feed_update_$groupId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt("progress", 0)
                        val completed = workInfo.progress.getInt("completed", 0)
                        val total = workInfo.progress.getInt("total", 0)
                        val failed = workInfo.progress.getInt("failed", 0)
                        _workState.value = FeedWorkState.Running(progress, completed, total, failed)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val completed = workInfo.outputData.getInt("completed", 0)
                        val failed = workInfo.outputData.getInt("failed", 0)
                        val total = workInfo.outputData.getInt("total", 0)
                        _workState.value = FeedWorkState.Success(completed, failed, total)
                    }
                    WorkInfo.State.FAILED -> {
                        val error = workInfo.outputData.getString("error")
                        _workState.value = FeedWorkState.Failed(error ?: "Unknown error")
                    }
                    WorkInfo.State.CANCELLED -> {
                        _workState.value = FeedWorkState.Idle
                    }
                    else -> {}
                }
            }
        }
    }

    fun cancelFeedUpdate(context: Context) {
        currentWorkId?.let {
            WorkManager.getInstance(context).cancelWorkById(it)
            currentWorkId = null
        }
        _workState.value = FeedWorkState.Idle
    }

    fun resetState() {
        _workState.value = FeedWorkState.Idle
    }
}

sealed class FeedWorkState {
    object Idle : FeedWorkState()
    data class Running(
        val progress: Int,
        val completed: Int,
        val total: Int,
        val failed: Int
    ) : FeedWorkState()
    data class Success(
        val completed: Int,
        val failed: Int,
        val total: Int
    ) : FeedWorkState()
    data class Failed(val error: String) : FeedWorkState()
}
