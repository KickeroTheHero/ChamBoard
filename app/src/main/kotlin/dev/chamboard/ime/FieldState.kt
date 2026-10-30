package dev.chamboard.ime

import android.view.inputmethod.EditorInfo
import dev.chamboard.core.mask.Mask
import dev.chamboard.core.mask.MaskBook
import dev.chamboard.core.policy.FieldDecision
import dev.chamboard.core.policy.FieldInfo
import dev.chamboard.core.policy.FieldPolicy
import dev.chamboard.core.policy.Mode
import dev.chamboard.core.settings.KeyboardSettings

/** Everything about the field currently being typed into. */
class FieldState {
    var key: String? = null
        private set

    /** Changes whenever the field changes; playback only runs while it matches (INV-7). */
    var token: Long = 0
        private set

    var editorInfo: EditorInfo? = null
        private set

    var packageName: String = ""
        private set

    /** Mode chosen with the mode chip for this field only (UI-3). */
    var modeOverride: Mode? = null

    /** Emergency Passthrough for this field only (POL-5). */
    var bypass: Boolean = false

    lateinit var decision: FieldDecision
        private set
    lateinit var mask: Mask
        private set

    /** Returns true if this is a different field from the last one. */
    fun enter(info: EditorInfo): Boolean {
        editorInfo = info
        packageName = info.packageName.orEmpty()
        val newKey = "$packageName#${info.fieldId}#${info.inputType}"
        if (newKey == key) return false
        key = newKey
        token++
        modeOverride = null
        bypass = false
        return true
    }

    fun refresh(settings: KeyboardSettings, masks: MaskBook) {
        val info = editorInfo ?: return
        decision =
            FieldPolicy.decide(FieldInfo(info.inputType, info.imeOptions), modeOverride ?: settings.defaultMode, bypass)
        mask = masks.maskFor(packageName)
    }

    val isReady: Boolean get() = editorInfo != null && ::decision.isInitialized
}
