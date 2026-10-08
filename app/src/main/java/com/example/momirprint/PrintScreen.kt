package com.example.momirprint

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.momirprint.ui.theme.MomirPrintTheme

// ---------------------------------------------------------------------------------------------
// The Print screen: header, two tabs (Search & filter | Momir), a card preview, a Print button,
// and the filter bottom sheet.
//
// The screen is split in two layers, which is the standard Compose pattern ("state hoisting"):
//
//   PrintScreen          stateful: knows about the ViewModel, reads its state, wires up callbacks
//   PrintScreenContent   stateless: just draws whatever state it is given and reports taps
//
// Only the top layer touches the ViewModel, so the stateless layer can be shown in Android
// Studio's @Preview pane with fake data (no network, no Bluetooth, no ViewModel needed).
// ---------------------------------------------------------------------------------------------

/**
 * Every "something was tapped" callback the Print screen can raise, bundled into one object.
 *
 * Each parameter has a default of `{}` (do nothing), so `PrintActions()` is a ready-made
 * "no-op" set of callbacks for previews. Real wiring to the ViewModel is in rememberPrintActions.
 */
class PrintActions(
    val onModeSelected: (QueryMode) -> Unit = {},
    val onSearchTextChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onDrawRandom: () -> Unit = {},
    val onMomirManaValueChange: (Int) -> Unit = {},
    val onDrawMomir: () -> Unit = {},
    val onPrint: () -> Unit = {},
    val filters: FilterActions = FilterActions()
)

/**
 * `viewModel::setMode` is a function reference: it turns an existing function into a value that
 * can be passed around, so it fits wherever a `(QueryMode) -> Unit` is expected.
 *
 * `remember(viewModel) { ... }` builds the PrintActions once and reuses it across recompositions
 * (Compose re-runs composable functions whenever state changes, so anything expensive or
 * identity-sensitive is wrapped in `remember`).
 */
@Composable
private fun rememberPrintActions(viewModel: PrintViewModel): PrintActions = remember(viewModel) {
    PrintActions(
        onModeSelected = viewModel::setMode,
        onSearchTextChange = viewModel::setSearchText,
        onSearch = viewModel::search,
        onDrawRandom = viewModel::drawRandom,
        onMomirManaValueChange = viewModel::setMomirManaValue,
        onDrawMomir = viewModel::drawMomir,
        onPrint = viewModel::print,
        filters = FilterActions(
            onToggleType = viewModel::toggleCardType,
            onToggleColor = viewModel::toggleColor,
            onToggleColorIdentity = viewModel::toggleColorIdentity,
            onColorMatchChange = viewModel::setColorMatch,
            onMinManaValueChange = viewModel::setMinManaValue,
            onMaxManaValueChange = viewModel::setMaxManaValue,
            onToggleRarity = viewModel::toggleRarity,
            onFormatStatusChange = viewModel::setFormatStatus,
            onCustomQueryChange = viewModel::setCustomQuery,
            onReset = viewModel::resetFilters
        )
    )
}

/** Stateful entry point, called from the navigation graph in MainActivity. */
@Composable
fun PrintScreen(viewModel: PrintViewModel, onSettingsClick: () -> Unit) {
    // `by` unwraps the MutableState: reading `state` subscribes this composable to changes,
    // so it redraws whenever the ViewModel assigns a new PrintUIState.
    val state by viewModel.uiState
    val actions = rememberPrintActions(viewModel)

    PrintScreenContent(state = state, actions = actions, onSettingsClick = onSettingsClick)
}

@Composable
fun PrintScreenContent(
    state: PrintUIState,
    actions: PrintActions,
    onSettingsClick: () -> Unit
) {
    // Whether the filter sheet is showing is pure UI state (nothing the rest of the app cares
    // about), so it lives here instead of in the ViewModel. rememberSaveable keeps it across
    // screen rotation; plain `remember` would reset it.
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        PrintHeader(onSettingsClick = onSettingsClick)
        Spacer(Modifier.height(16.dp))

        PrintModeTabs(selectedMode = state.queryMode, onModeSelected = actions.onModeSelected)
        Spacer(Modifier.height(14.dp))

        // `when` over an enum must cover every case; add a third tab and the compiler tells you
        // everything that needs updating.
        when (state.queryMode) {
            QueryMode.SEARCH_FILTER -> SearchFilterPanel(
                state = state,
                actions = actions,
                onOpenFilters = { showFilterSheet = true }
            )

            QueryMode.MOMIR -> MomirPanel(
                manaValue = state.momirManaValue,
                onManaValueChange = actions.onMomirManaValueChange,
                onDraw = actions.onDrawMomir
            )
        }

        // The preview takes all the height the controls above and the button below leave over.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            CardPreview(
                card = state.selectedCard,
                cardState = state.cardState,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 260.dp)
                    .aspectRatio(5f / 7f, matchHeightConstraintsFirst = true)
            )
        }
        // TODO(design): the mockup has a caption under the card ("Best match for ...",
        //  "Random card matching your filters"). PrintUIState would need a field for it.

        state.printMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        }
        Button(
            onClick = actions.onPrint,
            enabled = state.cardState == CardState.Ready && !state.isPrinting,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Icon(Icons.Default.Print, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Print card")
        }
        Spacer(Modifier.height(16.dp))
    }

    // Composables can be conditional: when the flag is false the sheet simply isn't in the tree.
    if (showFilterSheet) {
        FilterSheet(
            filters = state.filters,
            actions = actions.filters,
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun PrintHeader(onSettingsClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Print a card", style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

/** The text shown on each tab. Kept in the UI layer so the ViewModel's enum stays plain. */
private fun QueryMode.label(): String = when (this) {
    QueryMode.SEARCH_FILTER -> "Search & filter"
    QueryMode.MOMIR -> "Momir"
}

@Composable
private fun PrintModeTabs(selectedMode: QueryMode, onModeSelected: (QueryMode) -> Unit) {
    val options = QueryMode.entries

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {} // hides the default check mark
            ) {
                Text(text = mode.label())
            }
        }
    }
}

@Composable
fun CardPreview(card: MagicCard?, cardState: CardState, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier
            .clip(shape)
            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when (cardState) {
            CardState.Empty -> Text(
                "Search for a card or draw a random one",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            CardState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(10.dp))
                Text("Asking Scryfall…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is CardState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(cardState.message, textAlign = TextAlign.Center)
            }

            CardState.Ready -> if (card != null) {
                val url = card.imageUrl()
                if (url != null) {
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        loading = { CircularProgressIndicator() },
error = { CardText(card) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // TEMPORARY DEBUG: imageUrl() was null. Restore `CardText(card)` once fixed.
                    Text(
                        "DEBUG: imageUrl() is null\nimage_uris keys=${card.image_uris.keys}\nfaces=${card.card_faces.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Plain-text fallback used when card art is unavailable.
 * TODO: double-faced cards keep their text in card.card_faces, so these top-level fields are
 *  blank for them. PrintFormatter only handles layout == "normal" for the same reason.
 */
@Composable
private fun CardText(card: MagicCard) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(card.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(card.mana_cost, style = MaterialTheme.typography.bodyMedium)
        }
        Text(card.type_line, style = MaterialTheme.typography.labelMedium)
        Text(card.oracle_text, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        if (card.power.isNotEmpty() || card.toughness.isNotEmpty()) {
            Text(
                "${card.power}/${card.toughness}",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---- Previews: pick one in Android Studio's Split/Design view to see it without an emulator ----

private val previewCard = MagicCard(
    name = "Serra Angel",
    layout = "normal",
    mana_cost = "{3}{W}{W}",
    type_line = "Creature — Angel",
    oracle_text = "Flying, vigilance",
    power = "4",
    toughness = "4"
)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun PrintScreenSearchPreview() {
    MomirPrintTheme {
        PrintScreenContent(
            state = PrintUIState(
                filters = CardFilters(types = setOf("creature"), colors = setOf('W', 'U'), maxManaValue = 4),
                selectedCard = previewCard,
                cardState = CardState.Ready
            ),
            actions = PrintActions(),
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun PrintScreenMomirPreview() {
    MomirPrintTheme {
        PrintScreenContent(
            state = PrintUIState(queryMode = QueryMode.MOMIR, cardState = CardState.Loading),
            actions = PrintActions(),
            onSettingsClick = {}
        )
    }
}
