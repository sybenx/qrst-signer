package cc.aaro.qrst.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.aaro.qrst.core.hexToBytes

// ---- layout -------------------------------------------------------------------------------

/**
 * One screen: an optional back row, content that scrolls, and actions pinned to the
 * bottom. The actions ride above the keyboard, so the send control is never hidden
 * behind it while digits are typed.
 */
@Composable
fun Frame(
    onBack: (() -> Unit)?,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        if (onBack != null) {
            Row(Modifier.padding(start = 8.dp, top = 4.dp)) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Vault.Muted) }
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val room = maxHeight
            androidx.compose.runtime.CompositionLocalProvider(LocalRoom provides room) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    content = content,
                )
            }
        }
        if (actions != null) {
            Column(
                Modifier.fillMaxWidth().background(Vault.Ground).padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = actions,
            )
        }
    }
}

/** Height of a screen's scrolling area, so a QR can be sized to fit whole on screen. */
val LocalRoom = androidx.compose.runtime.staticCompositionLocalOf { 600.dp }

@Composable
fun Eyebrow(text: String, color: Color = Vault.Muted) {
    Text(text.uppercase(), fontFamily = PlexMono, fontSize = 13.sp, letterSpacing = 1.sp, color = color)
}

@Composable
fun Headline(text: String) {
    Text(text, fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 35.sp, color = Vault.Text)
}

@Composable
fun Lede(text: String, color: Color = Vault.Text) {
    Text(text, fontFamily = PlexSans, fontSize = 17.sp, lineHeight = 24.sp, color = color)
}

@Composable
fun Note(text: String, color: Color = Vault.Muted) {
    Text(text, fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 20.sp, color = color)
}

// ---- buttons ------------------------------------------------------------------------------

@Composable
private fun ButtonBox(
    onClick: () -> Unit,
    enabled: Boolean,
    height: Dp,
    shape: RoundedCornerShape,
    fill: Color?,
    edge: Color?,
    dashed: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var m = modifier.fillMaxWidth().height(height).clip(shape)
    if (fill != null) m = m.background(fill)
    if (edge != null) {
        m = if (dashed) {
            m.drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    edge, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
        } else {
            m.border(1.5.dp, edge, shape)
        }
    }
    m = m.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    Box(m, contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ButtonLabel(text: String, color: Color, weight: FontWeight = FontWeight.Medium) {
    Text(text, fontFamily = PlexSans, fontWeight = weight, fontSize = 17.sp, color = color, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
}

/** The light, filled button: the main action of a screen that sends nothing. */
@Composable
fun MainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null) {
    ButtonBox(onClick, enabled, 58.dp, RoundedCornerShape(18.dp), if (enabled) Vault.Text else Vault.Surface, null, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            leading?.invoke()
            ButtonLabel(text, if (enabled) Vault.Ground else Vault.Muted, FontWeight.SemiBold)
        }
    }
}

@Composable
fun EdgeButton(text: String, onClick: () -> Unit, color: Color = Vault.Text, edge: Color = color, modifier: Modifier = Modifier, enabled: Boolean = true) {
    ButtonBox(onClick, enabled, 54.dp, RoundedCornerShape(16.dp), null, if (enabled) edge else Vault.Line, modifier = modifier) {
        ButtonLabel(text, if (enabled) color else Vault.Muted)
    }
}

/**
 * The control that releases a key (§9.1, as revised in spec PR #13): visibly
 * unavailable, dashed, until the user has done what the screen asks, and only then
 * the filled primary control. Never focused, never a default action.
 */
@Composable
fun SendButton(text: String, onClick: () -> Unit, ready: Boolean) {
    if (ready) {
        ButtonBox(onClick, true, 56.dp, RoundedCornerShape(16.dp), Vault.Amber, null) {
            ButtonLabel(text, Vault.OnAmber, FontWeight.SemiBold)
        }
    } else {
        ButtonBox(onClick, false, 56.dp, RoundedCornerShape(16.dp), null, Vault.Line, dashed = true) {
            ButtonLabel(text, Vault.Muted)
        }
    }
}

@Composable
fun QuietLink(text: String, onClick: () -> Unit, color: Color = Vault.Muted) {
    Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.CenterStart) {
        Text(text, fontFamily = PlexSans, fontSize = 14.sp, color = color, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
    }
}

// ---- blocks -------------------------------------------------------------------------------

@Composable
fun Panel(modifier: Modifier = Modifier, edge: Color = Vault.Edge, fill: Color = Vault.Surface, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(fill).border(1.dp, edge, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Label and value rows, as on the send prompt: what leaves, and where it goes. */
@Composable
fun Facts(vararg rows: Pair<String, String>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Vault.Edge), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        for ((label, value) in rows) {
            Row(Modifier.fillMaxWidth().background(Vault.Surface).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, fontFamily = PlexSans, fontSize = 15.sp, color = Vault.Muted, modifier = Modifier.width(44.dp))
                Text(value, fontFamily = PlexSans, fontSize = 15.sp, color = Vault.Text, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A warning that something about this pairing is weaker than a scan by this app. */
@Composable
fun Callout(text: String, color: Color = Vault.AmberText, edge: Color = Vault.AmberEdge) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, edge, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.padding(top = 3.dp).size(16.dp)) {
            val w = size.width
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(w / 2, 0f); lineTo(w, w); lineTo(0f, w); close() }
            drawPath(path, color, style = Stroke(1.6.dp.toPx()))
            drawLine(color, Offset(w / 2, w * 0.38f), Offset(w / 2, w * 0.66f), 1.6.dp.toPx())
            drawCircle(color, 1.dp.toPx(), Offset(w / 2, w * 0.82f))
        }
        Text(text, fontFamily = PlexSans, fontSize = 14.sp, lineHeight = 20.sp, color = color)
    }
}

@Composable
fun Pill(text: String, color: Color, edge: Color) {
    Text(
        text, fontFamily = PlexSans, fontSize = 12.sp, color = color,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, edge, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---- keys ---------------------------------------------------------------------------------

private val PATTERN_COLORS = listOf(
    Color(0xFFF2B33D), Color(0xFF7FC8C0), Color(0xFFD98E73), Color(0xFF9DB4E8), Color(0xFFC3B6E9), Color(0xFFA9C98A),
)

/**
 * A 5×5 mirrored pattern drawn from the public key, so two keys look different at a
 * glance. It identifies a key on this phone's list; it is not a security check.
 */
@Composable
fun KeyPattern(pubHex: String, size: Dp = 44.dp) {
    val bytes = androidx.compose.runtime.remember(pubHex) { runCatching { pubHex.hexToBytes() }.getOrDefault(ByteArray(32)) }
    val color = PATTERN_COLORS[(bytes[0].toInt() and 0xff) % PATTERN_COLORS.size]
    Canvas(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Vault.Ground).semantics { contentDescription = "" }) {
        val cell = this.size.width / 5
        for (row in 0 until 5) for (col in 0 until 3) {
            val on = (bytes[1 + row * 3 + col].toInt() and 1) == 1
            if (!on) continue
            for (c in setOf(col, 4 - col)) drawRect(color, Offset(c * cell, row * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** Names an older key stored before keys had names, rather than repeating its npub. */
fun displayName(label: String): String = if (label.startsWith("npub1")) "Unnamed key" else label

fun shortNpub(npub: String) = npub.take(12) + "…" + npub.takeLast(6)

// ---- digits -------------------------------------------------------------------------------

/**
 * The five digits as a readout: one panel, the digits spaced across it, a bar under
 * each. Used on the receiving phone, where the user reads them off.
 */
@Composable
fun DigitReadout(code: String, accent: Color, fill: Color, edge: Color, size: TextUnit = 60.sp) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(fill).border(1.dp, edge, RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 22.dp)
            .semantics { contentDescription = "Digits: " + code.toCharArray().joinToString(" ") },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (c in code) Text(c.toString(), fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = size, color = accent)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(5) { Box(Modifier.weight(1f).height(3.dp).background(accent)) }
        }
    }
}

/** Which of the session's relays are connected, by short name, as on the receiving phone. */
@Composable
fun RelayChips(relays: List<Pair<String, Boolean>>) {
    if (relays.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((name, open) in relays.take(3)) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Vault.Surface).padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name, fontFamily = PlexMono, fontSize = 12.sp, color = Vault.Muted, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (open) Vault.Teal else Vault.Line))
            }
        }
    }
}

/** A relay URL without its scheme and a leading "relay.": "wss://relay.primal.net" is "primal.net". */
fun relayName(url: String): String =
    url.removePrefix("wss://").removePrefix("ws://").substringBefore('/').removePrefix("relay.")
