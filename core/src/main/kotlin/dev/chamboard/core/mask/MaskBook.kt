package dev.chamboard.core.mask

import dev.chamboard.core.style.StyleProfile

/** A user-created identity (MASK-2). [name] is the user's label, e.g. "Reddit throwaway". */
data class NamedMask(val id: String, val name: String)

/**
 * Everything persisted about masks (MASK-3): rotation counters and pins keyed
 * by [MaskDeriver.storageKey], named-mask labels, and the Uniform switch.
 * Never any typed text (INV-3).
 */
data class MaskState(
    val rotations: Map<String, Int> = emptyMap(),
    /** App storage key -> named mask id. */
    val pins: Map<String, String> = emptyMap(),
    val named: List<NamedMask> = emptyList(),
    /** MASK-4: use the fixed Uniform style profile (timing stays per mask). */
    val uniformStyle: Boolean = false,
) {
    /** Flat string map for SharedPreferences. */
    fun toPrefs(): Map<String, String> = buildMap {
        rotations.forEach { (k, v) -> put("$ROT$k", v.toString()) }
        pins.forEach { (k, v) -> put("$PIN$k", v) }
        named.forEach { put("$NAMED${it.id}", it.name) }
        put(UNIFORM, uniformStyle.toString())
    }

    companion object {
        private const val ROT = "mask.rot."
        private const val PIN = "mask.pin."
        private const val NAMED = "mask.named."
        private const val UNIFORM = "mask.uniform"

        fun fromPrefs(prefs: Map<String, String>): MaskState = MaskState(
            rotations = prefs.filterKeys { it.startsWith(ROT) }
                .mapNotNull { (k, v) -> v.toIntOrNull()?.let { k.removePrefix(ROT) to it } }.toMap(),
            pins = prefs.filterKeys { it.startsWith(PIN) }.mapKeys { it.key.removePrefix(PIN) },
            named = prefs.filterKeys { it.startsWith(NAMED) }
                .map { (k, v) -> NamedMask(k.removePrefix(NAMED), v) }.sortedBy { it.name },
            uniformStyle = prefs[UNIFORM].toBoolean(),
        )
    }
}

/** Resolves and edits masks (MASK-2). Pure: every edit returns a new [MaskState] to persist. */
class MaskBook(private val deriver: MaskDeriver, val state: MaskState) {

    /** The scope used for an app: its pinned named mask, or the app's own. */
    fun scopeFor(packageName: String): MaskScope =
        pinnedMask(packageName)?.let { MaskScope.named(it.id) } ?: MaskScope.app(packageName)

    fun maskFor(packageName: String): Mask = maskFor(scopeFor(packageName))

    /** The named mask [packageName] is pinned to, or null if it uses its own. */
    fun pinnedMask(packageName: String): NamedMask? {
        val id = state.pins[deriver.storageKey(MaskScope.app(packageName))] ?: return null
        return state.named.firstOrNull { it.id == id }
    }

    fun maskFor(scope: MaskScope): Mask {
        val mask = deriver.derive(scope, state.rotations[deriver.storageKey(scope)] ?: 0)
        return if (state.uniformStyle) mask.copy(style = StyleProfile.UNIFORM) else mask
    }

    /** A fresh mask for [scope]; the old one can't be recovered. */
    fun rotate(scope: MaskScope): MaskBook {
        val key = deriver.storageKey(scope)
        return with(state.copy(rotations = state.rotations + (key to (state.rotations[key] ?: 0) + 1)))
    }

    fun createNamed(id: String, name: String): MaskBook {
        require(state.named.none { it.id == id }) { "duplicate mask id" }
        return with(state.copy(named = state.named + NamedMask(id, name)))
    }

    /** Deletes a named mask and every pin to it. */
    fun deleteNamed(id: String): MaskBook = with(
        state.copy(
            named = state.named.filterNot { it.id == id },
            pins = state.pins.filterValues { it != id },
            rotations = state.rotations - deriver.storageKey(MaskScope.named(id)),
        ),
    )

    /** Pins [packageName] to a named mask, or back to its own mask when [namedId] is null. */
    fun pin(packageName: String, namedId: String?): MaskBook {
        val key = deriver.storageKey(MaskScope.app(packageName))
        return with(state.copy(pins = if (namedId == null) state.pins - key else state.pins + (key to namedId)))
    }

    fun withUniformStyle(enabled: Boolean): MaskBook = with(state.copy(uniformStyle = enabled))

    private fun with(newState: MaskState) = MaskBook(deriver, newState)
}
