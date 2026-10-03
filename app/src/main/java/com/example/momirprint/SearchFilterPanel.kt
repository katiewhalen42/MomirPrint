package com.example.momirprint

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Everything under the tabs on the "Search & filter" tab:
 *   [ name search field ][search]
 *   [Filters] [active filter chips ...........]
 *   [ Random card from filters ]
 *
 * The filter controls themselves live in the bottom sheet (FilterSheet.kt); this panel only
 * shows a summary of what is currently active.
 */
@Composable
fun SearchFilterPanel(
    state: PrintUIState,
    actions: PrintActions,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = activeFilterChips(state.filters, actions.filters)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.searchText,
                onValueChange = actions.onSearchTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search by card name") },
                singleLine = true,
                // Show a "search" key on the keyboard and run the search when it's pressed.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.onSearch() })
            )
            FilledTonalIconButton(onClick = actions.onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onOpenFilters,
                label = { Text(if (chips.isEmpty()) "Filters" else "Filters (${chips.size})") },
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, Modifier.size(18.dp)) }
                // TODO(design): the mockup uses a pill with a round count badge.
            )
            ActiveFilterChips(chips, Modifier.weight(1f))
        }

        OutlinedButton(onClick = actions.onDrawRandom, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Casino, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Random card from filters")
        }
    }
}

/** A horizontally scrolling row of removable chips, one per active filter. */
@Composable
private fun ActiveFilterChips(chips: List<ActiveFilterChip>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) {
        Text(
            "No filters: any card",
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEach { chip ->
            InputChip(
                selected = true,
                onClick = chip.onRemove,
                label = { Text(chip.label) },
                trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${chip.label} filter", Modifier.size(16.dp))
                }
            )
        }
    }
}

/** One chip in the summary row: what to display, and what tapping its X does. */
class ActiveFilterChip(val label: String, val onRemove: () -> Unit)

/**
 * Turns the current filters into the list of chips to show. A plain function with no Android
 * types, so it can be unit-tested with ordinary JUnit, like ScryfallQueryBuilder.
 *
 * Mirrors ScryfallQueryBuilder: a custom query overrides every other filter, so when one is
 * set it is the only chip.
 */
fun activeFilterChips(filters: CardFilters, actions: FilterActions): List<ActiveFilterChip> {
    if (filters.customQuery.isNotBlank()) {
        return listOf(ActiveFilterChip("Custom query") { actions.onCustomQueryChange("") })
    }

    val chips = mutableListOf<ActiveFilterChip>()

    filters.types.sorted().forEach { type ->
        chips += ActiveFilterChip(type.capitalized()) { actions.onToggleType(type) }
    }

    if (filters.colors.isNotEmpty()) {
        val letters = filters.colors.sortedBy { "WUBRGC".indexOf(it) }.joinToString(" ")
        val label = if ('C' in filters.colors) "Colorless" else "${filters.colorMatch.label} $letters"
        // Toggling every selected color off is how "clear colors" is expressed with the
        // ViewModel's existing API.
        chips += ActiveFilterChip(label) { filters.colors.forEach(actions.onToggleColor) }
    }

    if (filters.minManaValue > 0 || filters.maxManaValue < MAX_MANA_VALUE) {
        chips += ActiveFilterChip(manaValueLabel(filters.minManaValue, filters.maxManaValue)) {
            actions.onMinManaValueChange(0)
            actions.onMaxManaValueChange(MAX_MANA_VALUE)
        }
    }

    filters.rarities.sorted().forEach { rarity ->
        chips += ActiveFilterChip(rarity.capitalized()) { actions.onToggleRarity(rarity) }
    }

    filters.formats.entries.sortedBy { it.key }.forEach { (format, status) ->
        chips += ActiveFilterChip("${format.capitalized()} · ${status.label}") {
            actions.onFormatStatusChange(format, null)
        }
    }

    return chips
}

/** "Any", "MV ≤ 4", "MV ≥ 2", "MV 3" or "MV 2–6". The top of the slider means "no upper limit". */
fun manaValueLabel(min: Int, max: Int): String = when {
    min == 0 && max >= MAX_MANA_VALUE -> "Any"
    max >= MAX_MANA_VALUE -> "MV ≥ $min"
    min == 0 -> "MV ≤ $max"
    min == max -> "MV $min"
    else -> "MV $min–$max"
}
