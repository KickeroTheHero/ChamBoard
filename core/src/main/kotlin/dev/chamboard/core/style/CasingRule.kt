package dev.chamboard.core.style

import dev.chamboard.core.style.Token.Kind

/**
 * STY-2 / STY-9: sentence starts follow the profile. Sentence-case profiles
 * capitalise all-lowercase starts; lowercase profiles lower-case capitalised
 * starts, except words the text also capitalises mid-sentence (likely names).
 * The pronoun "I" is always capitalised; "iPhone"/"NASA" styling is kept.
 */
internal object CasingRule {

    private val abbreviations = setOf(
        "etc", "vs", "eg", "ie", "approx", "mr", "mrs", "ms", "dr", "st", "jr", "sr", "no", "fig", "est",
    )

    fun apply(tokens: List<Token>, sentenceCase: Boolean = true): List<Token> {
        val names = if (sentenceCase) emptySet() else midSentenceCapitalised(tokens)
        val out = ArrayList<Token>(tokens.size)
        var sentenceStart = true
        for ((i, t) in tokens.withIndex()) {
            out += if (t.isWord) casedWord(t, sentenceStart, sentenceCase, names) else t
            // Spaces, emoticons, emoji and quotes don't end or start a sentence.
            sentenceStart = nextSentenceStart(tokens, i, sentenceStart)
        }
        return out
    }

    private fun casedWord(t: Token, sentenceStart: Boolean, sentenceCase: Boolean, names: Set<String>): Token {
        val text = t.text
        val lowered = text.lowercase()
        val isLower = text == lowered
        val isCapitalisedCommonWord = text == lowered.replaceFirstChar { it.uppercaseChar() } && text !in names
        return when {
            text == "i" || text.startsWith("i'") -> Token(Kind.WORD, "I" + text.drop(1))
            lowered == "i" || lowered.startsWith("i'") -> t
            // Only all-lowercase words: "iPhone" and "eBay" keep their styling.
            sentenceStart && sentenceCase && isLower -> Token(Kind.WORD, text.replaceFirstChar { it.uppercaseChar() })
            sentenceStart && !sentenceCase && isCapitalisedCommonWord -> Token(Kind.WORD, lowered)
            else -> t
        }
    }

    /** Capitalised words seen away from a sentence start: probably names, so never lowered. */
    private fun midSentenceCapitalised(tokens: List<Token>): Set<String> {
        val names = HashSet<String>()
        var sentenceStart = true
        for ((i, t) in tokens.withIndex()) {
            if (t.isWord && !sentenceStart && t.text.first().isUpperCase()) names += t.text
            sentenceStart = nextSentenceStart(tokens, i, sentenceStart)
        }
        return names
    }

    private fun nextSentenceStart(tokens: List<Token>, i: Int, current: Boolean): Boolean {
        val t = tokens[i]
        return when (t.kind) {
            Kind.NEWLINE -> true
            Kind.PUNCT -> if (TokenOps.isSentenceEnd(t)) !afterAbbreviation(tokens, i) else current
            Kind.WORD, Kind.NUMBER, Kind.PROTECTED -> false
            else -> current
        }
    }

    /** "e.g." / "Dr." / single initials don't end a sentence. */
    private fun afterAbbreviation(tokens: List<Token>, dot: Int): Boolean {
        if (tokens[dot].text != ".") return false
        val prev = tokens.getOrNull(dot - 1) ?: return false
        return prev.isWord && (prev.text.length == 1 || prev.text.lowercase() in abbreviations)
    }
}
