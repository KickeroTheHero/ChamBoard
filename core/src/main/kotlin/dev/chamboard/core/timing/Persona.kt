package dev.chamboard.core.timing

import kotlin.random.Random

/**
 * A synthetic typist (TIM-1), part of a mask (MASK-1). Every parameter is drawn
 * from population-level ranges, so no fixed constant marks the tool, and none is
 * derived from the real user's timing (INV-6): there is deliberately no
 * constructor path that takes measured keystrokes.
 */
data class Persona(
    /** Overall speed in words per minute (5 chars per word). */
    val wpm: Double,
    /** Log-normal spread of character gaps; higher = more irregular. */
    val sigma: Double,
    /** Median extra pause after a space, ms. */
    val wordPauseMs: Double,
    /** Median extra pause after sentence-ending punctuation, ms. */
    val sentencePauseMs: Double,
    /** Chance of a long "thinking" pause at a word boundary. */
    val thinkProbability: Double,
    /** Shortest possible gap, ms (TIM-2). */
    val minGapMs: Long,
    /** Longest Typed playback before falling back to Glide, ms (TIM-4). */
    val maxTypedMs: Long,
    /** Multiplier (< 1) for common digraphs typed as a burst (TIM-7). */
    val burstFactor: Double,
    /** Share of words that get a typo-and-correction (TIM-8). */
    val typoRate: Double,
    /** Median pause before backspacing a typo, ms (TIM-8). */
    val reactionMs: Double,
    /** Chance of typing one more letter before noticing a typo (TIM-8). */
    val lookaheadProbability: Double,
) {
    /** Median gap between two characters on the home row, before layout and pause effects. */
    val baseGapMs: Double get() = MS_PER_MINUTE / (wpm * CHARS_PER_WORD) * BASE_GAP_SHARE

    companion object {
        // Population ranges for mobile typing; typical mobile speed is ~36 WPM (see docs/design/engine.md, Timing).
        val WPM_RANGE = 25.0..55.0
        val SIGMA_RANGE = 0.25..0.45
        val WORD_PAUSE_RANGE = 60.0..220.0
        val SENTENCE_PAUSE_RANGE = 250.0..700.0
        val THINK_PROBABILITY_RANGE = 0.01..0.05
        val MIN_GAP_RANGE = 20L..45L
        val MAX_TYPED_RANGE = 35_000L..60_000L
        val BURST_RANGE = 0.55..0.85
        val TYPO_RATE_RANGE = 0.005..0.03
        val REACTION_RANGE = 250.0..600.0
        val LOOKAHEAD_RANGE = 0.2..0.6

        private const val MS_PER_MINUTE = 60_000.0
        private const val CHARS_PER_WORD = 5.0

        // Pauses and layout effects account for the rest of the average gap.
        private const val BASE_GAP_SHARE = 0.75

        /** A random persona. Masks call this with their seeded source so it is stable per scope. */
        fun random(rng: Random): Persona = Persona(
            wpm = rng.nextIn(WPM_RANGE),
            sigma = rng.nextIn(SIGMA_RANGE),
            wordPauseMs = rng.nextIn(WORD_PAUSE_RANGE),
            sentencePauseMs = rng.nextIn(SENTENCE_PAUSE_RANGE),
            thinkProbability = rng.nextIn(THINK_PROBABILITY_RANGE),
            minGapMs = rng.nextLong(MIN_GAP_RANGE.first, MIN_GAP_RANGE.last + 1),
            maxTypedMs = rng.nextLong(MAX_TYPED_RANGE.first, MAX_TYPED_RANGE.last + 1),
            burstFactor = rng.nextIn(BURST_RANGE),
            typoRate = rng.nextIn(TYPO_RATE_RANGE),
            reactionMs = rng.nextIn(REACTION_RANGE),
            lookaheadProbability = rng.nextIn(LOOKAHEAD_RANGE),
        )

        private fun Random.nextIn(range: ClosedFloatingPointRange<Double>) = nextDouble(range.start, range.endInclusive)
    }
}
