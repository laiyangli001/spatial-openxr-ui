package com.moonlightxr.nativeui

/** Routes spatial-keyboard input into the currently focused in-app text field. */
internal object VirtualKeyboardTarget {
    private var valueProvider: () -> String = { "" }
    private var updater: ((String) -> Unit)? = null
    var clipboardGet: () -> String = { "" }
    var clipboardSet: (String) -> Unit = {}

    val active: Boolean get() = updater != null

    fun attach(value: () -> String, update: (String) -> Unit) {
        valueProvider = value
        updater = update
    }

    fun detach() { updater = null }

    fun insert(text: String) {
        val update = updater ?: return
        update(valueProvider() + text)
    }

    fun backspace() {
        val update = updater ?: return
        update(valueProvider().dropLast(1))
    }

    fun clear() { updater?.invoke("") }
    fun copy() { clipboardSet(valueProvider()) }
    fun cut() { copy(); clear() }
    fun paste() { insert(clipboardGet()) }
}
