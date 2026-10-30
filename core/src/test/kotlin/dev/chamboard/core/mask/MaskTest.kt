package dev.chamboard.core.mask

import dev.chamboard.core.style.StyleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class MaskTest {

    private val secret = ByteArray(32) { it.toByte() }
    private val deriver = MaskDeriver(secret)
    private val book = MaskBook(deriver, MaskState())

    @Test
    fun stableRandomMatchesSplitMix64ReferenceOutput() { // MASK-3: seeds must mean the same mask forever
        // First SplitMix64 output for seed 0 is 0xE220A8397B1DCDAF; nextInt() returns its top 32 bits.
        assertEquals(0xE220A839.toInt(), StableRandom(0).nextInt())
    }

    @Test
    fun externalSignerGivesTheSameMasksAsTheSameSecret() { // MASK-3: a Keystore key plugs in unchanged
        val mac = javax.crypto.Mac.getInstance("HmacSHA256").apply {
            init(javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"))
        }
        val external = MaskDeriver.withSigner { mac.doFinal(it) }
        val scope = MaskScope.app("org.example.chat")
        assertEquals(deriver.derive(scope, 2), external.derive(scope, 2))
        assertEquals(deriver.storageKey(scope), external.storageKey(scope))
    }

    @Test
    fun sameSecretScopeAndRotationGiveTheSameMask() { // MASK-3
        val a = MaskDeriver(secret.copyOf()).derive(MaskScope.app("org.example.chat"), 0)
        val b = MaskDeriver(secret.copyOf()).derive(MaskScope.app("org.example.chat"), 0)
        assertEquals(a, b)
    }

    @Test
    fun scopesRotationsAndSecretsAllChangeTheMask() { // MASK-2, MASK-3
        val base = deriver.derive(MaskScope.app("org.example.chat"), 0)
        assertNotEquals(base, deriver.derive(MaskScope.app("org.example.forum"), 0))
        assertNotEquals(base, deriver.derive(MaskScope.app("org.example.chat"), 1))
        assertNotEquals(base, deriver.derive(MaskScope.named("org.example.chat"), 0))
        assertNotEquals(
            base,
            MaskDeriver(MaskDeriver.newSecret(SecureRandom())).derive(MaskScope.app("org.example.chat"), 0),
        )
    }

    @Test
    fun storageKeysDontRevealTheApp() { // MASK-3
        val key = deriver.storageKey(MaskScope.app("org.example.chat"))
        assertFalse(key.contains("example"))
        assertEquals(64, key.length)
        assertNotEquals(key, MaskDeriver(MaskDeriver.newSecret()).storageKey(MaskScope.app("org.example.chat")))
    }

    @Test
    fun eachAppGetsItsOwnStableMask() { // MASK-2
        assertEquals(book.maskFor("org.example.chat"), book.maskFor("org.example.chat"))
        assertNotEquals(book.maskFor("org.example.chat"), book.maskFor("org.example.forum"))
    }

    @Test
    fun rotatingChangesOnlyThatScope() { // MASK-2
        val chat = MaskScope.app("org.example.chat")
        val rotated = book.rotate(chat)
        assertNotEquals(book.maskFor("org.example.chat"), rotated.maskFor("org.example.chat"))
        assertEquals(book.maskFor("org.example.forum"), rotated.maskFor("org.example.forum"))
        assertEquals(rotated.maskFor("org.example.chat"), rotated.maskFor("org.example.chat"))
    }

    @Test
    fun pinnedAppsShareTheirNamedMask() { // MASK-2
        val b = book.createNamed("n1", "Forum alt").pin("org.example.chat", "n1").pin("org.example.forum", "n1")
        assertEquals(MaskScope.named("n1"), b.scopeFor("org.example.chat"))
        assertEquals(NamedMask("n1", "Forum alt"), b.pinnedMask("org.example.chat"))
        assertEquals(null, book.pinnedMask("org.example.chat"))
        assertEquals(b.maskFor("org.example.chat"), b.maskFor("org.example.forum"))
        assertEquals(book.maskFor("org.example.chat"), b.pin("org.example.chat", null).maskFor("org.example.chat"))
    }

    @Test
    fun deletingANamedMaskUnpinsItsApps() { // MASK-2
        val b = book.createNamed("n1", "Alt").pin("org.example.chat", "n1").deleteNamed("n1")
        assertEquals(MaskScope.app("org.example.chat"), b.scopeFor("org.example.chat"))
        assertTrue(b.state.pins.isEmpty())
    }

    @Test
    fun uniformStyleKeepsTimingPerMask() { // MASK-4
        val uniform = book.withUniformStyle(true)
        assertEquals(StyleProfile.UNIFORM, uniform.maskFor("org.example.chat").style)
        assertEquals(book.maskFor("org.example.chat").timing, uniform.maskFor("org.example.chat").timing)
        assertNotEquals(uniform.maskFor("org.example.chat").timing, uniform.maskFor("org.example.forum").timing)
    }

    @Test
    fun stateRoundTripsThroughPreferences() { // MASK-3
        val b = book.createNamed(
            "n1",
            "Alt",
        ).pin("org.example.chat", "n1").rotate(MaskScope.named("n1")).withUniformStyle(true)
        val restored = MaskState.fromPrefs(b.state.toPrefs())
        assertEquals(b.state, restored)
        assertEquals(b.maskFor("org.example.chat"), MaskBook(deriver, restored).maskFor("org.example.chat"))
        assertTrue(b.state.toPrefs().keys.none { "example" in it })
    }

    @Test
    fun masksVaryAcrossAppsInStyleAndTiming() { // MASK-5
        val masks = (1..50).map { book.maskFor("org.example.app$it") }
        assertTrue(masks.map { it.style }.toSet().size > 40)
        assertTrue(masks.map { it.timing.wpm }.toSet().size == 50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortSecretsAreRejected() {
        MaskDeriver(ByteArray(8))
    }
}
