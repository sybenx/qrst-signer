package cc.aaro.qrst.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Five discrete positions that accept exactly five digits (§9.2 "Entry"). Never
 * pre-filled; anything that is not a digit, and anything past the fifth, is dropped.
 */
@Composable
fun DigitField(value: String, onChange: (String) -> Unit, focus: FocusRequester, enabled: Boolean = true) {
    BasicTextField(
        value = value,
        onValueChange = { next -> onChange(next.filter { it.isDigit() }.take(5)) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        modifier = Modifier.focusRequester(focus),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0 until 5) {
                    val c = value.getOrNull(i)
                    val current = i == value.length
                    Box(
                        Modifier.size(52.dp, 64.dp).border(
                            if (current) 2.dp else 1.dp,
                            if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(10.dp),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(c?.toString() ?: "", fontSize = 30.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
    )
}
