package dev.chamboard.core.style

import dev.chamboard.core.style.Token.Kind

/** Small helpers shared by the style rules. */
internal object TokenOps {

    /** Index of the word right after [i], separated only by spaces, or -1. */
    fun nextWord(tokens: List<Token>, i: Int): Int {
        var j = i + 1
        if (j < tokens.size && tokens[j].isSpace) j++
        return if (j < tokens.size && j > i + 1 && tokens[j].isWord) j else -1
    }

    /** Index of the previous non-space token, or -1. */
    fun prevNonSpace(tokens: List<Token>, i: Int): Int {
        var j = i - 1
        while (j >= 0 && tokens[j].isSpace) j--
        return j
    }

    /** Index of the next non-space token, or [List.size]. */
    fun nextNonSpace(tokens: List<Token>, i: Int): Int {
        var j = i + 1
        while (j < tokens.size && tokens[j].isSpace) j++
        return j
    }

    /**
     * Matches consecutive words separated by single-space runs against
     * [phrases] (lowercase, single-spaced), longest phrase first. Returns the
     * index of the phrase's last word token, or -1.
     */
    fun matchPhrase(tokens: List<Token>, start: Int, phrase: String): Int {
        val words = phrase.split(' ')
        var i = start
        for ((n, w) in words.withIndex()) {
            if (n > 0) {
                i = nextWord(tokens, i)
                if (i < 0) return -1
            }
            if (i >= tokens.size || !tokens[i].isWord || tokens[i].text.lowercase() != w) return -1
        }
        return i
    }

    /** Re-applies [original]'s capitalisation to [replacement]. */
    fun applyCase(original: String, replacement: String): String {
        val letters = original.filter { it.isLetter() }
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> replacement.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.uppercaseChar() }
            else -> replacement
        }
    }

    fun words(text: String): List<Token> = Tokenizer.tokenize(text)

    fun isSentenceEnd(t: Token): Boolean = t.kind == Kind.PUNCT && t.text in setOf(".", "!", "?")

    fun isPunct(t: Token, vararg texts: String): Boolean = t.kind == Kind.PUNCT && t.text in texts

    val space = Token(Kind.SPACE, " ")
}
