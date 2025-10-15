package project.pipepipe.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import project.pipepipe.shared.viewmodel.SubscriptionsViewModel

class SubscriptionsViewModel: ViewModel() {
    private val sharedViewModel = SubscriptionsViewModel()
    val uiState = sharedViewModel.uiState
    fun init() {
        viewModelScope.launch{ sharedViewModel.init() }
    }
    fun createFeedGroup(name: String, iconId: Int) {
        viewModelScope.launch{ sharedViewModel.createFeedGroup(name, iconId) }
    }
}