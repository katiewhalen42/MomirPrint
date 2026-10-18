package com.example.momirprint

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

enum class QueryMode {
    SEARCH_FILTER,
    MOMIR,
    TOKEN
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
    val searchText: String = "",
    val filters: CardFilters = CardFilters(),
    val momirManaValue: Int = 4,
    val suggestions: List<String> = emptyList(),
    val selectedCard: MagicCard? = null,
    val cardState: CardState = CardState.Empty,
    val isPrinting: Boolean = false,
    val printMessage: String? = null,
    // ---- Tokens tab ----
    // Its own text, so searching on one tab doesn't leave stale text in the other tab's field.
    val tokenSearchText: String = "",
    // The card whose tokens the dropdown lists. Separate from selectedCard on purpose:
    // selectedCard is the shared preview, and picking a card here must not replace it.
    val tokenSource: MagicCard? = null,
    // The dropdown's current choice. This is the thin stub from all_parts (name, type line, uri);
    // the full token card is what ends up in selectedCard.
    val selectedToken: RelatedCard? = null
    //val customToken: CustomTokenDraft = CustomTokenDraft()
)

private fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

class PrintViewModel (private val settingsRepository: SettingsRepository,
                      private val apiService: ApiService,
                      private val printerService: PrinterService,
                      private val formatter: PrintFormatter) : ViewModel() {

    private val _uiState = mutableStateOf(PrintUIState())
    val uiState = _uiState
    private var suggestJob: Job? = null

    fun setMode(queryMode: QueryMode) {
        // Both name fields share one suggestions list, so close it when the tab changes;
        // otherwise a menu opened on one tab would reappear under the other tab's field.
        dismissSuggestions()
        _uiState.value = _uiState.value.copy(queryMode = queryMode)
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

    fun toggleColorIdentity(color: Char) = updateFilters { f ->
        val colorId = when {
            // Colorless is exclusive: picking it clears the real colors, and vice versa.
            color == 'C' -> if ('C' in f.colorId) emptySet<Char>() else setOf('C')
            else -> (f.colorId - 'C').toggled(color)
        }
        f.copy(colorId = colorId)
    }

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

    fun setSearchText(searchText: String) {
        _uiState.value = _uiState.value.copy(searchText = searchText)
        requestSuggestions(searchText)
    }

    fun setTokenSearchText(text: String) {
        _uiState.value = _uiState.value.copy(tokenSearchText = text)
        requestSuggestions(text)
    }

    /** Debounced autocomplete, shared by both name fields (only one is on screen at a time). */
    private fun requestSuggestions(text: String) {
        suggestJob?.cancel()
        val query = text.trim()
        if (query.length < 2) {
            _uiState.value = _uiState.value.copy(suggestions = emptyList())
            return
        }
        suggestJob = viewModelScope.launch {
            // Debounce: wait for a pause in typing. If another keystroke arrives during these
            // 300 ms, the line above cancels this coroutine, delay() throws a CancellationException,
            // and the network request below is never sent. Only the last keystroke of a burst
            // gets as far as the API. (Scryfall asks clients to space out their requests.)
            delay(300)
            try {
                val names = apiService.getCardAutocomplete(query).data
                _uiState.value = _uiState.value.copy(suggestions = names)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(suggestions = emptyList())
            }
        }
    }

    fun selectSuggestion(name: String) {
        _uiState.value = _uiState.value.copy(searchText = name)
        dismissSuggestions()
        search()                                   // reads searchText from state, which is now the exact name
    }

    fun dismissSuggestions() {
        suggestJob?.cancel()
        _uiState.value = _uiState.value.copy(suggestions = emptyList())
    }

    // ---- Fetching a card ------------------------------------------------------------------

    /** Name search. Scryfall's `fuzzy` lookup returns the single best match. */
    fun search() {
        val name = _uiState.value.searchText.trim()
        if (name.isEmpty()) return
        // Close the dropdown and cancel any lookup still in flight. Otherwise a slow autocomplete
        // response could arrive after the card loads and pop the menu open again.
        dismissSuggestions()
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
     *
     * `onLoaded` decides what to do with the card once it arrives. By default it goes in the
     * preview (showCard). The Tokens tab overrides that to store the card somewhere else.
     * It comes first and has a default, so the existing `loadCard { ... }` calls still compile:
     * a trailing lambda always binds to the LAST parameter, which is `fetch`.
     */
    private fun loadCard(
        onLoaded: (MagicCard) -> Unit = { showCard(it) },
        fetch: suspend () -> MagicCard
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cardState = CardState.Loading)
            try {
                onLoaded(fetch())
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

    /** Puts a fetched card in the shared preview. What every tab does when a lookup succeeds. */
    private fun showCard(card: MagicCard) {
        _uiState.value = _uiState.value.copy(selectedCard = card, cardState = CardState.Ready)
    }

    // ---- Tokens tab -----------------------------------------------------------------------
    // Two steps: (1) look up the card that makes tokens, (2) pick one of its tokens.

    /** The user picked an autocomplete suggestion in the Tokens tab's name field. */
    fun selectTokenSourceSuggestion(name: String) {
        _uiState.value = _uiState.value.copy(tokenSearchText = name)
        searchTokenSource()
    }

    /**
     * Step 1: look up the card. Unlike the other tabs, the result does NOT go in the preview:
     * it goes in tokenSource, which fills the dropdown. The preview only changes in step 2.
     */
    fun searchTokenSource() {
        val name = _uiState.value.tokenSearchText.trim()
        if (name.isEmpty()) return
        dismissSuggestions()
        loadCard(onLoaded = { onTokenSourceLoaded(it) }) { apiService.getCardByName(name) }
    }

    private fun onTokenSourceLoaded(source: MagicCard) {
        val state = _uiState.value
        _uiState.value = state.copy(
            tokenSource = source,
            selectedToken = null,
            // loadCard switched cardState to Loading before the fetch. We aren't replacing the
            // preview, so restore it to what it was showing before the lookup.
            cardState = if (state.selectedCard != null) CardState.Ready else CardState.Empty
        )
        // A card with exactly one token needs no choosing, so select it for the user.
        // singleOrNull() gives the element if there is exactly one, and null for zero or several.
        source.tokenParts().singleOrNull()?.let { selectToken(it) }
    }

    /**
     * Step 2: the user picked a token. The stub in all_parts has no image or power/toughness, so
     * this fetches the full card from the stub's own URL (the "lazy fetch") into the preview.
     */
    fun selectToken(token: RelatedCard) {
        _uiState.value = _uiState.value.copy(selectedToken = token)
        loadCard { apiService.getCardByUri(token.uri) }
    }

    fun print() {
        val card = _uiState.value.selectedCard ?: return
        if (_uiState.value.isPrinting) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPrinting = true, printMessage = null)

            val result: Result<Unit> = try {
                when (settingsRepository.printMode.first()) {
                    PrintMode.TEXT -> {
                        val text = formatter.formatCardText(card)
                        if (text.isBlank()) Result.failure(Exception("This card layout can't be printed as text yet"))
                        else printerService.printText(text)
                    }
                    PrintMode.IMAGE -> {
                        val faces = formatter.loadCardBitmapsForPrint(card)
                        val raw = formatter.combineBitmapsVertically(faces)
                        if (raw == null) {
                            Result.failure(Exception("No printable image is available for this card"))
                        } else {
                            // CPU-bound pixel crunching -> Default; blocking Bluetooth write -> IO.
                            val bw = withContext(Dispatchers.Default) { Dither.toPrinterBitmap(raw) }
                            Log.d("CardImage", "loaded=${raw.width}x${raw.height} dithered=${bw.width}x${bw.height}")
                            printerService.printImage(bw)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e                       // same rule as in loadCard()
            } catch (e: Exception) {
                Result.failure(e)
            }

            _uiState.value = _uiState.value.copy(
                isPrinting = false,
                printMessage = result.fold(
                    onSuccess = { "Sent to printer" },
                    onFailure = { it.message ?: "Print failed" }
                )
            )
        }
    }
}