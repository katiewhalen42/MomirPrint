package com.example.momirprint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PrintViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val apiService: ApiService,
    private val printerService: PrinterService,
    private val formatter: PrintFormatter
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PrintViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PrintViewModel(settingsRepository, apiService, printerService, formatter) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}