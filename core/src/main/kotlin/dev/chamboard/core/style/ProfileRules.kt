package dev.chamboard.core.style

import dev.chamboard.core.style.Token.Kind
import dev.chamboard.core.style.TokenOps.applyCase
import dev.chamboard.core.style.lexicon.Spelling
import dev.chamboard.core.style.lexicon.WordLists

/**
 * STY-9: map canonical forms to the active profile's forms. Every rule here
 * takes the canonical output of the normaliser, so the result depends on the
 * profile and never on which variant the author typed (INV-5).
 */
internal object ProfileRules {

    fun spelling(tokens: List<Token>, profile: StyleProfile): List<Token> =
        if (profile.spelling == SpellingRegion.UK) WordRules.replaceWords(tokens, Spelling.usToUk) else tokens

    fun intensifiers(tokens: List<Token>, profile: StyleProfile): List<Token> =
        WordRules.replaceWords(tokens, WordLists.intensifiers.associateWith { profile.intensifier })

    fun okForm(tokens: List<Token>, profile: StyleProfile): List<Token> = tokens.map { t ->
        if (t.isWord && t.text.lowercase() in WordLists.okVariants) Token(Kind.WORD, profile.okForm) else t
    }

    /** Canonical "haha" and laugh emoji become the profile's laugh. */
    fun laugh(tokens: List<Token>, profile: StyleProfile): List<Token> = tokens.map { t ->
        val isLaugh =
            (t.isWord && t.text == WordLists.LAUGH) || (t.kind == Kind.OTHER && t.text in WordLists.laughEmoji)
        when {
            !isLaugh -> t
            profile.laugh.first().isLetter() -> Token(Kind.WORD, profile.laugh)
            else -> Token(Kind.OTHER, profile.laugh)
        }
    }

    fun emoticonNose(tokens: List<Token>, profile: StyleProfile): List<Token> {
        if (!profile.emoticonNose) return tokens
        return tokens.map { t ->
            if (t.kind == Kind.EMOTICON && t.text.length == 2 && t.text != "<3") {
                Token(Kind.EMOTICON, "${t.text[0]}-${t.text[1]}")
            } else {
                t
            }
        }
    }

    fun ellipsis(tokens: List<Token>, profile: StyleProfile): List<Token> {
        if (!profile.unicodeEllipsis) return tokens
        return tokens.map { if (TokenOps.isPunct(it, "...")) Token(Kind.PUNCT, "…") else it }
    }

    /** Sentence-initial "Also," becomes "Additionally," for formal profiles. */
    fun formalTransitions(tokens: List<Token>, profile: StyleProfile): List<Token> {
        if (!profile.formalTransitions) return tokens
        return tokens.mapIndexed { i, t ->
            val isAlso = t.isWord && t.text.lowercase() == "also"
            val followedByComma = tokens.getOrNull(i + 1)?.let { TokenOps.isPunct(it, ",") } == true
            if (isAlso && followedByComma && startsSentence(tokens, i)) {
                Token(Kind.WORD, applyCase(t.text, "additionally"))
            } else {
                t
            }
        }
    }

    private fun startsSentence(tokens: List<Token>, i: Int): Boolean {
        val prev = TokenOps.prevNonSpace(tokens, i)
        return prev < 0 || tokens[prev].kind == Kind.NEWLINE || TokenOps.isSentenceEnd(tokens[prev])
    }
}
