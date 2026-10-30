package dev.chamboard.ime

import android.widget.Toast
import dev.chamboard.core.buffer.PrivateClipboard

/** Paste and copy chips (CLIP-1..3): tap = private clipboard, hold = the phone's clipboard. */
class ClipActions(
    private val service: ChamImeService,
    private val controller: DraftController,
    private val bridge: ClipboardBridge,
    private val clipboard: PrivateClipboard,
) {
    fun pastePrivate() {
        val text = clipboard.latest()
        if (text ==
            null
        ) {
            toast("Private clipboard is empty. Hold Paste for the phone's clipboard.")
        } else {
            controller.insert(text)
        }
    }

    fun pasteFromSystem() {
        if (!service.settings.systemPaste) return toast("Pasting from the phone's clipboard is off in settings")
        bridge.readOnTap()?.let(controller::insert) ?: toast("The phone's clipboard is empty")
    }

    fun copyPrivate() {
        val text = controller.buffer.snapshot()
        if (text.isEmpty()) return
        clipboard.copy(text)
        toast("Copied to ChamBoard's private clipboard")
    }

    fun exportToSystem() {
        if (!service.settings.systemExport) return toast("Copying to the phone's clipboard is off in settings")
        val text = controller.buffer.snapshot()
        if (text.isEmpty()) return
        bridge.exportOnTap(text, service.settings.exportTtlMs)
        toast("Copied to the phone's clipboard; it clears itself shortly")
    }

    private fun toast(message: String) = Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
}
