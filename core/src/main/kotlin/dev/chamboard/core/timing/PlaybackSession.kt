package dev.chamboard.core.timing

/** How a stopped playback should be cleaned up. */
data class StopResult(
    /** Chars to delete before the cursor (uncorrected typo letters). */
    val deleteStray: Int,
    /** Text not yet sent; goes back into the draft (INV-7). */
    val remaining: String,
)

/**
 * One playback of a [PlaybackPlan] into one field (INV-7). The emitter asks
 * [nextFor] before every step, passing the current field's token; a different
 * token means the user moved to another field, and playback must stop without
 * touching anything.
 */
class PlaybackSession(val plan: PlaybackPlan, private val fieldToken: Long) {

    var executed: Int = 0
        private set

    val isFinished: Boolean get() = executed >= plan.chunks.size

    /** The next step if it may run in the field identified by [currentToken], else null. */
    fun nextFor(currentToken: Long): Chunk? =
        if (currentToken != fieldToken || isFinished) null else plan.chunks[executed]

    /** Call after a step's delete/commit has been sent to the field. */
    fun markExecuted() {
        check(!isFinished) { "playback already finished" }
        executed++
    }

    /** The user pressed Stop in the same field: remove stray typo letters, keep the rest as draft. */
    fun stopByUser(): StopResult {
        val p = plan.progress(executed)
        return StopResult(deleteStray = p.stray, remaining = plan.text.substring(p.correctPrefix))
    }

    /** The field changed or went away: never touch it again (INV-7); unsent text returns to the draft. */
    fun abortFieldChanged(): StopResult = StopResult(deleteStray = 0, remaining = plan.remainingText(executed))
}
