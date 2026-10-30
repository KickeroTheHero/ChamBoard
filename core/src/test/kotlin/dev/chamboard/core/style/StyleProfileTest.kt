package dev.chamboard.core.style

import dev.chamboard.core.mask.StableRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StyleProfileTest {

    private val profiles = (1..40).map { StyleProfile.sample(StableRandom(it.toLong())) }

    private fun engine(profile: StyleProfile, level: StyleLevel = StyleLevel.STANDARD) =
        StyleEngine(StyleOptions(level = level, profile = profile))

    private fun StyleEngine.run(text: String) = normalise(text, Random(7)).text

    private val variantGroups = listOf(
        listOf("I don't know.", "I do not know.", "i dont know.", "I don’t know."),
        listOf("It isn't here.", "It is not here.", "It's not here.", "it isnt here."),
        listOf("My favorite color.", "My favourite colour.", "my favourite colour."),
        listOf("Hello!", "Hello!!!", "hello !", "Hello!!"),
        listOf("Wait... ok", "Wait… ok", "wait.... ok", "wait. . . ok"),
        listOf("Nice :)", "nice :-)", "Nice =)", "nice :]"),
        listOf("Also, we won.", "Furthermore, we won.", "Moreover, we won.", "Additionally, we won."),
        listOf("Haha ok", "hahaha ok", "LOL ok", "lmao ok", "😂 ok", "🤣 ok"),
        listOf("It was late - very late.", "It was late—very late.", "It was late – very late."),
        listOf("It is extremely good.", "It is incredibly good.", "It is remarkably good."),
        listOf("Sure, ok.", "Sure, okay.", "Sure, OK.", "sure, Ok."),
        listOf("The plan works.", "the plan works."),
    )

    @Test
    fun everyVariantMapsToTheMasksFormUnderEveryProfile() { // INV-5, STY-9
        for (profile in profiles) {
            for (level in listOf(StyleLevel.STANDARD, StyleLevel.STRONG)) {
                val e = engine(profile, level)
                for (group in variantGroups) {
                    val outputs = group.map { e.run(it) }.toSet()
                    assertEquals("$level $profile: $group -> $outputs", 1, outputs.size)
                }
            }
        }
    }

    @Test
    fun strongLevelReplacesSentenceEndingsWhateverTheAuthorTyped() { // INV-5, STY-9
        val group = listOf("Hello there", "Hello there.", "Hello there!", "Hello there!!!")
        for (profile in profiles) {
            val e = engine(profile, StyleLevel.STRONG)
            val outputs = group.map { e.run(it) }.toSet()
            assertEquals("$profile: $outputs", 1, outputs.size)
        }
    }

    @Test
    fun normalisationIsIdempotentUnderEveryProfile() { // INV-5
        val corpus = variantGroups.flatten() + listOf(
            "honestly i think teh colour is , like , fine!!! :-) haha. The end",
            "It's been ages; furthermore I'd better go -- see u tmrw.",
            "they're not here… and we can not wait. Also, it is extremely odd",
        )
        for (profile in profiles) {
            for (level in StyleLevel.entries) {
                val e = engine(profile, level)
                for (text in corpus) {
                    val once = e.run(text)
                    assertEquals("$level $profile: $text", once, e.run(once))
                }
            }
        }
    }

    @Test
    fun ukProfileUsesUkSpellingButLeavesAmbiguousWords() { // STY-9
        val uk = engine(StyleProfile.UNIFORM.copy(spelling = SpellingRegion.UK))
        assertEquals(
            "My favourite colour. Check the program, organise it.",
            uk.run("my favorite color. check the program, organize it."),
        )
    }

    @Test
    fun lowercaseProfileLowersCommonSentenceStartsButNotNames() { // STY-9
        val lower = engine(StyleProfile.UNIFORM.copy(sentenceCase = false))
        assertEquals(
            "the cat sat. Paris is nice, I love Paris. hello. I think so.",
            lower.run("The cat sat. Paris is nice, I love Paris. Hello. I think so."),
        )
    }

    @Test
    fun profileFormsApply() { // STY-9
        val p = StyleProfile.UNIFORM.copy(
            laugh = "lol",
            emoticonNose = true,
            unicodeEllipsis = true,
            clauseDash = "—",
            formalTransitions = true,
            intensifier = "really",
            okForm = "okay",
        )
        assertEquals(
            "Additionally, it's really good lol :-) okay… it was late — very late. I also won.",
            engine(p).run("Also, it's extremely good haha :) OK... it was late - very late. I also won."),
        )
    }

    @Test
    fun exclamationRateAndFinalPeriodComeFromTheProfile() { // STY-9
        val shouty = engine(StyleProfile.UNIFORM.copy(exclamationRate = 1.0), StyleLevel.STRONG)
        assertEquals("One! Two! Three!", shouty.run("One. Two. Three"))
        val bare = engine(StyleProfile.UNIFORM.copy(finalPeriod = false), StyleLevel.STRONG)
        assertEquals("One. Two. Three", bare.run("One! Two. Three."))
        assertEquals("Is it?", bare.run("Is it?"))
    }

    @Test
    fun masksProduceVariedOutputSoTheToolHasNoSingleSignature() { // MASK-5
        val text = "honestly i dont think its workable lol... the colour is grey - also it's extremely odd, ok? :-)"
        val outputs = profiles.map { engine(it, StyleLevel.STRONG).run(text) }.toSet()
        assertTrue("only ${outputs.size} distinct outputs from ${profiles.size} masks", outputs.size >= 25)
    }

    @Test
    fun sampledProfilesCoverEveryOption() { // MASK-5
        val many = (1..500).map { StyleProfile.sample(StableRandom(it.toLong())) }
        assertEquals(StyleProfile.LAUGHS.toSet(), many.map { it.laugh }.toSet())
        assertEquals(StyleProfile.DASHES.toSet(), many.map { it.clauseDash }.toSet())
        assertEquals(StyleProfile.OK_FORMS.toSet(), many.map { it.okForm }.toSet())
        assertEquals(SpellingRegion.entries.toSet(), many.map { it.spelling }.toSet())
        assertTrue(many.any { !it.sentenceCase } && many.any { it.sentenceCase })
    }
}
