package dev.chamboard.core.style

import dev.chamboard.core.style.lexicon.Contractions
import dev.chamboard.core.style.lexicon.Spelling
import kotlin.math.abs

/**
 * A small stylometric feature vector: the habits the normaliser targets
 * (punctuation, casing, contractions, spelling, emoticons) plus common
 * function-word rates. Used to measure how far normalisation pulls two
 * authors together; a stand-in for the adversarial test page (docs/design/testing.md).
 */
class StyleFingerprint private constructor(val features: Map<String, Double>) {

    /** Canberra distance: scale-free, 0 = identical, grows with each differing feature. */
    fun distanceTo(other: StyleFingerprint): Double = features.keys.sumOf { k ->
        val a = features.getValue(k)
        val b = other.features.getValue(k)
        if (a == 0.0 && b == 0.0) 0.0 else abs(a - b) / (abs(a) + abs(b))
    }

    companion object {
        private val functionWords = listOf(
            "the", "a", "and", "but", "so", "also", "however", "very", "really", "just", "not", "that", "which",
            "because", "though", "while", "among", "maybe", "perhaps", "yes",
        )

        /** Punctuation habits by family, so "!!!" and "!" or "…" and "..." count as the same habit. */
        private val punctuation: Map<String, (String) -> Boolean> = mapOf(
            "!" to { p -> '!' in p },
            "?" to { p -> '?' in p },
            "," to { p -> ',' in p },
            ";" to { p -> p == ";" },
            ":" to { p -> p == ":" },
            "..." to { p -> p == "…" || p.startsWith("..") || p.startsWith(". .") },
            "dash" to { p -> p.all { it in "-–—" } },
            "quote" to { p -> p.all { it in "\"'‘’“”„‟«»" } },
            "(" to { p -> p == "(" },
        )
        private val canonicalEmoticons = setOf(":)", ":(", ":D", ";)", ":P", ":/", ":O", "<3")

        fun of(text: String): StyleFingerprint {
            val tokens = Tokenizer.tokenize(text)
            val words = tokens.filter { it.isWord }.map { it.text }
            val wordCount = words.size.coerceAtLeast(1).toDouble()
            val chars = text.length.coerceAtLeast(1).toDouble()
            val f = LinkedHashMap<String, Double>()
            for ((name, family) in punctuation) {
                f["punct:$name"] = tokens.count { it.kind == Token.Kind.PUNCT && family(it.text) } / chars
            }
            f["punct:runs"] =
                tokens.count { it.kind == Token.Kind.PUNCT && it.text.length > 1 && it.text != "..." } / chars
            f["space:double"] = tokens.count { it.isSpace && it.text != " " } / chars
            f["curly"] = text.count { it in "‘’“”" } / chars
            f["case:lower-i"] = words.count { it == "i" || it.startsWith("i'") } / wordCount
            f["case:lower-start"] = lowercaseSentenceStarts(tokens)
            f["contraction:rate"] = words.count { it.lowercase().replace('’', '\'') in contractionForms } / wordCount
            f["spelling:variant"] = words.count { it.lowercase() in Spelling.all } / wordCount
            f["emoticon:noncanonical"] =
                tokens.count { it.kind == Token.Kind.EMOTICON && it.text !in canonicalEmoticons } / chars
            for (w in functionWords) f["fw:$w"] = words.count { it.lowercase() == w } / wordCount
            return StyleFingerprint(f)
        }

        private val contractionForms = Contractions.symmetric.keys + Contractions.alwaysExpand.keys

        private fun lowercaseSentenceStarts(tokens: List<Token>): Double {
            var starts = 0
            var lower = 0
            var atStart = true
            for (t in tokens) {
                if (t.isWord && atStart) {
                    starts++
                    if (t.text[0].isLowerCase()) lower++
                }
                if (t.isWord || t.kind == Token.Kind.NUMBER) atStart = false
                if (TokenOps.isSentenceEnd(t) || t.kind == Token.Kind.NEWLINE) atStart = true
            }
            return if (starts == 0) 0.0 else lower.toDouble() / starts
        }
    }
}
