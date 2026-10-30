package dev.chamboard.core.timing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Shared random timing primitives for one plan (TIM-2). */
internal class Timing(private val persona: Persona, private val rng: Random) {

    fun logNormal(median: Double): Double = median * exp(persona.sigma * gaussian())

    fun floor(ms: Double): Long = ms.toLong().coerceAtLeast(persona.minGapMs)

    /** Gap before typing [next] after [previous]: key distance, shift, digraph bursts (TIM-2, TIM-7). */
    fun keyGap(previous: Char?, next: Char): Double {
        var median = persona.baseGapMs * KeyboardGeometry.movementFactor(previous, next)
        if (KeyboardGeometry.isBurst(previous, next)) median *= persona.burstFactor
        return logNormal(median)
    }

    /** Extra pause following [previous]: word, sentence, and rare thinking pauses. */
    fun pauseAfter(previous: String): Double {
        val last = previous.lastOrNull() ?: return 0.0
        val beforeLast = previous.dropLast(1).lastOrNull()
        return when {
            last == '\n' -> logNormal(persona.sentencePauseMs)
            last.isWhitespace() && beforeLast != null && beforeLast in SENTENCE_END -> logNormal(
                persona.sentencePauseMs,
            )
            last.isWhitespace() -> logNormal(persona.wordPauseMs) + thinkingPause()
            else -> 0.0
        }
    }

    fun reaction(): Double = logNormal(persona.reactionMs)

    fun chance(p: Double): Boolean = rng.nextDouble() < p

    fun <T> pick(items: List<T>): T = items[rng.nextInt(items.size)]

    fun nextInt(until: Int): Int = rng.nextInt(until)

    private fun thinkingPause(): Double =
        if (rng.nextDouble() < persona.thinkProbability) rng.nextDouble(THINK_MIN_MS, THINK_MAX_MS) else 0.0

    // Box–Muller; 1 - nextDouble() avoids ln(0).
    private fun gaussian(): Double = sqrt(2.0 * -ln(1.0 - rng.nextDouble())) * cos(TWO_PI * rng.nextDouble())

    private companion object {
        const val TWO_PI = 2.0 * PI
        const val THINK_MIN_MS = 600.0
        const val THINK_MAX_MS = 2_500.0
        val SENTENCE_END = setOf('.', '!', '?')
    }
}
