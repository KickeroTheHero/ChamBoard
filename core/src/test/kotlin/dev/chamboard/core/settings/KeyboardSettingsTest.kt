package dev.chamboard.core.settings

import dev.chamboard.core.policy.Mode
import dev.chamboard.core.style.StyleLevel
import dev.chamboard.core.timing.PlaybackStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSettingsTest {

    @Test
    fun defaultsFavourPrivacyWithLowFriction() { // UI-8
        val d = KeyboardSettings()
        assertEquals(Mode.FULL_CLOAK, d.defaultMode)
        assertEquals(60_000L, d.hideGraceMs)
        assertTrue(d.systemPaste && d.systemExport && d.enterEmitsAndSends && d.suggestions)
        assertEquals(0.0, d.synonymRate, 0.0)
    }

    @Test
    fun roundTripsThroughPreferences() { // UI-4
        val s = KeyboardSettings(
            defaultMode = Mode.BIOMETRIC_SHIELD,
            styleLevel = StyleLevel.STRONG,
            playback = PlaybackStyle.GLIDE,
            synonymRate = 0.5,
            hideGraceMs = 0,
            systemPaste = false,
            enterEmitsAndSends = false,
        )
        assertEquals(s, KeyboardSettings.fromPrefs(s.toPrefs()))
    }

    @Test
    fun malformedPreferencesFallBackToDefaultsAndAreClamped() { // UI-4
        val s = KeyboardSettings.fromPrefs(
            mapOf(
                "mode" to "BOGUS",
                "synonymRate" to "7",
                "hideGraceMs" to "-5",
                "exportTtlMs" to "x",
                "suggestions" to "maybe",
            ),
        )
        assertEquals(Mode.FULL_CLOAK, s.defaultMode)
        assertEquals(1.0, s.synonymRate, 0.0)
        assertEquals(0L, s.hideGraceMs)
        assertEquals(60_000L, s.exportTtlMs)
        assertTrue(s.suggestions)
    }

    @Test
    fun preferencesHoldNoText() { // INV-3
        val keys = KeyboardSettings().toPrefs().keys
        assertTrue(keys.none { it.contains("draft") || it.contains("text") })
    }
}
