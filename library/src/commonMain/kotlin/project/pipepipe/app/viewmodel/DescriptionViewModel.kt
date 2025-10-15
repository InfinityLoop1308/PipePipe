//package project.pipepipe.shared.viewmodel
//
//import project.pipepipe.shared.infoitem.StreamInfo
//import project.pipepipe.shared.uistate.DescriptionUiState
//
//class DescriptionViewModel(streamInfo: StreamInfo) :
//    BaseViewModel<DescriptionUiState>(
//        initialState = DescriptionUiState(streamInfo = streamInfo)
//    ) {
//
//    fun toggleDescriptionSelection() {
//        setState {
//            it.copy(isDescriptionSelectable = !it.isDescriptionSelectable)
//        }
//    }
//
//    fun disableDescriptionSelection() {
//        setState {
//            it.copy(isDescriptionSelectable = false)
//        }
//    }
//
//    fun toggleStaffsVisibility() {
//        setState {
//            it.copy(isStaffsVisible = !it.isStaffsVisible)
//        }
//    }
//}