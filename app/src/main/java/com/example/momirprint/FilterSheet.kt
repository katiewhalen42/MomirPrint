@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.momirprint

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
    "creature", "instant", "sorcery", "artifact", "enchantment", "planeswalker", "land"
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

            FilterGroup("Format") {
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
 * One chip per format. Tapping cycles that format's rule:
 *   no rule -> Legal -> Not Legal -> Banned -> Restricted -> no rule
 *
 * TODO(design): this is a placeholder. The mockup still shows a plain "Legal in" row, which
 *  can't express banned / restricted / not legal. Options: keep this cycling chip, or an
 *  "Add format rule" button that opens a format + status picker and shows `Modern · Banned`
 *  chips here.
 */
@Composable
private fun FormatRules(
    formats: Map<String, FormatStatus>,
    onStatusChange: (String, FormatStatus?) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        formatOptions.forEach { format ->
            val status = formats[format]
            FilterChip(
                selected = status != null,
                onClick = { onStatusChange(format, status.next()) },
                label = {
                    Text(if (status == null) format.capitalized() else "${format.capitalized()}: ${status.label}")
                }
            )
        }
    }
    Text(
        "Tap a format to cycle: any, legal, not legal, banned, restricted.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * An extension function on a *nullable* FormatStatus. null means "no rule", so a single `when`
 * covers the whole cycle, including wrapping back around to null.
 */
private fun FormatStatus?.next(): FormatStatus? = when (this) {
    null -> FormatStatus.LEGAL
    FormatStatus.LEGAL -> FormatStatus.NOT_LEGAL
    FormatStatus.NOT_LEGAL -> FormatStatus.BANNED
    FormatStatus.BANNED -> FormatStatus.RESTRICTED
    FormatStatus.RESTRICTED -> null
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
