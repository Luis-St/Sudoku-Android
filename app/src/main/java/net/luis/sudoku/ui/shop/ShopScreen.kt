package net.luis.sudoku.ui.shop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R

/**
 * UI item 10: the shop exists as a destination but is deliberately empty for now.
 *
 * The machinery it will need is already in place - `BoardThemeCatalog` carries a price and an
 * owned-by-default flag per theme, `SettingsStore.setBoardThemeId` persists the selection, and the board
 * only ever reads `LocalBoardPalette` - so filling this in later is a catalog entry plus an unlock
 * check, not a rewrite.
 */
@Composable
fun ShopScreen(modifier: Modifier = Modifier) {
	Column(
		modifier = modifier.fillMaxSize().padding(32.dp),
		verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Icon(
			painter = painterResource(R.drawable.ic_shop),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(56.dp)
		)
		Text(
			text = stringResource(R.string.shop_empty_title),
			style = MaterialTheme.typography.titleMedium,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(top = 16.dp)
		)
		Text(
			text = stringResource(R.string.shop_empty_body),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(top = 8.dp)
		)
	}
}
