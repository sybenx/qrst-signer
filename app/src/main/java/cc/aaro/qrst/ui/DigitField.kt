package cc.aaro.qrst.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Five discrete positions that accept exactly five digits (§9.2 "Entry"), drawn as a
 * readout: the digits spaced across one panel, a bar under each that turns amber as it
 * fills. Never pre-filled; anything that is not a digit, and anything past the fifth,
 * is dropped. Tapping anywhere on the panel opens the number pad.
 */
@Composable
fun DigitField(value: String, onChange: (String) -> Unit, focus: FocusRequester, label: String, size: TextUnit = 46.sp, enabled: Boolean = true) {
    BasicTextField(
        value = value,
        onValueChange = { next -> onChange(next.filter { it.isDigit() }.take(5)) },
        enabled = enabled,
        cursorBrush = SolidColor(Vault.Amber),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Vault.Surface)
                    .border(1.dp, Vault.Edge, RoundedCornerShape(18.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(label, fontFamily = PlexSans, fontSize = 14.sp, color = Vault.Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 0 until 5) {
                        val c = value.getOrNull(i)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                c?.toString() ?: " ", fontSize = size, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                                color = Vault.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                            Box(
                                Modifier.fillMaxWidth().height(3.dp).background(
                                    when {
                                        c != null -> Vault.Amber
                                        i == value.length -> Vault.Muted
                                        else -> Vault.Line
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        },
    )
}
