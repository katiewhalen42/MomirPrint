package com.example.momirprint

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

enum class QueryMode {
    SEARCH_FILTER,
    MOMIR
}
/*enum class RandomMode{
    FILTERS,
    MOMIR
}*/

sealed interface CardState {
    data object Empty : CardState
    data object Loading : CardState
    data class Error(val message: String) : CardState
    data object Ready : CardState
}

data class PrintUIState(
    val queryMode: QueryMode = QueryMode.SEARCH_FILTER,
    //val randomMode: RandomMode = RandomMode.FILTERS,
    val searchText: String = "",
    val filters: CardFilters = CardFilters(),
    val momirManaValue: Int = 4,

    val selectedCard: MagicCard? = null,
    val cardState: CardState = CardState.Empty
)

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

class PrintViewModel (private val settingsRepository: SettingsRepository,
                      private val apiService: ApiService) : ViewModel() {

    private val _uiState = mutableStateOf(PrintUIState())
    val uiState = _uiState

    fun setMode(queryMode: QueryMode) {
        _uiState.value = _uiState.value.copy(queryMode = queryMode)
    }

    fun setSearchText(searchText: String) {
        _uiState.value = _uiState.value.copy(searchText = searchText)
    }

    fun setMomirManaValue(value: Int) {
        _uiState.value = _uiState.value.copy(momirManaValue = value.coerceIn(0, MAX_MANA_VALUE))
    }

    private fun updateFilters(transform: (CardFilters) -> CardFilters) {
        val state = _uiState.value
        _uiState.value = state.copy(filters = transform(state.filters))
    }

    fun toggleCardType(type: String) {
        updateFilters { it.copy(types = it.types.toggled(type)) }
    }

    fun toggleColor(color: Char) = updateFilters { f ->
        val colors = when {
            // Colorless is exclusive: picking it clears the real colors, and vice versa.
            color == 'C' -> if ('C' in f.colors) emptySet<Char>() else setOf('C')
            else -> (f.colors - 'C').toggled(color)
        }
        f.copy(colors = colors)
    }

    fun setColorMatch(match: ColorMatch) = updateFilters { it.copy(colorMatch = match) }

    // Dragging one slider past the other pushes the other along, so min <= max always holds.
    fun setMinManaValue(value: Int) = updateFilters {
        it.copy(minManaValue = value, maxManaValue = maxOf(value, it.maxManaValue))
    }

    fun setMaxManaValue(value: Int) = updateFilters {
        it.copy(maxManaValue = value, minManaValue = minOf(value, it.minManaValue))
    }

    fun toggleRarity(rarity: String) = updateFilters { it.copy(rarities = it.rarities.toggled(rarity)) }

    /** Require `status` for `format`. Pass null to stop filtering on that format. */
    fun setFormatStatus(format: String, status: FormatStatus?) = updateFilters { f ->
        f.copy(formats = if (status == null) f.formats - format else f.formats + (format to status))
    }

    fun setCustomQuery(query: String) = updateFilters { it.copy(customQuery = query) }

    fun resetFilters() = updateFilters { CardFilters() }

    // ---- Fetching a card ------------------------------------------------------------------

    /** Name search. Scryfall's `fuzzy` lookup returns the single best match. */
    fun search() {
        val name = _uiState.value.searchText.trim()
        if (name.isEmpty()) return
        loadCard { apiService.getCardByName(name) }
    }

    /** A random card matching the current filters (or any printable card if there are none). */
    fun drawRandom() {
        val query = ScryfallQueryBuilder.build(_uiState.value.filters)
        loadCard { apiService.getRandomCard(query.ifBlank { null }) }
    }

    /** A random creature with exactly the chosen mana value. */
    fun drawMomir() {
        val query = ScryfallQueryBuilder.momir(_uiState.value.momirManaValue)
        loadCard { apiService.getRandomCard(query) }
    }

    /**
     * Shared loading / success / error handling. `fetch` is a suspend lambda: a block of code
     * that is allowed to wait for the network without freezing the UI thread.
     */
    private fun loadCard(fetch: suspend () -> MagicCard) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cardState = CardState.Loading)
            try {
                val card = fetch()
                _uiState.value = _uiState.value.copy(selectedCard = card, cardState = CardState.Ready)
            } catch (e: CancellationException) {
                // Coroutines cancel by throwing. Swallowing it would break cancellation, so re-throw.
                throw e
            } catch (e: HttpException) {
                // Scryfall answers 404 when nothing matches, which is normal, not a failure.
                val message = if (e.code() == 404) "No matching card found" else "Scryfall error (${e.code()})"
                _uiState.value = _uiState.value.copy(cardState = CardState.Error(message))
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(
                    cardState = CardState.Error("Couldn't reach Scryfall. Check your connection.")
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cardState = CardState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    fun print() {
        val card = _uiState.value.selectedCard
        //TODO: Implement printing logic using PrinterService
    }
}