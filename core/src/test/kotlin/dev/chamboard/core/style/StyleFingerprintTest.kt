package dev.chamboard.core.style

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Evaluation stand-in for the adversarial test page (docs/design/testing.md): two authors
 * with different habits write about the same things. Normalisation must pull
 * their fingerprints much closer together.
 */
class StyleFingerprintTest {

    private val formalBrit = """
        I have been thinking about the proposal; furthermore, I do not believe it is workable… The colour scheme is grey and the programme is poorly organised!!!
        Moreover, the centre of the plan is weak. It is extremely unclear what we are doing – nobody has realised the cost.
        Nevertheless, I am willing to try again tomorrow; I shall be at the theatre until six. Thus we should meet afterwards.
        In addition, I do not think the travelling budget is sensible. I have not seen a proper analysis, and I cannot recommend it.
    """.trimIndent()

    private val casualAmerican = """
        ive been thinking about the proposal and honestly i dont think its workable lol. the color scheme is gray and the program is kinda badly organized :-)
        also the center of the plan is weak. its super unclear what were doing -- nobody realized the cost tbh.
        still, im willing to try again tmrw, ill be at the theater til six. so we should meet after haha.
        plus i dont think the traveling budget makes sense. havent seen a real analysis and i cant recommend it.
    """.trimIndent()

    private fun gap(engine: StyleEngine?): Double {
        val a = engine?.normalise(formalBrit, Random(1))?.text ?: formalBrit
        val b = engine?.normalise(casualAmerican, Random(1))?.text ?: casualAmerican
        return StyleFingerprint.of(a).distanceTo(StyleFingerprint.of(b))
    }

    @Test
    fun normalisationShrinksTheStylisticGapBetweenAuthors() { // INV-5, STY-3, STY-4
        val raw = gap(null)
        val light = gap(StyleEngine(StyleOptions(level = StyleLevel.LIGHT)))
        val standard = gap(StyleEngine(StyleOptions(level = StyleLevel.STANDARD)))
        val strong = gap(StyleEngine(StyleOptions(level = StyleLevel.STRONG)))
        println("fingerprint gap raw=%.2f light=%.2f standard=%.2f strong=%.2f".format(raw, light, standard, strong))
        assertTrue("light should help", light < raw)
        // Measured at introduction: raw 10.0, light 7.9, standard 6.5 (-35%), strong 3.5 (-65%).
        // Thresholds leave a little margin so they catch regressions, not noise.
        assertTrue("standard ($standard) should cut the gap by 30%+ vs raw ($raw)", standard < raw * 0.7)
        assertTrue("strong ($strong) should cut the gap by 55%+ vs raw ($raw)", strong < raw * 0.45)
    }

    @Test
    fun identicalTextsHaveZeroDistance() {
        assertTrue(StyleFingerprint.of(formalBrit).distanceTo(StyleFingerprint.of(formalBrit)) == 0.0)
    }
}
