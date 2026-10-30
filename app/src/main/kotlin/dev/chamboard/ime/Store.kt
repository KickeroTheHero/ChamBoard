package dev.chamboard.ime

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.chamboard.core.mask.MaskBook
import dev.chamboard.core.mask.MaskDeriver
import dev.chamboard.core.mask.MaskState
import dev.chamboard.core.settings.KeyboardSettings
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac

/**
 * The only persistent state: settings, mask state and the device secret
 * (MASK-3). Never typed text (INV-3). App-private and excluded from backups.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun settings(): KeyboardSettings = KeyboardSettings.fromPrefs(stringsWithPrefix(SETTINGS))

    fun saveSettings(settings: KeyboardSettings) = replaceWithPrefix(SETTINGS, settings.validated().toPrefs())

    fun maskBook(): MaskBook = MaskBook(MaskDeriver.withSigner(signer()), MaskState.fromPrefs(stringsWithPrefix(MASKS)))

    fun saveMasks(book: MaskBook) = replaceWithPrefix(MASKS, book.state.toPrefs())

    /**
     * The device secret is a non-exportable Keystore HMAC key (MASK-3): this process can ask it to
     * sign but never read it. The old hex secret in preferences is dropped, so masks change once.
     */
    private fun signer(): MaskDeriver.Signer {
        if (prefs.contains(LEGACY_SECRET)) dropLegacySecret()
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
                .apply { init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build()) }
                .generateKey()
        }
        val key = keyStore.getKey(KEY_ALIAS, null)
        return MaskDeriver.Signer { message ->
            Mac.getInstance(HMAC).run {
                init(key)
                doFinal(message)
            }
        }
    }

    /**
     * Rotations and pins are keyed by HMACs under the old secret, so they can never match again:
     * drop them instead of leaving dead entries. Named masks (id and label) are kept.
     */
    private fun dropLegacySecret() {
        val state = MaskState.fromPrefs(stringsWithPrefix(MASKS))
        replaceWithPrefix(MASKS, state.copy(rotations = emptyMap(), pins = emptyMap()).toPrefs())
        prefs.edit().remove(LEGACY_SECRET).apply()
    }

    private fun stringsWithPrefix(prefix: String): Map<String, String> = prefs.all
        .filterKeys { it.startsWith(prefix) }
        .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(prefix) to it } }
        .toMap()

    private fun replaceWithPrefix(prefix: String, values: Map<String, String>) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        values.forEach { (k, v) -> editor.putString(prefix + k, v) }
        editor.apply()
    }

    private companion object {
        const val FILE = "chamboard"
        const val SETTINGS = "settings."
        const val MASKS = "masks."
        const val LEGACY_SECRET = "device_secret"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "chamboard.device_secret"
        const val HMAC = "HmacSHA256"
    }
}
