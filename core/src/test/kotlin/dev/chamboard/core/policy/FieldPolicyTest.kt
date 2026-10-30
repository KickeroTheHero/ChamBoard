package dev.chamboard.core.policy

import dev.chamboard.core.policy.InputTypes.IME_ACTION_GO
import dev.chamboard.core.policy.InputTypes.IME_ACTION_SEARCH
import dev.chamboard.core.policy.InputTypes.IME_ACTION_SEND
import dev.chamboard.core.policy.InputTypes.TYPE_CLASS_DATETIME
import dev.chamboard.core.policy.InputTypes.TYPE_CLASS_NUMBER
import dev.chamboard.core.policy.InputTypes.TYPE_CLASS_PHONE
import dev.chamboard.core.policy.InputTypes.TYPE_CLASS_TEXT
import dev.chamboard.core.policy.InputTypes.TYPE_NULL
import dev.chamboard.core.policy.InputTypes.TYPE_NUMBER_VARIATION_PASSWORD
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_FLAG_MULTI_LINE
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_FILTER
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_LONG_MESSAGE
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_PASSWORD
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_PERSON_NAME
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_PHONETIC
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_URI
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
import dev.chamboard.core.policy.InputTypes.TYPE_TEXT_VARIATION_WEB_PASSWORD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldPolicyTest {

    private fun text(variation: Int = 0, flags: Int = 0, ime: Int = 0) =
        FieldInfo(TYPE_CLASS_TEXT or variation or flags, ime)

    @Test
    fun sensitiveTextFieldsAreNeverRewrittenOrPaced() { // INV-4, POL-2
        val sensitive = listOf(
            TYPE_TEXT_VARIATION_PASSWORD, TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, TYPE_TEXT_VARIATION_WEB_PASSWORD,
            TYPE_TEXT_VARIATION_URI, TYPE_TEXT_VARIATION_EMAIL_ADDRESS, TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            TYPE_TEXT_VARIATION_PERSON_NAME, TYPE_TEXT_VARIATION_POSTAL_ADDRESS, TYPE_TEXT_VARIATION_PHONETIC,
        )
        for (v in sensitive) {
            val field = text(v, TYPE_TEXT_FLAG_MULTI_LINE)
            for (user in Mode.entries) {
                val mode = FieldPolicy.decide(field, user).mode
                assertFalse("variation 0x${v.toString(16)} rewritten in $user", mode.rewrites)
                assertFalse("variation 0x${v.toString(16)} paced in $user", mode.paced)
            }
            assertFalse(FieldPolicy.mayRewrite(field))
        }
    }

    @Test
    fun numericPinIsGuardedNotPassedThrough() { // INV-4
        val pin = FieldInfo(TYPE_CLASS_NUMBER or TYPE_NUMBER_VARIATION_PASSWORD)
        assertEquals(Mode.DRAFT_GUARD, FieldPolicy.decide(pin, Mode.FULL_CLOAK).mode)
    }

    @Test
    fun numbersPhonesDatesAndNullArePassthrough() { // POL-4
        for (type in listOf(TYPE_CLASS_NUMBER, TYPE_CLASS_PHONE, TYPE_CLASS_DATETIME, TYPE_NULL)) {
            assertEquals(Mode.PASSTHROUGH, FieldPolicy.decide(FieldInfo(type), Mode.FULL_CLOAK).mode)
        }
    }

    @Test
    fun searchAndUrlFieldsDefaultToDraftGuardWithEmitAndGo() { // POL-3
        val fields = listOf(
            text(ime = IME_ACTION_SEARCH),
            text(ime = IME_ACTION_GO),
            text(TYPE_TEXT_VARIATION_URI),
            text(TYPE_TEXT_VARIATION_FILTER),
        )
        for (f in fields) {
            val d = FieldPolicy.decide(f, Mode.FULL_CLOAK)
            assertEquals(Mode.DRAFT_GUARD, d.mode)
            assertTrue(d.emitAndGo)
            assertTrue(d.lowered)
        }
    }

    @Test
    fun longMessageFieldsFollowTheUserDefault() { // POL-1
        val field = text(TYPE_TEXT_VARIATION_LONG_MESSAGE, TYPE_TEXT_FLAG_MULTI_LINE, IME_ACTION_SEND)
        for (user in Mode.entries) {
            val d = FieldPolicy.decide(field, user)
            assertEquals(user, d.mode)
            assertFalse(d.lowered)
            assertFalse(d.emitAndGo)
            assertFalse(d.singleLine)
        }
    }

    @Test
    fun userDefaultBelowCeilingIsRespected() { // POL-1
        assertEquals(Mode.PASSTHROUGH, FieldPolicy.decide(text(ime = IME_ACTION_SEARCH), Mode.PASSTHROUGH).mode)
    }

    @Test
    fun bypassForcesPassthroughWithoutLoweredFlag() { // POL-5
        val d = FieldPolicy.decide(text(TYPE_TEXT_VARIATION_LONG_MESSAGE), Mode.FULL_CLOAK, bypass = true)
        assertEquals(Mode.PASSTHROUGH, d.mode)
        assertFalse(d.lowered)
        assertFalse(d.emitAndGo)
    }

    @Test
    fun singleLineUnlessMultiLineFlag() { // TIM-6
        assertTrue(FieldPolicy.decide(text(), Mode.FULL_CLOAK).singleLine)
        assertFalse(FieldPolicy.decide(text(flags = TYPE_TEXT_FLAG_MULTI_LINE), Mode.FULL_CLOAK).singleLine)
    }
}
