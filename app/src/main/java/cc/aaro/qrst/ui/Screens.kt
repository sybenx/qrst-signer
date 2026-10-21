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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import cc.aaro.qrst.core.Role as QrstRole
import cc.aaro.qrst.net.TransferView
import cc.aaro.qrst.vault.VaultEntry

@Composable
fun App(model: AppModel) {
    val state by model.state.collectAsState()
    QrstTheme {
        Surface(Modifier.fillMaxSize(), color = Vault.Ground, contentColor = Vault.Text) {
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
                        containerColor = Vault.Surface,
                        confirmButton = { TextButton({ model.say(null) }) { Text("OK", color = Vault.Text) } },
                        text = { Text(msg, color = Vault.Text) },
                    )
                }
                if (state.interferenceWarning) {
                    AlertDialog(
                        onDismissRequest = {},
                        containerColor = Vault.Surface,
                        title = { Text("Several transfers failed", color = Vault.Text) },
                        text = { Text("Three or more transfers ended on wrong digits in the last hour. That can be mistyping, but it can also mean someone is trying to get in the middle. Make sure the other device is yours and in front of you.", color = Vault.Text) },
                        confirmButton = { TextButton({ model.acknowledgeWarning() }) { Text("I understand", color = Vault.Text) } },
                    )
                }
            }
        }
    }
}

private fun copy(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Vault.Muted, unfocusedBorderColor = Vault.Line, cursorColor = Vault.Text,
    focusedLabelColor = Vault.Muted, unfocusedLabelColor = Vault.Muted, focusedTextColor = Vault.Text, unfocusedTextColor = Vault.Text,
)

// ---- home -------------------------------------------------------------------------------

@Composable
private fun Home(model: AppModel, keys: List<VaultEntry>, demo: Boolean) {
    var editing by remember { mutableStateOf<VaultEntry?>(null) }
    Frame(
        onBack = null,
        actions = {
            // C's round Scan button: the one thing most visits come here to do.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(150.dp).clip(CircleShape).background(Vault.Surface).border(2.dp, Vault.Line, CircleShape)
                        .clickable(role = Role.Button) { model.go(Screen.Scan) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.QrCodeScanner, null, tint = Vault.Text, modifier = Modifier.size(44.dp))
                        Text("Scan a code", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Vault.Text)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EdgeButton("Receive a key", { model.receiveByShowing() }, color = Vault.Teal, edge = Vault.TealEdge, modifier = Modifier.weight(1f))
                EdgeButton("Send a key", { model.go(Screen.PickKey(null, true)) }, color = Vault.Amber, edge = Vault.AmberEdge, modifier = Modifier.weight(1f), enabled = keys.isNotEmpty() || demo)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Eyebrow("QRST Signer") }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Vault.Line, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { model.go(Screen.Settings) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Settings, "Settings", tint = Vault.Muted) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Your keys", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = Vault.Text)
            Note("Held in this phone's secure hardware. They leave only by a transfer you approve.")
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (k in keys) KeyCard(k) { editing = k }
            DashedRow("+ Paste an nsec") { model.go(Screen.Import) }
        }
        if (demo) {
            Panel(edge = Vault.TealEdge) {
                Eyebrow("Test mode", Vault.Teal)
                Note("The reference demo's text profile, for trying this app against sybenx.github.io/qr-secret-transfer.")
                EdgeButton("Receive test text", { model.receiveByShowing(CodeCheck.COMPARE) }, color = Vault.Teal, edge = Vault.TealEdge)
            }
        }
    }
    editing?.let { k -> KeyDialog(model, k) { editing = null } }
}

@Composable
private fun KeyCard(k: VaultEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Vault.Surface).border(1.dp, Vault.Edge, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        KeyPattern(k.pub)
        Column(Modifier.weight(1f)) {
            Text(displayName(k.label), fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Vault.Text, maxLines = 1)
            Text(shortNpub(k.npub), fontFamily = PlexMono, fontSize = 13.sp, color = Vault.Muted)
        }
        if (k.viaTransfer) Pill("received", Vault.Teal, Vault.TealEdge)
    }
}

@Composable
private fun DashedRow(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, Vault.Line, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = PlexSans, fontSize = 15.sp, color = Vault.Muted) }
}

@Composable
private fun KeyDialog(model: AppModel, k: VaultEntry, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(displayName(k.label).takeIf { it != "Unnamed key" } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Vault.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KeyPattern(k.pub, 36.dp)
                Text(displayName(k.label), color = Vault.Text)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(k.npub, fontFamily = PlexMono, fontSize = 12.sp, color = Vault.Muted)
                QuietLink("Copy npub", { copy(context, "npub", k.npub) }, Vault.Text)
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true, colors = fieldColors())
                if (confirmDelete) {
                    Text("This deletes the copy on this phone only. Nostr keys can't be revoked: any other device or app holding this key keeps it.", color = Vault.Danger, fontSize = 14.sp)
                }
            }
        },
        confirmButton = { TextButton({ model.rename(k, label.trim().ifEmpty { k.label }); onDismiss() }) { Text("Save", color = Vault.Text) } },
        dismissButton = {
            TextButton({ if (confirmDelete) { model.delete(k); onDismiss() } else confirmDelete = true }) {
                Text(if (confirmDelete) "Delete this phone's copy" else "Delete…", color = Vault.Danger)
            }
        },
    )
}

// ---- scan / import / pick ---------------------------------------------------------------

@Composable
private fun ScanScreen(model: AppModel) {
    BackHandler { model.go(Screen.Home) }
    var pasting by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Frame(onBack = { model.go(Screen.Home) }) {
            Eyebrow("Scan")
            Headline("Point at a QRST code")
            Box(Modifier.fillMaxWidth().height(LocalRoom.current * 0.55f).clip(RoundedCornerShape(20.dp)).border(1.dp, Vault.Line, RoundedCornerShape(20.dp))) {
                QrScanner { model.openLink(it, viaCamera = true) }
            }
            Note("The code says which way the key goes. Nothing moves until digits are checked on both screens.")
            if (!pasting) {
                QuietLink("Paste a pairing link instead", { pasting = true })
            } else {
                OutlinedTextField(link, { link = it }, label = { Text("Pairing link") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors())
                MainButton("Use this link", { model.openLink(link, viaCamera = false) }, enabled = link.isNotBlank())
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
    Frame(
        onBack = { model.go(Screen.Home) },
        actions = {
            MainButton("Keep this key", {
                model.importKey(nsec, label.trim()) { problem ->
                    if (problem == null) {
                        // The nsec was pasted: don't leave it in the clipboard.
                        try { context.getSystemService(ClipboardManager::class.java).clearPrimaryClip() } catch (_: Exception) {}
                        nsec = ""
                        model.go(Screen.Home)
                    } else error = problem
                }
            }, enabled = nsec.isNotEmpty())
        },
    ) {
        Eyebrow("Add a key")
        Headline("Paste an nsec")
        Lede("Or 64 hex characters. It's encrypted with a key in this phone's secure hardware, and leaves only by a transfer you approve.", Vault.Muted)
        OutlinedTextField(
            nsec, { nsec = it.trim() }, label = { Text("nsec1…") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
        )
        OutlinedTextField(label, { label = it }, label = { Text("Name it (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        error?.let { Note(it, Vault.Danger) }
        Note("The clipboard is cleared once the key is stored.")
    }
}

@Composable
private fun PickKeyScreen(model: AppModel, s: Screen.PickKey, keys: List<VaultEntry>, demo: Boolean) {
    BackHandler { model.go(Screen.Home) }
    var text by remember { mutableStateOf("") }
    val demoLink = s.params?.profile == cc.aaro.qrst.core.DemoTextProfile.id
    Frame(onBack = { model.go(Screen.Home) }) {
        Eyebrow("Send · Nostr key", Vault.Amber)
        if (!demoLink) {
            Headline(if (s.params == null) "Which key?" else "The other device wants a key")
            Note(
                if (s.params == null) "This phone then shows a code. Scan it with the device that should get the key."
                else "Pick which one it gets. You'll check digits before anything leaves.",
            )
            for (k in keys) KeyCard(k) { if (s.params == null) model.sendByShowing(k) else model.sendTo(s.params, s.viaCamera, k) }
        }
        if (demo && (s.params == null || demoLink)) {
            Panel(edge = Vault.TealEdge) {
                Eyebrow("Test text", Vault.Teal)
                OutlinedTextField(text, { text = it }, label = { Text("Made-up text, not a real secret") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                EdgeButton("Send this text", {
                    if (s.params == null) model.sendDemoByShowing(text, CodeCheck.COMPARE) else model.sendDemoTo(s.params, s.viaCamera, text, s.demoCheck)
                }, enabled = text.isNotEmpty())
            }
        }
    }
}

// ---- settings and log -------------------------------------------------------------------

@Composable
private fun SettingsScreen(model: AppModel, demo: Boolean) {
    var relays by remember { mutableStateOf(model.store.configured().joinToString("\n")) }
    val leave = { model.store.setConfigured(relays.lines().filter { it.isNotBlank() }); model.go(Screen.Home) }
    BackHandler { leave() }
    Frame(onBack = leave) {
        Eyebrow("Settings")
        Headline("Relays")
        Note("Tried first when this phone shows a code, one wss:// address per line. Every relay is tested before it's named in a code, and relays that worked before are remembered.")
        OutlinedTextField(
            relays, { relays = it }, modifier = Modifier.fillMaxWidth().height(130.dp), colors = fieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = PlexMono, fontSize = 14.sp, color = Vault.Text),
        )
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Test mode", fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Vault.Text)
                    Note("Also carry the reference demo's text profile, with made-up text.")
                }
                Switch(demo, { model.setDemo(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Vault.Teal, checkedThumbColor = Vault.Ground))
            }
        }
        DashedRow("Transfer log") { model.store.setConfigured(relays.lines().filter { it.isNotBlank() }); model.go(Screen.Log) }
        Panel {
            Eyebrow("What this protects")
            Note("A stranger who photographs a code gets digits that won't match, and nothing moves. What it can't protect against: sending your key to a device that isn't yours. Only you saying no at the send prompt stops that.")
        }
    }
}

@Composable
private fun LogScreen(model: AppModel) {
    BackHandler { model.go(Screen.Settings) }
    val records = remember { model.store.records() }
    Frame(onBack = { model.go(Screen.Settings) }) {
        Eyebrow("Settings")
        Headline("Transfer log")
        if (records.isEmpty()) Note("No transfers yet.")
        for (r in records) {
            val ts = (r["ts"] as? Long ?: 0L) * 1000
            val sent = r["role"] == "sender"
            Panel(edge = if (sent) Vault.AmberEdge else Vault.TealEdge) {
                Text("${if (sent) "Sent" else "Received"} · ${r["outcome"]}", fontFamily = PlexSans, fontWeight = FontWeight.Medium, color = if (sent) Vault.Amber else Vault.Teal)
                Note(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(ts)), Vault.Text)
                Note("Digits ${r["sas"]} · check ${r["check"]} · ${r["profile"]}${if (r["multi"] == true) " · another device also answered" else ""}")
                Text("Peer ${(r["peer"] as? String)?.take(16)}…", fontFamily = PlexMono, fontSize = 12.sp, color = Vault.Muted)
            }
        }
    }
}

// ---- the transfer -----------------------------------------------------------------------

@Composable
private fun TransferScreen(model: AppModel, active: Active, view: TransferView<*>) {
    val s = view.session
    BackHandler { model.closeTransfer() }
    when {
        s.phase == Phase.ENDED -> EndedScreen(model, active, view)
        view.stage == TransferView.Stage.RELAYS -> WaitScreen(model, active, view, "Testing relays before showing a code")
        s.phase == Phase.WAITING && view.showing -> ShowCodeScreen(model, active, view)
        s.phase == Phase.WAITING -> WaitScreen(model, active, view, "Reaching the other device")
        s.phase == Phase.RELEASE -> ReleaseScreen(model, active, view)
        s.phase == Phase.CODE -> DigitsScreen(model, active, view)
        s.phase == Phase.ACCEPT -> AcceptScreen(model, active, view)
        s.phase == Phase.SENT -> WaitScreen(model, active, view, "Sent. Waiting for the other device to confirm it kept the key.")
    }
}

private fun eyebrowFor(active: Active, view: TransferView<*>, step: String? = null): Pair<String, Color> {
    val what = if (active.demo) "text" else "Nostr key"
    return if (view.role == QrstRole.SENDER) "Send · $what" to Vault.Amber
    else (if (step != null) "Receive · $step" else "Receive · $what") to Vault.Teal
}

@Composable
private fun MultipleResponders(view: TransferView<*>) {
    if (view.session.multipleResponders && view.session.phase != Phase.ENDED) {
        Callout("Another device also answered this code. If that wasn't you, someone nearby may have scanned it. Nothing was shared with them.", Vault.Danger, Vault.Danger)
    }
}

@Composable
private fun RelayDetails(view: TransferView<*>) {
    var open by remember { mutableStateOf(false) }
    val connected = view.relays.count { it.inUse && it.state == "open" }
    QuietLink("$connected relay${if (connected == 1) "" else "s"} connected · ${if (open) "hide" else "details"}", { open = !open })
    if (open) {
        for (r in view.relays) Text("${r.url}  ${r.state}${r.why?.let { " ($it)" } ?: ""}", fontFamily = PlexMono, fontSize = 12.sp, color = Vault.Muted)
        for (line in view.log.takeLast(30)) Text(line, fontFamily = PlexMono, fontSize = 11.sp, color = Vault.Muted)
    }
}

@Composable
private fun WaitScreen(model: AppModel, active: Active, view: TransferView<*>, text: String) {
    val (eyebrow, color) = eyebrowFor(active, view)
    Frame(onBack = { model.closeTransfer() }) {
        Eyebrow(eyebrow, color)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = color)
            Lede(text)
        }
        if (!view.connected && view.stage != TransferView.Stage.RELAYS) Note("Not connected to a relay yet. Still trying.")
        MultipleResponders(view)
        RelayDetails(view)
    }
}

@Composable
private fun ShowCodeScreen(model: AppModel, active: Active, view: TransferView<*>) {
    val uri = view.uri ?: return
    val context = LocalContext.current
    val config = LocalConfiguration.current
    // §11.2b: a mode=offer code, which makes whoever scans it the Sender, carries more weight.
    val offer = view.mode == Mode.OFFER
    val what = if (active.demo) "text" else "Nostr key"
    val (eyebrow, color) = eyebrowFor(active, view, "step 1 of 3")
    KeepBright()
    Frame(onBack = { model.closeTransfer() }) {
        val wide = config.screenWidthDp > config.screenHeightDp
        val room = LocalRoom.current - 24.dp
        val side = if (wide) minOf(room, (config.screenWidthDp * 0.6f).dp) else minOf((config.screenWidthDp - 44).dp, room - 150.dp).coerceAtLeast(200.dp)
        val words: @Composable () -> Unit = {
            Eyebrow(eyebrow, color)
            Text(
                if (offer) "Scan this with the device that has the key. It sends the $what here."
                else "Scan this with the device that should get the $what.",
                fontFamily = PlexSans, fontWeight = if (offer) FontWeight.SemiBold else FontWeight.Normal, fontSize = 18.sp, lineHeight = 25.sp, color = Vault.Text,
            )
            if (!offer && active.entry != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KeyPattern(active.entry.pub, 28.dp)
                    Text(displayName(active.entry.label), fontFamily = PlexSans, fontWeight = FontWeight.Medium, color = Vault.Amber)
                }
            }
        }
        val code: @Composable () -> Unit = {
            Box(
                Modifier.size(side).clip(RoundedCornerShape(18.dp))
                    .border(if (offer) 6.dp else 1.dp, if (offer) Vault.Amber else Vault.Line, RoundedCornerShape(18.dp))
                    .padding(if (offer) 10.dp else 6.dp),
            ) { QrImage(uri, Modifier.fillMaxSize()) }
        }
        val extras: @Composable () -> Unit = {
            Note("Only show this to your own devices. Whoever scans it can answer it.")
            QuietLink("Copy the link instead", { copy(context, "QRST pairing link", uri) }, Vault.Text)
            MultipleResponders(view)
            RelayDetails(view)
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                code()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) { words(); extras() }
            }
        } else {
            words(); code(); extras()
        }
    }
}

/** Full brightness while a code is on screen, so another camera can read it. */
@Composable
private fun KeepBright() {
    val activity = LocalContext.current as? android.app.Activity ?: return
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = activity.window
        val before = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose { window.attributes = window.attributes.apply { screenBrightness = before } }
    }
}

@Composable
private fun DigitsScreen(model: AppModel, active: Active, view: TransferView<*>) {
    val code = view.session.code
    val (eyebrow, color) = eyebrowFor(active, view, "step 2 of 3")
    Frame(onBack = { model.closeTransfer() }) {
        Eyebrow(eyebrow, color)
        if (code == null) {
            Headline("Confirm on the other device")
            Note("No digits are checked at this level.")
        } else {
            Headline(if (view.session.check == CodeCheck.TYPE) "Type these on the device that's sending" else "Check the other device shows these")
            Spacer(Modifier.height(8.dp))
            DigitReadout(code, Vault.Teal, Vault.TealGround, Vault.TealEdge)
            Note("These digits belong to this transfer only. They are not a PIN.")
        }
        RelayChips(view.relays.filter { it.inUse }.map { relayName(it.url) to (it.state == "open") })
        MultipleResponders(view)
        if (view.showing) QuietLink("The other device rejected these digits", { model.advance() })
        RelayDetails(view)
    }
}

@Composable
private fun ReleaseScreen(model: AppModel, active: Active, view: TransferView<*>) {
    val s = view.session
    var digits by remember(s.attemptsLeft) { mutableStateOf("") }
    var attested by remember { mutableStateOf(false) }
    var allowSend by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val origin = view.peerOrigin
    // §9.1: maximum friction when the peer claims a web origin, or the pairing didn't come from this app's own camera.
    val maxFriction = origin != null || !view.viaCamera
    val receiveOnly = active.entry?.viaTransfer == true
    val what = if (active.demo) "text" else "key"
    val gated = s.check == CodeCheck.TYPE || maxFriction || receiveOnly
    val ready = (s.check != CodeCheck.TYPE || digits.length == 5) && (!maxFriction || attested) && (!receiveOnly || allowSend)

    Frame(
        onBack = { model.closeTransfer() },
        actions = {
            // §9.1 (spec PR #13): declining comes first and is never the smaller control.
            // Sending stays visibly off until the user has done what this screen asks,
            // and only then leads. With nothing to do first, declining stays prominent.
            if (gated) {
                EdgeButton("Don't send", { model.decline() })
                SendButton(
                    when {
                        s.check == CodeCheck.COMPARE -> "They match. Send my $what"
                        origin != null -> "Send my $what to $origin"
                        else -> "Send my $what to that device"
                    },
                    { model.release(digits.takeIf { it.length == 5 }); digits = "" },
                    ready,
                )
            } else {
                MainButton("Don't send", { model.decline() })
                EdgeButton(if (s.check == CodeCheck.COMPARE) "They match. Send my $what" else "Send my $what to that device", { model.release(null) }, color = Vault.Amber, edge = Vault.AmberEdge)
            }
            if (s.check == CodeCheck.COMPARE) QuietLink("The digits are different", { model.rejectMatch() })
            if (!active.demo && model.vault.needsAuth) {
                Text("Your fingerprint or screen lock confirms it next", fontFamily = PlexSans, fontSize = 13.sp, color = Vault.Muted, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        },
    ) {
        Eyebrow(eyebrowFor(active, view).first, Vault.Amber)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Headline("This is not a login.")
            Lede(
                if (active.demo) "You're giving another device your text. Once sent it can't be taken back."
                else "You're giving another device your key. It keeps a working copy for good. Nostr keys can't be revoked.",
            )
        }
        val entry = active.entry
        Facts(
            *listOfNotNull(
                entry?.let { "Key" to "${displayName(it.label)}  ${shortNpub(it.npub)}" },
                "To" to (origin?.let { "a web page claiming $it" } ?: "an app, by its own account"),
            ).toTypedArray(),
        )
        if (!view.viaCamera) Callout("Opened as a link, not by this app's scanner. Only continue if the code is on a screen in front of you.")
        if (s.ready > 1 || s.multipleResponders) Callout("More than one device answered this code. Only send to the one in front of you.", Vault.Danger, Vault.Danger)
        if (receiveOnly) {
            Panel(edge = Vault.TealEdge) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow sending this key once", fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Vault.Text)
                        Note("It arrived by transfer, so this phone only receives it unless you say so.")
                    }
                    Switch(allowSend, { allowSend = it }, colors = SwitchDefaults.colors(checkedTrackColor = Vault.Amber, checkedThumbColor = Vault.OnAmber))
                }
            }
        }
        when (s.check) {
            CodeCheck.TYPE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Note("Digits shown on your other device")
                DigitField(digits, { digits = it }, focus)
                if (s.attemptsLeft < MAX_ATTEMPTS) Note("Those digits didn't match. ${s.attemptsLeft} tries left.", Vault.Danger)
            }
            CodeCheck.COMPARE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Note("Does the other device show these digits?")
                DigitReadout(s.compare ?: "", Vault.Amber, Vault.Surface, Vault.AmberEdge, 48.sp)
                if (s.attemptsLeft < MAX_ATTEMPTS) Note("${s.attemptsLeft} tries left.", Vault.Danger)
            }
            CodeCheck.NONE -> Callout("No digits are checked at this level. The $what goes to whichever device answered the code.", Vault.Danger, Vault.Danger)
        }
        if (maxFriction) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { attested = !attested },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(attested, { attested = it }, colors = CheckboxDefaults.colors(checkedColor = Vault.Amber, checkmarkColor = Vault.OnAmber, uncheckedColor = Vault.Muted))
                Text("I scanned this code myself, from a screen in front of me", fontFamily = PlexSans, fontSize = 15.sp, color = Vault.Text)
            }
        }
    }
}

@Composable
private fun AcceptScreen(model: AppModel, active: Active, view: TransferView<*>) {
    var label by remember { mutableStateOf("") }
    val (eyebrow, color) = eyebrowFor(active, view, "step 3 of 3")
    Frame(
        onBack = { model.closeTransfer() },
        actions = {
            MainButton("Keep it", { model.accept(label.trim()) })
            EdgeButton("Discard it", { model.decline() })
        },
    ) {
        Eyebrow(eyebrow, color)
        Headline(if (active.demo) "Your other device sent a text" else "Your other device sent a key")
        if (active.demo) {
            Lede(view.session.rendering ?: "")
        } else {
            Note("Log in as this identity?")
            Panel(edge = Vault.TealEdge) {
                Text(view.session.rendering ?: "", fontFamily = PlexMono, fontSize = 13.sp, color = Vault.Text)
            }
            OutlinedTextField(label, { label = it }, label = { Text("Name it (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        }
        view.session.code?.let { Note("Digits on screen: $it") }
    }
}

@Composable
private fun EndedScreen(model: AppModel, active: Active, view: TransferView<*>) {
    val o = view.session.outcome
    val what = if (active.demo) "text" else "key"
    val (headline, detail) = when (o) {
        Outcome.DELIVERED -> "Sent" to "The other device confirmed it kept the $what."
        Outcome.SENT_UNCONFIRMED -> "Sent, unconfirmed" to "The $what left this phone, but the other device didn't confirm it kept it."
        Outcome.RECEIVED -> when {
            active.demo -> "Received" to "“${view.value ?: ""}”"
            active.storeError != null -> "Received, but not stored" to "Couldn't store it: ${active.storeError}"
            active.stored != null -> "Key added" to "${displayName(active.stored!!.label)} is now on this phone."
            else -> "Received" to "Storing…"
        }
        Outcome.DECLINED -> "Stopped" to "Nothing was sent or kept."
        Outcome.CANCELLED -> "Cancelled" to "Nothing moved."
        Outcome.PEER_ABORTED -> "The other device stopped" to "Nothing moved."
        Outcome.ATTEMPTS_EXHAUSTED -> "Too many wrong digits" to "Nothing was sent. Start again from a fresh code on the other device. Repeated failures can mean someone is interfering, not just mistyping."
        Outcome.EXPIRED -> "Timed out" to "Ten minutes passed. Nothing moved."
        Outcome.BAD_PAYLOAD -> "That wasn't a key" to "What arrived wasn't a valid Nostr key, so it was thrown away."
        Outcome.BLOCKED_PEER -> "Refused" to "Digits already failed against that device in the last hour."
        Outcome.SECOND_RESPONDER -> "Stopped: two devices answered" to "With no digits to tell them apart, nothing moved."
        null -> "Ended" to ""
    }
    val good = o == Outcome.DELIVERED || o == Outcome.SENT_UNCONFIRMED || (o == Outcome.RECEIVED && active.storeError == null)
    val (eyebrow, color) = eyebrowFor(active, view)
    Frame(onBack = { model.closeTransfer() }, actions = { MainButton("Done", { model.closeTransfer() }) }) {
        Eyebrow(eyebrow, color)
        Headline(headline)
        Lede(detail, if (good) Vault.Text else Vault.Muted)
        active.stored?.let { k -> if (o == Outcome.RECEIVED) KeyCard(k) {} }
    }
}
