package dev.chamboard.ime

import android.view.KeyCharacterMap
import android.view.KeyEvent

/** What a physical-keyboard key does while a staging mode is active (BUF-1). */
sealed interface HardwareAction {
    /** Edit the private draft exactly as the on-screen key would. */
    data class Draft(val key: Key) : HardwareAction

    /** Move the draft cursor by [delta] characters (arrow keys). */
    data class MoveCursor(val delta: Int) : HardwareAction

    /** A dead key (e.g. ´ on many European layouts): combine [accent] with the next letter. */
    data class DeadKey(val accent: Int) : HardwareAction

    /** Not a typing key (Back, volume, Tab, shortcuts…): let the system handle it. */
    data object Pass : HardwareAction
}

/**
 * Maps physical-keyboard events to draft edits (BUF-1). Pure: takes the key code, the
 * character the layout produces and whether Ctrl/Meta is held, so it is testable off-device.
 * In Passthrough the service never asks, and keys reach the field untouched (POL-4).
 */
object HardwareKeys {

    fun map(keyCode: Int, unicodeChar: Int, shortcut: Boolean): HardwareAction = when {
        // Ctrl/Meta+Enter sends the draft; other shortcuts (copy, select all…) stay the app's.
        shortcut -> if (isEnter(keyCode)) HardwareAction.Draft(Key.Emit) else HardwareAction.Pass
        keyCode == KeyEvent.KEYCODE_DEL -> HardwareAction.Draft(Key.Backspace)
        isEnter(keyCode) -> HardwareAction.Draft(Key.Enter)
        keyCode == KeyEvent.KEYCODE_SPACE -> HardwareAction.Draft(Key.Space)
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> HardwareAction.MoveCursor(-1)
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> HardwareAction.MoveCursor(1)
        unicodeChar and KeyCharacterMap.COMBINING_ACCENT != 0 ->
            HardwareAction.DeadKey(unicodeChar and KeyCharacterMap.COMBINING_ACCENT_MASK)
        isTyped(unicodeChar) -> HardwareAction.Draft(Key.Text(String(Character.toChars(unicodeChar))))
        else -> HardwareAction.Pass
    }

    private fun isEnter(keyCode: Int) = keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    // Tab, Escape and other control characters move focus or close things; they aren't text.
    private fun isTyped(unicodeChar: Int) = unicodeChar > 0 && !Character.isISOControl(unicodeChar)
}
