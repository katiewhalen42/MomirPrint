package com.example.momirprint

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class QueryMode{
    RANDOM,
    SEARCH
}
enum class RandomMode{
    FILTERS,
    MOMIR
}

sealed interface CardState {
    data object Empty : CardState
    data object Loading : CardState
    data class Error(val message: String) : CardState
    data object Ready : CardState
}

data class PrintUIState(
    val queryMode: QueryMode = QueryMode.RANDOM,
    val randomMode: RandomMode = RandomMode.FILTERS,

    val cardTypes: Set<String> = emptySet(),
    val cardColors: Set<Char> = emptySet(),
    val cardColorId: Set<Char> = emptySet(),
    val minManaValue: Int? = 0,
    val maxManaValue: Int? = null,
    val eqManaValue: Int? = null,
    val scryfallQuery: String = "",

    val selectedCard: MagicCard? = null,
    val cardState: CardState = CardState.Empty
)

class PrintViewModel (private val settingsRepository: SettingsRepository,
                      private val apiService: ApiService) : ViewModel() {

    private val _uiState = mutableStateOf(PrintUIState())
    val uiState = _uiState

    fun setMode(queryMode: QueryMode) {
        _uiState.value = _uiState.value.copy(queryMode = queryMode)
    }

    fun setRandomMode(randomMode: RandomMode) {
        _uiState.value = _uiState.value.copy(randomMode = randomMode)
    }

    fun setCardTypes(cardTypes: Set<String>) {
        _uiState.value = _uiState.value.copy(cardTypes = cardTypes)
    }

    fun setCardColors(cardColors: Set<Char>) {
        _uiState.value = _uiState.value.copy(cardColors = cardColors)
    }

    fun setCardColorId(cardColorId: Set<Char>) {
        _uiState.value = _uiState.value.copy(cardColorId = cardColorId)
    }

    fun setMinManaValue(minManaValue: Int?) {
        _uiState.value = _uiState.value.copy(minManaValue = minManaValue)
    }

    fun setMaxManaValue(maxManaValue: Int?) {
        _uiState.value = _uiState.value.copy(maxManaValue = maxManaValue)
    }

    fun setEqManaValue(eqManaValue: Int?) {
        _uiState.value = _uiState.value.copy(eqManaValue = eqManaValue)
    }

    fun setScryfallQuery(scryfallQuery: String) {
        _uiState.value = _uiState.value.copy(scryfallQuery = scryfallQuery)
    }

    fun reroll() {
        val state = _uiState.value
        val query = buildScryfallQuery()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cardState = CardState.Loading)
            try {
                val card = apiService.getRandomCard(query)
                _uiState.value = _uiState.value.copy(selectedCard = card, cardState = CardState.Ready)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cardState = CardState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    fun buildScryfallQuery(): String {
        val state = _uiState.value
        val queryParts = mutableListOf<String>()

        if (state.scryfallQuery.isNotBlank()) {
            return state.scryfallQuery
        } else {
            if (state.cardTypes.isNotEmpty()) {
                queryParts.add("(")
                queryParts.add(state.cardTypes.joinToString(" or ") { "t:${it}" })
                queryParts.add(")")
            }
            if (state.cardColors.isNotEmpty()) {
                queryParts.add(state.cardColors.joinToString("") { "c:${it}" })
            }
            if (state.cardColorId.isNotEmpty()) {
                queryParts.add(state.cardColorId.joinToString("") { "id:${it}" })
            }
            if (state.minManaValue != null) {
                queryParts.add("mv>=${state.minManaValue}")
            }
            if (state.maxManaValue != null) {
                queryParts.add("mv<=${state.maxManaValue}")
            }
            if (state.eqManaValue != null) {
                queryParts.add("mv=${state.eqManaValue}")
            }
        }
        return queryParts.joinToString(" ")
    }

    fun print() {
        val card = _uiState.value.selectedCard
        //TODO: Implement printing logic using PrinterService
    }
}