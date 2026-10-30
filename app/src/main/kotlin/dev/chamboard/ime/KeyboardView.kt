package dev.chamboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeProvider

/**
 * Draws the keys and turns touches into [Key]s. Keys fire on release; the
 * backspace key fires on press and repeats while held. No logic beyond that:
 * the service decides what each key means.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context, private val onKey: (Key) -> Unit) : View(context) {

    enum class ShiftState { OFF, ONCE, LOCKED }

    private var rows: List<List<KeySpec>> = Layouts.LETTERS
    private val rects = mutableListOf<Pair<RectF, KeySpec>>()
    private val pressed = mutableMapOf<Int, KeySpec>()
    private val repeatHandler = Handler(Looper.getMainLooper())
    private val repeatBackspace = object : Runnable {
        override fun run() {
            onKey(Key.Backspace)
            repeatHandler.postDelayed(this, REPEAT_MS)
        }
    }

    private val accessibility = KeyboardAccessibility(this)

    var shift: ShiftState = ShiftState.OFF
        set(value) {
            if (field == value) return
            field = value
            changed()
        }

    /** Label on the EMIT key: "EMIT", "GO", "STOP"… */
    var emitLabel: String = "EMIT"
        set(value) {
            if (field == value) return
            field = value
            changed()
        }

    var emitEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            changed()
        }

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.KEY }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.KEY_PRESSED }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.ACCENT }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.TEXT
        textAlign = Paint.Align.CENTER
        textSize = sp(TEXT_SP)
    }

    init {
        setBackgroundColor(Palette.BACKGROUND)
        contentDescription = "ChamBoard keys"
    }

    fun showSymbols(symbols: Boolean) {
        rows = if (symbols) Layouts.SYMBOLS else Layouts.LETTERS
        layoutKeys()
        changed()
    }

    private fun changed() {
        invalidate()
        accessibility.contentChanged()
    }

    // Per-key accessibility: keys are virtual views served by KeyboardAccessibility.
    internal val keys: List<Pair<RectF, KeySpec>> get() = rects

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = accessibility

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        if (manager == null || !manager.isTouchExplorationEnabled) return super.dispatchHoverEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                accessibility.onHover(rects.indexOfFirst { it.first.contains(event.x, event.y) })
            MotionEvent.ACTION_HOVER_EXIT -> accessibility.onHover(-1)
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (rows.size * dp(KEY_HEIGHT_DP) + dp(PADDING_DP) * 2).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutKeys()

    private fun layoutKeys() {
        rects.clear()
        if (width == 0) return
        val gap = dp(GAP_DP)
        val rowHeight = dp(KEY_HEIGHT_DP)
        rows.forEachIndexed { r, row ->
            val unit = (width - dp(PADDING_DP) * 2) / row.sumOf { it.weight.toDouble() }.toFloat()
            var x = dp(PADDING_DP)
            val top = dp(PADDING_DP) + r * rowHeight
            for (spec in row) {
                val w = spec.weight * unit
                rects += RectF(x + gap, top + gap, x + w - gap, top + rowHeight - gap) to spec
                x += w
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val radius = dp(RADIUS_DP)
        for ((rect, spec) in rects) {
            val paint = when {
                spec in pressed.values -> pressedPaint
                spec.key == Key.Emit && emitEnabled -> accentPaint
                spec.key == Key.Shift && shift != ShiftState.OFF -> accentPaint
                else -> keyPaint
            }
            canvas.drawRoundRect(rect, radius, radius, paint)
            val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(labelFor(spec), rect.centerX(), baseline, textPaint)
        }
    }

    private fun labelFor(spec: KeySpec): String = when {
        spec.key == Key.Emit -> emitLabel
        spec.key is Key.Text && shift != ShiftState.OFF -> spec.label.uppercase()
        else -> spec.label
    }

    // Accessibility services click keys through KeyboardAccessibility's virtual views, not performClick().
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val id = event.getPointerId(index)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> press(id, event.getX(index), event.getY(index))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> release(id)
            MotionEvent.ACTION_CANCEL -> {
                pressed.clear()
                repeatHandler.removeCallbacks(repeatBackspace)
            }
        }
        invalidate()
        return true
    }

    private fun press(pointer: Int, x: Float, y: Float) {
        val spec = rects.firstOrNull { it.first.contains(x, y) }?.second ?: return
        pressed[pointer] = spec
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (spec.key == Key.Backspace) {
            onKey(Key.Backspace)
            repeatHandler.postDelayed(repeatBackspace, REPEAT_START_MS)
        }
    }

    private fun release(pointer: Int) {
        val spec = pressed.remove(pointer) ?: return
        if (spec.key == Key.Backspace) {
            repeatHandler.removeCallbacks(repeatBackspace)
            return
        }
        typeKey(spec)
    }

    /** One key press, from a tap or from TalkBack's double-tap or lift-to-type. */
    internal fun typeKey(spec: KeySpec) {
        val key = spec.key
        onKey(if (key is Key.Text && shift != ShiftState.OFF) Key.Text(key.value.uppercase()) else key)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun sp(v: Float) = v * resources.displayMetrics.scaledDensity

    private companion object {
        const val KEY_HEIGHT_DP = 50f
        const val PADDING_DP = 4f
        const val GAP_DP = 3f
        const val RADIUS_DP = 6f
        const val TEXT_SP = 20f
        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 50L
    }
}
