package project.pipepipe.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import project.pipepipe.shared.uistate.DashboardUiState
import project.pipepipe.shared.viewmodel.DashboardViewModel as SharedDashboardViewModel

class DashboardViewModel : ViewModel() {
    private val sharedViewModel = SharedDashboardViewModel()
    val uiState: StateFlow<DashboardUiState> = sharedViewModel.uiState

    fun loadDashboard() {
        viewModelScope.launch {
            sharedViewModel.loadDashboard()
        }
    }
}
