package dev.chamboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import dev.chamboard.core.policy.InputTypes
import org.junit.Assert.assertEquals
import org.junit.Test

/** core mirrors Android's constants to stay Android-free (INV-2); they must match the platform. */
class InputTypesMirrorTest {

    @Test
    fun mirroredConstantsMatchThePlatform() { // INV-4
        val pairs = listOf(
            InputTypes.TYPE_NULL to InputType.TYPE_NULL,
            InputTypes.TYPE_MASK_CLASS to InputType.TYPE_MASK_CLASS,
            InputTypes.TYPE_MASK_VARIATION to InputType.TYPE_MASK_VARIATION,
            InputTypes.TYPE_CLASS_TEXT to InputType.TYPE_CLASS_TEXT,
            InputTypes.TYPE_CLASS_NUMBER to InputType.TYPE_CLASS_NUMBER,
            InputTypes.TYPE_CLASS_PHONE to InputType.TYPE_CLASS_PHONE,
            InputTypes.TYPE_CLASS_DATETIME to InputType.TYPE_CLASS_DATETIME,
            InputTypes.TYPE_TEXT_FLAG_MULTI_LINE to InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            InputTypes.TYPE_TEXT_VARIATION_NORMAL to InputType.TYPE_TEXT_VARIATION_NORMAL,
            InputTypes.TYPE_TEXT_VARIATION_URI to InputType.TYPE_TEXT_VARIATION_URI,
            InputTypes.TYPE_TEXT_VARIATION_EMAIL_ADDRESS to InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputTypes.TYPE_TEXT_VARIATION_EMAIL_SUBJECT to InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            InputTypes.TYPE_TEXT_VARIATION_SHORT_MESSAGE to InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputTypes.TYPE_TEXT_VARIATION_LONG_MESSAGE to InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
            InputTypes.TYPE_TEXT_VARIATION_PERSON_NAME to InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
            InputTypes.TYPE_TEXT_VARIATION_POSTAL_ADDRESS to InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            InputTypes.TYPE_TEXT_VARIATION_PASSWORD to InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputTypes.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD to InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputTypes.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT to InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
            InputTypes.TYPE_TEXT_VARIATION_FILTER to InputType.TYPE_TEXT_VARIATION_FILTER,
            InputTypes.TYPE_TEXT_VARIATION_PHONETIC to InputType.TYPE_TEXT_VARIATION_PHONETIC,
            InputTypes.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS to InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputTypes.TYPE_TEXT_VARIATION_WEB_PASSWORD to InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputTypes.TYPE_NUMBER_VARIATION_PASSWORD to InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputTypes.IME_MASK_ACTION to EditorInfo.IME_MASK_ACTION,
            InputTypes.IME_ACTION_GO to EditorInfo.IME_ACTION_GO,
            InputTypes.IME_ACTION_SEARCH to EditorInfo.IME_ACTION_SEARCH,
            InputTypes.IME_ACTION_SEND to EditorInfo.IME_ACTION_SEND,
        )
        pairs.forEachIndexed { i, (mirror, platform) -> assertEquals("pair $i", platform, mirror) }
    }
}
