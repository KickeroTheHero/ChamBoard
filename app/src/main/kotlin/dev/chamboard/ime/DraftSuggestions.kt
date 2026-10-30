package dev.chamboard.ime

import dev.chamboard.core.buffer.StagingBuffer
import dev.chamboard.core.suggest.Suggester

/** UI-7 completions for the word before the cursor. Reads and edits the draft only (BUF-1). */
class DraftSuggestions(private val host: DraftController.Host, private val buffer: StagingBuffer) {

    fun offered(): List<String> {
        if (!active()) return emptyList()
        return Suggester.complete(Suggester.wordPrefixBefore(buffer.view, buffer.cursor))
    }

    /** Replace the word fragment before the cursor with [word] and a space. */
    fun accept(word: String) {
        if (!active()) return
        val prefix = Suggester.wordPrefixBefore(buffer.view, buffer.cursor)
        repeat(prefix.length) { buffer.deleteBackward() }
        buffer.insert("$word ")
        host.refreshUi()
    }

    private fun active() = host.field.decision.mode.stages && host.settings.suggestions && !host.emitter.isRunning
}
