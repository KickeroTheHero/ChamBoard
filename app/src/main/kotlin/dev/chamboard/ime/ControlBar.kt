package dev.chamboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The strip above the keys (UI-3): mode, mask, style strength, clipboard and
 * wipe. Tap and long-press actions are forwarded to [Listener].
 */
@SuppressLint("ViewConstructor")
class ControlBar(context: Context, private val listener: Listener) : HorizontalScrollView(context) {

    interface Listener {
        fun onChip(chip: Chip, longPress: Boolean)
    }

    enum class Chip { MODE, MASK, LEVEL, PASTE, COPY, WIPE }

    private val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val chips = Chip.entries.associateWith { makeChip(it) }

    init {
        isHorizontalScrollBarEnabled = false
        setBackgroundColor(Palette.BAR)
        val pad = dp(PAD_DP)
        row.setPadding(pad, pad, pad, pad)
        addView(row)
        chips.values.forEach(row::addView)
        set(Chip.PASTE, "Paste", description = "Paste from the private clipboard, hold for the system clipboard")
        set(Chip.COPY, "Copy", description = "Copy the draft privately, hold to copy to the system clipboard")
        set(Chip.WIPE, "✕", description = "Wipe draft")
    }

    /** [description] is what TalkBack says when the label alone isn't clear. */
    fun set(chip: Chip, label: String, highlighted: Boolean = false, description: String? = null) {
        val view = chips.getValue(chip)
        view.text = label
        view.contentDescription = description
        (view.background as GradientDrawable).setColor(if (highlighted) Palette.WARN else Palette.KEY)
    }

    /** Only the mode chip matters in Passthrough; the rest act on the draft. */
    fun setDraftChipsVisible(visible: Boolean) {
        listOf(Chip.LEVEL, Chip.COPY, Chip.WIPE).forEach {
            chips.getValue(it).visibility =
                if (visible) VISIBLE else GONE
        }
    }

    private fun makeChip(chip: Chip) = TextView(context).apply {
        setTextColor(Palette.TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
        gravity = Gravity.CENTER
        // One line, never wrapped or cut; the bar scrolls sideways instead.
        isSingleLine = true
        minHeight = dp(CHIP_MIN_HEIGHT_DP)
        val h = dp(CHIP_SIDE_PAD_DP)
        setPadding(h, dp(PAD_DP), h, dp(PAD_DP))
        background = GradientDrawable().apply {
            cornerRadius = dp(RADIUS_DP).toFloat()
            setColor(Palette.KEY)
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = dp(PAD_DP) }
        setOnClickListener { listener.onChip(chip, longPress = false) }
        setOnLongClickListener {
            listener.onChip(chip, longPress = true)
            true
        }
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val PAD_DP = 4f
        const val RADIUS_DP = 14f
        const val TEXT_SP = 13f
        const val CHIP_MIN_HEIGHT_DP = 32f
        const val CHIP_SIDE_PAD_DP = 10f
    }
}
