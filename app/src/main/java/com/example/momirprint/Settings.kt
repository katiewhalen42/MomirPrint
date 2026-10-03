package com.example.momirprint

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class PrintMode(val storedValue: String) {
    TEXT("text"),
    IMAGE("image");

    companion object {
        fun fromStoredValue(value: String): PrintMode {
            return entries.firstOrNull { it.storedValue == value } ?: TEXT
        }
    }
}

class SettingsRepository(private val context: Context) {

    companion object {
        val PRINT_MODE = stringPreferencesKey("print_mode")
        val QR_CODE_ENABLED = booleanPreferencesKey("qr_code_enabled")
        val PRINTER_ADDRESS = stringPreferencesKey("printer_address")

        private val DEFAULT_PRINT_MODE = PrintMode.TEXT
        private const val DEFAULT_QR_CODE_ENABLED = false
        private const val DEFAULT_PRINTER_ADDRESS = ""
    }

    val printMode = context.dataStore.data.map { preferences ->
        val value = preferences[PRINT_MODE] ?: DEFAULT_PRINT_MODE.storedValue
        PrintMode.fromStoredValue(value)
    }

    val qrCodeEnabled = context.dataStore.data.map { preferences ->
        preferences[QR_CODE_ENABLED] ?: DEFAULT_QR_CODE_ENABLED
    }

    val printerAddress = context.dataStore.data.map { preferences ->
        preferences[PRINTER_ADDRESS] ?: DEFAULT_PRINTER_ADDRESS
    }

    // TODO(settings): restore this saved printer address on app launch so the last connected
    //  printer survives app restarts.
    suspend fun setPrintMode(mode: PrintMode) {
        context.dataStore.edit { preferences ->
            preferences[PRINT_MODE] = mode.storedValue
        }
    }

    suspend fun setQrCodeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[QR_CODE_ENABLED] = enabled
        }
    }

    suspend fun setPrinterAddress(address: String) {
        context.dataStore.edit { preferences ->
            preferences[PRINTER_ADDRESS] = address
        }
    }

}