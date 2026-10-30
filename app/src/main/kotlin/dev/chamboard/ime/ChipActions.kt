package dev.chamboard.ime

import android.widget.Toast
import dev.chamboard.core.policy.FieldInfo
import dev.chamboard.core.policy.FieldPolicy
import dev.chamboard.core.policy.Mode
import dev.chamboard.core.style.StyleLevel

/**
 * What the control-bar chips do (UI-3, MASK-2, CLIP-1..3). Tap and long-press
 * are deliberately different so destructive or outward actions need a hold.
 */
class ChipActions(
    private val service: ChamImeService,
    private val controller: DraftController,
    private val clip: ClipActions,
) : ControlBar.Listener {

    override fun onChip(chip: ControlBar.Chip, longPress: Boolean) {
        when (chip) {
            ControlBar.Chip.MODE -> if (longPress) toggleBypass() else cycleMode()
            ControlBar.Chip.MASK -> if (longPress) rotateMask() else cyclePin()
            ControlBar.Chip.LEVEL -> cycleLevel()
            ControlBar.Chip.PASTE -> if (longPress) clip.pasteFromSystem() else clip.pastePrivate()
            ControlBar.Chip.COPY -> if (longPress) clip.exportToSystem() else clip.copyPrivate()
            ControlBar.Chip.WIPE -> controller.wipe()
        }
        service.refreshUi()
    }

    fun render(bar: ControlBar) {
        val field = service.field
        val mode = field.decision.mode
        val modeLabel = mode.name.replace('_', ' ') + if (field.decision.lowered) " ↓" else ""
        // UI-3: say what the chip does, and that the field lowered the mode.
        val lowered = if (field.decision.lowered) ", lowered for this field" else ""
        val modeHelp = "Mode: " + mode.name.replace('_', ' ').lowercase() + lowered +
            ". Tap to change, hold to switch Passthrough on or off"
        bar.set(ControlBar.Chip.MODE, modeLabel, highlighted = mode == Mode.PASSTHROUGH, description = modeHelp)
        bar.set(ControlBar.Chip.MASK, "Mask: " + maskName())
        bar.set(ControlBar.Chip.LEVEL, "Style: " + service.settings.styleLevel.name.lowercase())
        bar.setDraftChipsVisible(mode.stages)
    }

    /** UI-3: cycle through the modes this field allows. */
    private fun cycleMode() {
        val field = service.field
        val info = field.editorInfo ?: return
        val ceiling = FieldPolicy.ceiling(FieldInfo(info.inputType, info.imeOptions))
        val allowed = Mode.entries.filter { it.ordinal <= ceiling.ordinal }
        val current = allowed.indexOf(field.decision.mode).coerceAtLeast(0)
        field.bypass = false
        field.modeOverride = allowed[(current + 1) % allowed.size]
        service.onStateChanged()
    }

    /** POL-5: emergency Passthrough for this field. */
    private fun toggleBypass() {
        service.field.bypass = !service.field.bypass
        service.onStateChanged()
        toast(if (service.field.bypass) "Passthrough for this box: no protection" else "Protection back on")
    }

    private fun maskName(): String {
        val book = service.maskBook
        val uniform = if (book.state.uniformStyle) " (uniform style)" else ""
        return (book.pinnedMask(service.field.packageName)?.name ?: "this app") + uniform
    }

    /** MASK-2: this app's own mask -> each named mask -> back. */
    private fun cyclePin() {
        val book = service.maskBook
        val options = listOf<String?>(null) + book.state.named.map { it.id }
        val current = book.state.named.firstOrNull {
            book.scopeFor(service.field.packageName).key.endsWith(":${it.id}")
        }?.id
        val next = options[(options.indexOf(current) + 1) % options.size]
        service.onStateChanged(newMasks = book.pin(service.field.packageName, next))
        if (options.size == 1) toast("Create named masks in ChamBoard settings")
    }

    /** MASK-2: a fresh mask for this app (or its pinned named mask). */
    private fun rotateMask() {
        val book = service.maskBook
        service.onStateChanged(newMasks = book.rotate(book.scopeFor(service.field.packageName)))
        toast("New mask: this app will now see a different writer and typist")
    }

    private fun cycleLevel() {
        val next = StyleLevel.entries[(service.settings.styleLevel.ordinal + 1) % StyleLevel.entries.size]
        service.onStateChanged(newSettings = service.settings.copy(styleLevel = next))
    }

    private fun toast(message: String) = Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
}
