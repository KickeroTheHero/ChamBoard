package dev.chamboard.core.buffer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StagingBufferTest {

    private fun storage(buffer: StagingBuffer): CharArray {
        val field = StagingBuffer::class.java.getDeclaredField("chars")
        field.isAccessible = true
        return field.get(buffer) as CharArray
    }

    @Test
    fun insertsAtCursorAndMovesIt() { // BUF-1
        val b = StagingBuffer()
        b.insert("helo")
        b.moveCursorTo(3)
        b.insert("l")
        assertEquals("hello", b.snapshot())
        assertEquals(4, b.cursor)
    }

    @Test
    fun deleteBackwardRemovesCharBeforeCursor() {
        val b = StagingBuffer()
        b.insert("abc")
        b.moveCursorBy(-1)
        assertTrue(b.deleteBackward())
        assertEquals("ac", b.snapshot())
        assertEquals(1, b.cursor)
        b.moveCursorTo(0)
        assertFalse(b.deleteBackward())
    }

    @Test
    fun deleteBackwardRemovesWholeSurrogatePair() {
        val b = StagingBuffer()
        b.insert("hi 👍")
        b.deleteBackward()
        assertEquals("hi ", b.snapshot())
    }

    @Test
    fun deleteWordBackwardSkipsTrailingSpaces() {
        val b = StagingBuffer()
        b.insert("one two  ")
        b.deleteWordBackward()
        assertEquals("one ", b.snapshot())
    }

    @Test
    fun growsBeyondInitialCapacityAndZeroesOldStorage() { // BUF-2
        val b = StagingBuffer(initialCapacity = 4)
        val old = storage(b)
        b.insert("abcdefgh")
        assertEquals("abcdefgh", b.snapshot())
        assertTrue("old array must be zero-filled", old.all { it == '\u0000' })
    }

    @Test
    fun wipeZeroFillsStorage() { // BUF-3, INV-3
        val b = StagingBuffer()
        b.insert("secret draft")
        b.wipe()
        assertTrue(b.isEmpty)
        assertEquals(0, b.cursor)
        assertTrue(storage(b).all { it == '\u0000' })
    }

    @Test
    fun deletionsLeaveNoStaleCharsBehindLength() { // BUF-2
        val b = StagingBuffer()
        b.insert("secret")
        b.deleteWordBackward()
        assertTrue(storage(b).all { it == '\u0000' })
    }

    @Test
    fun capsLengthAtMaximum() { // BUF-4
        val b = StagingBuffer(initialCapacity = 2, maxLength = 5)
        assertEquals(5, b.insert("abcdefg"))
        assertEquals(0, b.insert("x"))
        assertEquals("abcde", b.snapshot())
    }

    @Test
    fun viewReadsTheDraftWithoutCopyingAndFollowsEdits() { // BUF-2
        val b = StagingBuffer(initialCapacity = 2)
        val view = b.view
        b.insert("draft")
        assertEquals(5, view.length)
        assertEquals('d', view[0])
        assertEquals("aft", view.subSequence(2, 5).toString())
        b.insert(" grows") // forces the array to grow; the view follows
        assertEquals("draft grows", view.toString())
        b.wipe()
        assertEquals(0, view.length)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun viewRefusesReadsPastTheDraft() { // BUF-2: stale chars past length are never exposed
        val b = StagingBuffer()
        b.insert("ab")
        b.deleteBackward()
        b.view[1]
    }

    @Test
    fun cursorMarkShowsTheCursorInsideTheText() { // UI-1
        val b = StagingBuffer()
        b.insert("helo")
        assertEquals("he|lo", CursorMarked(b.view, 2, '|').toString())
        assertEquals("|helo", CursorMarked(b.view, 0, '|').toString())
        assertEquals("helo|", CursorMarked(b.view, 4, '|').toString())
        assertEquals("|", CursorMarked("", 0, '|').toString())
        assertEquals("el", CursorMarked(b.view, 0, '|').subSequence(2, 4).toString())
    }

    @Test
    fun replaceAllRestoresUnsentText() { // INV-7
        val b = StagingBuffer()
        b.insert("already sent and unsent")
        b.replaceAll("unsent")
        assertEquals("unsent", b.snapshot())
        assertEquals(6, b.cursor)
    }
}
