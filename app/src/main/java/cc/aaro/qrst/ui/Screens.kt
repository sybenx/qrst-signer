package cc.aaro.qrst.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.aaro.qrst.Active
import cc.aaro.qrst.AppModel
import cc.aaro.qrst.Screen
import cc.aaro.qrst.core.CodeCheck
import cc.aaro.qrst.core.MAX_ATTEMPTS
import cc.aaro.qrst.core.Mode
import cc.aaro.qrst.core.Outcome
import cc.aaro.qrst.core.Phase
import cc.aaro.qrst.core.Role
import cc.aaro.qrst.net.TransferView
import cc.aaro.qrst.vault.VaultEntry

@Composable
fun App(model: AppModel) {
    val state by model.state.collectAsState()
    QrstTheme {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (val s = state.screen) {
                Screen.Home -> Home(model, state.keys, state.demo)
                Screen.Scan -> ScanScreen(model)
                Screen.Import -> ImportScreen(model)
                Screen.Settings -> SettingsScreen(model, state.demo)
                Screen.Log -> LogScreen(model)
                is Screen.PickKey -> PickKeyScreen(model, s, state.keys, state.demo)
                Screen.Transfer -> {
                    val active = state.active
                    val view = state.view
                    if (active != null && view != null) TransferScreen(model, active, view) else model.go(Screen.Home)
                }
            }
            state.message?.let { msg ->
                AlertDialog(
                    onDismissRequest = { model.say(null) },
                    confirmButton = { TextButton({ model.say(null) }) { Text("OK") } },
                    text = { Text(msg) },
                )
            }
            if (state.interferenceWarning) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Several transfers failed") },
                    text = { Text("Three or more transfers ended on a wrong pairing code in the last hour. That can be mistyping, but it can also mean someone is trying to get in the middle. Make sure the other device is yours and in front of you.") },
                    confirmButton = { TextButton({ model.acknowledgeWarning() }) { Text("I understand") } },
                )
            }
        }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } else Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        content()
    }
}

private fun shortNpub(npub: String) = npub.take(12) + "…" + npub.takeLast(6)

// ---- home -------------------------------------------------------------------------------

@Composable
private fun Home(model: AppModel, keys: List<VaultEntry>, demo: Boolean) {
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("QRST Signer", null) {
            IconButton({ model.go(Screen.Settings) }) { Icon(Icons.Filled.Settings, "Settings") }
        }
        Page {
            Text(
                "Moves a Nostr key between your devices by QR code. The key travels sealed over public relays, and only after you check five digits across the two screens.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button({ model.go(Screen.Scan) }, Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(10.dp)); Text("Scan a QRST code")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ model.receiveByShowing() }, Modifier.weight(1f)) { Text("Receive a key") }
                OutlinedButton({ model.go(Screen.PickKey(null, true)) }, Modifier.weight(1f), enabled = keys.isNotEmpty() || demo) { Text("Send a key") }
            }
            HorizontalDivider()
            Text("Keys on this phone", style = MaterialTheme.typography.titleMedium)
            if (keys.isEmpty()) {
                Text("None yet. Receive one from another device, or paste an nsec.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (k in keys) KeyRow(k) { editing = k }
            TextButton({ model.go(Screen.Import) }) { Text("Add a key by pasting an nsec") }
            if (demo) {
                HorizontalDivider()
                Text("Test mode: the reference demo's text profile", style = MaterialTheme.typography.titleSmall, color = Receipt)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ model.receiveByShowing(CodeCheck.COMPARE) }, Modifier.weight(1f)) { Text("Receive text") }
                }
            }
        }
    }
    editing?.let { k -> KeyDialog(model, k) { editing = null } }
}

@Composable
private fun KeyRow(k: VaultEntry, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip12().background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(k.label, fontWeight = FontWeight.SemiBold)
        Text(shortNpub(k.npub), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (k.viaTransfer) Text("Arrived by transfer", fontSize = 12.sp, color = Receipt)
    }
}

private fun Modifier.clip12() = clip(RoundedCornerShape(12.dp))

@Composable
private fun KeyDialog(model: AppModel, k: VaultEntry, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(k.label) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(k.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(k.npub, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                TextButton({ copy(context, "npub", k.npub) }) { Text("Copy npub") }
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true)
                if (confirmDelete) {
                    Text(
                        "This deletes the copy on this phone only. Nostr keys can't be revoked: any other device or app holding this key keeps it.",
                        color = Danger,
                    )
                }
            }
        },
        confirmButton = { TextButton({ model.rename(k, label.trim().ifEmpty { k.label }); onDismiss() }) { Text("Save") } },
        dismissButton = {
            TextButton({ if (confirmDelete) { model.delete(k); onDismiss() } else confirmDelete = true }) {
                Text(if (confirmDelete) "Delete this phone's copy" else "Delete…", color = Danger)
            }
        },
    )
}

private fun copy(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}

// ---- scan / import / pick ---------------------------------------------------------------

@Composable
private fun ScanScreen(model: AppModel) {
    BackHandler { model.go(Screen.Home) }
    var pasting by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        TopBar("Scan a QRST code", { model.go(Screen.Home) })
        Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp).clip(RoundedCornerShape(16.dp))) {
            QrScanner { model.openLink(it, viaCamera = true) }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (!pasting) {
                TextButton({ pasting = true }) { Text("Paste a pairing link instead") }
            } else {
                OutlinedTextField(link, { link = it }, label = { Text("Pairing link") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button({ model.openLink(link, viaCamera = false) }, enabled = link.isNotBlank()) { Text("Use this link") }
            }
        }
    }
}

@Composable
private fun ImportScreen(model: AppModel) {
    BackHandler { model.go(Screen.Home) }
    var nsec by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopBar("Add a key", { model.go(Screen.Home) })
        Page {
            Text("Paste an nsec (or 64 hex characters). It is encrypted with a key held in this phone's secure hardware, and never leaves except by a QRST transfer you approve.")
            OutlinedTextField(
                nsec, { nsec = it.trim() }, label = { Text("nsec1…") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(label, { label = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = Danger) }
            Button({
                model.importKey(nsec, label.trim()) { problem ->
                    if (problem == null) {
                        // The nsec was pasted: don't leave it in the clipboard.
                        try { context.getSystemService(ClipboardManager::class.java).clearPrimaryClip() } catch (_: Exception) {}
                        nsec = ""
                        model.go(Screen.Home)
                    } else error = problem
                }
            }, enabled = nsec.isNotEmpty()) { Text("Keep this key") }
            Text("The clipboard is cleared once the key is stored.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PickKeyScreen(model: AppModel, s: Screen.PickKey, keys: List<VaultEntry>, demo: Boolean) {
    BackHandler { model.go(Screen.Home) }
    var text by remember { mutableStateOf("") }
    val demoLink = s.params?.profile == cc.aaro.qrst.core.DemoTextProfile.id
    Column(Modifier.fillMaxSize()) {
        TopBar(if (s.params == null) "Send a key" else "Which key?", { model.go(Screen.Home) })
        Page {
            if (!demoLink) {
                Text(
                    if (s.params == null) "Pick the key to send. This phone shows a QR code; scan it with the device that should receive the key."
                    else "The other device is asking for a key. Pick which one it gets.",
                )
                for (k in keys) KeyRow(k) { if (s.params == null) model.sendByShowing(k) else model.sendTo(s.params, s.viaCamera, k) }
            }
            if (demo && (s.params == null || demoLink)) {
                HorizontalDivider()
                Text("Test text (reference demo profile)", color = Receipt)
                OutlinedTextField(text, { text = it }, label = { Text("Made-up text, not a real secret") }, modifier = Modifier.fillMaxWidth())
                Button({
                    if (s.params == null) model.sendDemoByShowing(text, CodeCheck.COMPARE) else model.sendDemoTo(s.params, s.viaCamera, text)
                }, enabled = text.isNotEmpty()) { Text("Send this text") }
            }
        }
    }
}

// ---- settings and log -------------------------------------------------------------------

@Composable
private fun SettingsScreen(model: AppModel, demo: Boolean) {
    BackHandler { model.go(Screen.Home) }
    var relays by remember { mutableStateOf(model.store.configured().joinToString("\n")) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", { model.store.setConfigured(relays.lines().filter { it.isNotBlank() }); model.go(Screen.Home) })
        Page {
            Text("Relays", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tried first when this phone shows a code, one wss:// address per line. Every relay is tested before it's named in a code, and relays that worked before are remembered. Leave empty to start from a built-in list.",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(relays, { relays = it }, modifier = Modifier.fillMaxWidth().height(140.dp), textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Test mode")
                    Text("Also carry the reference demo's text profile, to try this app against sybenx.github.io/qr-secret-transfer with made-up text.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(demo, { model.setDemo(it) })
            }
            HorizontalDivider()
            TextButton({ model.store.setConfigured(relays.lines().filter { it.isNotBlank() }); model.go(Screen.Log) }) { Text("Transfer log") }
            Text(
                "What this protects against: a stranger who photographs a code gets a pairing code that won't match, and nothing moves. What it can't protect against: sending your key to a device that isn't yours. Only you saying no at the send prompt stops that.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogScreen(model: AppModel) {
    BackHandler { model.go(Screen.Settings) }
    val records = remember { model.store.records() }
    Column(Modifier.fillMaxSize()) {
        TopBar("Transfer log", { model.go(Screen.Settings) })
        Page {
            if (records.isEmpty()) Text("No transfers yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (r in records) {
                val ts = (r["ts"] as? Long ?: 0L) * 1000
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(12.dp)) {
                    Text("${r["role"]} · ${r["outcome"]} · ${r["profile"]}", fontWeight = FontWeight.SemiBold)
                    Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(ts)), fontSize = 13.sp)
                    Text("Digits ${r["sas"]} · check ${r["check"]}${if (r["multi"] == true) " · another device also answered" else ""}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Peer ${(r["peer"] as? String)?.take(16)}…", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- the transfer -----------------------------------------------------------------------

@Composable
private fun TransferScreen(model: AppModel, active: Active, view: TransferView<*>) {
    val s = view.session
    val ended = s.phase == Phase.ENDED
    BackHandler { model.closeTransfer() }
    val title = when {
        view.role == Role.SENDER -> if (active.demo) "Send text" else "Send a key"
        else -> if (active.demo) "Receive text" else "Receive a key"
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(title, { model.closeTransfer() })
        Page {
            when {
                ended -> EndedPanel(model, active, view)
                view.stage == TransferView.Stage.RELAYS -> FindingRelays(view)
                s.phase == Phase.WAITING && view.showing -> ShowCode(model, active, view)
                s.phase == Phase.WAITING -> Waiting("Reaching the other device…", view)
                s.phase == Phase.RELEASE -> ReleasePrompt(model, active, view)
                s.phase == Phase.CODE -> ShowDigits(model, view)
                s.phase == Phase.ACCEPT -> AcceptPrompt(model, active, view)
                s.phase == Phase.SENT -> Waiting("Sent. Waiting for the other device to confirm it kept the key.", view)
            }
            if (s.multipleResponders && !ended) {
                Text(
                    "Another device also answered this code. If that wasn't you, someone nearby may have scanned it. Nothing was shared with them.",
                    color = Danger, fontSize = 14.sp,
                )
            }
            RelayFooter(view)
        }
    }
}

@Composable
private fun FindingRelays(view: TransferView<*>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text("Testing relays before showing a code…")
    }
}

@Composable
private fun Waiting(text: String, view: TransferView<*>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(text)
    }
    if (!view.connected) Text("Not connected to a relay yet. Still trying.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ShowCode(model: AppModel, active: Active, view: TransferView<*>) {
    val uri = view.uri ?: return
    val context = LocalContext.current
    // §11.2b: a mode=offer code, which makes whoever scans it the Sender, carries more weight.
    val offer = view.mode == Mode.OFFER
    val what = if (active.demo) "text" else "Nostr key"
    Text(
        if (offer) "Scanning this sends a $what from your other device to this phone."
        else "This phone is sending a $what. Scan this with the device that should receive it.",
        style = if (offer) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        fontWeight = if (offer) FontWeight.SemiBold else FontWeight.Normal,
    )
    if (!offer && active.entry != null) Text("Key: ${active.entry.label}", color = Release)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .border(if (offer) 6.dp else 1.dp, if (offer) Release else MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(if (offer) 14.dp else 8.dp),
    ) {
        QrImage(uri, Modifier.fillMaxSize())
    }
    Text("Only show this to your own devices. Whoever scans it can answer it.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton({ copy(context, "QRST pairing link", uri) }) { Text("Copy the link instead") }
}

@Composable
private fun ShowDigits(model: AppModel, view: TransferView<*>) {
    val code = view.session.code
    if (code == null) {
        Text("Nothing to compare at this level. Confirm on the other device.")
        return
    }
    Text(
        if (view.session.check == CodeCheck.TYPE) "Type these digits on the device that's sending" else "Check the other device shows these digits",
        style = MaterialTheme.typography.titleMedium,
    )
    Text(code.chunked(1).joinToString(" "), fontSize = 54.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Text("These digits belong to this transfer only. They are not a PIN.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (view.showing) {
        TextButton({ model.advance() }, enabled = true) {
            Text("The other device rejected these digits; show the next")
        }
    }
}

@Composable
private fun ReleasePrompt(model: AppModel, active: Active, view: TransferView<*>) {
    val s = view.session
    var digits by remember(rememberKey(view)) { mutableStateOf("") }
    var attested by remember { mutableStateOf(false) }
    var allowSend by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val origin = view.peerOrigin
    // §9.1: maximum friction when the peer claims a web origin, or the pairing didn't come from this app's own camera.
    val maxFriction = origin != null || !view.viaCamera
    val receiveOnly = active.entry?.viaTransfer == true
    val what = if (active.demo) "text" else "key"

    Text(
        "This is not a login. You are about to give another device your $what.",
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Release,
    )
    Text(
        if (active.demo) "The text itself leaves this phone. Once it is sent it can't be taken back."
        else "Your Nostr key itself leaves this phone${active.entry?.let { " (${it.label})" } ?: ""}. The other device keeps a working copy of this identity for good. Nostr keys can't be rotated or revoked, so nothing you do later takes it back.",
    )
    Text(
        "The other device: " + (origin?.let { "a web page claiming to be $it (not verified)" } ?: "an app, by its own account (not verified)"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!view.viaCamera) {
        Text(
            "This request didn't come from this app's camera. It was opened as a link, from the camera app or another app, so nothing proves the code was on a screen in front of you.",
            color = Danger,
        )
    }
    if (receiveOnly) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This key arrived on this phone by transfer, so the phone is receive-only for it. Allow sending it this once?", Modifier.weight(1f), fontSize = 14.sp)
            Switch(allowSend, { allowSend = it })
        }
    }
    if (s.ready > 1 || s.multipleResponders) {
        Text("More than one device answered this code. Only release to the one in front of you.", color = Danger)
    }

    when (s.check) {
        CodeCheck.TYPE -> {
            Text("Type the digits shown on your other device", style = MaterialTheme.typography.titleMedium)
            DigitField(digits, { digits = it }, focus)
            if (s.attemptsLeft < MAX_ATTEMPTS) Text("Those digits didn't match. ${s.attemptsLeft} tries left.", color = Danger)
        }
        CodeCheck.COMPARE -> {
            Text("Does the other device show these digits?", style = MaterialTheme.typography.titleMedium)
            Text((s.compare ?: "").chunked(1).joinToString(" "), fontSize = 44.sp, fontFamily = FontFamily.Monospace)
            if (s.attemptsLeft < MAX_ATTEMPTS) Text("${s.attemptsLeft} tries left.", color = Danger)
        }
        CodeCheck.NONE -> Text("No digits are checked at this level. The $what goes to whichever device answered the code.", color = Danger)
    }
    if (maxFriction) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(attested, { attested = it })
            Text("I scanned this code myself, from a screen in front of me", fontSize = 14.sp)
        }
    }

    val ready = (s.check != CodeCheck.TYPE || digits.length == 5) && (!maxFriction || attested) && (!receiveOnly || allowSend)
    // Declining is the prominent control; the release is not (§9.1).
    Button({ model.decline() }, Modifier.fillMaxWidth().height(52.dp)) { Text("Don't send") }
    if (s.check == CodeCheck.COMPARE) {
        OutlinedButton({ model.rejectMatch() }, Modifier.fillMaxWidth()) { Text("The digits are different") }
    }
    TextButton(
        { model.release(digits.takeIf { it.length == 5 }); digits = "" },
        Modifier.fillMaxWidth(), enabled = ready,
    ) {
        Text(
            when {
                s.check == CodeCheck.COMPARE -> "They match. Send my $what to that device"
                origin != null -> "Send my $what to $origin"
                else -> "Send my $what to that device"
            },
            color = if (ready) Release else MaterialTheme.colorScheme.outline,
        )
    }
}

private fun rememberKey(view: TransferView<*>) = view.session.attemptsLeft

@Composable
private fun AcceptPrompt(model: AppModel, active: Active, view: TransferView<*>) {
    var label by remember { mutableStateOf("") }
    Text(if (active.demo) "Your other device sent a text" else "Your other device sent a Nostr key", style = MaterialTheme.typography.titleLarge)
    view.session.code?.let { Text("Digits on screen: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (active.demo) {
        Text(view.session.rendering ?: "")
    } else {
        Text("Log in as this identity?")
        Text(view.session.rendering ?: "", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        OutlinedTextField(label, { label = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    Button({ model.accept(label.trim()) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Keep it") }
    TextButton({ model.decline() }, Modifier.fillMaxWidth()) { Text("Discard it") }
}

@Composable
private fun EndedPanel(model: AppModel, active: Active, view: TransferView<*>) {
    val o = view.session.outcome
    val what = if (active.demo) "text" else "key"
    val (headline, detail) = when (o) {
        Outcome.DELIVERED -> "Sent" to "The other device confirmed it kept the $what."
        Outcome.SENT_UNCONFIRMED -> "Sent, unconfirmed" to "The $what left this phone, but the other device didn't confirm it kept it."
        Outcome.RECEIVED -> when {
            active.demo -> "Received" to "“${view.value ?: ""}”"
            active.storeError != null -> "Received, but not stored" to "Couldn't store it: ${active.storeError}"
            active.stored != null -> "Key added" to "${active.stored!!.label} is now on this phone."
            else -> "Received" to "Storing…"
        }
        Outcome.DECLINED -> "Stopped" to "Nothing was sent or kept."
        Outcome.CANCELLED -> "Cancelled" to "Nothing moved."
        Outcome.PEER_ABORTED -> "The other device stopped" to "Nothing moved."
        Outcome.ATTEMPTS_EXHAUSTED -> "Too many wrong digits" to "Nothing was sent. Start again from the other device's code. Repeated failures can mean someone is interfering, not just mistyping."
        Outcome.EXPIRED -> "Timed out" to "Ten minutes passed. Nothing moved."
        Outcome.BAD_PAYLOAD -> "That wasn't a key" to "What arrived wasn't a valid Nostr key, so it was thrown away."
        Outcome.BLOCKED_PEER -> "Refused" to "A pairing code already failed against that device in the last hour."
        Outcome.SECOND_RESPONDER -> "Stopped: two devices answered" to "With no digits to tell them apart, nothing moved."
        null -> "Ended" to ""
    }
    Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(detail)
    Button({ model.closeTransfer() }, Modifier.fillMaxWidth()) { Text("Done") }
}

@Composable
private fun RelayFooter(view: TransferView<*>) {
    var open by remember { mutableStateOf(false) }
    HorizontalDivider()
    TextButton({ open = !open }) {
        Text(
            "${view.relays.count { it.inUse && it.state == "open" }} relay${if (view.relays.count { it.inUse && it.state == "open" } == 1) "" else "s"} connected · details",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        for (r in view.relays) {
            Text("${r.url}  ${r.state}${r.why?.let { " ($it)" } ?: ""}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (line in view.log.takeLast(30)) Text(line, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
    }
}
