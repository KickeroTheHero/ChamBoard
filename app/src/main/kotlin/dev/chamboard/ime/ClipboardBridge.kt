package dev.chamboard.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.PersistableBundle

/**
 * The only code allowed to touch the system clipboard (INV-3;
 * enforced by CI). Both directions run only on an explicit tap.
 */
class ClipboardBridge(context: Context, private val handler: Handler) {

    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var clearExport: Runnable? = null

    /** CLIP-2: read plain text from the system clipboard, on tap only. */
    fun readOnTap(): String? {
        val clip = clipboard?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()
    }

    /** CLIP-3: export on tap, marked sensitive, cleared after [ttlMs] if it's still ours. */
    fun exportOnTap(text: String, ttlMs: Long) {
        val cm = clipboard ?: return
        val clip = ClipData.newPlainText(LABEL, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(sensitiveExtra(), true) }
        cm.setPrimaryClip(clip)
        clearExport?.let(handler::removeCallbacks)
        clearExport = Runnable {
            val current = cm.primaryClipDescription
            if (current != null && current.label == LABEL) cm.clearPrimaryClip()
        }.also { handler.postDelayed(it, ttlMs) }
    }

    private fun sensitiveExtra(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        "android.content.extra.IS_SENSITIVE"
    }

    private companion object {
        const val LABEL = "ChamBoard export"
    }
}
