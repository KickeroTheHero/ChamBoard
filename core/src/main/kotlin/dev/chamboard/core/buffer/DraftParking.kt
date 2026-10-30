package dev.chamboard.core.buffer

/**
 * Holds the draft of the field the user just left (BUF-3, INV-7). Only the
 * same field can take it back, and only within [graceMs]; afterwards, on
 * screen-off, or when another draft is parked, it is zero-filled.
 */
class DraftParking(private val clock: () -> Long, var graceMs: Long) {

    private var fieldKey: String? = null
    private var chars: CharArray = CharArray(0)
    private var parkedAt = 0L

    val isEmpty: Boolean get() = expire().let { fieldKey == null }

    /** Parks [text] for [key], replacing (and wiping) anything parked before. */
    fun park(key: String, text: CharSequence) {
        wipe()
        if (text.isEmpty() || graceMs <= 0) return
        fieldKey = key
        chars = CharArray(text.length) { text[it] }
        parkedAt = clock()
    }

    /** The parked text if [key] is the field it was written for and the grace period hasn't run out. */
    fun takeFor(key: String): String? {
        expire()
        if (fieldKey != key) return null
        val text = String(chars)
        wipe()
        return text
    }

    fun wipe() {
        chars.fill('\u0000')
        chars = CharArray(0)
        fieldKey = null
    }

    private fun expire() {
        if (fieldKey != null && clock() - parkedAt >= graceMs) wipe()
    }
}
