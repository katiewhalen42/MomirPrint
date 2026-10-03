@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.momirprint

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.momirprint.ui.theme.MomirPrintTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// The filter bottom sheet. Opened from the "Filters" button on the Search & filter tab.
//
//   Card type        multi-select chips
//   Color            W U B R G C toggles + Includes / Exactly / At most
//   Mana value       min and max sliders
//   More filters     (collapsed)  rarity, format rules
//   Advanced         (collapsed)  raw Scryfall query, overrides everything above
//
// Nothing here stores state itself. Every control shows what is in `filters` and reports taps
// through `actions`; the ViewModel updates the filters and the sheet redraws.
// ---------------------------------------------------------------------------------------------

/** Every callback the sheet can raise. Defaults are no-ops so `FilterActions()` works in previews. */
class FilterActions(
    val onToggleType: (String) -> Unit = {},
    val onToggleColor: (Char) -> Unit = {},
    val onColorMatchChange: (ColorMatch) -> Unit = {},
    val onMinManaValueChange: (Int) -> Unit = {},
    val onMaxManaValueChange: (Int) -> Unit = {},
    val onToggleRarity: (String) -> Unit = {},
    val onFormatStatusChange: (String, FormatStatus?) -> Unit = { _, _ -> },
    val onCustomQueryChange: (String) -> Unit = {},
    val onReset: () -> Unit = {}
)

// What the sheet offers. The lower-case strings are the words Scryfall expects in queries.
private val cardTypeOptions = listOf(
    "creature", "instant", "sorcery", "artifact", "enchantment", "planeswalker", "land", "battle"
)
private val colorOptions = listOf(
    'W' to "White", 'U' to "Blue", 'B' to "Black", 'R' to "Red", 'G' to "Green", 'C' to "Colorless"
)
private val rarityOptions = listOf("common", "uncommon", "rare", "mythic")
// Vintage is included because "restricted" only exists there (and in a few other formats).
private val formatOptions = listOf(
    "standard", "pioneer", "modern", "legacy", "vintage", "commander", "pauper"
)

/** "creature" -> "Creature" */
internal fun String.capitalized(): String = replaceFirstChar { it.uppercase() }

/**
 * The sheet container. ModalBottomSheet handles the dimmed backdrop, the slide-up animation and
 * swipe-down-to-dismiss; we supply the contents.
 */
@Composable
fun FilterSheet(filters: CardFilters, actions: FilterActions, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        FilterSheetContent(
            filters = filters,
            actions = actions,
            onDone = {
                // hide() is a suspend function that plays the slide-down animation, so it has to
                // run in a coroutine. Only remove the sheet from the screen once it has finished.
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onDismiss()
                }
            }
        )
    }
}

/** The sheet's contents, separate from the container so it can be previewed on its own. */
@Composable
fun FilterSheetContent(
    filters: CardFilters,
    actions: FilterActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filters", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = actions.onReset) { Text("Reset") }
        }

        FilterGroup("Card type") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cardTypeOptions.forEach { type ->
                    FilterChip(
                        selected = type in filters.types,
                        onClick = { actions.onToggleType(type) },
                        label = { Text(type.capitalized()) }
                    )
                }
            }
        }

        FilterGroup("Color") {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colorOptions.forEach { (code, name) ->
                    FilterChip(
                        selected = code in filters.colors,
                        onClick = { actions.onToggleColor(code) },
                        label = { Text(code.toString()) },
                        // The chip only shows a letter, so give screen readers the full name.
                        modifier = Modifier.semantics { contentDescription = name }
                        // TODO(design): the mockup draws these as round, mana-colored pips.
                    )
                }
            }
            ColorMatchToggle(
                selected = filters.colorMatch,
                // Matching only makes sense with at least one real color chosen.
                enabled = filters.colors.isNotEmpty() && 'C' !in filters.colors,
                onSelected = actions.onColorMatchChange
            )
        }

        FilterGroup("Mana value (${manaValueLabel(filters.minManaValue, filters.maxManaValue)})") {
            ManaValueSlider("Min", filters.minManaValue, actions.onMinManaValueChange)
            ManaValueSlider("Max", filters.maxManaValue, actions.onMaxManaValueChange)
        }

        ExpandableGroup("More filters", expanded = showMore, onToggle = { showMore = !showMore }) {
            FilterGroup("Rarity") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rarityOptions.forEach { rarity ->
                        FilterChip(
                            selected = rarity in filters.rarities,
                            onClick = { actions.onToggleRarity(rarity) },
                            label = { Text(rarity.capitalized()) }
                        )
                    }
                }
            }

            FilterGroup("Format rules") {
                FormatRules(filters.formats, actions.onFormatStatusChange)
            }
        }

        ExpandableGroup(
            "Advanced: Scryfall query",
            expanded = showAdvanced,
            onToggle = { showAdvanced = !showAdvanced }
        ) {
            OutlinedTextField(
                value = filters.customQuery,
                onValueChange = actions.onCustomQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("t:instant mv<=3 c:ur -is:reprint") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                supportingText = {
                    Text("Random draws from every card matching this query. Overrides all the filters above.")
                }
            )
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

/** A titled block of controls. `content` is a "slot": the caller decides what goes inside. */
@Composable
private fun FilterGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

/** A FilterGroup that starts collapsed, with a tappable header row and a chevron. */
@Composable
private fun ExpandableGroup(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
        }
    }
}

@Composable
private fun ColorMatchToggle(
    selected: ColorMatch,
    enabled: Boolean,
    onSelected: (ColorMatch) -> Unit
) {
    val options = ColorMatch.entries

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, match ->
            SegmentedButton(
                selected = match == selected,
                onClick = { onSelected(match) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {}
            ) {
                Text(match.label)
            }
        }
    }
}

@Composable
private fun ManaValueSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            modifier = Modifier.weight(1f),
            valueRange = 0f..MAX_MANA_VALUE.toFloat(),
            steps = MAX_MANA_VALUE - 1
        )
        // The top stop means "no limit", shown as 16+.
        Text(
            if (value >= MAX_MANA_VALUE) "$value+" else "$value",
            Modifier.width(32.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * The format section: one removable chip per rule ("Modern · Banned"), then a button that opens
 * the dialog for adding one. The rules are a Map, so a format can only have one rule at a time.
 */
@Composable
private fun FormatRules(
    formats: Map<String, FormatStatus>,
    onStatusChange: (String, FormatStatus?) -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    if (formats.isEmpty()) {
        Text(
            "No format rules: any card",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            formats.entries.sortedBy { it.key }.forEach { (format, status) ->
                val label = "${format.capitalized()} · ${status.label}"
                InputChip(
                    selected = true,
                    // Passing null to the ViewModel removes the rule.
                    onClick = { onStatusChange(format, null) },
                    label = { Text(label) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Remove $label rule", Modifier.size(16.dp))
                    }
                )
            }
        }
    }

    OutlinedButton(onClick = { showDialog = true }) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Add format rule")
    }

    if (showDialog) {
        AddFormatRuleDialog(
            existing = formats,
            onAdd = { format, status ->
                onStatusChange(format, status)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Pick a format, pick a status, tap Add. The dialog keeps its two choices in its own local state
 * and only reports them to the ViewModel when Add is pressed, so Cancel leaves everything alone.
 *
 * "Restricted" is only offered for Vintage, the only format in our list that has restricted cards.
 */
@Composable
private fun AddFormatRuleDialog(
    existing: Map<String, FormatStatus>,
    onAdd: (String, FormatStatus) -> Unit,
    onDismiss: () -> Unit
) {
    var format by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf<FormatStatus?>(null) }

    // Copy to local vals so Kotlin can smart-cast them from "String?" to "String" below.
    val chosenFormat = format
    val chosenStatus = status
    val currentRule = chosenFormat?.let { existing[it] }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add format rule") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Format", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    formatOptions.forEach { option ->
                        FilterChip(
                            selected = option == chosenFormat,
                            onClick = {
                                format = option
                                // A previously chosen Restricted is no longer valid for this format.
                                if (option != "vintage" && status == FormatStatus.RESTRICTED) status = null
                            },
                            label = { Text(option.capitalized()) }
                        )
                    }
                }

                Text("Status", style = MaterialTheme.typography.titleSmall)
                val statuses = FormatStatus.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    statuses.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == chosenStatus,
                            onClick = { status = option },
                            enabled = option != FormatStatus.RESTRICTED || chosenFormat == "vintage",
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = statuses.size),
                            icon = {},
                            // The default padding is too wide for four buttons in a dialog.
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(option.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }

                Text(
                    "Restricted only applies to Vintage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (chosenFormat != null && currentRule != null) {
                    Text(
                        "Replaces the current ${chosenFormat.capitalized()} · ${currentRule.label} rule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosenFormat != null && chosenStatus != null,
                onClick = { if (chosenFormat != null && chosenStatus != null) onAdd(chosenFormat, chosenStatus) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun FilterSheetContentPreview() {
    MomirPrintTheme {
        FilterSheetContent(
            filters = CardFilters(
                types = setOf("creature"),
                colors = setOf('W', 'U'),
                maxManaValue = 4,
                formats = mapOf("modern" to FormatStatus.LEGAL)
            ),
            actions = FilterActions(),
            onDone = {}
        )
    }
}
