package com.example.momirprint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.momirprint.ui.theme.MomirPrintTheme

/** The app's screens. Named constants avoid typos in route strings. */
private object Routes {
    const val PRINT = "print"
    const val SETTINGS = "settings"
}

/**
 * The app's only Activity. Modern Compose apps use a single Activity as the container and draw
 * every screen (Print, Settings) as a composable, switching between them with the NavHost below.
 */
class MainActivity : ComponentActivity() {

    // The printer connection is shared state (Settings connects, Print will print through it),
    // so there must be exactly one PrinterService for both screens.
    private val printerService get() = (application as MomirPrintApp).printerService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomirPrintTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.PRINT,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Routes.PRINT) {
                            val printViewModel: PrintViewModel = viewModel(
                                factory = PrintViewModelFactory(
                                    SettingsRepository(applicationContext),
                                    ScryfallApi.service,
                                    printerService,
                                    PrintFormatter(applicationContext)
                                )
                            )
                            PrintScreen(
                                viewModel = printViewModel,
                                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
                            )
                        }
                        composable(Routes.SETTINGS) {
                            val settingsViewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModelFactory(
                                    SettingsRepository(applicationContext),
                                    printerService
                                )
                            )
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
