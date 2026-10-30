package dev.chamboard.core.timing

import kotlin.math.hypot
import kotlin.math.ln

/**
 * Key positions on the QWERTY letter layout, in key-width units, used to make
 * gaps between far-apart keys longer than between neighbours (TIM-2).
 */
object KeyboardGeometry {

    private val rows = listOf("qwertyuiop" to 0.0, "asdfghjkl" to 0.5, "zxcvbnm" to 1.5)

    private val positions: Map<Char, Pair<Double, Double>> = buildMap {
        rows.forEachIndexed { y, (keys, offset) ->
            keys.forEachIndexed { x, c -> put(c, x + offset to y.toDouble()) }
        }
        put(' ', 4.5 to 3.0)
    }

    /** Euclidean distance between two keys, or null if either isn't on the letter layout. */
    fun distance(a: Char, b: Char): Double? {
        val pa = positions[a.lowercaseChar()]
        val pb = positions[b.lowercaseChar()]
        return if (pa == null || pb == null) null else hypot(pa.first - pb.first, pa.second - pb.second)
    }

    /**
     * Fitts-style movement factor around 1.0: ~0.5 for a repeated key, 1.0 at a
     * typical distance of three keys, rising logarithmically beyond.
     */
    fun movementFactor(previous: Char?, next: Char): Double {
        if (previous == null) return 1.0
        var factor =
            distance(previous, next)?.let { d -> (FITTS_A + log2(1 + d)) / (FITTS_A + log2(1 + TYPICAL_DISTANCE)) }
                ?: OFF_LAYOUT_FACTOR // digits/symbols need a layer switch
        if (next.isUpperCase()) factor += SHIFT_EXTRA
        return factor
    }

    /** Letter keys adjacent to [c] (same case), for plausible typos (TIM-8). */
    fun neighbours(c: Char): List<Char> {
        val lower = c.lowercaseChar()
        if (lower !in positions || lower == ' ') return emptyList()
        return positions.keys
            .filter { it != lower && it != ' ' && distance(lower, it)!! <= NEIGHBOUR_DISTANCE }
            .map { if (c.isUpperCase()) it.uppercaseChar() else it }
    }

    /** Frequent English letter pairs that practised typists hit as a burst (TIM-7). */
    private val burstDigraphs = setOf(
        "th", "he", "in", "er", "an", "re", "on", "at", "en", "nd", "ti", "es", "or", "te", "of", "ed", "is",
        "it", "al", "ar", "st", "to", "nt", "ng", "se", "ha", "as", "ou", "io", "le", "ve", "co", "me", "de",
        "hi", "ri", "ro", "ic", "ne", "ea", "ra", "ce",
    )

    fun isBurst(previous: Char?, next: Char): Boolean =
        previous != null && "${previous.lowercaseChar()}${next.lowercaseChar()}" in burstDigraphs

    private fun log2(x: Double) = ln(x) / ln(2.0)

    private const val NEIGHBOUR_DISTANCE = 1.2

    private const val FITTS_A = 2.0
    private const val TYPICAL_DISTANCE = 3.0
    private const val OFF_LAYOUT_FACTOR = 1.4
    private const val SHIFT_EXTRA = 0.4
}
