package dev.chamboard.core.timing

import kotlin.random.Random

/** How emitted text is paced (TIM-3). */
enum class PlaybackStyle { TYPED, GLIDE, INSTANT }

/**
 * One emitter step: wait [delayMs], delete [deleteBefore] chars before the
 * cursor (a simulated backspace, TIM-8), then commit [text] (may be empty).
 */
data class Chunk(val text: String, val delayMs: Long, val deleteBefore: Int = 0)

/** State of the target field after some steps: the correct prefix of the text, plus uncorrected typo chars. */
data class Progress(val correctPrefix: Int, val stray: Int)

data class PlaybackPlan(
    val chunks: List<Chunk>,
    val style: PlaybackStyle,
    /** The exact text the field holds once every step has run. */
    val text: String,
    /** A TYPED request was switched to GLIDE because it would take too long (TIM-4). */
    val fellBackToGlide: Boolean = false,
) {
    val totalMs: Long get() = chunks.sumOf { it.delayMs }

    /** What the field contains after [steps] chunks (TIM-5): replays commits and backspaces. */
    fun progress(steps: Int): Progress {
        val field = StringBuilder()
        for (chunk in chunks.take(steps.coerceIn(0, chunks.size))) {
            field.setLength((field.length - chunk.deleteBefore).coerceAtLeast(0))
            field.append(chunk.text)
        }
        val common = field.commonPrefixWith(text).length
        return Progress(correctPrefix = common, stray = field.length - common)
    }

    /**
     * Text still to send after [steps] chunks, for returning to the buffer on
     * abort (INV-7). The emitter deletes [Progress.stray] chars first when it can.
     */
    fun remainingText(steps: Int): String = text.substring(progress(steps).correctPrefix)
}

/**
 * Turns finished text into timed steps. Inputs are the text, the mask's
 * [Persona] and a random source only — never the user's real timing (INV-6).
 */
class PlaybackPlanner(private val maxTypedMs: Long? = null) {

    fun plan(
        text: String,
        style: PlaybackStyle,
        persona: Persona,
        rng: Random,
        singleLine: Boolean = false,
    ): PlaybackPlan {
        val prepared = if (singleLine) text.replace(NEWLINES, " ") else text // TIM-6
        if (prepared.isEmpty()) return PlaybackPlan(emptyList(), style, prepared)
        return when (style) {
            PlaybackStyle.INSTANT -> PlaybackPlan(listOf(Chunk(prepared, 0)), style, prepared)
            PlaybackStyle.GLIDE -> PlaybackPlan(glide(prepared, persona, rng), style, prepared)
            PlaybackStyle.TYPED -> {
                val typed = PlaybackPlan(TypedComposer(persona, rng).compose(prepared), style, prepared)
                if (typed.totalMs <= (maxTypedMs ?: persona.maxTypedMs)) {
                    typed
                } else {
                    PlaybackPlan(glide(prepared, persona, rng), PlaybackStyle.GLIDE, prepared, fellBackToGlide = true)
                }
            }
        }
    }

    /** One chunk per word plus its trailing whitespace, like swipe typing (TIM-3). */
    private fun glide(text: String, persona: Persona, rng: Random): List<Chunk> {
        val timing = Timing(persona, rng)
        val words = WORD.findAll(text).map { it.value }.toList()
        return words.mapIndexed { i, w ->
            val delay = if (i == 0) {
                0L
            } else {
                val gesture = persona.baseGapMs * (w.trim().length + 1) * GLIDE_SPEEDUP
                val pause = timing.pauseAfter(words[i - 1].trimEnd().takeLast(1) + " ")
                timing.floor(timing.logNormal(gesture) + pause)
            }
            Chunk(w, delay)
        }
    }

    private companion object {
        const val GLIDE_SPEEDUP = 0.8
        val NEWLINES = Regex("\r\n|\r|\n")

        // Leading whitespace sticks to the first word; each word keeps its trailing whitespace.
        val WORD = Regex("""^\s+\S*\s*|\S+\s*""")
    }
}
