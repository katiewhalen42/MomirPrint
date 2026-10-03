package com.example.momirprint

import android.annotation.SuppressLint
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One row in the printer list. */
data class PrinterItem(val name: String, val address: String)

data class SettingsUIState(
    val printers: List<PrinterItem> = emptyList(),
    val isScanning: Boolean = false,
    /** Address of the printer we are currently connected to; "" when none. */
    val connectedAddress: String = "",
    val printMode: PrintMode = PrintMode.TEXT,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val printerService: PrinterService
) : ViewModel() {

    private val _uiState = mutableStateOf(SettingsUIState())
    val uiState = _uiState

    // The list rows only carry an address, but PrinterService.connectToPrinter() needs the
    // library's BluetoothConnection object, so keep the ones found by the last scan.
    private var foundConnections: Map<String, BluetoothConnection> = emptyMap()

    init {
        // DataStore exposes settings as a Flow: a stream that emits a new value every time the
        // setting changes. collect { } runs the block for each emission, so the UI stays in sync
        // with whatever is saved. (It runs until the ViewModel is cleared.)
        viewModelScope.launch {
            settingsRepository.printMode.collect { mode ->
                _uiState.value = _uiState.value.copy(printMode = mode)
            }
        }
        // TODO: read settingsRepository.printerAddress once and try to reconnect to that printer.
        // TODO: call scanPrinters() on entry once the Bluetooth permission has been granted.
    }

    /**
     * Lists the printers already paired in Android's Bluetooth settings. (This doesn't discover
     * new devices; the design's "Scan for devices" button refreshes this list.)
     *
     * TODO: on Android 12+ BLUETOOTH_CONNECT is a runtime permission. It is declared in the
     *  manifest, but the screen must also ask for it (ActivityResultContracts.RequestPermission)
     *  before calling this, or reading a device's name throws SecurityException.
     */
    //@SuppressLint("MissingPermission")
    fun scanPrinters() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true, errorMessage = null)
            try {
                // Bluetooth calls block, so keep them off the main (UI) thread.
                val found = withContext(Dispatchers.IO) { printerService.getPairedPrinters().orEmpty() }
                foundConnections = found.associateBy { it.device.address }
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    printers = found.map {
                        PrinterItem(name = it.device.name ?: "Unknown printer", address = it.device.address)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "Couldn't list printers: ${e.message}"
                )
            }
        }
    }

    fun connect(address: String) {
        // Guard: without a connection from the last scan, connectToPrinter(null) would quietly
        // pick "the first paired printer", which may not be the one that was tapped.
        val connection = foundConnections[address] ?: return

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { printerService.connectToPrinter(connection) }
            result
                .onSuccess {
                    settingsRepository.setPrinterAddress(address)
                    _uiState.value = _uiState.value.copy(connectedAddress = address, errorMessage = null)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(errorMessage = it.message)
                }
        }
    }

    fun setPrintMode(mode: PrintMode) {
        // No need to update uiState by hand: saving triggers the printMode Flow above.
        viewModelScope.launch { settingsRepository.setPrintMode(mode) }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val printerService: PrinterService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepository, printerService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
