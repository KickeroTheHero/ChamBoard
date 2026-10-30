package dev.chamboard.ime

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Physical-keyboard keys edit the draft in staging modes (BUF-1). */
class HardwareKeysTest {

    private fun map(keyCode: Int, char: Int = 0, shortcut: Boolean = false) = HardwareKeys.map(keyCode, char, shortcut)

    @Test
    fun lettersDigitsAndPunctuationGoToTheDraft() { // BUF-1
        assertEquals(HardwareAction.Draft(Key.Text("a")), map(KeyEvent.KEYCODE_A, 'a'.code))
        assertEquals(HardwareAction.Draft(Key.Text("A")), map(KeyEvent.KEYCODE_A, 'A'.code))
        assertEquals(HardwareAction.Draft(Key.Text("7")), map(KeyEvent.KEYCODE_7, '7'.code))
        assertEquals(HardwareAction.Draft(Key.Text("?")), map(KeyEvent.KEYCODE_SLASH, '?'.code))
        assertEquals(HardwareAction.Draft(Key.Text("é")), map(KeyEvent.KEYCODE_E, 'é'.code))
    }

    @Test
    fun editingKeysMatchTheOnScreenKeys() { // BUF-1
        assertEquals(HardwareAction.Draft(Key.Backspace), map(KeyEvent.KEYCODE_DEL))
        assertEquals(HardwareAction.Draft(Key.Space), map(KeyEvent.KEYCODE_SPACE, ' '.code))
        assertEquals(HardwareAction.Draft(Key.Enter), map(KeyEvent.KEYCODE_ENTER, '\n'.code))
        assertEquals(HardwareAction.Draft(Key.Enter), map(KeyEvent.KEYCODE_NUMPAD_ENTER, '\n'.code))
        assertEquals(HardwareAction.MoveCursor(-1), map(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(HardwareAction.MoveCursor(1), map(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun ctrlEnterEmitsAndOtherShortcutsStayTheApps() { // UI-6
        assertEquals(HardwareAction.Draft(Key.Emit), map(KeyEvent.KEYCODE_ENTER, shortcut = true))
        assertEquals(HardwareAction.Pass, map(KeyEvent.KEYCODE_C, 'c'.code, shortcut = true))
    }

    @Test
    fun deadKeysWaitForTheNextLetter() {
        val acute = 0x0301
        assertEquals(
            HardwareAction.DeadKey(acute),
            map(
                KeyEvent.KEYCODE_APOSTROPHE,
                acute or KeyCharacterMap.COMBINING_ACCENT,
            ),
        )
    }

    @Test
    fun systemAndNavigationKeysPassThrough() {
        assertEquals(HardwareAction.Pass, map(KeyEvent.KEYCODE_BACK))
        assertEquals(HardwareAction.Pass, map(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(HardwareAction.Pass, map(KeyEvent.KEYCODE_TAB, '\t'.code))
        assertEquals(HardwareAction.Pass, map(KeyEvent.KEYCODE_ESCAPE, 27))
        assertEquals(HardwareAction.Pass, map(KeyEvent.KEYCODE_SHIFT_LEFT))
    }
}
