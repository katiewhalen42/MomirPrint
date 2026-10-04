package com.example.momirprint

import android.content.Context
import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.exceptions.EscPosConnectionException
import com.dantsu.escposprinter.exceptions.EscPosEncodingException
import com.dantsu.escposprinter.exceptions.EscPosParserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val MAX_IMAGE_SLICE_HEIGHT = 255   // the library rescales anything taller than 256

/** Internal marker: this failure is about the connection, so reconnecting might fix it. */
private class ConnectionLostException(message: String) : Exception(message)

class PrinterService(private val context: Context) {

    private var printer: EscPosPrinter? = null
    private var connection: BluetoothConnection? = null
    private val _connectedAddress = MutableStateFlow("")
    val connectedAddress: StateFlow<String> = _connectedAddress
    private val reconnectLock = Mutex()
    private val settings by lazy { SettingsRepository(context) }

    fun getPairedPrinters(): Array<BluetoothConnection>? {
        return BluetoothPrintersConnections().list
    }

    fun connectToPrinter(btConnection: BluetoothConnection?): Result<Unit> {
        return try {
            val conn = btConnection ?: BluetoothPrintersConnections.selectFirstPaired()
            conn!!.connect()                       // see the note below this block
            connection = conn
            printer = EscPosPrinter(conn, 203, 48f, 32)
            _connectedAddress.value = conn.device.address
            Result.success(Unit)
        } catch (e: Exception) {
            // Log the full stack trace to Logcat (filter on "PrinterService"), and put the
            // cause in the on-screen message instead of discarding it.
            Log.e("PrinterService", "connectToPrinter failed", e)
            Result.failure(Exception("Failed to connect to printer: ${e.javaClass.simpleName}: ${e.message}", e))
        }
    }

    fun isConnected(): Boolean {
        return connection?.isConnected ?: false
    }

    fun disconnect() {
        connection?.disconnect()
        connection = null
        printer = null
        _connectedAddress.value = ""
    }

    suspend fun reconnectSaved(settings: SettingsRepository): Result<Unit> =
        reconnectLock.withLock {
            // Bluetooth calls block, so run on the IO thread pool, not the main thread.
            withContext(Dispatchers.IO) {
                if (isConnected()) return@withContext Result.success(Unit)
                if (!context.hasBluetoothConnectPermission()) {
                    return@withContext Result.failure(Exception("Bluetooth permission not granted"))
                }
                // first() reads the Flow once and stops: "give me the current value".
                val address = settings.printerAddress.first()
                if (address.isBlank()) {
                    return@withContext Result.failure(Exception("No saved printer"))
                }
                try {
                    val match = getPairedPrinters()?.firstOrNull { it.device.address == address }
                        ?: return@withContext Result.failure(
                            Exception("Saved printer not found (unpaired, or Bluetooth is off)")
                        )
                    connectToPrinter(match)
                } catch (e: SecurityException) {
                    Result.failure(e)
                }
            }
        }

    /*fun printText(formattedText: String): Result<Unit> {
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
    }*/

    suspend fun printText(formattedText: String): Result<Unit> =
        printWithRetry { p -> p.printFormattedText(formattedText) }

    suspend fun printImage(bitmap: android.graphics.Bitmap): Result<Unit> =
        printWithRetry { p ->
            val markup = StringBuilder()
            var y = 0
            while (y < bitmap.height) {
                val sliceHeight = minOf(MAX_IMAGE_SLICE_HEIGHT, bitmap.height - y)
                val slice = android.graphics.Bitmap.createBitmap(bitmap, 0, y, bitmap.width, sliceHeight)
                markup.append(
                    "[C]<img>${com.dantsu.escposprinter.textparser.PrinterTextParserImg.bitmapToHexadecimalString(p, slice)}</img>\n"
                )
                y += sliceHeight
            }
            p.printFormattedText(markup.toString())   // one call, so the slices print contiguously
        }

    /**
     * Runs `action` against the printer. If it fails because the connection is gone, drops the
     * dead connection, reconnects to the saved printer, and tries exactly once more.
     *
     * `action` is a lambda that takes the printer and does the actual printing. Passing the
     * "what to print" as a function lets both print methods share all the retry logic.
     */
    private suspend fun printWithRetry(action: (EscPosPrinter) -> Unit): Result<Unit> {
        val first = attempt(action)
        if (first.isSuccess || first.exceptionOrNull() !is ConnectionLostException) return first

        // The old socket is dead. Clear it so reconnectSaved doesn't see isConnected() == true
        // and return early, and so the Settings screen stops claiming we're connected.
        disconnect()

        val reconnect = reconnectSaved(settings)
        if (reconnect.isFailure) {
            return Result.failure(
                Exception("Lost the printer and couldn't reconnect: ${reconnect.exceptionOrNull()?.message}")
            )
        }
        return attempt(action)   // second and last try; its failure goes straight to the caller
    }

    /** One print try. Blocking Bluetooth I/O, so it runs on the IO thread pool. */
    private suspend fun attempt(action: (EscPosPrinter) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            // "Not connected" counts as retryable: a reconnect may well fix it.
            val p = printer
                ?: return@withContext Result.failure(ConnectionLostException("Printer not connected"))
            try {
                action(p)
                Result.success(Unit)
            } catch (e: EscPosConnectionException) {
                Result.failure(ConnectionLostException("Connection error: ${e.message}"))
            } catch (e: EscPosParserException) {
                Result.failure(Exception("Parser error: ${e.message}"))
            } catch (e: EscPosEncodingException) {
                Result.failure(Exception("Encoding error: ${e.message}"))
            } catch (e: Exception) {
                Result.failure(Exception("Unknown error: ${e.message}"))
            }
        }
}