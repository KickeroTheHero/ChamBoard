package dev.chamboard.core.timing

import kotlin.random.Random

/**
 * Builds a Typed plan (TIM-3): one step per grapheme with human-like gaps, and
 * at the persona's rate a typo that is noticed and corrected (TIM-8). The
 * field always ends up holding exactly the intended text.
 */
internal class TypedComposer(private val persona: Persona, rng: Random) {

    private val timing = Timing(persona, rng)
    private val out = mutableListOf<Chunk>()
    private var previous: Char? = null
    private var previousGrapheme = ""

    fun compose(text: String): List<Chunk> {
        val graphemes = Graphemes.split(text)
        var typoAt = -1
        for ((i, g) in graphemes.withIndex()) {
            if (startsWord(graphemes, i) && timing.chance(persona.typoRate)) typoAt = pickTypoIndex(graphemes, i)
            if (i == typoAt) typo(graphemes, i)
            type(g)
        }
        return out
    }

    private fun type(g: String) {
        val delay = if (out.isEmpty()) {
            0L
        } else {
            timing.floor(
                timing.keyGap(previous, g[0]) + timing.pauseAfter(previousGrapheme),
            )
        }
        out += Chunk(g, delay)
        previous = g[0]
        previousGrapheme = g
    }

    /** Hit a neighbouring key, maybe type one more letter, pause, backspace; the caller then types correctly. */
    private fun typo(graphemes: List<String>, i: Int) {
        val intended = graphemes[i][0]
        val wrong = timing.pick(KeyboardGeometry.neighbours(intended))
        type(wrong.toString())
        var mistakes = 1
        val next = graphemes.getOrNull(i + 1)
        if (next != null && isLetter(next) && timing.chance(persona.lookaheadProbability)) {
            type(next)
            mistakes++
        }
        out += Chunk("", timing.floor(timing.reaction()), deleteBefore = mistakes)
        previous = null // the finger comes from the backspace key
    }

    private fun startsWord(graphemes: List<String>, i: Int): Boolean =
        isLetter(graphemes[i]) && (i == 0 || graphemes[i - 1].isBlank())

    /** A random letter of the word starting at [start] that has keyboard neighbours. */
    private fun pickTypoIndex(graphemes: List<String>, start: Int): Int {
        var end = start
        while (end < graphemes.size && isLetter(graphemes[end])) end++
        val candidates = (start until end).filter { KeyboardGeometry.neighbours(graphemes[it][0]).isNotEmpty() }
        return if (candidates.isEmpty()) -1 else candidates[timing.nextInt(candidates.size)]
    }

    private fun isLetter(g: String) = g.length == 1 && g[0] in 'a'..'z' || g.length == 1 && g[0] in 'A'..'Z'
}
