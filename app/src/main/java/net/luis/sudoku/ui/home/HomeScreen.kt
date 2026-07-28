package net.luis.sudoku.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.luis.sudoku.R
import net.luis.sudoku.data.local.ServerConfig
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.common.StreakRestoreDialog
import net.luis.sudoku.ui.theme.ActionAccent

/**
 * The app's landing screen (UI item 5). Before this existed the app opened straight into a board, which
 * left the daily, the generator, share codes and the shop with no entry point at all.
 *
 * The daily card doubles as the streak-restore home (UI item 11): the restore is a daily-streak concern,
 * so it belongs next to the streak it repairs rather than buried in the running game.
 */
@Composable
fun HomeScreen(
	serverConfig: ServerConfig,
	onOpenDaily: () -> Unit,
	onOpenGenerator: () -> Unit,
	onOpenEnterCode: () -> Unit,
	onOpenShop: () -> Unit,
	onOpenStats: () -> Unit,
	onOpenMultiplayer: () -> Unit,
	onContinue: () -> Unit,
	modifier: Modifier = Modifier,
	viewModel: HomeViewModel = hiltViewModel()
) {
	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		CurrencyRow(viewModel.currencyBalance)

		DailyCard(
			streak = viewModel.streak,
			solvedToday = viewModel.dailySolvedToday,
			restoreAvailable = viewModel.restoreAvailable,
			onPlay = onOpenDaily,
			onRestore = viewModel::openStreakRestore,
			modifier = Modifier.padding(top = 12.dp)
		)

		// Home item 1: every entry point carries its own gradient, on a different base colour, so the list
		// reads as a set of distinct destinations rather than a stack of identical outlined rows. The accents
		// are assigned by position and mean nothing beyond "not the one above" - see ActionAccent.
		SectionCard(title = stringResource(R.string.home_section_play), modifier = Modifier.padding(top = 12.dp)) {
			Column {
				GradientButton(
					text = stringResource(R.string.home_continue),
					onClick = onContinue,
					icon = Icons.Filled.PlayArrow,
					accent = ActionAccent.TEAL,
					modifier = Modifier.fillMaxWidth()
				)
				GradientButton(
					text = stringResource(R.string.home_generator),
					onClick = onOpenGenerator,
					iconPainter = painterResource(R.drawable.ic_generator),
					accent = ActionAccent.AMBER,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				GradientButton(
					text = stringResource(R.string.home_enter_code),
					onClick = onOpenEnterCode,
					iconPainter = painterResource(R.drawable.ic_import),
					accent = ActionAccent.SKY,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
			}
		}

		SectionCard(title = stringResource(R.string.home_section_more), modifier = Modifier.padding(top = 12.dp)) {
			Column {
				GradientButton(
					text = stringResource(R.string.home_stats),
					onClick = onOpenStats,
					iconPainter = painterResource(R.drawable.ic_stats),
					accent = ActionAccent.VIOLET,
					modifier = Modifier.fillMaxWidth()
				)
				GradientButton(
					text = stringResource(R.string.home_shop),
					onClick = onOpenShop,
					iconPainter = painterResource(R.drawable.ic_shop),
					accent = ActionAccent.ROSE,
					modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
				)
				// feature-spec §9.1: nothing multiplayer-shaped exists until a server is configured.
				if (serverConfig.isConfigured && serverConfig.isAuthenticated) {
					GradientButton(
						text = stringResource(R.string.home_multiplayer),
						onClick = onOpenMultiplayer,
						iconPainter = painterResource(R.drawable.ic_multiplayer),
						accent = ActionAccent.INDIGO,
						modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
					)
				}
			}
		}
	}

	viewModel.restorePreview?.let { preview ->
		StreakRestoreDialog(
			preview = preview,
			onDismiss = viewModel::dismissRestorePreview,
			onConfirm = viewModel::restoreStreak
		)
	}

	viewModel.errorMessage?.let { message ->
		AlertDialog(
			onDismissRequest = viewModel::dismissError,
			title = { Text(stringResource(R.string.dialog_error_title)) },
			text = { Text(message) },
			confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) } }
		)
	}
}

@Composable
private fun CurrencyRow(balance: Long) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.End,
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			painter = painterResource(R.drawable.ic_currency),
			contentDescription = null,
			tint = MaterialTheme.colorScheme.secondary,
			modifier = Modifier.size(20.dp)
		)
		Text(
			text = stringResource(R.string.currency_amount_short, balance),
			style = MaterialTheme.typography.titleMedium,
			modifier = Modifier.padding(start = 6.dp)
		)
	}
}

@Composable
private fun DailyCard(
	streak: Int,
	solvedToday: Boolean,
	restoreAvailable: Boolean,
	onPlay: () -> Unit,
	onRestore: () -> Unit,
	modifier: Modifier = Modifier
) {
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(20.dp),
		color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
		// Same reason as SectionCard: an alpha-modified container resolves to no scheme role, so the
		// content color has to be stated or the text renders black on a dark card.
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
	) {
		Column(modifier = Modifier.padding(18.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(
					painter = painterResource(R.drawable.ic_daily),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(26.dp)
				)
				Text(
					text = stringResource(R.string.home_daily_title),
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.padding(start = 10.dp)
				)
			}

			Text(
				text = stringResource(R.string.daily_streak, streak),
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier.padding(top = 8.dp)
			)
			if (solvedToday) {
				Text(
					text = stringResource(R.string.home_daily_solved_today),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.secondary,
					modifier = Modifier.padding(top = 2.dp)
				)
			}

			Box(modifier = Modifier.padding(top = 14.dp)) {
				GradientButton(
					text = stringResource(if (solvedToday) R.string.home_daily_review else R.string.home_daily_play),
					onClick = onPlay,
					iconPainter = painterResource(R.drawable.ic_daily)
				)
			}

			if (restoreAvailable) {
				TextButton(onClick = onRestore, modifier = Modifier.padding(top = 4.dp)) {
					Text(stringResource(R.string.daily_streak_restore_button))
				}
			}
		}
	}
}
