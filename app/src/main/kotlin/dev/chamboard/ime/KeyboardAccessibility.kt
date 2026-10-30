package dev.chamboard.ime

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.view.accessibility.AccessibilityNodeProvider

/** What TalkBack says for each key (UI-1..3). Pure, so it is testable off-device. */
object KeyDescriptions {

    private val fixed = mapOf<Key, String>(
        Key.Backspace to "Delete",
        Key.Space to "Space",
        Key.Enter to "Enter",
        Key.ToSymbols to "Symbols",
        Key.ToLetters to "Letters",
    )

    fun describe(key: Key, label: String, shift: KeyboardView.ShiftState, emitLabel: String): String = when (key) {
        is Key.Text -> if (shift != KeyboardView.ShiftState.OFF) label.uppercase() else label
        Key.Shift -> shiftName(shift)
        Key.Emit -> emitName(emitLabel)
        else -> fixed[key] ?: label
    }

    private fun shiftName(shift: KeyboardView.ShiftState) = when (shift) {
        KeyboardView.ShiftState.OFF -> "Shift"
        KeyboardView.ShiftState.ONCE -> "Shift, on"
        KeyboardView.ShiftState.LOCKED -> "Shift, caps lock"
    }

    // UI-2: the same key is EMIT, GO (emit and send) or STOP while sending.
    private fun emitName(emitLabel: String) = when (emitLabel) {
        "STOP" -> "Stop sending, keep the rest as a draft"
        "GO" -> "Emit and send the draft"
        else -> "Emit the draft"
    }
}

/**
 * Exposes each key of [KeyboardView] to TalkBack as its own virtual view, without
 * AndroidX: one node per key with its bounds, spoken name and a click action that types it.
 * Nothing typed passes through here; a node describes a key, never the draft (INV-3).
 */
class KeyboardAccessibility(private val host: KeyboardView) : AccessibilityNodeProvider() {

    private var focused = NONE
    private var hovered = NONE

    private fun spec(id: Int): KeySpec? = host.keys.getOrNull(id)?.second

    private fun describe(spec: KeySpec) = KeyDescriptions.describe(spec.key, spec.label, host.shift, host.emitLabel)

    private fun enabled(spec: KeySpec) = spec.key != Key.Emit || host.emitEnabled

    // AccessibilityNodeInfo(View) and AccessibilityEvent(Int) need API 30; minSdk is 29.
    @Suppress("DEPRECATION")
    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        if (virtualViewId == View.NO_ID) {
            val info = AccessibilityNodeInfo.obtain(host)
            host.onInitializeAccessibilityNodeInfo(info)
            host.keys.indices.forEach { info.addChild(host, it) }
            return info
        }
        val spec = spec(virtualViewId) ?: return null
        return AccessibilityNodeInfo.obtain(host, virtualViewId).apply {
            packageName = host.context.packageName
            className = BUTTON
            setParent(host)
            setSource(host, virtualViewId)
            setBoundsInScreen(screenBounds(virtualViewId))
            contentDescription = describe(spec)
            isEnabled = enabled(spec)
            isClickable = true
            isFocusable = true
            isVisibleToUser = true
            isAccessibilityFocused = focused == virtualViewId
            addAction(AccessibilityAction.ACTION_CLICK)
            addAction(
                if (focused == virtualViewId) {
                    AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
                } else {
                    AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS
                },
            )
        }
    }

    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (virtualViewId == View.NO_ID) return host.performAccessibilityAction(action, arguments)
        val spec = spec(virtualViewId) ?: return false
        return when (action) {
            AccessibilityNodeInfo.ACTION_CLICK -> {
                host.typeKey(spec)
                send(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                true
            }
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> moveFocus(virtualViewId, virtualViewId)
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> moveFocus(virtualViewId, NONE)
            else -> false
        }
    }

    private fun moveFocus(id: Int, to: Int): Boolean {
        val gaining = to != NONE
        if (gaining == (focused == id)) return false
        focused = to
        host.invalidate()
        send(
            id,
            if (gaining) {
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
            } else {
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
            },
        )
        return true
    }

    /** Explore by touch: announce the key under the finger as it moves; a negative [key] means none. */
    fun onHover(key: Int) {
        val index = if (key < 0) NONE else key
        if (index == hovered) return
        val previous = hovered
        hovered = index
        if (index != NONE) send(index, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        if (previous != NONE) send(previous, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
    }

    /** Shift, layout or EMIT label changed: TalkBack re-reads the keys. */
    fun contentChanged() {
        send(View.NO_ID, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    @Suppress("DEPRECATION") // see createAccessibilityNodeInfo
    private fun send(virtualViewId: Int, type: Int) {
        val parent = host.parent ?: return
        if (!host.isAttachedToWindow) return
        val spec = spec(virtualViewId)
        val event = AccessibilityEvent.obtain(type).apply {
            packageName = host.context.packageName
            if (spec == null) {
                setSource(host)
                className = host.javaClass.name
            } else {
                setSource(host, virtualViewId)
                className = BUTTON
                contentDescription = describe(spec)
                isEnabled = enabled(spec)
            }
        }
        parent.requestSendAccessibilityEvent(host, event)
    }

    private fun screenBounds(index: Int): Rect {
        val r = host.keys[index].first
        val origin = IntArray(2)
        host.getLocationOnScreen(origin)
        return Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()).apply {
            offset(origin[0], origin[1])
        }
    }

    private companion object {
        const val NONE = Int.MIN_VALUE
        const val BUTTON = "android.widget.Button"
    }
}
