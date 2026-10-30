package dev.chamboard.core.policy

/**
 * The parts of Android's `EditorInfo` the policy needs, as raw ints so `core`
 * stays free of Android (INV-2). The app passes `inputType` and `imeOptions`
 * straight through; [InputTypes] mirrors the platform constant values.
 */
data class FieldInfo(val inputType: Int, val imeOptions: Int = 0)

/** What the keyboard should do in one text field. */
data class FieldDecision(
    val mode: Mode,
    /** The field forced a lower mode than the user's default; show it on the mode chip (UI-3). */
    val lowered: Boolean,
    /** Newlines must be emitted as spaces (TIM-6). */
    val singleLine: Boolean,
    /** Offer "Emit & Go": commit, then perform the field's editor action (POL-3). */
    val emitAndGo: Boolean,
)

object FieldPolicy {

    /**
     * POL-1: effective mode = lower of the user's default and the field's ceiling.
     * POL-5: [bypass] (emergency one-tap Passthrough) wins until the next field.
     */
    fun decide(field: FieldInfo, userDefault: Mode, bypass: Boolean = false): FieldDecision {
        val ceiling = ceiling(field)
        val chosen = if (bypass) Mode.PASSTHROUGH else Mode.min(userDefault, ceiling)
        return FieldDecision(
            mode = chosen,
            lowered = !bypass && chosen != userDefault,
            singleLine = field.inputType and InputTypes.TYPE_TEXT_FLAG_MULTI_LINE == 0,
            emitAndGo = chosen.stages && isSearchLike(field),
        )
    }

    /** The most processing a field allows, whatever the user prefers. */
    fun ceiling(field: FieldInfo): Mode {
        val cls = field.inputType and InputTypes.TYPE_MASK_CLASS
        val variation = field.inputType and InputTypes.TYPE_MASK_VARIATION
        return when {
            // POL-4: nothing to protect / needs live keys.
            field.inputType == InputTypes.TYPE_NULL -> Mode.PASSTHROUGH
            cls == InputTypes.TYPE_CLASS_NUMBER && variation == InputTypes.TYPE_NUMBER_VARIATION_PASSWORD ->
                Mode.DRAFT_GUARD // PIN: hide the draft, commit atomically, never rewrite.
            cls != InputTypes.TYPE_CLASS_TEXT -> Mode.PASSTHROUGH
            // POL-2 / INV-4: never rewritten, never paced.
            variation in NEVER_REWRITE -> Mode.DRAFT_GUARD
            // POL-3: search & URL bars leak every keystroke through autocomplete.
            isSearchLike(field) -> Mode.DRAFT_GUARD
            else -> Mode.FULL_CLOAK
        }
    }

    /** INV-4, for callers that only need a yes/no before running the normaliser. */
    fun mayRewrite(field: FieldInfo): Boolean = ceiling(field).rewrites

    private fun isSearchLike(field: FieldInfo): Boolean {
        val variation = field.inputType and InputTypes.TYPE_MASK_VARIATION
        val action = field.imeOptions and InputTypes.IME_MASK_ACTION
        return variation == InputTypes.TYPE_TEXT_VARIATION_URI ||
            variation == InputTypes.TYPE_TEXT_VARIATION_FILTER ||
            action == InputTypes.IME_ACTION_SEARCH ||
            action == InputTypes.IME_ACTION_GO
    }

    private val NEVER_REWRITE = setOf(
        InputTypes.TYPE_TEXT_VARIATION_PASSWORD,
        InputTypes.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputTypes.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputTypes.TYPE_TEXT_VARIATION_URI,
        InputTypes.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputTypes.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputTypes.TYPE_TEXT_VARIATION_PERSON_NAME,
        InputTypes.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
        InputTypes.TYPE_TEXT_VARIATION_PHONETIC,
        InputTypes.TYPE_TEXT_VARIATION_FILTER,
    )
}

/**
 * Mirrors of `android.text.InputType` and `android.view.inputmethod.EditorInfo`
 * constants. These are stable public API values; the app module asserts they
 * match the platform at compile time.
 */
@Suppress("MagicNumber") // Values are defined by the Android platform.
object InputTypes {
    const val TYPE_NULL = 0
    const val TYPE_MASK_CLASS = 0x0000000f
    const val TYPE_MASK_VARIATION = 0x00000ff0
    const val TYPE_CLASS_TEXT = 0x00000001
    const val TYPE_CLASS_NUMBER = 0x00000002
    const val TYPE_CLASS_PHONE = 0x00000003
    const val TYPE_CLASS_DATETIME = 0x00000004
    const val TYPE_TEXT_FLAG_MULTI_LINE = 0x00020000
    const val TYPE_TEXT_VARIATION_NORMAL = 0x00000000
    const val TYPE_TEXT_VARIATION_URI = 0x00000010
    const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020
    const val TYPE_TEXT_VARIATION_EMAIL_SUBJECT = 0x00000030
    const val TYPE_TEXT_VARIATION_SHORT_MESSAGE = 0x00000040
    const val TYPE_TEXT_VARIATION_LONG_MESSAGE = 0x00000050
    const val TYPE_TEXT_VARIATION_PERSON_NAME = 0x00000060
    const val TYPE_TEXT_VARIATION_POSTAL_ADDRESS = 0x00000070
    const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
    const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    const val TYPE_TEXT_VARIATION_WEB_EDIT_TEXT = 0x000000a0
    const val TYPE_TEXT_VARIATION_FILTER = 0x000000b0
    const val TYPE_TEXT_VARIATION_PHONETIC = 0x000000c0
    const val TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS = 0x000000d0
    const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010
    const val IME_MASK_ACTION = 0x000000ff
    const val IME_ACTION_GO = 0x00000002
    const val IME_ACTION_SEARCH = 0x00000003
    const val IME_ACTION_SEND = 0x00000004
}
