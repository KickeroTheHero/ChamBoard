package dev.chamboard.core.buffer

/**
 * The private draft the user edits while a staging mode is active (BUF-1).
 *
 * Backed by a [CharArray] rather than a [String] so the contents can be
 * overwritten in place: [wipe] zero-fills, and growing the array zero-fills the
 * old one (BUF-2). This is best-effort hygiene — any [snapshot] creates an
 * immutable copy the JVM may keep until garbage collection (see docs/design/engine.md).
 */
class StagingBuffer(initialCapacity: Int = 256, val maxLength: Int = MAX_LENGTH) {
    private var chars = CharArray(initialCapacity.coerceIn(1, maxLength))

    var length: Int = 0
        private set

    /** Insertion point, 0..[length]. */
    var cursor: Int = 0
        private set

    val isEmpty: Boolean get() = length == 0

    /**
     * Inserts [text] at the cursor and moves the cursor after it. Text beyond
     * [maxLength] is dropped (BUF-4); returns how many chars were inserted.
     */
    fun insert(text: CharSequence): Int {
        val count = minOf(text.length, maxLength - length)
        if (count <= 0) return 0
        ensureCapacity(length + count)
        chars.copyInto(chars, cursor + count, cursor, length)
        for (i in 0 until count) chars[cursor + i] = text[i]
        length += count
        cursor += count
        return count
    }

    /** Deletes the char (or surrogate pair) before the cursor, like a backspace key. */
    fun deleteBackward(): Boolean {
        if (cursor == 0) return false
        val width = if (cursor >= 2 &&
            chars[cursor - 1].isLowSurrogate() &&
            chars[cursor - 2].isHighSurrogate()
        ) {
            2
        } else {
            1
        }
        removeRange(cursor - width, cursor)
        return true
    }

    /** Deletes back to the start of the previous word (skipping trailing whitespace first). */
    fun deleteWordBackward(): Boolean {
        if (cursor == 0) return false
        var start = cursor
        while (start > 0 && chars[start - 1].isWhitespace()) start--
        while (start > 0 && !chars[start - 1].isWhitespace()) start--
        removeRange(start, cursor)
        return true
    }

    fun moveCursorTo(position: Int) {
        cursor = position.coerceIn(0, length)
    }

    fun moveCursorBy(delta: Int) = moveCursorTo(cursor + delta)

    /** Replaces the whole draft, e.g. to return unsent text after an aborted playback (INV-7). */
    fun replaceAll(text: CharSequence) {
        wipe()
        insert(text)
    }

    /** An immutable copy of the draft. Every call leaves a [String] on the heap; call sparingly. */
    fun snapshot(): String = String(chars, 0, length)

    /**
     * A read-only view of the draft that copies nothing (BUF-2): reading it reads
     * the array. It is live, so it changes with later edits; use it and drop it, don't keep it.
     */
    val view: CharSequence = LiveView()

    private inner class LiveView : CharSequence {
        override val length: Int get() = this@StagingBuffer.length

        override fun get(index: Int): Char {
            if (index !in 0 until length) throw IndexOutOfBoundsException("index $index, length $length")
            return chars[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            String(chars, startIndex, endIndex - startIndex)

        override fun toString(): String = snapshot()
    }

    /** Zero-fills the storage and resets to empty (BUF-3). */
    fun wipe() {
        chars.fill('\u0000')
        length = 0
        cursor = 0
    }

    private fun removeRange(start: Int, end: Int) {
        chars.copyInto(chars, start, end, length)
        val removed = end - start
        chars.fill('\u0000', length - removed, length)
        length -= removed
        cursor = start
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= chars.size) return
        val grown = CharArray(maxOf(needed, minOf(chars.size * 2, maxLength)))
        chars.copyInto(grown, 0, 0, length)
        chars.fill('\u0000')
        chars = grown
    }

    companion object {
        /** BUF-4: hard cap on draft size. */
        const val MAX_LENGTH = 20_000
    }
}
