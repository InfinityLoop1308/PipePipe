package project.pipepipe.app.platform

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
