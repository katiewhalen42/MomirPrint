package com.example.momirprint

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import kotlinx.coroutines.flow.first

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

}