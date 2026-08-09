package com.example.momirprint

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val PRINT_MODE = stringPreferencesKey("print_mode")
        val PRINTER_ADDRESS = stringPreferencesKey("printer_address")
    }

    val printMode = context.dataStore.data.map { preferences ->
        preferences[PRINT_MODE] ?: "default"
    }

}