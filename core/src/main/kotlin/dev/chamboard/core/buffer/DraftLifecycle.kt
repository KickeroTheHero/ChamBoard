package dev.chamboard.core.buffer

/**
 * When the draft must be wiped (BUF-3). The app reports lifecycle events and
 * wipes the [StagingBuffer] whenever a call returns true; it also schedules a
 * timer for [wipeDueAt] so a hidden draft is erased even if never reopened.
 */
class DraftLifecycle(private val graceMs: Long) {

    private var hiddenAt: Long? = null

    /** Keyboard hidden. Returns true if the draft must be wiped now (grace 0). */
    fun onHidden(now: Long): Boolean {
        hiddenAt = now
        return graceMs <= 0
    }

    /** Keyboard shown again for the same field. Returns true if the grace period ran out. */
    fun onShown(now: Long): Boolean {
        val hidden = hiddenAt ?: return false
        hiddenAt = null
        return now - hidden >= graceMs
    }

    /** When a still-hidden draft must be wiped, or null if the keyboard is showing. */
    fun wipeDueAt(): Long? = hiddenAt?.plus(graceMs)

    /** Screen off always wipes (BUF-3). */
    fun onScreenOff(): Boolean {
        hiddenAt = null
        return true
    }

    /** A different field always wipes, so a draft can't reach the wrong field (INV-7). */
    fun onFieldChanged(): Boolean {
        hiddenAt = null
        return true
    }
}
