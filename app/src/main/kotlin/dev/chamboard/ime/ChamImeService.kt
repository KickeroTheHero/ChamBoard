package dev.chamboard.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import dev.chamboard.core.buffer.DraftLifecycle
import dev.chamboard.core.buffer.DraftParking
import dev.chamboard.core.buffer.PrivateClipboard
import dev.chamboard.core.mask.MaskBook
import dev.chamboard.core.settings.KeyboardSettings

/**
 * The Android shell: wires lifecycle events, views and the
 * [DraftController] to `core`. No decisions are made here that `core` could make.
 */
class ChamImeService :
    InputMethodService(),
    DraftController.Host,
    Emitter.Listener {

    private val handler = Handler(Looper.getMainLooper())
    private val clock = { SystemClock.elapsedRealtime() }
    private lateinit var store: Store
    private lateinit var masks: MaskBook
    private lateinit var lifecycle: DraftLifecycle
    private lateinit var parking: DraftParking
    private lateinit var privateClipboard: PrivateClipboard
    private lateinit var clipboardBridge: ClipboardBridge
    private lateinit var controller: DraftController
    private lateinit var chips: ChipActions

    override lateinit var settings: KeyboardSettings
    override val field = FieldState()
    override lateinit var emitter: Emitter

    private var keyboard: KeyboardView? = null
    private var controlBar: ControlBar? = null
    private var draftPanel: DraftPanel? = null
    private var suggestionStrip: SuggestionStrip? = null
    private var symbols = false
    private var capsLock = false

    /** Physical keys whose down event went to the draft, so their up event is kept from the app too. */
    private val draftKeysDown = HashSet<Int>()

    /** Pending dead-key accent from a physical keyboard, combined with the next letter. */
    private var deadAccent = 0

    /** Set while switching fields, so aborted playback text is parked, not shown (INV-7). */
    private var switching = false
    private var unsentOnSwitch = ""

    private val hiddenWipe = Runnable { wipeDraft() }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onScreenOff()
    }

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        loadState()
        clipboardBridge = ClipboardBridge(this, handler)
        emitter = Emitter(handler, ::connection, { field.token }, this)
        controller = DraftController(this)
        chips = ChipActions(this, controller, ClipActions(this, controller, clipboardBridge, privateClipboard))
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOff, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        onScreenOff()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        // UI-5: drafts never appear in screenshots or screen recordings.
        window.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val bar = ControlBar(this, chips).also { controlBar = it }
        val panel = DraftPanel(this) { offset ->
            controller.buffer.moveCursorTo(offset)
            refreshUi()
        }.also { draftPanel = it }
        val strip = SuggestionStrip(this, controller.suggestions::accept).also { suggestionStrip = it }
        val keys = KeyboardView(this, ::onKey).also { keyboard = it }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(panel)
            addView(strip)
            addView(keys)
        }
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val oldKey = field.key
        if (field.enter(attribute)) switchField(oldKey)
        field.refresh(settings, masks)
    }

    /** BUF-5 / INV-7: park the old field's text; restore this field's if it was parked. */
    private fun switchField(oldKey: String?) {
        switching = true
        unsentOnSwitch = ""
        emitter.abortFieldChanged()
        switching = false
        val leftover = unsentOnSwitch + controller.buffer.snapshot()
        if (oldKey != null && leftover.isNotEmpty()) parking.park(oldKey, leftover)
        unsentOnSwitch = ""
        lifecycle.onFieldChanged()
        wipeDraft()
        field.key?.let { key -> parking.takeFor(key)?.let(controller.buffer::replaceAll) }
        symbols = false
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        handler.removeCallbacks(hiddenWipe)
        loadState() // settings may have changed in the settings screen
        field.refresh(settings, masks)
        if (lifecycle.onShown(clock())) wipeDraft()
        refreshUi()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (lifecycle.onHidden(clock())) {
            wipeDraft()
        } else {
            lifecycle.wipeDueAt()?.let { handler.postAtTime(hiddenWipe, it) }
        }
    }

    private fun onScreenOff() {
        emitter.abortFieldChanged()
        lifecycle.onScreenOff()
        wipeDraft()
        parking.wipe()
        privateClipboard.wipe()
    }

    private fun wipeDraft() {
        controller.wipe()
        refreshUi()
    }

    private fun loadState() {
        settings = store.settings()
        masks = store.maskBook()
        if (!::lifecycle.isInitialized) {
            lifecycle = DraftLifecycle(settings.hideGraceMs)
            parking = DraftParking(clock, settings.hideGraceMs)
            privateClipboard = PrivateClipboard(clock, settings.privateClipboardTtlMs)
        }
        parking.graceMs = settings.hideGraceMs
        privateClipboard.ttlMs = settings.privateClipboardTtlMs
    }

    private fun onKey(key: Key) {
        when (key) {
            Key.Shift -> onShift()
            Key.ToSymbols, Key.ToLetters -> {
                symbols = key == Key.ToSymbols
                keyboard?.showSymbols(symbols)
            }
            else -> {
                if (!field.isReady) return
                controller.onKey(key)
                if (!capsLock && key is Key.Text) keyboard?.shift = autoShift()
            }
        }
    }

    private val staging get() = field.isReady && field.decision.mode.stages

    /**
     * A physical keyboard often hides on-screen keyboards. In staging modes the draft and EMIT
     * must stay visible, or typing would vanish into a draft nobody can see (BUF-1).
     */
    override fun onEvaluateInputViewShown(): Boolean = super.onEvaluateInputViewShown() || staging

    /** Physical keys edit the draft in staging modes; in Passthrough they reach the field (POL-4). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!staging) return super.onKeyDown(keyCode, event)
        val action = HardwareKeys.map(
            keyCode,
            event.getUnicodeChar(event.metaState),
            event.isCtrlPressed || event.isMetaPressed,
        )
        when (action) {
            HardwareAction.Pass -> return super.onKeyDown(keyCode, event)
            is HardwareAction.DeadKey -> deadAccent = action.accent
            is HardwareAction.MoveCursor -> {
                val buffer = controller.buffer
                if (buffer.isEmpty) return super.onKeyDown(keyCode, event)
                buffer.moveCursorTo((buffer.cursor + action.delta).coerceIn(0, buffer.length))
                refreshUi()
            }
            is HardwareAction.Draft -> onKey(withAccent(action.key))
        }
        draftKeysDown += keyCode
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        draftKeysDown.remove(keyCode) || super.onKeyUp(keyCode, event)

    /** Applies a pending dead-key accent: ´ then e gives é; an accent that can't combine is dropped. */
    private fun withAccent(key: Key): Key {
        val accent = deadAccent
        deadAccent = 0
        if (accent == 0 || key !is Key.Text || key.value.length != 1) return key
        val combined = KeyCharacterMap.getDeadChar(accent, key.value[0].code)
        return if (combined != 0) Key.Text(String(Character.toChars(combined))) else key
    }

    private fun onShift() {
        val kb = keyboard ?: return
        kb.shift = when (kb.shift) {
            KeyboardView.ShiftState.OFF -> KeyboardView.ShiftState.ONCE
            KeyboardView.ShiftState.ONCE -> KeyboardView.ShiftState.LOCKED
            KeyboardView.ShiftState.LOCKED -> KeyboardView.ShiftState.OFF
        }
        capsLock = kb.shift == KeyboardView.ShiftState.LOCKED
    }

    private fun autoShift() =
        if (controller.shouldAutoShift()) KeyboardView.ShiftState.ONCE else KeyboardView.ShiftState.OFF

    /** Settings or masks changed from a chip: persist and re-decide. */
    fun onStateChanged(newSettings: KeyboardSettings = settings, newMasks: MaskBook = masks) {
        settings = newSettings
        masks = newMasks
        store.saveSettings(settings)
        store.saveMasks(masks)
        field.refresh(settings, masks)
        refreshUi()
    }

    val maskBook: MaskBook get() = masks

    override fun refreshUi() {
        if (!field.isReady) return
        val staging = field.decision.mode.stages
        val sending = emitter.isRunning
        draftPanel?.visibility = if (staging) View.VISIBLE else View.GONE
        controlBar?.let { chips.render(it) }
        suggestionStrip?.show(if (sending) emptyList() else controller.suggestions.offered())
        keyboard?.apply {
            emitEnabled = staging
            emitLabel = when {
                sending -> "STOP"
                field.decision.emitAndGo -> "GO"
                else -> "EMIT"
            }
            if (!capsLock) shift = autoShift()
        }
        if (!sending) {
            val placeholder = if (staging) "Private draft: the app sees nothing until you press EMIT" else ""
            draftPanel?.showDraft(controller.buffer.view, controller.buffer.cursor, placeholder)
            draftPanel?.showPreview(controller.preview())
        }
    }

    override fun connection(): InputConnection? = currentInputConnection

    override fun sendKey(keyCode: Int) = sendDownUpKeyEvents(keyCode)

    override fun performFieldAction(): Boolean = sendDefaultEditorAction(true)

    override fun onProgress(done: Int, total: Int) {
        draftPanel?.showStatus("Sending… $done/$total  (tap STOP to keep the rest as a draft)")
    }

    override fun onEnded(completed: Boolean, unsent: String) {
        if (switching) {
            unsentOnSwitch = unsent
            return
        }
        controller.onPlaybackEnded(completed, unsent)
    }
}
