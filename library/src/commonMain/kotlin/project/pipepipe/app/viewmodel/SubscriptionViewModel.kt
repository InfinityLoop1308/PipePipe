package project.pipepipe.app.viewmodel

import project.pipepipe.app.database.DatabaseOperations
import project.pipepipe.app.uistate.SubscriptionsUiState

class SubscriptionsViewModel : BaseViewModel<SubscriptionsUiState>(SubscriptionsUiState()) {

    suspend fun init() {
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

    suspend fun createFeedGroup(name: String, iconId: Int){
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