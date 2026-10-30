package dev.chamboard.core.style

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StyleEngineTest {

    private val light = StyleEngine(StyleOptions(level = StyleLevel.LIGHT))
    private val standard = StyleEngine(StyleOptions(level = StyleLevel.STANDARD))
    private val strong = StyleEngine(StyleOptions(level = StyleLevel.STRONG))
    private val expanding = StyleEngine(
        StyleOptions(profile = StyleProfile.UNIFORM.copy(contractions = ContractionPolicy.EXPAND)),
    )

    private fun StyleEngine.run(text: String) = normalise(text, Random(0)).text

    // --- STY-1 protected spans ---

    @Test
    fun protectedSpansAreNeverChanged() { // STY-1
        val text = "see https://Example.com/colour?x=1&y=2, mail Bob.Smith+tag@mail.co.uk or ping @colour_fan " +
            "#favourite `dont   change` 3.50 1,000 10:30"
        val out = strong.run(text)
        for (span in listOf(
            "https://Example.com/colour?x=1&y=2",
            "Bob.Smith+tag@mail.co.uk",
            "@colour_fan",
            "#favourite",
            "`dont   change`",
            "3.50",
            "1,000",
            "10:30",
        )) {
            assertTrue("$span missing from: $out", span in out)
        }
    }

    @Test
    fun urlTrailingPunctuationIsNotPartOfTheUrl() { // STY-1
        assertEquals("Go to https://a.com/x. Then stop.", standard.run("go to https://a.com/x.  then stop."))
    }

    // --- STY-2 light ---

    @Test
    fun lightNormalisesSpacingQuotesRunsAndCase() { // STY-2
        assertEquals("Hello, world! I think so... ok?", light.run("  hello ,world!!! i think so…  ok?!? "))
        assertEquals("She said \"hi\" and 'bye'.", light.run("she said “hi” and ‘bye’."))
        assertEquals("Wait... what", light.run("wait.... what"))
        assertEquals("Wait... what", light.run("wait. . . what"))
    }

    @Test
    fun lightCapitalisesSentencesButRespectsAbbreviationsAndBrands() { // STY-2
        assertEquals(
            "Use e.g. apples. Then eat. My iPhone works.",
            light.run("use e.g. apples. then eat. my iPhone works."),
        )
        assertEquals("Line one\nLine two", light.run("line one\nline two"))
        assertEquals("I'm sure I'll go.", light.run("i'm sure i'll go."))
    }

    @Test
    fun lightCanonicalisesEmoticonsAndStripsSkinTones() { // STY-2
        assertEquals("Nice :) :( :D ;) :P", light.run("nice :-) =( XD ;-) :-p"))
        assertEquals("Thanks 👍", light.run("thanks 👍🏽"))
    }

    @Test
    fun ratiosDecimalsAndTimesAreNotRespaced() { // STY-2
        assertEquals("It is 3.5 at 10:30, ok.", light.run("it is 3.5 at 10:30 , ok."))
    }

    // --- STY-3 standard ---

    @Test
    fun standardFixesSpellingAndTypos() { // STY-3
        assertEquals(
            "My favorite color is gray. I definitely realized it.",
            standard.run("my favourite colour is grey. i definately realised it."),
        )
        assertEquals(
            "Organized, organizing, and organization.",
            standard.run("organised, organising, and organisation."),
        )
        assertEquals("OK, a lot of that's weird.", standard.run("okay, alot of thats wierd."))
    }

    @Test
    fun standardPreservesCapitalisationOfReplacedWords() { // STY-3
        assertEquals("Color! COLOR!", standard.run("Colour! COLOUR!"))
    }

    @Test
    fun contractPolicyContractsSafelyAndCanonically() { // STY-3
        assertEquals("I don't know if it's done.", standard.run("I do not know if it is done."))
        assertEquals("It isn't here and they aren't there.", standard.run("it's not here and they're not there."))
        assertEquals("That is, it's fine.", standard.run("that is, it is fine."))
        assertEquals("Yes I am.", standard.run("yes i am."))
        assertEquals("I can't. You won't.", standard.run("I can not. You will not."))
        assertEquals("It has been a while and I have seen it.", standard.run("It's been a while and I've seen it."))
        assertEquals("I would go, but I had better not.", standard.run("I'd go, but I'd better not."))
    }

    @Test
    fun expandPolicyExpandsEverything() { // STY-3
        assertEquals("I do not know if it is done.", expanding.run("I don't know if it's done."))
        assertEquals("Do not. DO NOT. I cannot.", expanding.run("Don't. DON'T. I can't."))
    }

    @Test
    fun standardNormalisesTransitionsIntensifiersAndLaughter() { // STY-3
        assertEquals(
            "Also, it is very good. So we go.",
            expanding.run("Furthermore, it is extremely good. Thus we go."),
        )
        assertEquals("Also, we use it to win.", expanding.run("In addition, we utilise it in order to win."))
        assertEquals("Haha that was fun haha", standard.run("hahaha that was fun LOL"))
    }

    @Test
    fun standardNormalisesDashes() { // STY-3
        assertEquals("It was late - very late.", standard.run("it was late—very late."))
        assertEquals("It was late - very late.", standard.run("it was late -- very late."))
        assertEquals(
            "Pages 10-20 are well-known, at -5 degrees.",
            standard.run("pages 10–20 are well-known, at -5 degrees."),
        )
        assertEquals("- Item one", standard.run("- item one"))
    }

    // --- STY-4 strong ---

    @Test
    fun strongRemovesFillersAndExpandsChatAbbreviations() { // STY-4
        assertEquals("I think it's good.", strong.run("Honestly, i basically think it's good tbh."))
        assertEquals("It is, fine.", strong.run("it is, literally, fine."))
        assertEquals(
            "Thanks, I don't know because you're going to be late.",
            strong.run("thx, idk bc u are gonna be late."),
        )
        assertEquals("First part. Second part.", strong.run("first part; second part."))
        assertEquals("See you tomorrow, until then.", strong.run("see u tmrw, til then."))
    }

    @Test
    fun strongDoesNotExpandUppercaseLookalikes() { // STY-4
        assertEquals("The US team.", strong.run("the US team."))
    }

    // --- INV-5 choice erasure ---

    private val variantGroups = listOf(
        listOf("I don't know.", "I do not know.", "i dont know.", "I don’t know."),
        listOf("It isn't here.", "It is not here.", "It's not here.", "it isnt here."),
        listOf("I can't go.", "I cannot go.", "I can not go."),
        listOf("My favorite color.", "My favourite colour.", "my favourite colour."),
        listOf("Hello!", "Hello!!!", "hello !", "Hello!!"),
        listOf("Wait... ok", "Wait… ok", "wait.... ok", "wait. . . ok"),
        listOf("She said \"hi\".", "She said “hi”.", "she said “hi” ."),
        listOf("Nice :)", "nice :-)", "Nice =)", "nice :]"),
        listOf(
            "Also, we won.",
            "Furthermore, we won.",
            "Moreover, we won.",
            "In addition, we won.",
            "Additionally, we won.",
        ),
        listOf("So it works.", "Thus it works.", "Hence it works.", "Therefore it works."),
        listOf("Haha ok", "hahaha ok", "LOL ok", "lmao ok"),
        listOf(
            "It was late - very late.",
            "It was late—very late.",
            "It was late – very late.",
            "It was late -- very late.",
        ),
        listOf("Thumbs 👍", "Thumbs 👍🏻", "Thumbs 👍🏿"),
        listOf("I'm here.", "I am here.", "im here.", "i'm here."),
    )

    @Test
    fun everyVariantOfAHabitNormalisesIdentically() { // INV-5
        for (policy in ContractionPolicy.entries) {
            val engine = StyleEngine(
                StyleOptions(level = StyleLevel.STANDARD, profile = StyleProfile.UNIFORM.copy(contractions = policy)),
            )
            for (group in variantGroups) {
                val outputs = group.map { engine.run(it) }.toSet()
                assertEquals("$policy: $group -> $outputs", 1, outputs.size)
            }
        }
    }

    @Test
    fun strongLevelErasesChatHabitsToo() { // INV-5
        val groups = listOf(
            listOf(
                "Thank you, I'm going to go because it is late.",
                "ty, im gonna go bc it is late.",
                "Thank you, basically I'm going to go because it is late.",
            ),
            listOf("Yes, OK.", "yeah, ok.", "yep, okay.", "Yup, k."),
        )
        for (group in groups) {
            val outputs = group.map { strong.run(it) }.toSet()
            assertEquals("$group -> $outputs", 1, outputs.size)
        }
    }

    @Test
    fun synonymResamplingAtFullRateIgnoresTheAuthorsChoice() { // INV-5, STY-5
        val engine = StyleEngine(StyleOptions(synonymRate = 1.0))
        for (seed in 1..50) {
            val a = engine.normalise("Maybe it is a big and difficult task.", Random(seed)).text
            val b = engine.normalise("Perhaps it is a large and hard task.", Random(seed)).text
            assertEquals(a, b)
        }
        val picks = (1..200).map { engine.normalise("maybe", Random(it)).text }.toSet()
        assertEquals(setOf("Maybe", "Perhaps"), picks)
    }

    @Test
    fun normalisationIsIdempotent() { // INV-5: output is already canonical
        val corpus = variantGroups.flatten() + listOf(
            "honestly i think teh colour is , like , fine!!! :-) haha",
            "It's been ages; furthermore I'd better go -- see u tmrw.",
            "they're not here… and we can not wait",
        )
        for (engine in listOf(light, standard, strong, expanding)) {
            for (text in corpus) {
                val once = engine.run(text)
                assertEquals("not idempotent for: $text", once, engine.run(once))
            }
        }
    }

    // --- STY-6 changes ---

    @Test
    fun changesPointAtEditedSpansInTheOutput() { // STY-6
        val result = standard.normalise("my colour is grey", Random(0))
        assertEquals("My color is gray", result.text)
        val pairs = result.changes.map { it.before to it.after }
        assertEquals(listOf("my" to "My", "colour" to "color", "grey" to "gray"), pairs)
        for (c in result.changes) assertEquals(c.after, result.text.substring(c.start, c.end))
    }

    @Test
    fun changeOffsetsAreCorrectAcrossLines() { // STY-6
        val result = standard.normalise("fine\nmy colour", Random(0))
        val c = result.changes.single { it.before == "colour" }
        assertEquals("color", result.text.substring(c.start, c.end))
    }

    @Test
    fun unchangedTextHasNoChanges() { // STY-6
        assertTrue(standard.normalise("This is fine.", Random(0)).changes.isEmpty())
    }

    // --- STY-7 English only ---

    @Test
    fun nonEnglishTextIsLeftUnchanged() { // STY-7
        val french = "je pense que la couleur grise est vraiment jolie , non ?"
        val result = standard.normalise(french, Random(0))
        assertFalse(result.applied)
        assertEquals(french, result.text)
    }

    // --- STY-8 budget ---

    @Test
    fun fiveHundredWordsNormaliseQuickly() { // STY-8
        val text = ("Honestly, i think teh colour is grey!!! furthermore we can not go - it's late… :-) ").repeat(35)
        repeat(5) { strong.run(text) } // warm up the JIT
        val start = System.nanoTime()
        val runs = 10
        repeat(runs) { strong.run(text) }
        val avgMs = (System.nanoTime() - start) / 1e6 / runs
        // Phone budget is 50 ms; a CI JVM is faster, so this generous bound only catches regressions.
        assertTrue("average $avgMs ms for ~500 words", avgMs < 150)
    }
}
