package dev.chamboard.core.style

import dev.chamboard.core.style.Token.Kind
import kotlin.random.Random

/** STY-9 at the Strong level: sentence endings follow the profile, not the author. */
internal object TerminalRule {

    /**
     * Strong level: each statement ending ("." or "!") is redrawn — "!" with the
     * profile's rate, else "." — and the text's last sentence gets a full stop or
     * none per profile. One random draw per sentence, whatever the author typed.
     */
    fun apply(tokens: List<Token>, profile: StyleProfile, rng: Random): List<Token> {
        val out = tokens.toMutableList()
        for (i in out.indices) {
            if (isStatementEnd(out, i)) out[i] = Token(Kind.PUNCT, drawEnd(profile, rng))
        }
        val last = out.indexOfLast { !it.isSpace && it.kind != Kind.NEWLINE }
        if (last < 0) return out
        val lastToken = out[last]
        val endsBare = lastToken.kind in setOf(Kind.WORD, Kind.NUMBER, Kind.PROTECTED)
        val endsStatement = TokenOps.isPunct(lastToken, ".", "!") && !isAbbreviationDot(out, last)
        if (endsBare || endsStatement) {
            val end = drawEnd(profile, rng)
            val final = if (end == "." && !profile.finalPeriod) null else end
            when {
                endsStatement && final == null -> out.removeAt(last)
                endsStatement -> out[last] = Token(Kind.PUNCT, final!!)
                final != null -> out.add(last + 1, Token(Kind.PUNCT, final))
            }
        }
        return out
    }

    private fun drawEnd(profile: StyleProfile, rng: Random): String =
        if (rng.nextDouble() < profile.exclamationRate) "!" else "."

    /** "." or "!" closing a sentence (not "e.g.", not "file.txt", not the text's last token). */
    private fun isStatementEnd(tokens: List<Token>, i: Int): Boolean {
        val t = tokens[i]
        if (!TokenOps.isPunct(t, ".", "!")) return false
        val next = tokens.getOrNull(i + 1) ?: return false
        val separated = next.isSpace || next.kind == Kind.NEWLINE
        return separated && !isAbbreviationDot(tokens, i) && tokens.subList(i + 1, tokens.size).any { !it.isSpace }
    }

    private fun isAbbreviationDot(tokens: List<Token>, i: Int): Boolean {
        val prev = tokens.getOrNull(i - 1)
        return tokens[i].text == "." && prev != null && prev.isWord && prev.text.length == 1
    }
}
