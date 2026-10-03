package com.example.momirprint

import android.content.Context
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosEncodingException
import com.dantsu.escposprinter.exceptions.EscPosParserException

private const val MAX_IMAGE_SLICE_HEIGHT = 255   // the library rescales anything taller than 256

class PrinterService(private val context: Context) {

    private var printer: EscPosPrinter? = null
    private var connection: BluetoothConnection? = null

    fun getPairedPrinters(): Array<BluetoothConnection>? {
        return BluetoothPrintersConnections().list
    }

    fun connectToPrinter(btConnection: BluetoothConnection?): Result<Unit> {
        if(btConnection != null) {
            return try {
                connection = btConnection
                printer = EscPosPrinter(connection, 203, 48f, 32)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("Failed to connect to printer"))
            }
        } else {
            return try {
                connection = BluetoothPrintersConnections.selectFirstPaired()
                printer = EscPosPrinter(connection, 203, 48f, 32)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("Failed to connect to printer"))
            }
        }
    }

    fun isConnected(): Boolean {
        return connection?.isConnected ?: false
    }

    fun disconnect() {
        connection?.disconnect()
        connection = null
        printer = null
    }

    // TODO(print): add a blank buffer beneath card printings so text and image cards leave
    //  extra trailing whitespace for folding or cutting.
    fun printText(formattedText: String): Result<Unit> {
        val p = printer ?: return Result.failure(Exception("Printer not connected"))
        return try {
            p.printFormattedText(formattedText)
            Result.success(Unit)
        } catch (e: EscPosConnectionException) {
            Result.failure(Exception("Connection error: ${e.message}"))
        } catch (e: EscPosParserException) {
            Result.failure(Exception("Parser error: ${e.message}"))
        } catch (e: EscPosEncodingException) {
            Result.failure(Exception("Encoding error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unknown error: ${e.message}"))
        }
    }

    fun printImage(bitmap: android.graphics.Bitmap): Result<Unit> {
        val p = printer ?: return Result.failure(Exception("Printer not connected"))
        return try {
            val markup = StringBuilder()
            var y = 0
            while (y < bitmap.height) {
                val sliceHeight = minOf(MAX_IMAGE_SLICE_HEIGHT, bitmap.height - y)
                // createBitmap(source, x, y, width, height) copies out a rectangle (a crop).
                val slice = android.graphics.Bitmap.createBitmap(bitmap, 0, y, bitmap.width, sliceHeight)
                markup.append(
                    "[C]<img>${com.dantsu.escposprinter.textparser.PrinterTextParserImg.bitmapToHexadecimalString(p, slice)}</img>\n"
                )
                y += sliceHeight
            }
            p.printFormattedText(markup.toString())   // one call, so the slices print contiguously
            Result.success(Unit)
        } catch (e: EscPosConnectionException) {
            Result.failure(Exception("Connection error: ${e.message}"))
        } catch (e: EscPosParserException) {
            Result.failure(Exception("Parser error: ${e.message}"))
        } catch (e: EscPosEncodingException) {
            Result.failure(Exception("Encoding error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Unknown error: ${e.message}"))
        }
    }
}