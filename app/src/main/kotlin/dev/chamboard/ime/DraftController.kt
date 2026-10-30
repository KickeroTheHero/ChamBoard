package dev.chamboard.ime

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.chamboard.core.buffer.StagingBuffer
import dev.chamboard.core.settings.KeyboardSettings
import dev.chamboard.core.style.StyleEngine
import dev.chamboard.core.style.StyleOptions
import dev.chamboard.core.style.StyleResult
import dev.chamboard.core.timing.PlaybackPlanner
import dev.chamboard.core.timing.PlaybackStyle
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * What each key does in the current mode. Staging modes edit the private draft
 * (BUF-1) and emit through the style engine and planner; Passthrough commits
 * straight to the field.
 */
class DraftController(private val host: Host) {

    interface Host {
        val field: FieldState
        val settings: KeyboardSettings
        val emitter: Emitter
        fun connection(): InputConnection?
        fun sendKey(keyCode: Int)
        fun performFieldAction(): Boolean
        fun refreshUi()
    }

    val buffer = StagingBuffer()
    val suggestions = DraftSuggestions(host, buffer)
    private val planner = PlaybackPlanner()
    private val secureRandom = SecureRandom().asKotlinRandom()

    /** Seeds the preview so what's shown is exactly what's sent (STY-6). */
    private var previewSeed = secureRandom.nextLong()

    /** Perform the field's action after playback completes (Emit & Go / Enter = Emit & Send). */
    private var actionAfterPlayback = false

    fun onKey(key: Key) {
        val staging = host.field.decision.mode.stages
        when {
            key == Key.Emit -> if (host.emitter.isRunning) {
                host.emitter.stopByUser()
            } else {
                emit(
                    host.field.decision.emitAndGo,
                )
            }
            host.emitter.isRunning -> Unit // the draft is locked while sending
            staging -> onStagedKey(key)
            else -> onPassthroughKey(key)
        }
        host.refreshUi()
    }

    private fun onStagedKey(key: Key) {
        when (key) {
            is Key.Text -> buffer.insert(key.value)
            Key.Space -> buffer.insert(" ")
            Key.Backspace -> buffer.deleteBackward()
            Key.Enter -> when {
                !host.field.decision.singleLine -> buffer.insert("\n")
                host.settings.enterEmitsAndSends -> emit(andAction = true) // UI-6
                else -> emit(andAction = false)
            }
            else -> Unit
        }
    }

    private fun onPassthroughKey(key: Key) {
        val ic = host.connection() ?: return
        when (key) {
            is Key.Text -> ic.commitText(key.value, 1)
            Key.Space -> ic.commitText(" ", 1)
            Key.Backspace -> host.sendKey(KeyEvent.KEYCODE_DEL)
            Key.Enter -> if (host.field.decision.singleLine) {
                if (!host.performFieldAction()) host.sendKey(KeyEvent.KEYCODE_ENTER)
            } else {
                ic.commitText("\n", 1)
            }
            else -> Unit
        }
    }

    /** Insert text into the draft (paste) or the field (Passthrough). */
    fun insert(text: String) {
        if (host.field.decision.mode.stages) buffer.insert(text) else host.connection()?.commitText(text, 1)
        host.refreshUi()
    }

    /** What would be sent right now, for the preview; null when the mode doesn't rewrite. */
    fun preview(): StyleResult? {
        if (!host.field.decision.mode.rewrites || buffer.isEmpty) return null
        return engine().normalise(buffer.snapshot(), Random(previewSeed))
    }

    private fun emit(andAction: Boolean) {
        val decision = host.field.decision
        if (buffer.isEmpty) {
            if (andAction) host.performFieldAction()
            return
        }
        val draft = buffer.snapshot()
        val text = if (decision.mode.rewrites) engine().normalise(draft, Random(previewSeed)).text else draft
        val style = if (decision.mode.paced) host.settings.playback else PlaybackStyle.INSTANT
        val plan = planner.plan(text, style, host.field.mask.timing, secureRandom, decision.singleLine)
        buffer.wipe() // BUF-3: successful emit wipes; unsent text comes back via onPlaybackEnded
        previewSeed = secureRandom.nextLong()
        actionAfterPlayback = andAction
        host.emitter.start(plan, host.field.token)
    }

    /** Called by the service when playback ends in the same field. */
    fun onPlaybackEnded(completed: Boolean, unsent: String) {
        if (completed && actionAfterPlayback) host.performFieldAction()
        if (unsent.isNotEmpty()) buffer.replaceAll(unsent)
        actionAfterPlayback = false
        host.refreshUi()
    }

    /** New draft, new preview seed: wipe everything about the old one. */
    fun wipe() {
        buffer.wipe()
        previewSeed = secureRandom.nextLong()
    }

    /** Auto-capitalise at the start of a draft or sentence (when the style engine won't). */
    fun shouldAutoShift(): Boolean {
        if (!host.field.decision.mode.stages) {
            val info = host.field.editorInfo ?: return false
            return (host.connection()?.getCursorCapsMode(info.inputType) ?: 0) != 0
        }
        // Read the draft in place rather than copying it on every key.
        val text = buffer.view
        var i = buffer.cursor
        while (i > 0 && text[i - 1] == ' ') i--
        return i == 0 || text[i - 1] in ".!?\n"
    }

    private fun engine() = StyleEngine(
        StyleOptions(
            level = host.settings.styleLevel,
            profile = host.field.mask.style,
            synonymRate = host.settings.synonymRate,
        ),
    )
}
