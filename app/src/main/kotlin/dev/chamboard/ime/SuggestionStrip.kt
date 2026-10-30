package dev.chamboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Word completions above the keys (UI-7). Hidden when there is nothing to offer.
 * Tapping one is forwarded to [onPick]; it edits the draft, never the field (BUF-1).
 */
@SuppressLint("ViewConstructor")
class SuggestionStrip(context: Context, private val onPick: (String) -> Unit) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        setBackgroundColor(Palette.BAR)
        visibility = GONE
        repeat(SLOTS) { addView(slot()) }
    }

    fun show(words: List<String>) {
        visibility = if (words.isEmpty()) GONE else VISIBLE
        for (i in 0 until SLOTS) {
            val view = getChildAt(i) as TextView
            view.text = words.getOrNull(i).orEmpty()
            view.visibility = if (i < words.size) VISIBLE else INVISIBLE
        }
    }

    private fun slot() = TextView(context).apply {
        setTextColor(Palette.TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
        gravity = Gravity.CENTER
        val pad = (PAD_DP * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { text.toString().takeIf(String::isNotEmpty)?.let(onPick) }
    }

    private companion object {
        const val SLOTS = 3
        const val PAD_DP = 8f
        const val TEXT_SP = 16f
    }
}
