package dev.chamboard.core.style

import kotlin.random.Random

enum class SpellingRegion { US, UK }

/**
 * The "what" of style normalisation (STY-9): one form per habit. The level
 * decides which habits are overwritten; the profile decides what they become.
 * A mask samples one of these from population priors (MASK-1); [UNIFORM] is
 * the fixed house style (MASK-4).
 */
data class StyleProfile(
    val spelling: SpellingRegion,
    val contractions: ContractionPolicy,
    /** One of [LAUGHS]. */
    val laugh: String,
    /** `:-)` instead of `:)`. */
    val emoticonNose: Boolean,
    /** `…` instead of `...`. */
    val unicodeEllipsis: Boolean,
    /** One of [DASHES], used spaced between clauses. */
    val clauseDash: String,
    /** Capitalise sentence starts; false = common words start lowercase. */
    val sentenceCase: Boolean,
    /** Sentence-initial "Also," becomes "Additionally,". */
    val formalTransitions: Boolean,
    /** Target for "extremely"/"incredibly"/...: one of [INTENSIFIERS]. */
    val intensifier: String,
    /** One of [OK_FORMS]. */
    val okForm: String,
    /** Strong level: share of statement endings that get "!" (independent of the author's). */
    val exclamationRate: Double,
    /** Strong level: end the text with a full stop (true) or without (false). */
    val finalPeriod: Boolean,
) {
    companion object {
        val LAUGHS = listOf("haha", "lol", "hah", "😂")
        val DASHES = listOf("-", "—", "–")
        val INTENSIFIERS = listOf("very", "really")
        val OK_FORMS = listOf("OK", "ok", "okay")

        /** The fixed house style (MASK-4). */
        val UNIFORM = StyleProfile(
            spelling = SpellingRegion.US,
            contractions = ContractionPolicy.CONTRACT,
            laugh = "haha",
            emoticonNose = false,
            unicodeEllipsis = false,
            clauseDash = "-",
            sentenceCase = true,
            formalTransitions = false,
            intensifier = "very",
            okForm = "OK",
            exclamationRate = 0.0,
            finalPeriod = true,
        )

        /**
         * A random, plausible writer. Priors are documented guesses pending
         * calibration against public corpora (STY-9); they only need to be
         * varied and realistic, never derived from the user (INV-5).
         */
        fun sample(rng: Random): StyleProfile = StyleProfile(
            spelling = if (rng.chance(P_US_SPELLING)) SpellingRegion.US else SpellingRegion.UK,
            contractions = if (rng.chance(P_CONTRACT)) ContractionPolicy.CONTRACT else ContractionPolicy.EXPAND,
            laugh = rng.weighted(LAUGHS, LAUGH_WEIGHTS),
            emoticonNose = rng.chance(P_EMOTICON_NOSE),
            unicodeEllipsis = rng.chance(P_UNICODE_ELLIPSIS),
            clauseDash = rng.weighted(DASHES, DASH_WEIGHTS),
            sentenceCase = rng.chance(P_SENTENCE_CASE),
            formalTransitions = rng.chance(P_FORMAL),
            intensifier = rng.weighted(INTENSIFIERS, INTENSIFIER_WEIGHTS),
            okForm = rng.weighted(OK_FORMS, OK_WEIGHTS),
            exclamationRate = if (rng.chance(P_NO_EXCLAMATIONS)) 0.0 else rng.nextDouble(EXCLAMATION_RANGE),
            finalPeriod = rng.chance(P_FINAL_PERIOD),
        )

        // Priors: documented guesses pending calibration (STY-9).
        private const val P_US_SPELLING = 0.7
        private const val P_CONTRACT = 0.75
        private val LAUGH_WEIGHTS = listOf(0.45, 0.3, 0.1, 0.15)
        private const val P_EMOTICON_NOSE = 0.2
        private const val P_UNICODE_ELLIPSIS = 0.15
        private val DASH_WEIGHTS = listOf(0.6, 0.25, 0.15)
        private const val P_SENTENCE_CASE = 0.8
        private const val P_FORMAL = 0.2
        private val INTENSIFIER_WEIGHTS = listOf(0.5, 0.5)
        private val OK_WEIGHTS = listOf(0.3, 0.4, 0.3)
        private const val P_NO_EXCLAMATIONS = 0.6
        private val EXCLAMATION_RANGE = 0.02..0.15
        private const val P_FINAL_PERIOD = 0.65

        private fun Random.nextDouble(range: ClosedFloatingPointRange<Double>) =
            nextDouble(range.start, range.endInclusive)

        private fun Random.chance(p: Double) = nextDouble() < p

        private fun <T> Random.weighted(items: List<T>, weights: List<Double>): T {
            var r = nextDouble() * weights.sum()
            for ((i, w) in weights.withIndex()) {
                r -= w
                if (r < 0) return items[i]
            }
            return items.last()
        }
    }
}
