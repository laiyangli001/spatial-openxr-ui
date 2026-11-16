package com.moonlightxr.nativeui

import org.junit.Assert.*
import org.junit.Test

class SpatialInlineChoicesTest {
    private var selectedMode = "Mono"
    private var background = "Opaque"

    private val anchor = ParameterHelpAnchor("extension", 0.8f, 0.4f, 0.3f, 0.08f)

    @Test fun sameTitleControlsKeepIndependentOwnershipAndWriteCallbacks() {
        val state = MenuUiState()
        var firstWrites = 0
        var secondWrites = 0
        state.showOwnedInlineChoices("画质", listOf("高"), "高", anchor, "advanced:first") { firstWrites++ }
        state.showOwnedInlineChoices("画质", listOf("高"), "高", anchor, "advanced:second") { secondWrites++ }
        state.dismissOwnedInlineChoices("extension", "advanced:first")
        state.dismissOwnedInlineChoices("main", "advanced:second")
        assertEquals("advanced:second", state.inlineChoices?.controlKey)
        state.selectInlineChoice("高")
        assertEquals(0, firstWrites)
        assertEquals(1, secondWrites)
    }

    @Test fun removingCurrentOwnerDismissesWithoutWriting() {
        val state = MenuUiState()
        var writes = 0
        state.showOwnedInlineChoices("画质", listOf("高"), "高", anchor, "advanced:first") { writes++ }
        state.dismissOwnedInlineChoices("extension", "advanced:first")
        state.selectInlineChoice("高")
        assertNull(state.inlineChoices)
        assertEquals(0, writes)
    }

    @Test fun dismissingOldSameTitleHelpCannotHideTheReplacement() {
        val state = MenuUiState()
        state.showParameterHelp("画质", "第一个参数", anchor, "advanced:first")
        state.showParameterHelp("画质", "第二个参数", anchor, "advanced:second")
        state.hideParameterHelp("extension:advanced:first")
        assertEquals("第二个参数", (state.floatingPopup as FloatingPopup.Help).description)
        state.hideParameterHelp("extension:advanced:second")
        assertNull(state.floatingPopup)
    }

    @Test fun opensBelowWhenSpaceIsAvailable() {
        val p = placeInlineChoices(570f, 675f, anchor, 300f, 200f, 24f, 6f)
        assertEquals(303f, p.y, 0.01f)
        assertEquals(200f, p.height, 0.01f)
        assertTrue(p.x + p.width <= 546f)
    }

    @Test fun bottomPickerOpensUpAndNeverLeavesTheSourceQuad() {
        val bottom = anchor.copy(x = 0.98f, y = 0.92f)
        val p = placeInlineChoices(570f, 675f, bottom, 300f, 308f, 24f, 6f)
        assertTrue(p.y + p.height < bottom.y * 675f)
        assertTrue(p.x >= 24f && p.x + p.width <= 546f)
        assertTrue(p.y >= 24f && p.y + p.height <= 651f)
    }

    @Test fun longListsAreConstrainedToTheAvailableSpace() {
        val p = placeInlineChoices(570f, 675f, anchor.copy(x = 0.01f), 800f, 900f, 24f, 6f)
        assertEquals(522f, p.width, 0.01f)
        assertTrue(p.height < 900f)
        assertEquals(24f, p.x, 0.01f)
        assertTrue(p.y + p.height <= 651f)
    }

    @Test fun choiceSelectionDoesNotCreateAModalOrConsumeAnExtraLayer() {
        val state = MenuUiState()
        val main = UiQuad("main", QuadPose(Vec3(0f, 0f, -2f), Quat(0f, 0f, 0f, 1f)),
            2f, 1.125f, 1600, 900)
        val popup = popupInFrontOf(main, 0.12f, 0.9f, 0.5625f, 800, 500)
        var requests = 0
        val coordinator = SpatialPopupCoordinator(state, main, popup, object : PopupQuadBackend {
            override fun requestPopupQuad(quad: UiQuad, onReady: (Boolean) -> Unit) { requests++ }
            override fun releasePopupQuad(quad: UiQuad) {}
        }, LayerBudget(3, 0, 0))
        state.showInlineChoices("SBS", listOf("Mono", "Stereo"), selectedMode, anchor) { selectedMode = it }
        assertFalse(state.showDialog)
        assertFalse(state.modalPopupVisible)
        assertFalse(coordinator.plan().separatePopup)
        assertEquals(0, requests)
        state.showParameterHelp("SBS", "description", anchor)
        assertNull(state.floatingPopup)
        val option = listOf("Mono", "Stereo").last()
        state.selectInlineChoice(option)
        assertEquals(option, selectedMode)
        assertNull(state.inlineChoices)
    }

    @Test fun navigationAndModalDialogsDismissTheList() {
        val state = MenuUiState()
        fun open() = state.showInlineChoices("SBS", listOf("Mono", "Stereo"), selectedMode, anchor) {}
        open()
        state.page = MenuPage.Wheel
        assertNull(state.inlineChoices)
        open()
        state.selectWheelCategory("画面")
        assertNull(state.inlineChoices)
        open()
        state.openDialog()
        assertNull(state.inlineChoices)
        assertTrue(state.modalPopupVisible)
    }

    @Test fun replacementAndOutsideDismissDoNotModifyTheValue() {
        val state = MenuUiState()
        val original = background
        state.showInlineChoices("背景", listOf("Opaque", "Glass"), original, anchor) { background = it }
        state.showInlineChoices("SBS", listOf("Mono", "Stereo"), selectedMode, anchor) {}
        assertEquals("SBS", state.inlineChoices?.title)
        state.dismissInlineChoices()
        assertEquals(original, background)
    }
}
