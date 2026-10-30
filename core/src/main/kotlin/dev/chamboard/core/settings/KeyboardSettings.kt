package dev.chamboard.core.settings

import dev.chamboard.core.buffer.PrivateClipboard
import dev.chamboard.core.policy.Mode
import dev.chamboard.core.style.StyleLevel
import dev.chamboard.core.timing.PlaybackStyle

/**
 * User settings (UI-4). Only INV-1..7 are absolute; every convenience cost
 * below is a toggle with a sensible default (UI-8). Never holds typed text.
 */
data class KeyboardSettings(
    val defaultMode: Mode = Mode.DEFAULT,
    val styleLevel: StyleLevel = StyleLevel.STANDARD,
    val playback: PlaybackStyle = PlaybackStyle.TYPED,
    /** STY-5, 0.0–1.0. */
    val synonymRate: Double = 0.0,
    /** BUF-3: how long a hidden draft survives; 0 wipes on hide. */
    val hideGraceMs: Long = 60_000,
    /** CLIP-1. */
    val privateClipboardTtlMs: Long = PrivateClipboard.DEFAULT_TTL_MS,
    /** CLIP-2: paste key may read the system clipboard (on tap only). */
    val systemPaste: Boolean = true,
    /** CLIP-3: allow "copy to system clipboard". */
    val systemExport: Boolean = true,
    /** CLIP-3: auto-clear an exported clip after this long. */
    val exportTtlMs: Long = 60_000,
    /** UI-6. */
    val enterEmitsAndSends: Boolean = true,
    /** UI-7. */
    val suggestions: Boolean = true,
) {
    /** Clamps out-of-range values (e.g. from an edited preferences file). */
    fun validated(): KeyboardSettings = copy(
        synonymRate = synonymRate.coerceIn(0.0, 1.0),
        hideGraceMs = hideGraceMs.coerceIn(0, MAX_GRACE_MS),
        privateClipboardTtlMs = privateClipboardTtlMs.coerceIn(MIN_TTL_MS, MAX_TTL_MS),
        exportTtlMs = exportTtlMs.coerceIn(MIN_TTL_MS, MAX_TTL_MS),
    )

    fun toPrefs(): Map<String, String> = mapOf(
        "mode" to defaultMode.name,
        "level" to styleLevel.name,
        "playback" to playback.name,
        "synonymRate" to synonymRate.toString(),
        "hideGraceMs" to hideGraceMs.toString(),
        "privateClipboardTtlMs" to privateClipboardTtlMs.toString(),
        "systemPaste" to systemPaste.toString(),
        "systemExport" to systemExport.toString(),
        "exportTtlMs" to exportTtlMs.toString(),
        "enterEmitsAndSends" to enterEmitsAndSends.toString(),
        "suggestions" to suggestions.toString(),
    )

    companion object {
        private const val MAX_GRACE_MS = 10 * 60_000L
        private const val MIN_TTL_MS = 5_000L
        private const val MAX_TTL_MS = 24 * 60 * 60_000L

        /** Unknown or malformed values fall back to defaults, never crash. */
        fun fromPrefs(prefs: Map<String, String>): KeyboardSettings {
            val d = KeyboardSettings()
            return KeyboardSettings(
                defaultMode = enumOr(prefs["mode"], d.defaultMode),
                styleLevel = enumOr(prefs["level"], d.styleLevel),
                playback = enumOr(prefs["playback"], d.playback),
                synonymRate = prefs["synonymRate"]?.toDoubleOrNull() ?: d.synonymRate,
                hideGraceMs = prefs["hideGraceMs"]?.toLongOrNull() ?: d.hideGraceMs,
                privateClipboardTtlMs = prefs["privateClipboardTtlMs"]?.toLongOrNull() ?: d.privateClipboardTtlMs,
                systemPaste = prefs["systemPaste"]?.toBooleanStrictOrNull() ?: d.systemPaste,
                systemExport = prefs["systemExport"]?.toBooleanStrictOrNull() ?: d.systemExport,
                exportTtlMs = prefs["exportTtlMs"]?.toLongOrNull() ?: d.exportTtlMs,
                enterEmitsAndSends = prefs["enterEmitsAndSends"]?.toBooleanStrictOrNull() ?: d.enterEmitsAndSends,
                suggestions = prefs["suggestions"]?.toBooleanStrictOrNull() ?: d.suggestions,
            ).validated()
        }

        private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == value } ?: default
    }
}
