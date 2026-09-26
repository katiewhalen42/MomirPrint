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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomirPrintTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "print",
                        modifier = Modifier.padding(innerPadding)
                    ){
                        composable("print") {
                            val printViewModel: PrintViewModel = viewModel(factory = PrintViewModelFactory(
                                SettingsRepository(applicationContext),
                                ScryfallApi.service))
                            PrintScreen(viewModel = printViewModel, onSettingsClick = {
                                navController.navigate("settings")
                            })
                        }
                        /*composable("settings") {
                            SettingsScreen(navController = navController)
                        }*/
                    }
                }
            }
        }
    }
}
