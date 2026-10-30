package dev.chamboard.ime

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import dev.chamboard.core.mask.StableRandom
import dev.chamboard.core.style.StyleEngine
import dev.chamboard.core.style.StyleLevel
import dev.chamboard.core.style.StyleOptions
import dev.chamboard.core.style.StyleProfile
import dev.chamboard.core.suggest.Suggester
import java.io.File
import kotlin.random.Random

/**
 * Renders the real keyboard views in a few states, plus the settings screen,
 * to PNGs for CI. Plain [Instrumentation]: no test libraries.
 * Run: `adb shell am instrument -w dev.chamboard.test/dev.chamboard.ime.ScreenshotInstrumentation`
 */
class ScreenshotInstrumentation : Instrumentation() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        super.onStart()
        val results = Bundle()
        val code = try {
            val out = File(targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            runOnMainSync {
                keyboardScenes(targetContext).forEach { (name, view) -> save(render(view), File(out, name)) }
                save(launcherIcon(), File(out, "7-icon.png"))
            }
            settingsScreen(out)
            results.putString("screenshots", out.list()?.sorted()?.joinToString())
            Activity.RESULT_OK
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) { // report any failure to CI
            results.putString("error", e.stackTraceToString())
            Activity.RESULT_CANCELED
        }
        finish(code, results)
    }

    private fun keyboardScenes(context: Context): List<Pair<String, View>> {
        val draft = "honestly i dont think its workable lol... the colour is grey and it's extremely odd"
        val mask = StyleProfile.sample(StableRandom(MASK_SEED))
        val preview = StyleEngine(StyleOptions(level = StyleLevel.STANDARD, profile = mask)).normalise(draft, Random(1))
        return listOf(
            "1-full-cloak.png" to keyboard(context, "FULL CLOAK", "EMIT") {
                it.showDraft(draft, draft.length, "")
                it.showPreview(preview)
            },
            "2-search-box.png" to keyboard(context, "DRAFT GUARD ↓", "GO") {
                it.showDraft("cheap flights to lisbon", "cheap flights to lisbon".length, "")
                it.showPreview(null)
            },
            "3-sending.png" to keyboard(context, "FULL CLOAK", "STOP") {
                it.showStatus("Sending… 23/61  (tap STOP to keep the rest as a draft)")
            },
            "4-empty-draft.png" to keyboard(context, "BIOMETRIC SHIELD", "EMIT", symbols = true) {
                it.showDraft("", 0, "Private draft: the app sees nothing until you press EMIT")
                it.showPreview(null)
            },
            "6-suggestions.png" to keyboard(context, "DRAFT GUARD ↓", "EMIT") {
                it.showDraft(SUGGEST_DRAFT, SUGGEST_DRAFT.length, "")
                it.showPreview(null)
            }.also { view ->
                val strip = view.getChildAt(STRIP_INDEX) as SuggestionStrip
                strip.show(Suggester.complete(Suggester.wordPrefixBefore(SUGGEST_DRAFT, SUGGEST_DRAFT.length)))
            },
        )
    }

    private fun keyboard(
        context: Context,
        mode: String,
        emitLabel: String,
        symbols: Boolean = false,
        fillDraft: (DraftPanel) -> Unit,
    ): LinearLayout {
        val bar = ControlBar(
            context,
            object : ControlBar.Listener {
                override fun onChip(chip: ControlBar.Chip, longPress: Boolean) = Unit
            },
        )
        bar.set(ControlBar.Chip.MODE, mode, highlighted = false)
        bar.set(ControlBar.Chip.MASK, "Mask: this app")
        bar.set(ControlBar.Chip.LEVEL, "Style: standard")
        val panel = DraftPanel(context) {}.also(fillDraft)
        val strip = SuggestionStrip(context) {}
        val keys = KeyboardView(context) {}.apply {
            this.emitLabel = emitLabel
            showSymbols(symbols)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(panel)
            addView(strip)
            addView(keys)
        }
    }

    /** The launcher icon as the system draws it, mask applied. */
    private fun launcherIcon(): Bitmap {
        val icon = targetContext.packageManager.getApplicationIcon(targetContext.packageName)
        return Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888).also {
            icon.setBounds(0, 0, ICON_PX, ICON_PX)
            icon.draw(Canvas(it))
        }
    }

    /** Renders at the device's real width, so the CI phone profile sets the resolution (see docs/design/testing.md). */
    private fun render(view: View): Bitmap {
        val width = view.resources.displayMetrics.widthPixels
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, width, view.measuredHeight)
        return Bitmap.createBitmap(width, view.measuredHeight, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun settingsScreen(out: File) {
        val intent = Intent(targetContext, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = startActivitySync(intent)
        waitForIdleSync()
        Thread.sleep(SETTLE_MS)
        uiAutomation.takeScreenshot()?.let { save(it, File(out, "5-settings.png")) }
        activity.finish()
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }

    private companion object {
        const val ICON_PX = 432
        const val MASK_SEED = 7L
        const val STRIP_INDEX = 2
        const val SUGGEST_DRAFT = "thanks for the he"
        const val SETTLE_MS = 1_500L
        const val PNG_QUALITY = 100
    }
}
