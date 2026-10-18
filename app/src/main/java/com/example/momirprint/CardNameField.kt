package com.example.momirprint

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

/**
 * A text field for typing a card name, with a dropdown of autocomplete suggestions underneath.
 * Used by both the "Search & filter" tab and the "Tokens" tab.
 *
 * It deliberately knows nothing about PrintUIState or PrintActions: everything it shows comes in
 * as a parameter and everything the user does goes out through a callback. That is what lets two
 * screens with different state use the same composable. (It also makes it easy to preview.)
 *
 * The dropdown's open/closed state is derived from the data: the menu is open exactly when there
 * are suggestions to show. So "close the menu" really means "clear the suggestions", which the
 * caller does in response to onDismissSuggestions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardNameField(
    value: String,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onDismissSuggestions: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search by card name"
) {
    val expanded = suggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {},   // we drive `expanded` from the data, so ignore the box's own toggling
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            // menuAnchor tells the dropdown which view to position itself under. PrimaryEditable
            // means "this is a field the user types into", so tapping it doesn't toggle the menu.
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            // Show a "search" key on the keyboard and run the search when it's pressed.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissSuggestions
        ) {
            suggestions.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSuggestionSelected(name) }
                )
            }
        }
    }
}
