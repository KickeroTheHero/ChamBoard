package dev.chamboard.core.buffer

/**
 * [text] with [mark] shown at [cursor], for displaying the draft (UI-1) without building
 * intermediate strings: it reads through to [text]. Whatever finally draws it
 * makes the one copy it needs.
 */
class CursorMarked(private val text: CharSequence, cursor: Int, private val mark: Char) : CharSequence {

    private val at = cursor.coerceIn(0, text.length)

    override val length: Int get() = text.length + 1

    override fun get(index: Int): Char = when {
        index !in 0 until length -> throw IndexOutOfBoundsException("index $index, length $length")
        index < at -> text[index]
        index == at -> mark
        else -> text[index - 1]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        val out = CharArray(endIndex - startIndex) { get(startIndex + it) }
        return String(out).also { out.fill('\u0000') }
    }

    override fun toString(): String = subSequence(0, length).toString()
}
