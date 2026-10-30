package dev.chamboard.core.buffer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardAndLifecycleTest {

    private var now = 0L
    private val clock = { now }

    private fun storageOf(clip: PrivateClipboard): List<CharArray> {
        val field = PrivateClipboard::class.java.getDeclaredField("entries").apply { isAccessible = true }
        return (field.get(clip) as ArrayDeque<*>).map { e ->
            e!!::class.java.getDeclaredField("chars").apply { isAccessible = true }.get(e) as CharArray
        }
    }

    @Test
    fun copyAndPasteNewestFirst() { // CLIP-1
        val clip = PrivateClipboard(clock)
        clip.copy("one")
        clip.copy("two")
        assertEquals("two", clip.latest())
        assertEquals(listOf("two", "one"), clip.items())
    }

    @Test
    fun entriesExpireAfterTtlAndAreZeroed() { // CLIP-1, INV-3
        val clip = PrivateClipboard(clock, ttlMs = 1_000)
        clip.copy("secret")
        val chars = storageOf(clip).single()
        now = 999
        assertEquals("secret", clip.latest())
        now = 1_000
        assertNull(clip.latest())
        assertTrue(chars.all { it == '\u0000' })
    }

    @Test
    fun capacityEvictsAndZeroesOldest() { // CLIP-1
        val clip = PrivateClipboard(clock, capacity = 2)
        clip.copy("a1")
        val oldest = storageOf(clip).single()
        clip.copy("b2")
        clip.copy("c3")
        assertEquals(listOf("c3", "b2"), clip.items())
        assertTrue(oldest.all { it == '\u0000' })
    }

    @Test
    fun wipeClearsAndZeroesEverything() { // CLIP-1, INV-3
        val clip = PrivateClipboard(clock)
        clip.copy("x")
        clip.copy("y")
        val arrays = storageOf(clip)
        clip.wipe()
        assertEquals(0, clip.size)
        assertTrue(arrays.all { a -> a.all { it == '\u0000' } })
    }

    @Test
    fun emptyCopiesAreIgnored() { // CLIP-1
        val clip = PrivateClipboard(clock)
        clip.copy("")
        assertEquals(0, clip.size)
    }

    @Test
    fun draftSurvivesAShortHideButNotALongOne() { // BUF-3
        val life = DraftLifecycle(graceMs = 60_000)
        assertFalse(life.onHidden(0))
        assertEquals(60_000L, life.wipeDueAt())
        assertFalse(life.onShown(59_999))
        assertNull(life.wipeDueAt())
        life.onHidden(100_000)
        assertTrue(life.onShown(160_000))
    }

    @Test
    fun zeroGraceWipesOnHide() { // BUF-3
        assertTrue(DraftLifecycle(graceMs = 0).onHidden(0))
    }

    @Test
    fun screenOffAndFieldChangeAlwaysWipe() { // BUF-3, INV-7
        val life = DraftLifecycle(graceMs = 60_000)
        life.onHidden(0)
        assertTrue(life.onScreenOff())
        assertNull(life.wipeDueAt())
        assertTrue(life.onFieldChanged())
    }

    @Test
    fun showingWithoutHidingNeverWipes() { // BUF-3
        assertFalse(DraftLifecycle(graceMs = 0).onShown(5))
    }
}
