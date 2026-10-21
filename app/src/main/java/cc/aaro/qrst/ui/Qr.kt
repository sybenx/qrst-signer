package cc.aaro.qrst.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * A QR code, one pixel per module, scaled up without smoothing. No overlay (§11.2b), so
 * the lowest error correction will do, which keeps the modules as large as possible.
 */
fun qrBitmap(text: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2),
    )
    val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
        bmp.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    }
    return bmp.asImageBitmap()
}

@Composable
fun QrImage(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { qrBitmap(text) }
    Image(bitmap, contentDescription = "Pairing QR code", modifier = modifier, filterQuality = FilterQuality.None)
}
