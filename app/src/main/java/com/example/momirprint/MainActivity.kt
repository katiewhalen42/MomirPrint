package com.example.momirprint

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
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
import com.example.momirprint.ui.theme.MomirPrintTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MomirPrintTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TestSearchScreen(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                    )
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
            apiService.searchCards("q=${query}", ).let { result ->
                searchResult = result
            }
        }
    }
}

@Composable
fun TestCardDisplay(card: MagicCard) {
    var card by remember { mutableStateOf(card) }

    Text(
        text = card.toString()
    )
}

@Preview
@Composable
fun TestSearchScreen(viewModel: SearchViewModel = viewModel(), modifier: Modifier = Modifier) {
    var searchResult by remember { mutableStateOf<MagicCard>(MagicCard()) }

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
                    searchResult = viewModel.searchResult ?: MagicCard()
                }
            ) {
                Text("Search")
            }
        }
    }
}