package com.example.momirprint

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.momirprint.ui.theme.MomirPrintTheme

//TODO: Add the RandomPanel

@Composable
fun RandomScreen(viewModel: PrintViewModel = viewModel(), modifier: Modifier = Modifier) {
	val randomCard = viewModel.uiState.value.selectedCard ?: MagicCard()

	Column(
		verticalArrangement = Arrangement.spacedBy(8.dp),
		modifier = modifier
	) {
		/*TestCardDisplay(randomCard)*/

		Button(
			onClick = {
				viewModel.reroll()
			}
		) {
			Text("Fetch Random Card")
		}
	}
}

@Composable
fun PrintScreenContent(
	state: PrintUIState,
	onModeSelected: (QueryMode) -> Unit,
	onSettingsClick: () -> Unit
) {
	Column(Modifier.fillMaxSize().padding(20.dp)) {
		Row(
			Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text("Print a card", style = MaterialTheme.typography.titleLarge)
			IconButton(onClick = onSettingsClick) {
				Icon(Icons.Default.Settings, contentDescription = "Settings")
			}
		}
		Spacer(Modifier.height(16.dp))
		PrintModeTabs(selectedMode = state.queryMode, onModeSelected = onModeSelected)
	}
}

@Preview(showBackground = true)
@Composable
fun PrintScreenPreview() {
	MomirPrintTheme {
		PrintScreenContent(state = PrintUIState(), onModeSelected = { }, onSettingsClick = { })
	}
}

@Composable
fun PrintScreen(viewModel: PrintViewModel, onSettingsClick: () -> Unit) {
	val state by viewModel.uiState

	Column(
		Modifier
			.fillMaxSize()
			.padding(20.dp)
	) {
		Row(
			Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text("Print a card", style = MaterialTheme.typography.titleLarge)
			IconButton(onClick = onSettingsClick) {
				Icon(Icons.Default.Settings, contentDescription = "Settings")
			}
		}

		Spacer(Modifier.height(16.dp))

		PrintModeTabs(
			selectedMode = state.queryMode,
			onModeSelected = { viewModel.setMode(it) }
		)

		when (state.queryMode) {
			QueryMode.RANDOM -> RandomScreen(viewModel = viewModel)
			QueryMode.SEARCH -> SearchScreen(viewModel = viewModel)
		}

		Spacer(Modifier.weight(1f))
		CardPreview(card = state.selectedCard, cardState = state.cardState)
		Spacer(Modifier.height(12.dp))

		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			Button(onClick = { viewModel.print() }) {
				Icon(Icons.Default.Print, contentDescription = "Print", modifier = Modifier.weight(1f))
				Text("Print card")
			}
			OutlinedIconButton(onClick = { viewModel.reroll() }) {
				Icon(Icons.Default.Refresh, contentDescription = "Reroll", modifier = Modifier.weight(1f))
			}
		}
	}
}

@Composable
fun PrintModeTabs(selectedMode: QueryMode, onModeSelected: (QueryMode) -> Unit) {
	val options = QueryMode.values().toList()

	SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
		options.forEachIndexed { index, mode ->
			SegmentedButton(
				selected = mode == selectedMode,
				onClick = { onModeSelected(mode) },
				shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
				icon = {}
			) {
				Text(text = mode.name)
			}
		}
	}
}

@Composable
fun CardPreview(card: MagicCard?, cardState: CardState) {
	Box(
		Modifier
			.width(216.dp)
			.aspectRatio(5f / 7f)
			.clip(RoundedCornerShape(14.dp))
			.border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(14.dp)),
		contentAlignment = Alignment.Center
	) {
		when (cardState) {
			CardState.Empty -> {
				Text("Tap Draw to get a card", color = MaterialTheme.colorScheme.onSurfaceVariant)
			}

			CardState.Loading -> {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					CircularProgressIndicator()
					Text("Asking Scryfall...")
				}
			}

			is CardState.Error -> {
				Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
				Text("Error: ${cardState.message}", textAlign = TextAlign.Center)
			}

			CardState.Ready -> card?.let { /*TODO*/ }
		}
	}
}

@Composable
fun SearchScreen(viewModel: PrintViewModel) {

}

@Composable
fun RandomPanel(state: PrintUIState, viewModel: PrintViewModel) {
	RandomModeTabs(selectedMode = state.randomMode, onModeSelected = { viewModel.setRandomMode(it) })

	when(state.randomMode) {
		RandomMode.FILTERS -> { FilterSection(state = state, viewModel = viewModel) }
	}
}

@Composable
fun RandomModeTabs(selectedMode: RandomMode, onModeSelected: (RandomMode) -> Unit) {
	val options = RandomMode.values().toList()

	SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
		options.forEachIndexed { index, mode ->
			SegmentedButton(
				selected = mode == selectedMode,
				onClick = { onModeSelected(mode) },
				shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
				icon = {}
			) {
				Text(text = mode.name)
			}
		}
	}
}

@Composable
fun FilterSection(state: PrintUIState, viewModel: PrintViewModel) {
	Text("Filter by Type", style = MaterialTheme.typography.labelSmall)
	FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {

	}
}