package dev.chamboard.core.policy

/**
 * Protection modes (see docs/design/engine.md), ordered from least to most processing so the
 * effective mode is simply the lower of user default and field ceiling (POL-1).
 */
enum class Mode(
    /** Keystrokes go to the private buffer instead of the app (BUF-1). */
    val stages: Boolean,
    /** The style normaliser may rewrite the text (STY). */
    val rewrites: Boolean,
    /** Emitted text is paced with a synthetic rhythm (TIM) rather than committed at once. */
    val paced: Boolean,
) {
    PASSTHROUGH(stages = false, rewrites = false, paced = false),
    DRAFT_GUARD(stages = true, rewrites = false, paced = false),
    BIOMETRIC_SHIELD(stages = true, rewrites = false, paced = true),
    FULL_CLOAK(stages = true, rewrites = true, paced = true),
    ;

    companion object {
        val DEFAULT = FULL_CLOAK

        fun min(a: Mode, b: Mode): Mode = if (a.ordinal <= b.ordinal) a else b
    }
}
