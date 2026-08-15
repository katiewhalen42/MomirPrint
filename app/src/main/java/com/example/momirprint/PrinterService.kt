package com.example.momirprint

import android.content.Context
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosEncodingException
import com.dantsu.escposprinter.exceptions.EscPosParserException

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

    fun printText(formattedText: String): Result<Unit> {
        return try {
            printer?.printFormattedText(formattedText)
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