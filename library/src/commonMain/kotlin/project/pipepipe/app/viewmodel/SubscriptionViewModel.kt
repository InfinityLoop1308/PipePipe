package project.pipepipe.app.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.SubscriptionsUiState

class SubscriptionsViewModel : BaseViewModel<SubscriptionsUiState>(SubscriptionsUiState()) {

    fun init() {
        viewModelScope.launch {
            setState {
                it.copy(common = it.common.copy(isLoading = true))
            }

            val feedGroup = DatabaseOperations.getAllFeedGroups()
            val subscriptions = DatabaseOperations.getAllSubscriptions()

            setState {
                it.copy(
                    common = it.common.copy(isLoading = false),
                    feedGroups = feedGroup,
                    subscriptions = subscriptions
                )
            }
        }
    }

    fun createFeedGroup(name: String, iconId: Int) {
        viewModelScope.launch {
            DatabaseOperations.insertFeedGroup(name, iconId.toLong())
            val feedGroup = DatabaseOperations.getAllFeedGroups()
            setState {
                it.copy(
                    common = it.common.copy(isLoading = false),
                    feedGroups = feedGroup,
                )
            }
        }
    }
}