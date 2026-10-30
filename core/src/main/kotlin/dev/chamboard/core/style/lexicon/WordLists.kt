package dev.chamboard.core.style.lexicon

/** Smaller reviewed word lists for STY-3/4/5. Keys lowercase. */
object WordLists {

    /** STY-3: transition words and register markers -> one plain form. */
    val transitions: Map<String, String> = mapOf(
        "furthermore" to "also", "moreover" to "also", "additionally" to "also",
        "thus" to "so", "hence" to "so", "therefore" to "so", "consequently" to "so",
        "nevertheless" to "however", "nonetheless" to "however",
        "utilize" to "use", "utilizes" to "uses", "utilized" to "used", "utilizing" to "using",
    )

    /**
     * STY-3 intensifiers mapped to the profile's choice ("very"/"really"). "very"
     * and "really" themselves are not sources: "really" also modifies verbs.
     */
    val intensifiers: Set<String> = setOf("extremely", "incredibly", "exceptionally", "remarkably")

    /** Laugh emoji treated as laughter variants. */
    val laughEmoji: Set<String> = setOf("😂", "🤣")

    val okVariants: Set<String> = setOf("ok", "okay")

    /** STY-3 multi-word variants, matched on single-spaced words. */
    val transitionPhrases: Map<String, String> = mapOf(
        "in addition" to "also",
        "in order to" to "to",
    )

    /** STY-3 laughter: every style of laugh becomes one. */
    val laughter =
        Regex("""^(?:a?h+a+(?:h+a+)+h?|l+o+l+z?|lmf?a+o+|rofl|he(?:he)+|hi(?:hi)+)$""", RegexOption.IGNORE_CASE)
    const val LAUGH = "haha"

    /** STY-2: canonical emoticons. */
    fun canonicalEmoticon(raw: String): String? {
        val eyes = raw.firstOrNull() ?: return null
        val mouth = raw.last()
        return when {
            raw == "<3" -> "<3"
            raw == "(:" -> ":)"
            eyes == ';' && mouth in ")]" -> ";)"
            mouth in ")]" -> ":)"
            mouth in "([" -> ":("
            mouth == 'D' -> ":D"
            mouth in "Pp" -> ":P"
            mouth in "/\\|" -> ":/"
            mouth in "Oo" -> ":O"
            else -> null
        }
    }

    /** STY-4: fillers removed along with an adjacent comma. */
    val fillers: Set<String> = setOf("basically", "literally", "honestly", "actually", "totally", "tbh", "imo", "imho")
    val fillerPhrases: Set<String> = setOf("to be honest", "in my honest opinion")

    /** STY-4: chat abbreviations and informal spellings. Only matched in lowercase. */
    val abbreviations: Map<String, String> = mapOf(
        "u" to "you", "pls" to "please", "plz" to "please", "thx" to "thanks", "thanx" to "thanks",
        "thnx" to "thanks", "ty" to "thank you", "bc" to "because", "cuz" to "because", "coz" to "because",
        "tho" to "though", "thru" to "through", "idk" to "I don't know", "idc" to "I don't care",
        "btw" to "by the way", "rn" to "right now", "ppl" to "people", "abt" to "about",
        "prolly" to "probably", "gonna" to "going to", "wanna" to "want to", "gotta" to "have to",
        "dunno" to "don't know", "gimme" to "give me", "kinda" to "kind of", "sorta" to "sort of",
        "yea" to "yes", "yeah" to "yes", "yep" to "yes", "yup" to "yes", "k" to "OK", "kk" to "OK",
        "nvm" to "never mind", "omg" to "oh my god", "tbf" to "to be fair",
        "tmrw" to "tomorrow", "tmr" to "tomorrow", "til" to "until",
    )

    /**
     * STY-5: curated interchangeable sets. No embeddings (their neighbours are
     * often antonyms). Sampling is uniform over the whole set (INV-5).
     */
    val synonymSets: List<List<String>> = listOf(
        listOf("maybe", "perhaps"),
        listOf("big", "large"),
        listOf("huge", "enormous"),
        listOf("often", "frequently"),
        listOf("nearly", "almost"),
        listOf("begin", "start"),
        listOf("begins", "starts"),
        listOf("beginning", "starting"),
        listOf("choose", "pick"),
        listOf("anyway", "anyhow"),
        listOf("probably", "likely"),
        listOf("seem", "appear"),
        listOf("seems", "appears"),
        listOf("difficult", "hard"),
        listOf("quickly", "rapidly"),
    )

    /** For STY-7: frequent English function words used to detect English text. */
    val englishMarkers: Set<String> = setOf(
        "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "for", "with", "is", "are", "was",
        "were", "be", "been", "it", "this", "that", "i", "you", "he", "she", "we", "they", "my", "your", "not",
        "do", "does", "did", "have", "has", "had", "will", "would", "can", "could", "should", "what", "so",
        "if", "just", "me", "from", "about", "there", "then", "than", "when", "how", "all", "some", "no", "yes",
        "get", "go", "make", "know", "think", "like", "good", "one", "up", "out", "as", "by", "our", "us", "him",
        "her", "them", "their", "its", "who", "which", "more", "very", "also", "here", "now", "want", "see", "use",
        "i'm", "it's", "don't", "can't", "that's", "ok", "thanks", "please", "really", "well", "much", "because",
    )
}
