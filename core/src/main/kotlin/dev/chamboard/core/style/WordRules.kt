package dev.chamboard.core.style

import dev.chamboard.core.style.Token.Kind
import dev.chamboard.core.style.TokenOps.applyCase
import dev.chamboard.core.style.TokenOps.matchPhrase
import dev.chamboard.core.style.TokenOps.nextNonSpace
import dev.chamboard.core.style.TokenOps.words
import dev.chamboard.core.style.lexicon.WordLists
import kotlin.random.Random

/** Word-level rules (STY-3, STY-4, STY-5). Each takes a token list and returns a new one. */
internal object WordRules {

    /**
     * Replaces single words found in [map] (lowercase keys), keeping capitalisation.
     * With [plainCaseOnly], only "word" and "Word" match — not "WORD" (e.g. "US" is not "us").
     */
    fun replaceWords(tokens: List<Token>, map: Map<String, String>, plainCaseOnly: Boolean = false): List<Token> =
        tokens.flatMap { t ->
            val lower = t.text.lowercase()
            val replacement = map[lower]
            val caseOk = !plainCaseOnly || t.text == lower || t.text == lower.replaceFirstChar { it.uppercaseChar() }
            if (t.isWord && replacement != null && caseOk) words(applyCase(t.text, replacement)) else listOf(t)
        }

    /** Replaces multi-word phrases (lowercase, single-spaced keys), longest first. */
    fun replacePhrases(tokens: List<Token>, phrases: Map<String, String>): List<Token> {
        val ordered = phrases.entries.sortedByDescending { it.key.length }
        val out = ArrayList<Token>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val hit = if (tokens[i].isWord) {
                ordered.firstNotNullOfOrNull { (k, v) -> matchPhrase(tokens, i, k).takeIf { it >= 0 }?.let { it to v } }
            } else {
                null
            }
            if (hit == null) {
                out += tokens[i++]
            } else {
                out += words(applyCase(tokens[i].text, hit.second))
                i = hit.first + 1
            }
        }
        return out
    }

    /** STY-4: drops filler words/phrases and one adjacent comma. */
    fun removeFillers(tokens: List<Token>): List<Token> {
        val out = ArrayList<Token>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val end = fillerEnd(tokens, i)
            if (end < 0) {
                out += tokens[i++]
                continue
            }
            var resume = end + 1
            val after = nextNonSpace(tokens, end)
            if (after < tokens.size && TokenOps.isPunct(tokens[after], ",")) {
                resume = after + 1
            } else if (after >= tokens.size ||
                TokenOps.isSentenceEnd(tokens[after]) ||
                tokens[after].kind == Kind.NEWLINE
            ) {
                dropTrailingComma(out)
            }
            i = resume
        }
        return out
    }

    private fun fillerEnd(tokens: List<Token>, i: Int): Int {
        val t = tokens[i]
        if (!t.isWord) return -1
        if (t.text.lowercase() in WordLists.fillers) return i
        return WordLists.fillerPhrases.firstNotNullOfOrNull { p -> matchPhrase(tokens, i, p).takeIf { it >= 0 } } ?: -1
    }

    private fun dropTrailingComma(out: MutableList<Token>) {
        while (out.isNotEmpty() && out.last().isSpace) out.removeAt(out.lastIndex)
        if (out.isNotEmpty() && TokenOps.isPunct(out.last(), ",")) out.removeAt(out.lastIndex)
    }

    /** STY-3: every laugh style becomes one. */
    fun normaliseLaughter(tokens: List<Token>): List<Token> =
        tokens.map { t -> if (t.isWord && WordLists.laughter.matches(t.text)) Token(Kind.WORD, WordLists.LAUGH) else t }

    private val synonymIndex: Map<String, List<String>> =
        WordLists.synonymSets.flatMap { set -> set.map { it to set } }.toMap()

    /**
     * STY-5: each eligible word is, with probability [rate], replaced by a
     * uniform pick from its whole set. Random draws don't depend on which member
     * the author typed, so at rate 1.0 the output is identical for every variant (INV-5).
     */
    fun resampleSynonyms(tokens: List<Token>, rate: Double, rng: Random): List<Token> {
        if (rate <= 0.0) return tokens
        return tokens.map { t ->
            val set = if (t.isWord) synonymIndex[t.text.lowercase()] else null
            if (set == null) {
                t
            } else {
                val coin = rng.nextDouble()
                val pick = set[rng.nextInt(set.size)]
                if (coin < rate) Token(Kind.WORD, applyCase(t.text, pick)) else t
            }
        }
    }
}
