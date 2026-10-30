package dev.chamboard.core.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackSessionTest {

    private val text = "The quick brown fox jumps over the lazy dog."
    private val typoProne = Persona.random(Random(1)).copy(typoRate = 0.6, lookaheadProbability = 0.5)
    private val plan = PlaybackPlanner(Long.MAX_VALUE).plan(text, PlaybackStyle.TYPED, typoProne, Random(2))

    /** A fake text field that applies steps like an InputConnection would. */
    private class Field {
        val content = StringBuilder()

        fun apply(c: Chunk) {
            content.setLength(content.length - c.deleteBefore)
            content.append(c.text)
        }
    }

    private fun run(session: PlaybackSession, field: Field, steps: Int, token: Long = 1L) {
        repeat(steps) {
            val c = session.nextFor(token) ?: return
            field.apply(c)
            session.markExecuted()
        }
    }

    @Test
    fun fullPlaybackLeavesExactlyTheText() { // TIM-8, INV-7
        val session = PlaybackSession(plan, fieldToken = 1)
        val field = Field()
        run(session, field, plan.chunks.size)
        assertTrue(session.isFinished)
        assertEquals(text, field.content.toString())
        assertEquals(StopResult(0, ""), session.stopByUser())
    }

    @Test
    fun userStopMidTypoCleansTheFieldAndReturnsTheRest() { // TIM-5, INV-7
        val typoStep = plan.chunks.indexOfFirst { it.deleteBefore > 0 }
        val session = PlaybackSession(plan, fieldToken = 1)
        val field = Field()
        run(session, field, typoStep) // stop right before the backspace
        val stop = session.stopByUser()
        assertTrue("expected stray typo letters", stop.deleteStray > 0)
        field.content.setLength(field.content.length - stop.deleteStray)
        assertEquals(text, field.content.toString() + stop.remaining)
    }

    @Test
    fun anotherFieldGetsNothing() { // INV-7
        val session = PlaybackSession(plan, fieldToken = 1)
        val field = Field()
        run(session, field, 5)
        assertNull(session.nextFor(currentToken = 2))
        val abort = session.abortFieldChanged()
        assertEquals(0, abort.deleteStray)
        assertEquals(text, field.content.substring(0, plan.progress(5).correctPrefix) + abort.remaining)
    }

    @Test
    fun stopAtEveryStepAlwaysRecoversTheWholeText() { // INV-7
        for (steps in 0..plan.chunks.size) {
            val session = PlaybackSession(plan, fieldToken = 1)
            val field = Field()
            run(session, field, steps)
            val stop = session.stopByUser()
            field.content.setLength(field.content.length - stop.deleteStray)
            assertEquals("after $steps steps", text, field.content.toString() + stop.remaining)
        }
    }

    @Test(expected = IllegalStateException::class)
    fun cannotRunPastTheEnd() {
        val session = PlaybackSession(PlaybackPlan(listOf(Chunk("a", 0)), PlaybackStyle.INSTANT, "a"), 1)
        session.markExecuted()
        session.markExecuted()
    }
}
