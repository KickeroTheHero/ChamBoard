package dev.chamboard.core.buffer

/**
 * ChamBoard's own clipboard (CLIP-1): memory only, never the system
 * clipboard, a few entries, each expiring after [ttlMs]. Entries are stored as
 * [CharArray] and zero-filled when they expire, are evicted, or are wiped.
 */
class PrivateClipboard(
    private val clock: () -> Long,
    var ttlMs: Long = DEFAULT_TTL_MS,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private class Entry(val chars: CharArray, val copiedAt: Long)

    /** Newest first. */
    private val entries = ArrayDeque<Entry>()

    val size: Int get() = expire().let { entries.size }

    fun copy(text: CharSequence) {
        expire()
        if (text.isEmpty()) return
        entries.addFirst(Entry(CharArray(text.length) { text[it] }, clock()))
        while (entries.size > capacity) entries.removeLast().chars.fill('\u0000')
    }

    /** The newest live entry, or null. Every call creates a String copy; call only on an explicit paste. */
    fun latest(): String? = expire().let { entries.firstOrNull()?.let { String(it.chars) } }

    /** All live entries, newest first, for a picker. */
    fun items(): List<String> = expire().let { entries.map { String(it.chars) } }

    /** Zero-fills and drops everything (screen off, user wipe). */
    fun wipe() {
        entries.forEach { it.chars.fill('\u0000') }
        entries.clear()
    }

    private fun expire() {
        val now = clock()
        while (entries.isNotEmpty() && now - entries.last().copiedAt >= ttlMs) {
            entries.removeLast().chars.fill('\u0000')
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 10 * 60_000L
        const val DEFAULT_CAPACITY = 5
    }
}
