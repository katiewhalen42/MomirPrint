package com.example.momirprint

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.momirprint.ui.theme.MomirPrintTheme

/** Stateful entry point, called from the navigation graph. Same two-layer pattern as PrintScreen. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState
    val context = LocalContext.current
    // true after the user has refused; drives the "open settings" hint
    var permissionDenied by remember { mutableStateOf(false) }

    // Registers the launcher. Must be called unconditionally during composition
    // (not inside an if or a click handler).
    // RequestMultiplePermissions shows the system dialogs for several permissions in one go and
    // reports back a Map of permission name -> granted. We only proceed if all were granted.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        permissionDenied = !allGranted
        if (allGranted) viewModel.scanPrinters()
    }

    // Use this wherever the user triggers a Bluetooth action.
    val scanWithPermission: () -> Unit = {
        if (context.hasBluetoothPermissions()) {
            viewModel.scanPrinters()
        } else {
            permissionLauncher.launch(BLUETOOTH_RUNTIME_PERMISSIONS)
        }
    }


    LaunchedEffect(Unit) {
        if (context.hasBluetoothPermissions()) viewModel.scanPrinters()
    }

    SettingsScreenContent(
        state = state,
        permissionDenied = permissionDenied,
        onOpenAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            )
        },
        onBack = onBack,
        onScan = scanWithPermission,
        onConnect = viewModel::connect,
        onPrintModeSelected = viewModel::setPrintMode
    )
}

@Composable
fun SettingsScreenContent(
    state: SettingsUIState,
    permissionDenied: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
    onBack: () -> Unit,
    onScan: () -> Unit,
    onConnect: (String) -> Unit,
    onPrintModeSelected: (PrintMode) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack) {
                // AutoMirrored flips the arrow automatically in right-to-left languages.
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        PrinterSection(state = state, onScan = onScan, onConnect = onConnect, permissionDenied = permissionDenied, onOpenAppSettings = onOpenAppSettings)
        PrintFormatSection(selected = state.printMode, onSelected = onPrintModeSelected)
    }
}

@Composable
private fun PrinterSection(state: SettingsUIState, onScan: () -> Unit, onConnect: (String) -> Unit, permissionDenied: Boolean = false, onOpenAppSettings: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Printer")

        OutlinedCard(Modifier.fillMaxWidth()) {
            if (state.printers.isEmpty()) {
                Text(
                    "No paired printers found. Pair your printer in Android's Bluetooth settings, then scan.",
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
            }
            state.printers.forEach { printer ->
                PrinterRow(
                    printer = printer,
                    connected = printer.address == state.connectedAddress,
                    onConnect = { onConnect(printer.address) }
                )
                HorizontalDivider()
            }
            TextButton(onClick = onScan, enabled = !state.isScanning, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isScanning) "Scanning…" else "Scan for devices")
            }
        }

        if (permissionDenied) {
            Text(
                "Bluetooth permission is needed to list your paired printers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onOpenAppSettings) { Text("Open app settings") }
        }

        state.errorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PrinterRow(printer: PrinterItem, connected: Boolean, onConnect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(printer.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (connected) "Connected" else "Not connected",
                style = MaterialTheme.typography.bodySmall,
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (connected) {
            Icon(Icons.Default.Check, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onConnect) { Text("Connect") }
        }
    }
}

@Composable
private fun PrintFormatSection(selected: PrintMode, onSelected: (PrintMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Print format")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormatTile(
                label = "Card image",
                icon = Icons.Default.Image,
                selected = selected == PrintMode.IMAGE,
                onClick = { onSelected(PrintMode.IMAGE) },
                modifier = Modifier.weight(1f)
            )
            FormatTile(
                label = "Text only",
                icon = Icons.Default.TextFields,
                selected = selected == PrintMode.TEXT,
                onClick = { onSelected(PrintMode.TEXT) },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            "Text only prints name, mana cost, type, and rules text — faster and uses less thermal paper than a full image.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FormatTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun SettingsScreenPreview() {
    MomirPrintTheme {
        SettingsScreenContent(
            state = SettingsUIState(
                printers = listOf(
                    PrinterItem("MPT-II Thermal", "00:11:22:33:44:55"),
                    PrinterItem("Phomemo M02S", "AA:BB:CC:DD:EE:FF")
                ),
                connectedAddress = "00:11:22:33:44:55",
                printMode = PrintMode.TEXT
            ),
            onBack = {},
            onScan = {},
            onConnect = {},
            onPrintModeSelected = {}
        )
    }
}
