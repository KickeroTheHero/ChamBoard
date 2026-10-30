package dev.chamboard.ime

import android.os.Handler
import android.view.inputmethod.InputConnection
import dev.chamboard.core.timing.PlaybackPlan
import dev.chamboard.core.timing.PlaybackSession

/**
 * Runs a [PlaybackPlan] against the current field with real delays. All the
 * decisions (which step, when to stop, what to clean up) live in
 * [PlaybackSession]; this only drives the clock and the [InputConnection].
 */
class Emitter(
    private val handler: Handler,
    private val connection: () -> InputConnection?,
    private val fieldToken: () -> Long,
    private val listener: Listener,
) {
    interface Listener {
        fun onProgress(done: Int, total: Int)

        /** [unsent] is non-empty when playback was stopped or aborted early. */
        fun onEnded(completed: Boolean, unsent: String)
    }

    private var session: PlaybackSession? = null
    private val tick = Runnable { step() }

    val isRunning: Boolean get() = session != null

    fun start(plan: PlaybackPlan, token: Long) {
        cancelTimer()
        if (plan.chunks.isEmpty()) {
            listener.onEnded(completed = true, unsent = "")
            return
        }
        session = PlaybackSession(plan, token)
        handler.postDelayed(tick, plan.chunks.first().delayMs)
    }

    /** The user pressed Stop: clean stray typo letters, return the rest (TIM-5). */
    fun stopByUser() {
        val s = session ?: return
        cancelTimer()
        val result = s.stopByUser()
        if (result.deleteStray > 0) connection()?.deleteSurroundingText(result.deleteStray, 0)
        finish(completed = false, unsent = result.remaining)
    }

    /** The field changed or went away: touch nothing (INV-7). */
    fun abortFieldChanged() {
        val s = session ?: return
        cancelTimer()
        finish(completed = false, unsent = s.abortFieldChanged().remaining)
    }

    private fun step() {
        val s = session ?: return
        val chunk = s.nextFor(fieldToken())
        val ic = connection()
        if (chunk == null || ic == null) {
            if (s.isFinished) finish(completed = true, unsent = "") else abortFieldChanged()
            return
        }
        ic.beginBatchEdit()
        if (chunk.deleteBefore > 0) ic.deleteSurroundingText(chunk.deleteBefore, 0)
        if (chunk.text.isNotEmpty()) ic.commitText(chunk.text, 1)
        ic.endBatchEdit()
        s.markExecuted()
        listener.onProgress(s.executed, s.plan.chunks.size)
        if (s.isFinished) {
            finish(completed = true, unsent = "")
        } else {
            handler.postDelayed(tick, s.plan.chunks[s.executed].delayMs)
        }
    }

    private fun finish(completed: Boolean, unsent: String) {
        session = null
        listener.onEnded(completed, unsent)
    }

    private fun cancelTimer() = handler.removeCallbacks(tick)
}
