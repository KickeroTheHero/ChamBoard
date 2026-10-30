package dev.chamboard.core.style

/** A slice of text with a coarse kind. Rules only ever rewrite WORD, PUNCT, SPACE, EMOTICON and OTHER tokens. */
data class Token(val kind: Kind, val text: String) {
    enum class Kind {
        /** Letters, possibly with internal apostrophes ("don't", "o'clock"). */
        WORD,

        /** Digits with internal separators: "3.5", "1,000", "10:30", "2nd". Never rewritten. */
        NUMBER,

        /** URLs, emails, @handles, #tags, `inline code`. Never rewritten (STY-1). */
        PROTECTED,
        EMOTICON,

        /** Spaces and tabs (never newlines). */
        SPACE,
        NEWLINE,

        /** One punctuation mark, or a run of the same family ("!!!", "...", "--"). */
        PUNCT,

        /** Anything else: emoji, symbols, one code point each. */
        OTHER,
    }

    val isWord get() = kind == Kind.WORD
    val isSpace get() = kind == Kind.SPACE
}

object Tokenizer {

    private val patterns: List<Pair<Token.Kind, Regex>> = listOf(
        Token.Kind.PROTECTED to Regex("""`[^`\n]*`"""),
        Token.Kind.PROTECTED to Regex("""(?:https?://|www\.)\S*[^\s.,!?;:)\]'"”’]"""),
        Token.Kind.PROTECTED to Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+"""),
        Token.Kind.PROTECTED to Regex("""[@#][\p{L}\p{N}_]+"""),
        Token.Kind.EMOTICON to Regex(EMOTICON),
        Token.Kind.NUMBER to Regex("""\p{N}+(?:[.,:/]\p{N}+)*(?:st|nd|rd|th|s|%|am|pm)?(?![\p{L}])"""),
        Token.Kind.WORD to Regex("""[\p{L}\p{M}]+(?:['’][\p{L}\p{M}]+)*"""),
        Token.Kind.NEWLINE to Regex("""\r?\n"""),
        Token.Kind.SPACE to Regex("""[ \t ]+"""),
        Token.Kind.PUNCT to Regex("""\.{2,}|(?:\. ){2,}\.|…|[!?]+|[-–—]{1,3}|,+|[.;:'"‘’“”„‟‚‛«»()\[\]{}/&*]"""),
    )

    fun tokenize(text: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val match = patterns.firstNotNullOfOrNull { (kind, re) ->
                re.matchAt(text, i)?.let { m -> Token(kind, m.value) }?.takeIf { it.text.isNotEmpty() }
            }
            val token = match ?: Token(Token.Kind.OTHER, String(Character.toChars(text.codePointAt(i))))
            out += token
            i += token.text.length
        }
        return out
    }

    // Eyes, optional nose, one or more mouth chars; plus "(:" and "<3". Not glued to letters or digits.
    private const val EMOTICON =
        """(?<![\p{L}\p{N}])(?:[:;=][-o^']?[)(\]\[DPpOo/\\|*3]+|\(:|<3)(?![\p{L}\p{N}])"""

    fun join(tokens: List<Token>): String = tokens.joinToString("") { it.text }
}
