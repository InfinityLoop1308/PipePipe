package project.pipepipe.app.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import project.pipepipe.shared.infoitem.Info


class BottomSheetMenuViewModel {
    private val _menuContent = MutableStateFlow<Info?>(null)
    val menuContent = _menuContent.asStateFlow()
    fun show(content: Info) {
        _menuContent.value = content
    }
    fun dismiss() {
        _menuContent.value = null
    }
}