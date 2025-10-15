package project.pipepipe.app.helper

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


object ToastManager {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var job: Job? = null

    fun show(msg: String, timeout: Long = 3000) {
        job?.cancel()
        job = GlobalScope.launch {
            _message.value = msg
            delay(timeout)
            _message.value = null
        }
    }
}