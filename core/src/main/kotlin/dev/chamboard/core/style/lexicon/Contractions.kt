package dev.chamboard.core.style.lexicon

/**
 * STY-3 contraction tables. Choice erasure (INV-5) requires both variants of a
 * pair to produce the same output, so the rule always expands first (one
 * canonical long form), then, under the CONTRACT policy, re-contracts only
 * the [symmetric] pairs that are safe to contract.
 *
 * Keys and values are lowercase; long forms are space-separated.
 */
object Contractions {

    /** Safe in both directions; the user's policy picks the form. */
    val symmetric: Map<String, String> = mapOf(
        // Negations: valid in every position, including clause-final ("I don't.").
        "don't" to "do not", "doesn't" to "does not", "didn't" to "did not",
        "isn't" to "is not", "aren't" to "are not", "wasn't" to "was not", "weren't" to "were not",
        "haven't" to "have not", "hasn't" to "has not", "hadn't" to "had not",
        "won't" to "will not", "wouldn't" to "would not", "shouldn't" to "should not",
        "couldn't" to "could not", "can't" to "cannot", "mustn't" to "must not",
        // Pronoun + be / will: only contracted when another word follows ("that is, ..." stays).
        "i'm" to "i am", "you're" to "you are", "we're" to "we are", "they're" to "they are",
        "it's" to "it is", "that's" to "that is", "what's" to "what is", "he's" to "he is",
        "she's" to "she is", "there's" to "there is",
        "i'll" to "i will", "you'll" to "you will", "we'll" to "we will", "they'll" to "they will",
        "he'll" to "he will", "she'll" to "she will", "it'll" to "it will",
    )

    /** Long form -> contraction, for the CONTRACT pass. */
    val contractionOf: Map<String, String> = symmetric.entries.associate { (short, long) -> long to short }

    /** Negations may contract anywhere; the rest need a following word. */
    val isNegation: (String) -> Boolean = { it.endsWith("n't") }

    /** Spacing variants of long forms, normalised before anything else. */
    val longFormAliases: Map<String, String> = mapOf("can not" to "cannot")

    /** Contracting the long form is unsafe ("I have a dog" -> "I've a dog"), so these only ever expand. */
    val alwaysExpand: Map<String, String> = mapOf(
        "i've" to "i have", "you've" to "you have", "we've" to "we have", "they've" to "they have",
        "i'd" to "i would", "you'd" to "you would", "we'd" to "we would", "they'd" to "they would",
        "he'd" to "he would", "she'd" to "she would",
    )

    /** "'d" means "had" before these words ("I'd been", "you'd better"). */
    val hadMarkers: Set<String> = setOf("been", "better", "had", "gone", "done", "seen", "known")

    /**
     * "'s" means "has" before these words ("it's been"); "has" is never re-contracted.
     * Participles like "done"/"gone" are left out: "it's done" is usually "it is done".
     */
    val hasMarkers: Set<String> = setOf("been", "got", "gotten", "had")
}
