package com.example.momirprint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The "Momir" tab: pick a mana value, draw a random creature with exactly that mana value.
 * There are deliberately no filters here; the query is fixed by ScryfallQueryBuilder.momir().
 */
@Composable
fun MomirPanel(
    manaValue: Int,
    onManaValueChange: (Int) -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Draws a random creature of the chosen mana value, like casting off Momir Vig. " +
                "Filters are ignored in this mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // [ - ]   4   [ + ]
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { onManaValueChange(manaValue - 1) },
                enabled = manaValue > 0
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Lower mana value")
            }
            Column(
                Modifier.widthIn(min = 110.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("$manaValue", style = MaterialTheme.typography.displayMedium)
                Text("Mana value", style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalIconButton(
                onClick = { onManaValueChange(manaValue + 1) },
                enabled = manaValue < MAX_MANA_VALUE
            ) {
                Icon(Icons.Default.Add, contentDescription = "Raise mana value")
            }
        }

        // Sliders work in Floats, so convert in and out. `steps` is the number of stops
        // *between* the two ends, hence MAX - 1 to land on every whole number from 0 to 16.
        Slider(
            value = manaValue.toFloat(),
            onValueChange = { onManaValueChange(it.roundToInt()) },
            valueRange = 0f..MAX_MANA_VALUE.toFloat(),
            steps = MAX_MANA_VALUE - 1
        )

        OutlinedButton(onClick = onDraw, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Casino, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Draw a creature")
        }
    }
}
