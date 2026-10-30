package dev.chamboard.core.timing

/**
 * Splits text into user-perceived characters so typed playback never commits
 * half an emoji: surrogate pairs, ZWJ sequences, skin tones, variation
 * selectors, keycaps, combining marks and flag pairs stay together.
 */
internal object Graphemes {

    fun split(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        var regionalCount = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val joinsPrevious = current.isNotEmpty() &&
                (
                    isExtender(cp) ||
                        current.codePointBefore() == ZWJ ||
                        (isRegionalIndicator(cp) && regionalCount % 2 == 1)
                    )
            if (!joinsPrevious && current.isNotEmpty()) {
                out += current.toString()
                current.clear()
                regionalCount = 0
            }
            current.appendCodePoint(cp)
            if (isRegionalIndicator(cp)) regionalCount++
            i += Character.charCount(cp)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private fun StringBuilder.codePointBefore(): Int = Character.codePointBefore(this, length)

    private fun isExtender(cp: Int): Boolean = cp == ZWJ ||
        cp in VARIATION_SELECTORS ||
        cp in SKIN_TONES ||
        cp == KEYCAP ||
        Character.getType(cp).let {
            it == Character.NON_SPACING_MARK.toInt() ||
                it == Character.ENCLOSING_MARK.toInt() ||
                it == Character.COMBINING_SPACING_MARK.toInt()
        }

    private fun isRegionalIndicator(cp: Int) = cp in REGIONAL_INDICATORS

    private const val ZWJ = 0x200D
    private const val KEYCAP = 0x20E3
    private val VARIATION_SELECTORS = 0xFE00..0xFE0F
    private val SKIN_TONES = 0x1F3FB..0x1F3FF
    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
}
