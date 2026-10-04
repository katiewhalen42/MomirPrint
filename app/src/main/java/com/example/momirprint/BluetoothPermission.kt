package com.example.momirprint

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The runtime permissions we need on Android 12+ (API 31).
 *
 * - BLUETOOTH_CONNECT: list paired devices, read their names, open a connection.
 * - BLUETOOTH_SCAN: required by BluetoothConnection.connect() in the ESC/POS library, which
 *   calls BluetoothAdapter.cancelDiscovery() before connecting (an active discovery slows
 *   connections down). We never run a discovery ourselves, but Android still demands this
 *   permission for that call, and throws SecurityException without it.
 *
 * Before Android 12 neither exists as a runtime permission.
 */
val BLUETOOTH_RUNTIME_PERMISSIONS: Array<String> = arrayOf(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_SCAN
)

/**
 * True if we hold every Bluetooth permission needed to list, connect to and print on paired
 * devices. Before Android 12 there is no runtime permission, so the answer is always yes.
 */
fun Context.hasBluetoothPermissions(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            BLUETOOTH_RUNTIME_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
