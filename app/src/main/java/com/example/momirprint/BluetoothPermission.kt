package com.example.momirprint

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * True if we're allowed to talk to paired Bluetooth devices.
 * Before Android 12 there is no runtime permission, so the answer is always yes.
 */
fun Context.hasBluetoothConnectPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED