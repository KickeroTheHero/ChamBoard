package dev.chamboard.core.suggest

import dev.chamboard.core.suggest.lexicon.CommonWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/** UI-7 offline suggestions: prefix completion, stable ranking, and no path for user history. */
class SuggesterTest {

    @Test
    fun completesPrefixWithLongerListWordsMostCommonFirst() {
        // UI-7: "th" completes to the most common "th…" words, in list order.
        val expected = CommonWords.ranked.filter { it.startsWith("th") && it != "th" }.take(3)
        assertEquals(listOf("the", "that", "they"), expected)
        assertEquals(expected, Suggester.complete("th"))
    }

    @Test
    fun neverSuggestsTheWordAlreadyTyped() {
        // A completion equal to the prefix would be a no-op tap.
        assertTrue(Suggester.complete("the").none { it == "the" })
        assertTrue(Suggester.complete("the").all { it.startsWith("the") && it.length > 3 })
    }

    @Test
    fun casingFollowsThePrefix() {
        assertEquals(listOf("The", "That", "They"), Suggester.complete("Th"))
        assertEquals(listOf("THE", "THAT", "THEY"), Suggester.complete("TH"))
        // A single capital is a sentence start, not shouting.
        assertTrue(Suggester.complete("I").all { it[0] == 'I' && it.drop(1) == it.drop(1).lowercase() })
    }

    @Test
    fun rankingIsStableAcrossCallsAndHistory() {
        // UI-7: nothing is learned, so typing a rare word many times never promotes it.
        val before = Suggester.complete("wh", limit = 5)
        repeat(50) { Suggester.complete("whether") }
        assertEquals(before, Suggester.complete("wh", limit = 5))
        assertEquals(listOf("what", "when", "which"), before.take(3))
    }

    @Test
    fun respectsLimitAndRejectsNonWordPrefixes() {
        assertEquals(5, Suggester.complete("s", limit = 5).size)
        assertEquals(emptyList<String>(), Suggester.complete("s", limit = 0))
        assertEquals(emptyList<String>(), Suggester.complete(""))
        assertEquals(emptyList<String>(), Suggester.complete("th3"))
        assertEquals(emptyList<String>(), Suggester.complete("é"))
        assertEquals(emptyList<String>(), Suggester.complete("zzzq"))
    }

    @Test
    fun outputIsAlwaysAListWordNeverUserText() {
        // INV-3 spirit: a suggestion only ever echoes the built-in list (up to casing).
        val words = CommonWords.ranked.toSet()
        for (prefix in listOf("a", "Be", "CO", "xyz", "tomorr", "pl")) {
            Suggester.complete(prefix, limit = 10).forEach { assertTrue(it, it.lowercase() in words) }
        }
    }

    @Test
    fun apiHasNoInputForUserHistory() {
        // INV-3, INV-5 spirit: the only public entry points take the current prefix (or draft
        // text and cursor) and a limit, and the object holds no mutable state to learn into.
        val publicMethods = Suggester::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .associate { it.name to it.parameterTypes.toList() }
        assertEquals(
            mapOf(
                "complete" to listOf(String::class.java, Int::class.javaPrimitiveType),
                "wordPrefixBefore" to listOf(CharSequence::class.java, Int::class.javaPrimitiveType),
            ),
            publicMethods,
        )
        val mutableFields = Suggester::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && !Modifier.isFinal(it.modifiers) }
        assertEquals(emptyList<Any>(), mutableFields)
    }

    @Test
    fun wordListIsCleanAndUnique() {
        val ranked = CommonWords.ranked
        assertTrue(ranked.size > 1000)
        assertEquals(ranked.size, ranked.toSet().size)
        ranked.forEach { assertTrue(it, it.matches(Regex("[a-z]+"))) }
    }

    @Test
    fun wordPrefixBeforeTakesTheFragmentAtTheCursor() {
        assertEquals("wor", Suggester.wordPrefixBefore("hello wor", 9))
        assertEquals("he", Suggester.wordPrefixBefore("hello wor", 2))
        assertEquals("", Suggester.wordPrefixBefore("hello ", 6))
        assertEquals("", Suggester.wordPrefixBefore("", 0))
        assertEquals("go", Suggester.wordPrefixBefore("let's go", 99))
        assertEquals("s", Suggester.wordPrefixBefore("let's", 5))
    }
}
