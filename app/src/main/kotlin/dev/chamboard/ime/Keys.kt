package dev.chamboard.ime

/** What a key does. The service decides what that means in the current mode. */
sealed interface Key {
    data class Text(val value: String) : Key

    data object Backspace : Key

    data object Shift : Key

    data object Space : Key

    /** Newline in multi-line fields; otherwise the field's action (UI-6). */
    data object Enter : Key

    /** EMIT (or STOP during playback). */
    data object Emit : Key

    data object ToSymbols : Key

    data object ToLetters : Key
}

data class KeySpec(val key: Key, val label: String, val weight: Float = 1f)

/** QWERTY letters and one symbols page (see docs/design/ui.md). */
object Layouts {

    private fun letters(row: String) = row.map { KeySpec(Key.Text(it.toString()), it.toString()) }

    private fun bottomRow(switchKey: KeySpec) = listOf(
        switchKey,
        KeySpec(Key.Text(","), ","),
        KeySpec(Key.Space, "space", weight = 3.5f),
        KeySpec(Key.Text("."), "."),
        KeySpec(Key.Enter, "↵", weight = 1.2f),
        KeySpec(Key.Emit, "EMIT", weight = 1.8f),
    )

    val LETTERS: List<List<KeySpec>> = listOf(
        letters("qwertyuiop"),
        letters("asdfghjkl"),
        listOf(KeySpec(Key.Shift, "⇧", 1.4f)) + letters("zxcvbnm") + KeySpec(Key.Backspace, "⌫", 1.4f),
        bottomRow(KeySpec(Key.ToSymbols, "?123", 1.4f)),
    )

    val SYMBOLS: List<List<KeySpec>> = listOf(
        letters("1234567890"),
        letters("@#$%&-+()/"),
        letters("*\"':;!?_=") + KeySpec(Key.Backspace, "⌫", 1.4f),
        bottomRow(KeySpec(Key.ToLetters, "ABC", 1.4f)),
    )
}
