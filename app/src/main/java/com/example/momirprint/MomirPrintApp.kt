package com.example.momirprint

import android.app.Application

class MomirPrintApp : Application() {
    val printerService by lazy { PrinterService(this) }
}