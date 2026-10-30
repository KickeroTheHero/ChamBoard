package dev.chamboard.ime

import dev.chamboard.ime.KeyboardView.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TalkBack names for every key (UI-1..3). */
class KeyDescriptionsTest {

    private fun say(spec: KeySpec, shift: ShiftState = ShiftState.OFF, emit: String = "EMIT") =
        KeyDescriptions.describe(spec.key, spec.label, shift, emit)

    @Test
    fun everyKeyOnBothPagesHasASpokenName() { // UI-1..3
        for (spec in (Layouts.LETTERS + Layouts.SYMBOLS).flatten()) {
            val spoken = say(spec)
            assertTrue("no name for ${spec.label}", spoken.isNotBlank())
            assertTrue("glyph read as-is for ${spec.label}", spoken !in setOf("⇧", "⌫", "↵"))
        }
    }

    @Test
    fun lettersFollowShift() {
        val a = KeySpec(Key.Text("a"), "a")
        assertEquals("a", say(a))
        assertEquals("A", say(a, ShiftState.ONCE))
        assertEquals("A", say(a, ShiftState.LOCKED))
    }

    @Test
    fun shiftSaysItsState() {
        val shift = KeySpec(Key.Shift, "⇧")
        assertEquals("Shift", say(shift))
        assertEquals("Shift, on", say(shift, ShiftState.ONCE))
        assertEquals("Shift, caps lock", say(shift, ShiftState.LOCKED))
    }

    @Test
    fun emitKeyFollowsItsLabel() { // UI-2
        val emit = KeySpec(Key.Emit, "EMIT")
        assertEquals("Emit the draft", say(emit))
        assertEquals("Emit and send the draft", say(emit, emit = "GO"))
        assertEquals("Stop sending, keep the rest as a draft", say(emit, emit = "STOP"))
    }
}
