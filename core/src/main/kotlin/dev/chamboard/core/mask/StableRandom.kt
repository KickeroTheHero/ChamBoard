package dev.chamboard.core.mask

import kotlin.random.Random

/**
 * SplitMix64 behind the [Random] interface. Masks are derived from seeds
 * (MASK-3), so the same seed must give the same mask forever. `Random(seed)`
 * doesn't promise a stable algorithm across Kotlin versions; this does.
 */
class StableRandom(seed: Long) : Random() {
    private var state = seed

    @Suppress("MagicNumber") // Shift amounts are part of the published SplitMix64 algorithm.
    private fun nextLong64(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_1
        z = (z xor (z ushr 27)) * MIX_2
        return z xor (z ushr 31)
    }

    override fun nextBits(bitCount: Int): Int =
        if (bitCount == 0) 0 else (nextLong64() ushr (Long.SIZE_BITS - bitCount)).toInt()

    private companion object {
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL
        const val MIX_1 = -0x40a7b892e31b1a47L
        const val MIX_2 = -0x6b2fb644ecceee15L
    }
}
