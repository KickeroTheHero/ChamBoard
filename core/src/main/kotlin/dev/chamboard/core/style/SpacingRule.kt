package dev.chamboard.core.style

import dev.chamboard.core.style.Token.Kind
import dev.chamboard.core.style.TokenOps.space

/** STY-2: single spaces, none before closing punctuation, one after it, trimmed lines. */
internal object SpacingRule {

    private val noSpaceBefore = setOf(",", ".", "!", "?", ";", ":", "...", ")")
    private val spaceAfter = setOf(",", ";", ":", "!", "?", "...")

    fun apply(tokens: List<Token>): List<Token> {
        val merged = ArrayList<Token>(tokens.size)
        for (t in tokens) {
            if (t.isSpace && merged.lastOrNull()?.isSpace == true) continue
            merged += if (t.isSpace) space else t
        }
        val out = ArrayList<Token>(merged.size)
        for ((i, t) in merged.withIndex()) {
            val prev = out.lastOrNull()
            val next = merged.getOrNull(i + 1)
            if (t.isSpace && dropSpace(prev, next)) continue
            out += t
            if (next != null && needsSpaceAfter(prev, t, next)) out += space
        }
        return out
    }

    private fun dropSpace(prev: Token?, next: Token?): Boolean = prev == null ||
        prev.kind == Kind.NEWLINE ||
        next == null ||
        next.kind == Kind.NEWLINE ||
        (next.kind == Kind.PUNCT && next.text in noSpaceBefore) ||
        TokenOps.isPunct(prev, "(")

    private fun needsSpaceAfter(prev: Token?, t: Token, next: Token): Boolean {
        if (t.kind != Kind.PUNCT || !next.isWord) return false
        if (t.text in spaceAfter) return true
        // "end.Next" -> "end. Next", but leave "e.g", "U.S.A" and "file.txt" alone.
        return t.text == "." &&
            prev?.isWord == true &&
            prev.text.length > 1 &&
            next.text.length > 1 &&
            next.text[0].isUpperCase()
    }
}
