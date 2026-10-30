package dev.chamboard.core.suggest

import dev.chamboard.core.suggest.lexicon.CommonWords

/**
 * UI-7 offline word completions from the built-in [CommonWords] list.
 *
 * Privacy (INV-3, INV-5 spirit): this is a pure function of the word being typed and a fixed
 * list compiled into the app. There is no parameter, field or cache through which the user's
 * history could reach it, so it cannot learn personal words and its answers for a prefix are
 * the same for every user on every day. Nothing typed is kept after a call returns.
 */
object Suggester {

    /** How many completions the strip shows by default. */
    const val DEFAULT_LIMIT = 3

    /**
     * Up to [limit] words from the list that start with [prefix] and are longer than it,
     * most common first. Returns nothing for a prefix that isn't a plain word fragment
     * (digits, symbols, other scripts), so codes and numbers never get completions.
     *
     * Casing follows the prefix: "Th" gives "The", "TH" gives "THE", "th" gives "the".
     */
    fun complete(prefix: String, limit: Int = DEFAULT_LIMIT): List<String> {
        if (limit <= 0 || prefix.isEmpty() || !prefix.all(::isWordChar)) return emptyList()
        val key = prefix.lowercase()
        val casing = Casing.of(prefix)
        return CommonWords.ranked.asSequence()
            .filter { it.length > key.length && it.startsWith(key) }
            .take(limit)
            .map(casing::apply)
            .toList()
    }

    /**
     * The word fragment that ends at [cursor] in [text]: the run of word characters directly
     * before the cursor. Empty when the cursor follows a space, punctuation or nothing. The
     * app calls this on the draft, never on the target field (BUF-1).
     */
    fun wordPrefixBefore(text: CharSequence, cursor: Int): String {
        val end = cursor.coerceIn(0, text.length)
        var start = end
        while (start > 0 && isWordChar(text[start - 1])) start--
        return text.substring(start, end)
    }

    // ASCII letters only: the list is English, and other scripts would never match.
    private fun isWordChar(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    private enum class Casing {
        LOWER,
        CAPITALISED,
        UPPER,
        ;

        fun apply(word: String): String = when (this) {
            LOWER -> word
            CAPITALISED -> word.replaceFirstChar(Char::uppercaseChar)
            UPPER -> word.uppercase()
        }

        companion object {
            // One capital letter is "Capitalised", not "UPPER": "I" typed at a sentence start
            // should suggest "In", not "IN".
            fun of(prefix: String): Casing = when {
                prefix.length > 1 && prefix.all(Char::isUpperCase) -> UPPER
                prefix.first().isUpperCase() -> CAPITALISED
                else -> LOWER
            }
        }
    }
}
