package com.moonlightxr.nativeui
import androidx.compose.runtime.*
import androidx.compose.foundation.ScrollState


enum class MenuPage { Home, Streaming, Wheel, Settings }
enum class SettingsResetScope { All, Ai3d, Picture }

/** Content is independent of its placement: inline fallback and XR Quad render the same popup. */
sealed interface FloatingPopup {
    data object Confirmation : FloatingPopup
    data object ExitApplication : FloatingPopup
    data class ResetSettings(val scope: SettingsResetScope) : FloatingPopup
    data class Choices(
        val title: String,
        val options: List<String>,
        val selected: String,
        val onSelected: (String) -> Unit,
    ) : FloatingPopup
    data class Help(val title: String, val description: String, val anchor: ParameterHelpAnchor,
                    val controlKey: String = title) : FloatingPopup {
        val owner: String get() = "${anchor.panelId}:$controlKey"
    }
}

/** UI-only state extracted from the product menu. No streaming or AI settings are owned here. */
class MenuUiState {
    internal val scrollRegistry = SpatialScrollRegistry()
    var pointerVisual by mutableStateOf<SpatialPointerVisual?>(null)
    var secondaryPointerVisual by mutableStateOf<SpatialPointerVisual?>(null)
    var cursorType by mutableStateOf("圆点")
    var menuLayout by mutableStateOf("六边形菜单")
    private var currentPage by mutableStateOf(MenuPage.Home)
    var page: MenuPage
        get() = currentPage
        set(value) {
            if (value != currentPage) { dismissInlineChoices(); hideParameterHelp() }
            if (currentPage == MenuPage.Wheel && value != currentPage) extensionCoordinator?.hide()
            currentPage = value
        }
    var category by mutableStateOf("基本")
    fun selectWheelCategory(name: String) {
        require(name.isNotBlank())
        dismissInlineChoices()
        if (category != name) {
            hideParameterHelp()
            extensionScrollState.dispatchRawDelta(-extensionScrollState.value.toFloat())
        }
        category = name
        wheelSelectedCategory = name
        extensionCoordinator?.show()
    }
    internal fun scrollSurface(panelId: String, hit: PixelHit, wheelUnits: Float): Boolean {
        val scope = when {
            inlineChoices?.anchor?.panelId == panelId -> SpatialScrollScope.InlineChoices
            modalPopupVisible -> SpatialScrollScope.Modal
            else -> SpatialScrollScope.Content
        }
        return scrollRegistry.scroll(panelId, scope, hit, wheelUnits) || scope != SpatialScrollScope.Content
    }
    var wheelSelectedCategory by mutableStateOf<String?>(null)
    var spatialFloatingHost by mutableStateOf<SpatialFloatingHost?>(null)
    internal val parameterHelpAnchors = mutableMapOf<String, ParameterHelpAnchor>()
    internal val parameterControlAnchors = mutableMapOf<String, ParameterHelpAnchor>()
    val choiceScrollState = ScrollState(0)
    val extensionScrollState = ScrollState(0)
    var inlineChoices by mutableStateOf<InlineChoices?>(null)
    var showDialog by mutableStateOf(false)
    var separatePopupAllowed by mutableStateOf(false)
    var onExitRequested: (() -> Unit)? = null
    var separateExtensionAllowed by mutableStateOf(false)
    var floatingPopup by mutableStateOf<FloatingPopup?>(null)
    var rejectedParameterHelpOwner by mutableStateOf<String?>(null)
    var rejectedSpatialModalId by mutableStateOf<String?>(null)
        private set
    val modalPopupVisible: Boolean get() = showDialog && floatingPopup !is FloatingPopup.Help
    val spatialModalId: String? get() = if (!showDialog || spatialFloatingHost == null) null else
        when (floatingPopup) {
            FloatingPopup.Confirmation -> "menu-confirmation"
            FloatingPopup.ExitApplication -> "app-exit-confirmation"
            is FloatingPopup.ResetSettings -> "settings-reset"
            is FloatingPopup.Choices -> "menu-choices"
            else -> null
        }
    var popupCoordinator: SpatialPopupCoordinator? = null
    var extensionCoordinator: SpatialExtensionCoordinator? = null

    fun showFloatingPopup(content: FloatingPopup) {
        dismissInlineChoices()
        if (showDialog) closeDialog()
        floatingPopup = content
        if (spatialFloatingHost != null) showDialog = true
        else popupCoordinator?.showPopup() ?: run { showDialog = true }
    }

    fun showParameterHelp(title: String, description: String, anchor: ParameterHelpAnchor,
                          controlKey: String = title) {
        if (modalPopupVisible || inlineChoices != null) return
        val help = FloatingPopup.Help(title, description, anchor, controlKey)
        if (floatingPopup == help && showDialog) return
        showFloatingPopup(help)
    }

    fun hideParameterHelp(owner: String? = null) {
        val help = floatingPopup as? FloatingPopup.Help ?: return
        if (owner == null || help.owner == owner) closeDialog()
    }

    fun showInlineChoices(title: String, options: List<String>, selected: String,
                          anchor: ParameterHelpAnchor, onSelected: (String) -> Unit) {
        showOwnedInlineChoices(title, options, selected, anchor, title, onSelected)
    }

    fun showOwnedInlineChoices(title: String, options: List<String>, selected: String,
                               anchor: ParameterHelpAnchor, controlKey: String,
                               onSelected: (String) -> Unit) {
        if (options.isEmpty() || modalPopupVisible) return
        hideParameterHelp()
        choiceScrollState.dispatchRawDelta(-choiceScrollState.value.toFloat())
        inlineChoices = InlineChoices(title, options, selected, anchor, onSelected, controlKey)
    }

    fun dismissOwnedInlineChoices(panelId: String, controlKey: String) {
        if (inlineChoices?.let { it.anchor.panelId == panelId && it.controlKey == controlKey } == true)
            dismissInlineChoices()
    }

    fun dismissInlineChoices() { inlineChoices = null }

    fun selectInlineChoice(option: String) {
        val choices = inlineChoices ?: return
        require(option in choices.options)
        choices.onSelected(option)
        dismissInlineChoices()
    }

    fun openDialog() = showFloatingPopup(FloatingPopup.Confirmation)
    fun requestExitConfirmation() {
        if (onExitRequested != null) showFloatingPopup(FloatingPopup.ExitApplication)
    }

    fun confirmExitApplication() {
        if (!showDialog || floatingPopup != FloatingPopup.ExitApplication) return
        val exit = onExitRequested ?: return
        closeDialog()
        exit()
    }
    fun confirmResetSettings(scope: SettingsResetScope = SettingsResetScope.All) =
        showFloatingPopup(FloatingPopup.ResetSettings(scope))

    fun showChoices(title: String, options: List<String>, selected: String, onSelected: (String) -> Unit) {
        showFloatingPopup(FloatingPopup.Choices(title, options, selected, onSelected))
    }

    fun closeDialog() {
        popupCoordinator?.dismissPopup() ?: run { showDialog = false }
        floatingPopup = null
        rejectedParameterHelpOwner = null
        rejectedSpatialModalId = null
    }

    /** Only a new, unconsumed outside press may cancel a modal; never confirm its action. */
    internal fun dismissModalFromOutsideRay(hit: UiQuadHit?): Boolean {
        if (!modalPopupVisible || hit != null) return false
        closeDialog()
        return true
    }

    /** Try the reserved popup slot when the shared spatial pool cannot present a modal. */
    fun reportSpatialModalAdmission(id: String, admitted: Boolean?) {
        if (spatialModalId != id || admitted != false || rejectedSpatialModalId == id) return
        rejectedSpatialModalId = id
        popupCoordinator?.showPopup()
    }

    fun reportParameterHelpAdmission(owner: String, admitted: Boolean?) {
        val help = floatingPopup as? FloatingPopup.Help ?: return
        if (help.owner != owner || admitted != false || rejectedParameterHelpOwner == owner) return
        rejectedParameterHelpOwner = owner
        popupCoordinator?.showPopup()
    }

    fun shouldShowInlineParameterHelp(panelId: String): Boolean {
        val help = floatingPopup as? FloatingPopup.Help ?: return false
        if (!showDialog || help.anchor.panelId != panelId || separatePopupAllowed) return false
        return (spatialFloatingHost == null || rejectedParameterHelpOwner == help.owner) &&
            popupCoordinator?.awaitingPresentation != true
    }

    fun openWheel() {
        dismissInlineChoices()
        hideParameterHelp()
        extensionCoordinator?.hide()
        extensionScrollState.dispatchRawDelta(-extensionScrollState.value.toFloat())
        wheelSelectedCategory = null
        page = MenuPage.Wheel
    }

}
