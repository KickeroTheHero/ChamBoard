package dev.chamboard.core.style

/**
 * Word-level diff between the draft and the normalised text, for highlighting
 * in the preview (STY-6). Rules never add or remove newlines, so lines are
 * diffed pairwise with an LCS over tokens; oversized lines are marked whole.
 */
internal object ChangeDiff {

    private const val MAX_TOKENS_PER_LINE = 1_500

    fun between(before: String, after: String): List<StyleChange> {
        if (before == after) return emptyList()
        val a = before.split('\n')
        val b = after.split('\n')
        if (a.size != b.size) return listOf(StyleChange(before, after, 0, after.length))
        val changes = mutableListOf<StyleChange>()
        var offset = 0
        for (i in a.indices) {
            if (a[i] != b[i]) changes += diffLine(a[i], b[i], offset)
            offset += b[i].length + 1
        }
        return changes
    }

    private fun diffLine(before: String, after: String, offset: Int): List<StyleChange> {
        val x = Tokenizer.tokenize(before).map { it.text }
        val y = Tokenizer.tokenize(after).map { it.text }
        if (x.size > MAX_TOKENS_PER_LINE || y.size > MAX_TOKENS_PER_LINE) {
            return listOf(StyleChange(before, after, offset, offset + after.length))
        }
        return Walk(x, y, lcsTable(x, y), offset).run()
    }

    // Cell (i, j) holds the LCS length of the suffixes x from i and y from j.
    private fun lcsTable(x: List<String>, y: List<String>): Array<IntArray> {
        val lcs = Array(x.size + 1) { IntArray(y.size + 1) }
        for (i in x.indices.reversed()) {
            for (j in y.indices.reversed()) {
                lcs[i][j] = if (x[i] == y[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        return lcs
    }

    /** Walks the LCS table, grouping runs of removed/added tokens into changes. */
    private class Walk(
        private val x: List<String>,
        private val y: List<String>,
        private val lcs: Array<IntArray>,
        private var pos: Int,
    ) {
        private val out = mutableListOf<StyleChange>()
        private val removed = StringBuilder()
        private val added = StringBuilder()

        fun run(): List<StyleChange> {
            var i = 0
            var j = 0
            while (i < x.size || j < y.size) {
                val same = i < x.size && j < y.size && x[i] == y[j]
                val takeAdded = !same && j < y.size && (i == x.size || lcs[i][j + 1] >= lcs[i + 1][j])
                when {
                    same -> {
                        flush()
                        pos += y[j++].length
                        i++
                    }
                    takeAdded -> {
                        added.append(y[j])
                        pos += y[j++].length
                    }
                    else -> removed.append(x[i++])
                }
            }
            flush()
            return out.filter { it.before.isNotBlank() || it.after.isNotBlank() }
        }

        private fun flush() {
            if (removed.isEmpty() && added.isEmpty()) return
            out += StyleChange(removed.toString(), added.toString(), pos - added.length, pos)
            removed.clear()
            added.clear()
        }
    }
}
