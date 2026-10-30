package dev.chamboard.core.style

import dev.chamboard.core.style.Token.Kind
import dev.chamboard.core.style.TokenOps.space
import dev.chamboard.core.style.lexicon.WordLists

/** Punctuation, spacing and symbol rules (STY-2, STY-3, STY-4). */
internal object PunctuationRules {

    private val singleQuotes = setOf("‘", "’", "‚", "‛")
    private val doubleQuotes = setOf("“", "”", "„", "‟", "«", "»")

    /** STY-2: straight quotes and apostrophes. */
    fun straightenQuotes(tokens: List<Token>): List<Token> = tokens.map { t ->
        when {
            t.kind == Kind.PUNCT && t.text in singleQuotes -> Token(Kind.PUNCT, "'")
            t.kind == Kind.PUNCT && t.text in doubleQuotes -> Token(Kind.PUNCT, "\"")
            t.isWord && '’' in t.text -> Token(Kind.WORD, t.text.replace('’', '\''))
            else -> t
        }
    }

    private val ellipsis = Regex("""^(?:\.{2,}|…|(?:\. ){2,}\.)$""")

    /** STY-2: canonical ellipsis; collapse "!!!", "?!?" and ",," runs. */
    fun collapseRuns(tokens: List<Token>): List<Token> = tokens.map { t ->
        val text = t.text
        val canonical = when {
            t.kind != Kind.PUNCT -> null
            ellipsis.matches(text) -> "..."
            text.length > 1 && text.all { it == '!' || it == '?' } -> if ('?' in text) "?" else "!"
            text.length > 1 && text.all { it == ',' } -> ","
            else -> null
        }
        if (canonical == null) t else Token(Kind.PUNCT, canonical)
    }

    /** STY-2: one spelling per emoticon ("=)", ":-)" -> ":)"). */
    fun canonicalEmoticons(tokens: List<Token>): List<Token> = tokens.map { t ->
        when {
            t.kind == Kind.EMOTICON -> WordLists.canonicalEmoticon(t.text)?.let { Token(Kind.EMOTICON, it) } ?: t
            t.isWord && (t.text == "xD" || t.text == "XD") -> Token(Kind.EMOTICON, ":D")
            else -> t
        }
    }

    private val skinTones = 0x1F3FB..0x1F3FF

    /** STY-2: skin-tone modifiers are a stable personal choice, hence identifying. */
    fun stripSkinTones(tokens: List<Token>): List<Token> =
        tokens.filterNot { it.kind == Kind.OTHER && it.text.codePointAt(0) in skinTones }

    private val dash = Regex("""^[-–—]+$""")

    /** STY-3: spaced hyphen between clauses; "-" for ranges; compounds and minus signs untouched. */
    fun normaliseDashes(tokens: List<Token>, clauseDash: String = "-"): List<Token> {
        val out = ArrayList<Token>(tokens.size)
        for ((i, t) in tokens.withIndex()) {
            if (t.kind != Kind.PUNCT || !dash.matches(t.text)) {
                out += t
                continue
            }
            val prev = tokens.getOrNull(i - 1)
            val next = tokens.getOrNull(i + 1)
            when (dashRole(t, prev, next)) {
                DashRole.KEEP -> out += t
                DashRole.RANGE -> out += Token(Kind.PUNCT, "-")
                DashRole.SEPARATOR -> {
                    out += space
                    out += Token(Kind.PUNCT, clauseDash)
                    out += space
                }
            }
        }
        return out
    }

    private enum class DashRole { KEEP, RANGE, SEPARATOR }

    private fun dashRole(t: Token, prev: Token?, next: Token?): DashRole {
        if (prev == null || prev.kind == Kind.NEWLINE) return DashRole.KEEP // bullet
        val tight = next != null && prev.kind in solid && next.kind in solid
        val numbersBothSides = prev.kind == Kind.NUMBER && next?.kind == Kind.NUMBER
        val minusSign = t.text == "-" && prev.isSpace && next?.kind == Kind.NUMBER
        return when {
            tight && numbersBothSides -> DashRole.RANGE
            tight && t.text == "-" -> DashRole.KEEP // compound word
            minusSign -> DashRole.KEEP
            else -> DashRole.SEPARATOR
        }
    }

    private val solid = setOf(Kind.WORD, Kind.NUMBER, Kind.PROTECTED)

    /** STY-4: semicolons become sentence breaks (casing capitalises the next word). */
    fun semicolonsToPeriods(tokens: List<Token>): List<Token> =
        tokens.map { if (TokenOps.isPunct(it, ";")) Token(Kind.PUNCT, ".") else it }

    /** STY-4: ellipses after a word become full stops (exclamations are redrawn by the profile). */
    fun flattenEllipses(tokens: List<Token>): List<Token> = tokens.mapIndexed { i, t ->
        val prev = tokens.getOrNull(i - 1)
        val closesText = prev != null && (prev.isWord || prev.kind == Kind.NUMBER)
        if (TokenOps.isPunct(t, "...") && closesText) Token(Kind.PUNCT, ".") else t
    }
}
