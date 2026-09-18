package com.example.momirprint

import android.R.id.tabs
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.momirprint.ui.theme.MomirPrintTheme
import kotlinx.coroutines.launch

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
                            PrintScreen(navController = navController)
                        }
                        composable("settings") {
                            SettingsScreen(navController = navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MomirPrintTheme {
        Greeting("Android")
    }
}

class SearchViewModel : ViewModel() {
    val apiService = ScryfallApi.service
    var query by mutableStateOf("")
    var searchResult by mutableStateOf<MagicCard?>(null)

    fun sendQuery() {
        viewModelScope.launch{
            searchResult = apiService.searchCards(query).data.firstOrNull()
        }
    }
}

class RandomViewModel : ViewModel() {
    val apiService = ScryfallApi.service
    var randomCard by mutableStateOf<MagicCard?>(null)

    fun fetchRandomCard(query: String? = null, format: String? = null) {
        viewModelScope.launch {
            randomCard = apiService.getRandomCard(query, format)
        }
    }
}

@Composable
fun TestCardDisplay(card: MagicCard) {
    Text(
        text = card.toString()
    )
}

@Preview
@Composable
fun TestSearchScreen(viewModel: SearchViewModel = viewModel(), modifier: Modifier = Modifier) {
    val searchResult = viewModel.searchResult ?: MagicCard()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TestCardDisplay(searchResult)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextField(
                value = viewModel.query,
                onValueChange = { newText -> viewModel.query = newText },
                label = { Text("Card Search") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    viewModel.sendQuery()
                }
            ) {
                Text("Search")
            }
        }
    }
}

@Composable
fun RandomScreen(viewModel: RandomViewModel = viewModel(), modifier: Modifier = Modifier) {
    val randomCard = viewModel.randomCard ?: MagicCard()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TestCardDisplay(randomCard)

        Button(
            onClick = {
                viewModel.fetchRandomCard()
            }
        ) {
            Text("Fetch Random Card")
        }
    }
}

@Composable
fun PrintScreen(viewModel: PrintViewModel, onSettingsClick : () -> Unit){
    val state by viewModel.uiState

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Print a card", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}