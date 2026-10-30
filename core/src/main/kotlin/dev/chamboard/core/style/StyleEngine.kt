package dev.chamboard.core.style

import dev.chamboard.core.style.lexicon.Spelling
import dev.chamboard.core.style.lexicon.WordLists
import kotlin.random.Random

/** How much the normaliser changes (STY-2/3/4). Each level includes the ones before it. */
enum class StyleLevel { LIGHT, STANDARD, STRONG }

/**
 * Which form contractions end up in (STY-3). There is deliberately no "keep
 * as typed" option: that would pass the author's habit through (INV-5).
 */
enum class ContractionPolicy { CONTRACT, EXPAND }

data class StyleOptions(
    val level: StyleLevel = StyleLevel.STANDARD,
    /** What each overwritten habit becomes: a mask's profile, or [StyleProfile.UNIFORM] (MASK-4). */
    val profile: StyleProfile = StyleProfile.UNIFORM,
    /**
     * STY-5: share of eligible words resampled from their synonym set, 0.0–1.0.
     * Only 1.0 fully erases the choice; lower values erase it proportionally.
     */
    val synonymRate: Double = 0.0,
)

/** One changed span, for the side-by-side preview (STY-6). [start]/[end] index into [StyleResult.text]. */
data class StyleChange(val before: String, val after: String, val start: Int, val end: Int)

data class StyleResult(
    val text: String,
    val changes: List<StyleChange>,
    /** False when the text wasn't treated as English and was left unchanged (STY-7). */
    val applied: Boolean = true,
)

/**
 * The style normaliser: tokenise, protect spans, apply rules by level, and
 * report changed spans. Output depends on the text's content, never on which
 * variant of a habit the author used (INV-5).
 */
class StyleEngine(private val options: StyleOptions = StyleOptions()) {

    fun normalise(text: String, rng: Random = Random.Default): StyleResult {
        val tokens = Tokenizer.tokenize(text)
        if (!looksEnglish(tokens)) return StyleResult(text, emptyList(), applied = false)
        val output = Tokenizer.join(pipeline(tokens, rng))
        return StyleResult(output, ChangeDiff.between(text, output))
    }

    private fun pipeline(input: List<Token>, rng: Random): List<Token> {
        val standard = options.level >= StyleLevel.STANDARD
        val strong = options.level >= StyleLevel.STRONG
        val profile = options.profile
        var t = PunctuationRules.straightenQuotes(input)
        if (strong) t = WordRules.replaceWords(t, WordLists.abbreviations, plainCaseOnly = true)
        if (standard) t = lexical(t, profile)
        if (strong) t = WordRules.removeFillers(t)
        if (standard) t = ProfileRules.laugh(WordRules.normaliseLaughter(t), profile)
        t = ProfileRules.emoticonNose(PunctuationRules.canonicalEmoticons(t), profile)
        t = PunctuationRules.stripSkinTones(t)
        t = PunctuationRules.collapseRuns(t)
        if (standard) t = PunctuationRules.normaliseDashes(t, profile.clauseDash)
        if (strong) t = PunctuationRules.flattenEllipses(PunctuationRules.semicolonsToPeriods(t))
        t = SpacingRule.apply(t)
        if (standard) t = ProfileRules.formalTransitions(t, profile)
        t = WordRules.resampleSynonyms(t, options.synonymRate, rng)
        t = CasingRule.apply(t, profile.sentenceCase)
        if (strong) t = TerminalRule.apply(t, profile, rng)
        return ProfileRules.ellipsis(t, profile)
    }

    /** STY-3 word-level habits: canonical forms first, then the profile's forms. */
    private fun lexical(tokens: List<Token>, profile: StyleProfile): List<Token> {
        var t = WordRules.replaceWords(tokens, Spelling.all)
        t = ProfileRules.spelling(t, profile)
        t = ContractionRule.expandContractions(t)
        if (profile.contractions == ContractionPolicy.CONTRACT) t = ContractionRule.contract(t)
        t = WordRules.replacePhrases(t, WordLists.transitionPhrases)
        t = WordRules.replaceWords(t, WordLists.transitions)
        t = ProfileRules.intensifiers(t, profile)
        return ProfileRules.okForm(t, profile)
    }

    /** STY-7: English only. Short texts are assumed English; longer ones need common function words. */
    private fun looksEnglish(tokens: List<Token>): Boolean {
        val words = tokens.filter { it.isWord }
        if (words.size < MIN_WORDS_FOR_DETECTION) return true
        val markers = words.count { w ->
            val lower = w.text.lowercase().replace('’', '\'')
            lower in WordLists.englishMarkers ||
                lower in Spelling.all ||
                lower in WordLists.abbreviations ||
                lower in WordLists.fillers
        }
        return markers.toDouble() / words.size >= MIN_MARKER_SHARE
    }

    private companion object {
        const val MIN_WORDS_FOR_DETECTION = 6
        const val MIN_MARKER_SHARE = 0.15
    }
}
