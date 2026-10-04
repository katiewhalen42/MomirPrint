package com.example.momirprint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
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

private const val TEXT_CARD_BOTTOM_BUFFER_LINES = 2
private const val FACE_SEPARATOR = "[C]----------------\n"

class PrintFormatter(private val context: Context) {

    fun uriToBitMap(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source)
    }

    fun formatCardImage(printer: EscPosPrinter, bitmap: Bitmap): String {
        return "[C]<img>${PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)}</img>\n"
    }

    suspend fun formatCardText(card: MagicCard): String {
        val builder = StringBuilder()
        val repo = SettingsRepository(context)

        if (card.isFoldableDoubleFaced()) {
            val faces = card.printableFacesInOrder()
            faces.forEachIndexed { index, face ->
                appendFaceText(
                    builder = builder,
                    name = face.name,
                    manaCost = face.mana_cost,
                    typeLine = face.type_line,
                    oracleText = face.oracle_text,
                    power = face.power,
                    toughness = face.toughness,
                    loyalty = face.loyalty
                )
                if (index < faces.lastIndex) {
                    builder.append(FACE_SEPARATOR)
                }
            }
        } else if (card.layout == "normal") {
            appendFaceText(
                builder = builder,
                name = card.name,
                manaCost = card.mana_cost,
                typeLine = card.type_line,
                oracleText = card.oracle_text,
                power = card.power,
                toughness = card.toughness,
                loyalty = card.loyalty
            )
        }

        if (builder.isNotEmpty() && repo.qrCodeEnabled.first() == true) {
            builder.append("[C]<qr>${card.scryfall_uri}</qr>\n")
        }

        if (builder.isNotEmpty()) {
            repeat(TEXT_CARD_BOTTOM_BUFFER_LINES) {
                builder.append("[L]\n")
            }
        }
        return builder.toString()
    }

    suspend fun loadCardBitmap(url: String, widthPx: Int = 384): Bitmap {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)                       // printer needs CPU-readable pixels
            .size(Size(widthPx, Dimension.Undefined))   // scale to paper width, keep aspect ratio
            .build()

        return when (val result = SingletonImageLoader.get(context).execute(request)) {
            is SuccessResult -> {
                val bitmap = result.image.toBitmap()
                if (bitmap.width == widthPx) bitmap
                else bitmap.scale(
                    widthPx,
                    (bitmap.height * widthPx.toFloat() / bitmap.width).roundToInt()
                )
            }
            is ErrorResult -> throw Exception("Card image could not be loaded", result.throwable)
        }
    }

    suspend fun loadCardBitmapsForPrint(card: MagicCard, widthPx: Int = 384): List<Bitmap> {
        val urls = card.printableImageUrlsInOrder()
        if (urls.isEmpty()) return emptyList()

        val bitmaps = urls.map { url -> loadCardBitmap(url, widthPx) }.toMutableList()

        // With front-first order, the back face is second and needs 180 rotation.
        if (card.isFoldableDoubleFaced() && bitmaps.size >= 2) {
            bitmaps[1] = rotate180(bitmaps[1])
        }

        return bitmaps
    }

    fun combineBitmapsVertically(bitmaps: List<Bitmap>): Bitmap? {
        if (bitmaps.isEmpty()) return null

        val width = bitmaps.maxOf { it.width }
        val totalHeight = bitmaps.sumOf { it.height }
        val combined = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)

        var y = 0f
        for (bitmap in bitmaps) {
            val left = ((width - bitmap.width) / 2f)
            canvas.drawBitmap(bitmap, left, y, null)
            y += bitmap.height
        }

        return combined
    }

    private fun appendFaceText(
        builder: StringBuilder,
        name: String,
        manaCost: String,
        typeLine: String,
        oracleText: String,
        power: String,
        toughness: String,
        loyalty: String
    ) {
        builder.append("[L]<b>${name}</b>[R]<b>${manaCost}</b>\n")
        builder.append("[L]${typeLine}\n")
        builder.append("[L]${oracleText}\n")
        if (power.isNotEmpty() || toughness.isNotEmpty()) {
            builder.append("[R]${power}/${toughness}\n")
        }
        if (loyalty.isNotEmpty()) {
            builder.append("[R]${loyalty}\n")
        }
    }

    private fun rotate180(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(180f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}