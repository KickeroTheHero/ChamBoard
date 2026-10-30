package dev.chamboard.ime

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView

/** Tiny builder for the settings screen: views in code, no resources, so the local compile check works. */
class Form(private val context: Context, val root: LinearLayout) {

    fun heading(text: String) = add(
        TextView(context).apply {
            this.text = text
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADING_SP)
            setPadding(0, dp(SECTION_GAP_DP), 0, dp(SMALL_GAP_DP))
        },
    )

    fun note(text: String) = add(
        TextView(context).apply {
            this.text = text
            alpha = NOTE_ALPHA
            setPadding(0, 0, 0, dp(SMALL_GAP_DP))
        },
    )

    fun button(text: String, onClick: () -> Unit) = add(
        Button(context).apply {
            this.text = text
            setOnClickListener { onClick() }
        },
    )

    /** A switch with a one-line explanation of what it trades (UI-8). */
    fun toggle(title: String, tradeoff: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        add(
            Switch(context).apply {
                text = title
                isChecked = checked
                setOnCheckedChangeListener { _, value -> onChange(value) }
            },
        )
        note(tradeoff)
    }

    fun <T> choice(options: List<Pair<T, String>>, selected: T, onChange: (T) -> Unit) {
        val group = RadioGroup(context)
        options.forEachIndexed { i, (value, label) ->
            group.addView(
                RadioButton(context).apply {
                    id = View.generateViewId()
                    text = label
                    isChecked = value == selected
                    tag = i
                },
            )
        }
        group.setOnCheckedChangeListener { g, id -> onChange(options[g.findViewById<View>(id).tag as Int].first) }
        add(group)
    }

    fun <V : View> add(view: V): V = view.also(root::addView)

    private fun dp(v: Float) = (v * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val HEADING_SP = 18f
        const val SECTION_GAP_DP = 20f
        const val SMALL_GAP_DP = 6f
        const val NOTE_ALPHA = 0.7f
    }
}
