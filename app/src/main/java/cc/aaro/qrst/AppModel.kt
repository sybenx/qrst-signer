package cc.aaro.qrst

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import cc.aaro.qrst.core.CodeCheck
import cc.aaro.qrst.core.DemoTextProfile
import cc.aaro.qrst.core.Mode
import cc.aaro.qrst.core.NsecProfile
import cc.aaro.qrst.core.PairingParams
import cc.aaro.qrst.core.PairingUri
import cc.aaro.qrst.core.Phase
import cc.aaro.qrst.core.Role
import cc.aaro.qrst.core.SystemEnv
import cc.aaro.qrst.core.UriException
import cc.aaro.qrst.core.scannerRole
import cc.aaro.qrst.core.wipe
import cc.aaro.qrst.net.KeyValue
import cc.aaro.qrst.net.Refusal
import cc.aaro.qrst.net.Store
import cc.aaro.qrst.net.Transfer
import cc.aaro.qrst.net.TransferDeps
import cc.aaro.qrst.net.TransferView
import cc.aaro.qrst.vault.Vault
import cc.aaro.qrst.vault.VaultEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Where QRs this app shows point, and where App Links bring the camera back to us (§11.2a). */
const val BASE_URL = "https://qrst.aaro.cc/"

sealed class Screen {
    data object Home : Screen()
    data object Scan : Screen()
    data object Import : Screen()
    data object Settings : Screen()
    data object Log : Screen()
    /** Choose which key goes to the device behind `params`, or (params null) which key to offer by showing a code. */
    data class PickKey(val params: PairingParams?, val viaCamera: Boolean, val demoCheck: CodeCheck = CodeCheck.NONE) : Screen()
    data object Transfer : Screen()
}

/** The transfer on screen, and what the app knows around it. */
class Active(
    val transfer: Transfer<*>,
    val demo: Boolean,
    /** Sender: the key that goes out, revealed from the vault only at release. */
    val entry: VaultEntry?,
) {
    /** Sender: the payload has been handed to the session, so later attempts need no fresh unlock. */
    var unlocked = false
    /** Receiver: the received key has been written to the vault. */
    var stored: VaultEntry? = null
    var storeError: String? = null
}

data class UiState(
    val screen: Screen = Screen.Home,
    val keys: List<VaultEntry> = emptyList(),
    val active: Active? = null,
    val view: TransferView<*>? = null,
    val message: String? = null,
    val demo: Boolean = false,
    /** §9.3: several failed sessions within the hour. Acknowledge before another send. */
    val interferenceWarning: Boolean = false,
    val rev: Int = 0,
)

class PrefsKeyValue(context: Context) : KeyValue {
    private val prefs = context.getSharedPreferences("qrst", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}

class AppModel(app: Application) : AndroidViewModel(app) {
    val vault = Vault(app)
    val store = Store(PrefsKeyValue(app))
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState(keys = vault.list(), demo = prefs.getBoolean("demo", false)))
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Set by the activity: shows the platform credential prompt and reports whether it passed. */
    var authenticate: (title: String, onResult: (Boolean) -> Unit) -> Unit = { _, done -> done(true) }

    private fun refreshKeys() = _state.update { it.copy(keys = vault.list()) }

    fun go(screen: Screen) = _state.update { it.copy(screen = screen, message = null) }

    fun say(message: String?) = _state.update { it.copy(message = message) }

    fun setDemo(on: Boolean) {
        prefs.edit().putBoolean("demo", on).apply()
        _state.update { it.copy(demo = on) }
    }

    private fun <T> deps(profile: cc.aaro.qrst.core.Profile<T>, check: CodeCheck = CodeCheck.TYPE) =
        TransferDeps(profile, store, SystemEnv, BASE_URL, check)

    private fun attach(transfer: Transfer<*>, demo: Boolean, entry: VaultEntry?) {
        _state.value.active?.transfer?.dispose()
        val active = Active(transfer, demo, entry)
        transfer.onChange = { onTransferChange(active) }
        _state.update { it.copy(active = active, view = transfer.view(), screen = Screen.Transfer, message = null) }
    }

    private fun onTransferChange(active: Active) {
        val view = active.transfer.view()
        _state.update { if (it.active === active) it.copy(view = view, rev = it.rev + 1) else it }
    }

    // ---- starting transfers --------------------------------------------------------------

    /** Show a code that, scanned by the device holding a key, sends that key here (Flow A). */
    fun receiveByShowing(demoCheck: CodeCheck? = null) {
        if (demoCheck != null) attach(Transfer.show(deps(DemoTextProfile, demoCheck), Role.RECEIVER), true, null)
        else attach(Transfer.show(deps(NsecProfile), Role.RECEIVER), false, null)
    }

    /** Show a code that the receiving device scans (Flow B). The key stays locked until release. */
    fun sendByShowing(entry: VaultEntry) {
        if (warnFirst()) return
        attach(Transfer.show(deps(NsecProfile), Role.SENDER, null), false, entry)
    }

    fun sendDemoByShowing(text: String, check: CodeCheck) {
        attach(Transfer.show(deps(DemoTextProfile, check), Role.SENDER, text), true, null)
    }

    /** A pairing link arrived: from our camera, a pasted link, or the system camera through App Links. */
    fun openLink(text: String, viaCamera: Boolean) {
        val params = try {
            PairingUri.parse(text)
        } catch (e: UriException) {
            say(linkProblem(e.code)); return
        }
        val demo = params.profile == DemoTextProfile.id
        // §11.2c: a code handed over by qrst: came through whatever app claims that scheme.
        val handoff = PairingUri.isHandoff(text)
        val demoCheck = if (handoff) CodeCheck.COMPARE else CodeCheck.NONE
        if (params.profile != NsecProfile.id && !demo) {
            say("That code is for “${params.profile}”, which this app doesn't carry. It moves Nostr keys only."); return
        }
        if (demo && !_state.value.demo) {
            say("That code is for the reference demo's test text. Turn on test mode in Settings to answer it."); return
        }
        when (Transfer.refusal(store, params.profile, params)) {
            Refusal.BLOCKED_PEER -> { say("A pairing code already failed against that device in the last hour, so this app won't send to it. Show a fresh code on the other device and scan that."); return }
            else -> {}
        }
        if (scannerRole(params.mode) == Role.RECEIVER) {
            if (demo) attach(Transfer.join(deps(DemoTextProfile, demoCheck), params, viaCamera && !handoff), true, null)
            else attach(Transfer.join(deps(NsecProfile), params, viaCamera), false, null)
            return
        }
        // We are the Sender.
        if (demo) {
            go(Screen.PickKey(params, viaCamera && !handoff, demoCheck)); return
        }
        val keys = vault.list()
        when {
            keys.isEmpty() -> say("The other device is asking for a key, and there's no key on this phone to send.")
            warnFirst() -> {}
            keys.size == 1 -> sendTo(params, viaCamera, keys[0])
            else -> go(Screen.PickKey(params, viaCamera))
        }
    }

    fun sendTo(params: PairingParams, viaCamera: Boolean, entry: VaultEntry) {
        attach(Transfer.join(deps(NsecProfile), params, viaCamera), false, entry)
    }

    fun sendDemoTo(params: PairingParams, viaCamera: Boolean, text: String, check: CodeCheck = CodeCheck.NONE) {
        attach(Transfer.join(deps(DemoTextProfile, check), params, viaCamera, text), true, null)
    }

    private fun warnFirst(): Boolean {
        if (!store.shouldWarn()) return false
        _state.update { it.copy(interferenceWarning = true) }
        return true
    }

    fun acknowledgeWarning() {
        store.acknowledgeWarning()
        _state.update { it.copy(interferenceWarning = false) }
    }

    private fun linkProblem(code: String) = when (code) {
        "not-a-pairing-link" -> "That isn't a QRST pairing code."
        "unknown-version" -> "That code is from a newer version of QRST than this app understands."
        "bad-key" -> "That code's key is damaged. If it was typed or pasted, copy it again."
        "no-relays", "bad-relay" -> "That code names no relay this app can use."
        else -> "That code is incomplete ($code)."
    }

    // ---- inside a transfer ---------------------------------------------------------------

    /** Sender: release to the device whose digits were typed (`type`), or the compared one, or the lone one (`none`). */
    fun release(digits: String?) {
        val active = _state.value.active ?: return
        @Suppress("UNCHECKED_CAST")
        val t = active.transfer as Transfer<Any>
        val check = _state.value.view?.session?.check
        fun go(payload: Any?) = when (check) {
            CodeCheck.TYPE -> t.enterCode(digits!!, payload)
            CodeCheck.COMPARE -> t.confirmMatch(payload)
            else -> t.release(payload)
        }
        val entry = active.entry
        if (entry == null || active.unlocked) { go(null); return }
        val unlock = {
            try {
                val secret = vault.reveal(entry)
                active.unlocked = true
                go(secret)
            } catch (e: Exception) {
                say("Couldn't unlock the key: ${e.message}")
            }
        }
        if (!vault.needsAuth) { unlock(); return }
        authenticate("Confirm: this sends your key to that device") { ok -> if (ok) unlock() }
    }

    fun rejectMatch() = _state.value.active?.transfer?.rejectMatch()
    fun advance() = _state.value.active?.transfer?.advance()
    fun decline() = _state.value.active?.transfer?.decline()

    /** Receiver: keep what arrived. A key is unlocked for storing first, so nothing is acknowledged that can't be kept. */
    fun accept(label: String) {
        val active = _state.value.active ?: return
        if (active.demo) { active.transfer.accept(); return }
        val keep = {
            active.transfer.accept()
            Thread {
                // The value appears once the session has committed it.
                val deadline = System.currentTimeMillis() + 5000
                var value: Any? = null
                while (value == null && System.currentTimeMillis() < deadline) {
                    if (active.transfer.view().session.phase == Phase.ENDED) value = active.transfer.takeValue()
                    if (value == null) Thread.sleep(30)
                }
                val secret = value as? ByteArray
                if (secret != null) {
                    try {
                        active.stored = vault.add(secret, label, viaTransfer = true) ?: vault.list().firstOrNull { it.pub == cc.aaro.qrst.core.publicKeyHex(secret) }
                    } catch (e: Exception) {
                        active.storeError = e.message ?: "unknown error"
                    } finally {
                        wipe(secret)
                    }
                }
                refreshKeys()
                onTransferChange(active)
            }.start()
        }
        if (!vault.needsAuth) { keep(); return }
        authenticate("Confirm to keep this key on this phone") { ok -> if (ok) keep() }
    }

    fun closeTransfer() {
        _state.value.active?.transfer?.dispose()
        _state.update { it.copy(active = null, view = null, screen = Screen.Home) }
        refreshKeys()
    }

    fun wake() = _state.value.active?.transfer?.wake()

    // ---- the vault -----------------------------------------------------------------------

    /** Import by pasting an nsec. The pasted text is cleared from the clipboard by the caller. */
    fun importKey(text: String, label: String, onDone: (String?) -> Unit) {
        val secret = try { cc.aaro.qrst.core.Nip19.parseSecret(text) } catch (e: Exception) { onDone(e.message); return }
        val store = {
            try {
                val added = vault.add(secret, label, viaTransfer = false)
                refreshKeys()
                onDone(if (added == null) "That key is already on this phone." else null)
            } catch (e: Exception) {
                onDone("Couldn't store it: ${e.message}")
            } finally {
                wipe(secret)
            }
        }
        if (!vault.needsAuth) { store(); return }
        authenticate("Confirm to keep this key on this phone") { ok -> if (ok) store() else { wipe(secret); onDone("Not stored.") } }
    }

    fun rename(entry: VaultEntry, label: String) { vault.rename(entry, label); refreshKeys() }
    fun delete(entry: VaultEntry) { vault.delete(entry); refreshKeys() }

    override fun onCleared() {
        _state.value.active?.transfer?.dispose()
    }
}
