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
 * What it needs on the client is in place: `AppThemeCatalog` carries a price and an owned-by-default flag
 * per theme, `SettingsStore.setThemeId` persists the selection, and every screen reads named roles that
 * `SudokuAndroidTheme` fills from the selected theme - chrome, board, accents, region tints and shapes
 * alike. So a new look is a catalog entry.
 *
 * What is *not* in place, and is deliberately not faked here, is ownership. Nothing on the device and
 * nothing on the server records a purchase yet, and it cannot be a local spend: `CurrencyService.sync`
 * only ever raises a balance (`delta = max(0, accepted - current)`), so a locally deducted price would be
 * handed straight back on the next connect. Buying has to be a server endpoint that writes the ledger row
 * and the entitlement in one transaction, with the client adopting the balance it returns.
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
