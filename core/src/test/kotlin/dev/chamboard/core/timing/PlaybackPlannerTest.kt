package dev.chamboard.core.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackPlannerTest {

    private val sample = "The proposal was not particularly workable. We should try again tomorrow, maybe."
    private val planner = PlaybackPlanner()

    private fun persona(seed: Int) = Persona.random(Random(seed))

    @Test
    fun personasStayInPopulationRangesAndVary() { // TIM-1, INV-6
        val personas = (1..200).map { persona(it) }
        for (p in personas) {
            assertTrue(p.wpm in Persona.WPM_RANGE)
            assertTrue(p.sigma in Persona.SIGMA_RANGE)
            assertTrue(p.thinkProbability in Persona.THINK_PROBABILITY_RANGE)
        }
        assertTrue("personas must differ between sessions", personas.map { it.wpm }.toSet().size > 150)
    }

    @Test
    fun playbackReproducesTextExactlyInEveryStyle() { // TIM-3
        for (style in PlaybackStyle.entries) {
            val plan = planner.plan(sample, style, persona(1), Random(1))
            assertEquals(sample, plan.text)
        }
    }

    @Test
    fun instantIsOneChunk() { // TIM-3
        val plan = planner.plan(sample, PlaybackStyle.INSTANT, persona(1), Random(1))
        assertEquals(1, plan.chunks.size)
        assertEquals(0L, plan.totalMs)
    }

    @Test
    fun glideCommitsWholeWords() { // TIM-3
        val plan = planner.plan("  hello there world", PlaybackStyle.GLIDE, persona(2), Random(2))
        assertEquals(listOf("  hello ", "there ", "world"), plan.chunks.map { it.text })
    }

    @Test
    fun typedGapsRespectFloorAndAreRightSkewed() { // TIM-2
        val text = sample.repeat(3)
        val uncapped = PlaybackPlanner(maxTypedMs = Long.MAX_VALUE)
        val plan = uncapped.plan(text, PlaybackStyle.TYPED, persona(3), Random(3))
        assertEquals(PlaybackStyle.TYPED, plan.style)
        val gaps = plan.chunks.drop(1).map { it.delayMs }
        assertTrue(gaps.all { it >= persona(3).minGapMs })
        val mean = gaps.average()
        val median = gaps.sorted()[gaps.size / 2].toDouble()
        assertTrue("log-normal gaps: mean $mean should exceed median $median", mean > median)
    }

    @Test
    fun typedSpeedIsPlausibleForAPhone() { // TIM-1, TIM-2
        val text = sample.repeat(4)
        for (seed in 1..20) {
            val p = persona(seed)
            val plan = PlaybackPlanner(maxTypedMs = Long.MAX_VALUE).plan(text, PlaybackStyle.TYPED, p, Random(seed))
            val wpm = text.length / 5.0 / (plan.totalMs / 60_000.0)
            assertTrue("persona ${p.wpm} WPM produced $wpm WPM", wpm in 12.0..70.0)
        }
    }

    @Test
    fun farKeysAreSlowerThanNearKeys() { // TIM-2
        assertTrue(KeyboardGeometry.distance('q', 'p')!! > KeyboardGeometry.distance('a', 's')!!)
        assertTrue(KeyboardGeometry.movementFactor('q', 'p') > KeyboardGeometry.movementFactor('a', 's'))
        assertTrue(KeyboardGeometry.movementFactor('a', 'A') > KeyboardGeometry.movementFactor('a', 'a'))
    }

    @Test
    fun longTypedPlansFallBackToGlide() { // TIM-4
        val long = sample.repeat(30)
        val plan = planner.plan(long, PlaybackStyle.TYPED, persona(4), Random(4))
        assertEquals(PlaybackStyle.GLIDE, plan.style)
        assertTrue(plan.fellBackToGlide)
        assertEquals(long, plan.text)
    }

    @Test
    fun shortTypedPlansStayTyped() { // TIM-4
        val plan = planner.plan("hi there", PlaybackStyle.TYPED, persona(5), Random(5))
        assertEquals(PlaybackStyle.TYPED, plan.style)
        assertFalse(plan.fellBackToGlide)
    }

    @Test
    fun remainingTextSupportsResumeAfterAbort() { // TIM-5, INV-7
        val plan = planner.plan("one two three", PlaybackStyle.GLIDE, persona(6), Random(6))
        assertEquals("two three", plan.remainingText(1))
        assertEquals("", plan.remainingText(plan.chunks.size))
        assertEquals("one two three", plan.remainingText(0))
    }

    @Test
    fun singleLineFieldsGetSpacesForNewlines() { // TIM-6
        val plan = planner.plan("a\nb\r\nc", PlaybackStyle.INSTANT, persona(7), Random(7), singleLine = true)
        assertEquals("a b c", plan.text)
    }

    @Test
    fun sameSeedSamePlanDifferentSeedDifferentPlan() { // INV-6: all randomness is injected
        val a = planner.plan(sample, PlaybackStyle.TYPED, persona(8), Random(8)).chunks
        val b = planner.plan(sample, PlaybackStyle.TYPED, persona(8), Random(8)).chunks
        val c = planner.plan(sample, PlaybackStyle.TYPED, persona(9), Random(9)).chunks
        assertEquals(a, b)
        assertNotEquals(a.map { it.delayMs }, c.map { it.delayMs })
    }

    @Test
    fun emojiAreNeverSplitAcrossCommits() { // TIM-3
        val text = "ok 👍🏽 👨‍👩‍👧 🇬🇧 1️⃣ é"
        val chunks = planner.plan(text, PlaybackStyle.TYPED, persona(10), Random(10)).chunks.map { it.text }
        assertTrue(chunks.contains("👍🏽"))
        assertTrue(chunks.contains("👨‍👩‍👧"))
        assertTrue(chunks.contains("🇬🇧"))
        assertTrue(chunks.contains("1️⃣"))
        assertTrue(chunks.contains("é"))
        assertEquals(text, chunks.joinToString(""))
    }

    /** Replays a plan the way an emitter would against a text field. */
    private fun replay(plan: PlaybackPlan): String = plan.chunks.fold(StringBuilder()) { field, c ->
        field.setLength(field.length - c.deleteBefore)
        field.append(c.text)
    }.toString()

    private val typoProne = persona(12).copy(typoRate = 0.5, lookaheadProbability = 0.5)

    @Test
    fun correctionsBackspaceTyposAndEndOnTheExactText() { // TIM-8
        val uncapped = PlaybackPlanner(maxTypedMs = Long.MAX_VALUE)
        for (seed in 1..30) {
            val plan = uncapped.plan(sample, PlaybackStyle.TYPED, typoProne, Random(seed))
            assertEquals(sample, replay(plan))
            assertTrue("expected corrections", plan.chunks.any { it.deleteBefore > 0 })
            assertTrue(plan.chunks.all { it.deleteBefore in 0..2 })
        }
    }

    @Test
    fun typosUseNeighbouringKeys() { // TIM-8
        val plan = PlaybackPlanner(
            Long.MAX_VALUE,
        ).plan("aaaa aaaa aaaa aaaa", PlaybackStyle.TYPED, typoProne, Random(3))
        val typed = plan.chunks.map { it.text }.filter { it.isNotEmpty() && it != " " }.toSet()
        assertTrue((typed - setOf("a")).isNotEmpty())
        assertTrue(
            "typos must be neighbours of 'a': $typed",
            (typed - "a").all {
                it[0] in
                    KeyboardGeometry.neighbours('a')
            },
        )
    }

    @Test
    fun progressReportsStrayTypoCharsUntilTheBackspace() { // TIM-5, INV-7
        val plan = PlaybackPlanner(Long.MAX_VALUE).plan(sample, PlaybackStyle.TYPED, typoProne, Random(5))
        val backspace = plan.chunks.indexOfFirst { it.deleteBefore > 0 }
        val before = plan.progress(backspace)
        assertEquals(plan.chunks[backspace].deleteBefore, before.stray)
        assertEquals(0, plan.progress(backspace + 1).stray)
        assertEquals(sample.substring(before.correctPrefix), plan.remainingText(backspace))
        assertEquals(Progress(sample.length, 0), plan.progress(plan.chunks.size))
    }

    @Test
    fun glideAndInstantNeverMakeTypos() { // TIM-8
        for (style in listOf(PlaybackStyle.GLIDE, PlaybackStyle.INSTANT)) {
            assertTrue(planner.plan(sample, style, typoProne, Random(1)).chunks.all { it.deleteBefore == 0 })
        }
    }

    @Test
    fun commonDigraphsAreTypedAsBursts() { // TIM-7
        assertTrue(KeyboardGeometry.isBurst('t', 'h'))
        assertFalse(KeyboardGeometry.isBurst('q', 'z'))
        val p = persona(13).copy(sigma = 0.0001, burstFactor = 0.6, thinkProbability = 0.0)
        val gaps = PlaybackPlanner(Long.MAX_VALUE).plan("thth", PlaybackStyle.TYPED, p, Random(1)).chunks.drop(1)
        val th = gaps[0].delayMs.toDouble() // t -> h, a burst
        val ht = gaps[1].delayMs.toDouble() // h -> t, same distance, no burst
        assertTrue("burst $th should be faster than $ht", th < ht * 0.7)
    }

    @Test
    fun noFixedTimingConstantsAcrossPersonas() { // TIM-1, MASK-5
        val personas = (1..100).map { persona(it) }
        assertTrue(personas.map { it.minGapMs }.toSet().size > 10)
        assertTrue(personas.map { it.maxTypedMs }.toSet().size > 50)
        for (p in personas) {
            assertTrue(p.minGapMs in Persona.MIN_GAP_RANGE)
            assertTrue(p.maxTypedMs in Persona.MAX_TYPED_RANGE)
            assertTrue(p.typoRate in Persona.TYPO_RATE_RANGE)
        }
    }

    @Test
    fun typedCapComesFromThePersona() { // TIM-4
        val text = sample.repeat(8)
        val patient = persona(14).copy(maxTypedMs = Long.MAX_VALUE)
        val hasty = persona(14).copy(maxTypedMs = 1_000)
        assertEquals(PlaybackStyle.TYPED, planner.plan(text, PlaybackStyle.TYPED, patient, Random(1)).style)
        assertEquals(PlaybackStyle.GLIDE, planner.plan(text, PlaybackStyle.TYPED, hasty, Random(1)).style)
    }

    @Test
    fun emptyTextMakesEmptyPlan() {
        assertTrue(planner.plan("", PlaybackStyle.TYPED, persona(11), Random(11)).chunks.isEmpty())
    }
}
