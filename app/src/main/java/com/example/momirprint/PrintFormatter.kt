package com.example.momirprint

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.textparser.PrinterTextParserImg

class PrintFormatter {
    fun uriToBitMap(context: Context, uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source)
    }

    fun formatCardImage(printer: EscPosPrinter, bitmap: Bitmap): String {
        return "[C]<img>${PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)}</img>\n"
    }
}