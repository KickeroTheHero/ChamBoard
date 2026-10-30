package dev.chamboard.core.style

import dev.chamboard.core.style.Token.Kind
import dev.chamboard.core.style.TokenOps.applyCase
import dev.chamboard.core.style.TokenOps.nextWord
import dev.chamboard.core.style.TokenOps.words
import dev.chamboard.core.style.lexicon.Contractions

/** STY-3 contraction policy: expand every variant to one long form, then optionally re-contract (INV-5). */
internal object ContractionRule {

    /** First half of the contraction rule: every variant to one long form (INV-5). */
    fun expandContractions(tokens: List<Token>): List<Token> {
        val aliased = WordRules.replacePhrases(tokens, Contractions.longFormAliases)
        return aliased.flatMapIndexed { i, t ->
            val expanded = if (t.isWord) expansionOf(t.text.lowercase(), aliased, i) else null
            if (expanded == null) listOf(t) else words(applyCase(t.text, expanded))
        }
    }

    private fun expansionOf(lower: String, tokens: List<Token>, i: Int): String? {
        val next = nextWord(tokens, i).takeIf { it >= 0 }?.let { tokens[it].text.lowercase() }
        return when {
            lower.endsWith("'s") && lower in Contractions.symmetric && next in Contractions.hasMarkers ->
                lower.removeSuffix("'s") + " has"
            lower.endsWith("'d") && lower in Contractions.alwaysExpand && next in Contractions.hadMarkers ->
                lower.removeSuffix("'d") + " had"
            else -> Contractions.alwaysExpand[lower] ?: Contractions.symmetric[lower]
        }
    }

    /** Second half under the CONTRACT policy: re-contract the safe pairs. */
    fun contract(tokens: List<Token>): List<Token> {
        val out = ArrayList<Token>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = if (t.isWord) nextWord(tokens, i) else -1
            val short = if (next >= 0) contractionFor(tokens, i, next) else null
            when {
                short != null -> {
                    out += Token(Kind.WORD, applyCase(t.text, short))
                    i = next + 1
                }
                t.isWord && t.text.lowercase() == "cannot" -> {
                    out += Token(Kind.WORD, applyCase(t.text, "can't"))
                    i++
                }
                else -> {
                    out += t
                    i++
                }
            }
        }
        return out
    }

    private fun contractionFor(tokens: List<Token>, first: Int, second: Int): String? {
        val a = tokens[first].text.lowercase()
        val short = Contractions.contractionOf["$a ${tokens[second].text.lowercase()}"] ?: return null
        if (Contractions.isNegation(short)) return short
        // Pronoun + be/will needs a following word ("that is, ..." and "yes I am." stay long).
        // Prefer "it isn't" over "it's not" so the result is canonical; "I'm not" has no alternative.
        val after = nextWord(tokens, second)
        val negationFollows = after >= 0 && tokens[after].text.lowercase() == "not" && a != "i"
        return if (after < 0 || negationFollows) null else short
    }
}
