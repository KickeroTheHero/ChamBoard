package dev.chamboard.core.mask

import dev.chamboard.core.style.StyleProfile
import dev.chamboard.core.timing.Persona
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A writing and typing persona (MASK-1). Nothing in it comes from the user. */
data class Mask(val style: StyleProfile, val timing: Persona)

/** What a mask belongs to (MASK-2): one app, or a user-named identity. */
@ConsistentCopyVisibility
data class MaskScope private constructor(val key: String) {
    companion object {
        fun app(packageName: String) = MaskScope("app:$packageName")

        fun named(id: String) = MaskScope("named:$id")
    }
}

/**
 * Derives masks from a device secret (MASK-3): the same secret, scope and
 * rotation always give the same mask, so nothing about a mask is stored.
 */
class MaskDeriver private constructor(private val signer: Signer) {

    /** HMAC-SHA256 under a key this class never sees, so the app can keep it non-exportable. */
    fun interface Signer {
        fun sign(message: ByteArray): ByteArray
    }

    constructor(deviceSecret: ByteArray) : this(secretSigner(deviceSecret))

    fun derive(scope: MaskScope, rotation: Int): Mask {
        val seed = ByteBuffer.wrap(hmac("mask:${scope.key}#$rotation")).long
        val rng = StableRandom(seed)
        return Mask(style = StyleProfile.sample(rng), timing = Persona.random(rng))
    }

    /** Key under which a scope's state is stored; reveals nothing about the app or name. */
    fun storageKey(scope: MaskScope): String = hmac("store:${scope.key}").joinToString("") { "%02x".format(it) }

    private fun hmac(message: String): ByteArray = signer.sign(message.toByteArray(Charsets.UTF_8))

    companion object {
        const val SECRET_BYTES = 32
        private const val HMAC = "HmacSHA256"

        /** For a key held outside this process's memory, such as the Android Keystore. */
        fun withSigner(signer: Signer) = MaskDeriver(signer)

        fun newSecret(random: SecureRandom = SecureRandom()): ByteArray =
            ByteArray(SECRET_BYTES).also(random::nextBytes)

        private fun secretSigner(deviceSecret: ByteArray): Signer {
            require(deviceSecret.size >= SECRET_BYTES) { "device secret must be at least $SECRET_BYTES bytes" }
            val key = SecretKeySpec(deviceSecret.copyOf(), HMAC)
            return Signer { message ->
                Mac.getInstance(HMAC).run {
                    init(key)
                    doFinal(message)
                }
            }
        }
    }
}
