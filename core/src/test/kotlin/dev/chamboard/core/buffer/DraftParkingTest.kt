package dev.chamboard.core.buffer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftParkingTest {

    private var now = 0L
    private val parking = DraftParking({ now }, graceMs = 60_000)

    @Test
    fun sameFieldGetsItsDraftBackWithinGrace() { // BUF-3, INV-7
        parking.park("chat/42", "half a thought")
        now = 59_999
        assertEquals("half a thought", parking.takeFor("chat/42"))
        assertNull("taken once only", parking.takeFor("chat/42"))
    }

    @Test
    fun otherFieldsNeverGetIt() { // INV-7
        parking.park("chat/42", "for the chat")
        assertNull(parking.takeFor("browser/url"))
        assertEquals("for the chat", parking.takeFor("chat/42"))
    }

    @Test
    fun expiresAfterGrace() { // BUF-3
        parking.park("chat/42", "old")
        now = 60_000
        assertNull(parking.takeFor("chat/42"))
        assertTrue(parking.isEmpty)
    }

    @Test
    fun parkingAgainReplacesAndZeroesThePreviousDraft() { // INV-3
        parking.park("a", "first")
        val field = DraftParking::class.java.getDeclaredField("chars").apply { isAccessible = true }
        val old = field.get(parking) as CharArray
        parking.park("b", "second")
        assertTrue(old.all { it == '\u0000' })
        assertNull(parking.takeFor("a"))
    }

    @Test
    fun zeroGraceParksNothing() { // BUF-3
        val strict = DraftParking({ now }, graceMs = 0)
        strict.park("a", "text")
        assertTrue(strict.isEmpty)
    }
}
