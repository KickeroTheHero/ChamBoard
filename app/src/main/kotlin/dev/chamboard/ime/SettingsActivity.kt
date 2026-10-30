package dev.chamboard.ime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import dev.chamboard.core.policy.Mode
import dev.chamboard.core.settings.KeyboardSettings
import dev.chamboard.core.style.StyleLevel
import dev.chamboard.core.timing.PlaybackStyle
import java.util.UUID

/**
 * Setup and settings (UI-4). Every convenience that costs privacy is a switch
 * with its trade-off in one line (UI-8). Stores settings and masks only (INV-3).
 */
class SettingsActivity : Activity() {

    private lateinit var store: Store
    private lateinit var settings: KeyboardSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
    }

    override fun onResume() {
        super.onResume()
        settings = store.settings()
        render()
    }

    private fun render() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (PAD_DP * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val form = Form(this, column)
        setup(form)
        protection(form)
        masks(form)
        convenience(form)
        tryIt(form)
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun update(change: (KeyboardSettings) -> KeyboardSettings) {
        settings = change(settings).validated()
        store.saveSettings(settings)
    }

    private fun setup(form: Form) {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        form.heading("1. Turn on ChamBoard")
        form.note(
            if (enabled) {
                "ChamBoard is turned on. Switch to it with the keyboard icon while typing."
            } else {
                "Android will warn that a keyboard can see what you type. That's true of every keyboard; " +
                    "ChamBoard has no internet access, so it can't send anything anywhere."
            },
        )
        form.button("Open keyboard settings") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        form.button("Switch keyboard") { imm.showInputMethodPicker() }
    }

    private fun protection(form: Form) {
        form.heading("2. Protection")
        form.note("Default mode (some boxes, like passwords and search, always get a safer mode):")
        form.choice(
            listOf(
                Mode.FULL_CLOAK to "Full Cloak: hide draft, disguise style and rhythm",
                Mode.BIOMETRIC_SHIELD to "Biometric Shield: hide draft and rhythm, keep your wording",
                Mode.DRAFT_GUARD to "Draft Guard: hide draft, send all at once",
                Mode.PASSTHROUGH to "Passthrough: no protection",
            ),
            settings.defaultMode,
        ) { mode -> update { it.copy(defaultMode = mode) } }
        form.note("Style tidying strength:")
        form.choice(
            listOf(
                StyleLevel.LIGHT to "Light: spacing, quotes, capitals, smileys",
                StyleLevel.STANDARD to "Standard: + spelling, typos, contractions, stock phrases",
                StyleLevel.STRONG to "Strong: + chat shorthand, filler words, punctuation habits",
            ),
            settings.styleLevel,
        ) { level -> update { it.copy(styleLevel = level) } }
        form.note("Pace when sending:")
        form.choice(
            listOf(
                PlaybackStyle.TYPED to "Typed: letter by letter",
                PlaybackStyle.GLIDE to "Glide: word by word, like swipe typing",
                PlaybackStyle.INSTANT to "Instant: all at once (hides rhythm, but looks like a paste)",
            ),
            settings.playback,
        ) { style -> update { it.copy(playback = style) } }
    }

    private fun masks(form: Form) {
        var book = store.maskBook()
        form.heading("3. Masks")
        form.note(
            "Each app gets its own made-up writer and typist, and keeps it. Named masks let several apps share " +
                "one identity: pick them with the Mask button on the keyboard. Hold that button for a fresh mask.",
        )
        form.toggle(
            "Uniform style",
            "Everyone who turns this on writes in one shared style. Only worth it once many people use it.",
            book.state.uniformStyle,
        ) {
            book = book.withUniformStyle(it)
            store.saveMasks(book)
        }
        for (named in book.state.named) {
            form.button("Delete mask \"${named.name}\"") {
                store.saveMasks(store.maskBook().deleteNamed(named.id))
                render()
            }
        }
        val name = form.add(EditText(this).apply { hint = "New mask name, e.g. Forum account" })
        form.button("Add named mask") {
            val label = name.text.toString().trim()
            if (label.isNotEmpty()) {
                store.saveMasks(store.maskBook().createNamed(UUID.randomUUID().toString(), label))
                render()
            }
        }
    }

    private fun convenience(form: Form) {
        form.heading("4. Convenience")
        form.note("Each switch trades a little privacy for less hassle. Your choice.")
        form.toggle(
            "Keep draft for a minute",
            "Your draft survives a quick app switch. Off: it's erased the moment the keyboard closes.",
            settings.hideGraceMs > 0,
        ) { on -> update { it.copy(hideGraceMs = if (on) GRACE_MS else 0) } }
        form.toggle(
            "Paste from the phone's clipboard (hold Paste)",
            "Reads the phone's clipboard only when you hold Paste.",
            settings.systemPaste,
        ) { on -> update { it.copy(systemPaste = on) } }
        form.toggle(
            "Copy to the phone's clipboard (hold Copy)",
            "Other apps could read it until it's cleared, about a minute later.",
            settings.systemExport,
        ) { on -> update { it.copy(systemExport = on) } }
        form.toggle(
            "Enter sends",
            "In chat and search boxes, Enter emits and sends in one tap.",
            settings.enterEmitsAndSends,
        ) { on -> update { it.copy(enterEmitsAndSends = on) } }
        form.toggle(
            "Word suggestions",
            "Completes the word you're typing from a built-in list. It never learns your words.",
            settings.suggestions,
        ) { on -> update { it.copy(suggestions = on) } }
    }

    private fun tryIt(form: Form) {
        form.heading("5. Try it")
        form.note("Type here with ChamBoard to see what it does.")
        form.add(EditText(this).apply { hint = "Try typing here" })
    }

    private companion object {
        const val PAD_DP = 20f
        const val GRACE_MS = 60_000L
    }
}
