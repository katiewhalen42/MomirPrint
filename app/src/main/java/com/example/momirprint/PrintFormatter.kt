package com.example.momirprint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Dimension
import coil3.size.Size
import coil3.toBitmap
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.core.graphics.scale

class PrintFormatter(private val context: Context) {
    fun uriToBitMap(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source)
    }

    fun formatCardImage(printer: EscPosPrinter, bitmap: Bitmap): String {
        return "[C]<img>${PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)}</img>\n"
    }

    // TODO(print): handle double-faced cards by printing the back face upside-down
    //  before the front face so the paper can be folded.
    suspend fun formatCardText(card: MagicCard): String {
        val builder = StringBuilder()
        val repo = SettingsRepository(context)
        if(card.layout == "normal"){
            builder.append("[L]<b>${card.name}</b>[R]<b>${card.mana_cost}</b>\n");
            if (repo.qrCodeEnabled.first() == true) {
                builder.append("[C]<qr>${card.scryfall_uri}</qr>\n")
            }
            builder.append("[L]${card.type_line}\n")
            builder.append("[L]${card.oracle_text}\n")
            if(card.power.isNotEmpty() || card.toughness.isNotEmpty()){
                builder.append("[R]${card.power}/${card.toughness}\n")
            }
            if(card.loyalty.isNotEmpty()){
                builder.append("[R]${card.loyalty}\n")
            }
        }
        return builder.toString()
    }

    suspend fun loadCardBitmap(url: String, widthPx: Int = 384): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)                       // printer needs CPU-readable pixels
            .size(Size(widthPx, Dimension.Undefined))   // scale to paper width, keep aspect ratio
            .build()
        // Throw on failure (instead of returning null) so the real cause reaches the
        // "printMessage" text on the Print screen via print()'s existing catch block.
        return when (val result = SingletonImageLoader.get(context).execute(request)) {
            is SuccessResult -> {
                val bitmap = result.image.toBitmap()
                if (bitmap.width == widthPx) bitmap
                else bitmap.scale(
                    widthPx,
                    (bitmap.height * widthPx.toFloat() / bitmap.width).roundToInt()
                )
            }
            is ErrorResult -> throw Exception("Image load failed: ${result.throwable}", result.throwable)
        }
    }
}