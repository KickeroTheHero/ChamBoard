package dev.chamboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import dev.chamboard.core.buffer.CursorMarked
import dev.chamboard.core.style.StyleResult

/**
 * The draft (with cursor) and, in Full Cloak, what will actually be sent with
 * changes highlighted (UI-1, STY-6). Tapping the draft moves the cursor.
 */
@SuppressLint("ViewConstructor")
class DraftPanel(context: Context, private val onCursor: (Int) -> Unit) : LinearLayout(context) {

    private val draft = line(Palette.TEXT)
    private val preview = line(Palette.TEXT_DIM)
    private var cursorShown = -1

    init {
        orientation = VERTICAL
        setBackgroundColor(Palette.BAR)
        val pad = dp(PAD_DP)
        setPadding(pad, pad / 2, pad, pad / 2)
        addView(draft)
        addView(preview)
        draft.setOnTouchListener { v, e -> onDraftTouch(v as TextView, e) }
    }

    /** [text] may be the live draft view: the TextView makes the one copy it needs. */
    fun showDraft(text: CharSequence, cursor: Int, placeholder: String) {
        if (text.isEmpty()) {
            cursorShown = -1
            draft.text = SpannableString(placeholder).apply {
                setSpan(ForegroundColorSpan(Palette.TEXT_DIM), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return
        }
        cursorShown = cursor
        draft.text = CursorMarked(text, cursor, CURSOR)
    }

    fun showPreview(result: StyleResult?) {
        if (result == null) {
            preview.visibility = GONE
            return
        }
        preview.visibility = VISIBLE
        val prefix = "Sends: "
        val span = SpannableString(prefix + result.text)
        for (c in result.changes) {
            span.setSpan(
                BackgroundColorSpan(Palette.CHANGE),
                prefix.length + c.start,
                prefix.length + c.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        preview.text = span
    }

    fun showStatus(status: String) {
        preview.visibility = VISIBLE
        preview.text = status
    }

    private fun onDraftTouch(view: TextView, e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP || cursorShown < 0) return true
        val offset = view.getOffsetForPosition(e.x, e.y)
        // Offsets after the drawn cursor glyph are one too high.
        onCursor(if (offset > cursorShown) offset - 1 else offset)
        return true
    }

    private fun line(color: Int) = TextView(context).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
        maxLines = MAX_LINES
        isVerticalScrollBarEnabled = true
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val CURSOR = '│'
        const val PAD_DP = 8f
        const val TEXT_SP = 15f
        const val MAX_LINES = 3
    }
}
