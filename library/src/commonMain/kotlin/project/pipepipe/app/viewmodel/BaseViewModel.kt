package project.pipepipe.app.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import project.pipepipe.app.uistate.BaseUiState

abstract class BaseViewModel<S : BaseUiState>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    protected fun setState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }
}
