package com.example.momirprint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.momirprint.ui.theme.MomirPrintTheme

/**
 * Everything under the tabs on the "Tokens" tab:
 *   [ card name field ][search]      <- the same autocomplete field as the Search tab
 *   [ token dropdown            v ]  <- the tokens that card makes
 *
 * Picking a token shows it in the shared preview below, ready for the Print button. The
 * panel itself holds no state: it draws what it is given and reports taps through `actions`.
 */
@Composable
fun TokenPanel(
    state: PrintUIState,
    actions: PrintActions,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CardNameField(
                value = state.tokenSearchText,
                suggestions = state.suggestions,
                onValueChange = actions.onTokenSearchTextChange,
                onSearch = actions.onTokenSearch,
                onSuggestionSelected = actions.onTokenSourceSuggestionSelected,
                onDismissSuggestions = actions.onDismissSuggestions,
                modifier = Modifier.weight(1f),
                placeholder = "Card that makes the token"
            )
            FilledTonalIconButton(onClick = actions.onTokenSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }

        TokenDropdown(
            source = state.tokenSource,
            selected = state.selectedToken,
            onSelected = actions.onTokenSelected,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * A read-only dropdown listing the tokens made by `source`.
 *
 * It's the same ExposedDropdownMenuBox as the autocomplete field, with one difference: the
 * text field is read-only (the user picks, they don't type), so tapping it toggles the menu.
 * Because opening the menu is purely a UI matter, `expanded` is local state here. Contrast with
 * CardNameField, where the menu's open state is derived from the ViewModel's suggestions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenDropdown(
    source: MagicCard?,
    selected: RelatedCard?,
    onSelected: (RelatedCard) -> Unit,
    modifier: Modifier = Modifier
) {
    // remember(source) recomputes the list only when `source` changes, not on every redraw.
    val tokens = remember(source) { source?.tokenParts().orEmpty() }
    val enabled = tokens.isNotEmpty()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val showMenu = expanded && enabled

    // Shown in the field while nothing is selected, and tells the user why it might be disabled.
    val hint = when {
        source == null -> "Search a card first"
        tokens.isEmpty() -> "No tokens listed for this card"
        else -> "Choose a token"
    }

    ExposedDropdownMenuBox(
        expanded = showMenu,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},   // read-only: the only way to change it is the menu
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            placeholder = { Text(hint) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMenu) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { expanded = false }
        ) {
            tokens.forEach { token ->
                DropdownMenuItem(
                    // The stub has no power/toughness, but its type line ("Token Creature —
                    // Soldier") is enough to tell most tokens apart.
                    text = {
                        Column {
                            Text(token.name)
                            Text(
                                token.type_line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelected(token)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Android Studio's Split/Design view renders this without an emulator or any network.
@Preview(showBackground = true)
@Composable
private fun TokenPanelPreview() {
    val soldier = RelatedCard(id = "1", component = "token", name = "Soldier", type_line = "Token Creature — Soldier")
    val treasure = RelatedCard(id = "2", component = "token", name = "Treasure", type_line = "Token Artifact — Treasure")
    MomirPrintTheme {
        TokenPanel(
            state = PrintUIState(
                queryMode = QueryMode.TOKEN,
                tokenSearchText = "Example Card",
                tokenSource = MagicCard(name = "Example Card", all_parts = listOf(soldier, treasure)),
                selectedToken = soldier
            ),
            actions = PrintActions(),
            modifier = Modifier.padding(20.dp)
        )
    }
}
