package net.luis.sudoku.ui.generator

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.luis.sudoku.R
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.ui.common.DropdownTrigger
import net.luis.sudoku.ui.common.GradientButton
import net.luis.sudoku.ui.common.InfoDialog
import net.luis.sudoku.ui.common.SectionCard
import net.luis.sudoku.ui.theme.ActionAccent

/**
 * UI item 6: pick size, variant and difficulty from dropdowns, each with an info button explaining both
 * what the option is for and what the currently selected value means, then start.
 *
 * Stateless with respect to the game: it only produces a choice and hands it to [onStart], which routes
 * to the play destination carrying the three values (see `Routes.playGenerated`). That keeps the
 * generator out of `GameViewModel`'s lifecycle entirely.
 */
@Composable
fun GeneratorScreen(
	onStart: (GridSize, Variant, Difficulty) -> Unit,
	modifier: Modifier = Modifier
) {
	var size by remember { mutableStateOf(GridSize.NINE) }
	var variant by remember { mutableStateOf(Variant.CLASSIC) }
	var difficulty by remember { mutableStateOf(Difficulty.THREE) }
	var info by remember { mutableStateOf<InfoTopic?>(null) }

	// Not every variant exists at every size; a size change that orphans the selection resets it.
	val supportedVariants = Variant.values().filter { it.isSupportedAt(size) }
	if (variant !in supportedVariants) variant = supportedVariants.first()

	Column(
		modifier = modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		SectionCard(title = stringResource(R.string.generator_section_title)) {
			Column {
				OptionDropdown(
					label = stringResource(R.string.label_size),
					selectedLabel = sizeLabel(size),
					options = GridSize.values().toList(),
					optionLabel = ::sizeLabel,
					onSelect = { size = it },
					onInfo = { info = InfoTopic.SIZE }
				)

				OptionDropdown(
					label = stringResource(R.string.label_variant),
					selectedLabel = variantLabel(variant),
					options = supportedVariants,
					optionLabel = ::variantLabel,
					onSelect = { variant = it },
					onInfo = { info = InfoTopic.VARIANT },
					modifier = Modifier.padding(top = 12.dp)
				)

				OptionDropdown(
					label = stringResource(R.string.label_difficulty),
					selectedLabel = difficultyLabel(difficulty),
					options = Difficulty.values().toList(),
					optionLabel = ::difficultyLabel,
					onSelect = { difficulty = it },
					onInfo = { info = InfoTopic.DIFFICULTY },
					modifier = Modifier.padding(top = 12.dp)
				)
			}
		}

		Box(modifier = Modifier.padding(top = 20.dp)) {
			GradientButton(
				text = stringResource(R.string.generator_start),
				onClick = { onStart(size, variant, difficulty) },
				accent = GENERATOR_ACCENT
			)
		}

		Text(
			text = stringResource(R.string.generator_overwrites_warning),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(top = 12.dp)
		)

		// Generator item 7: warn *before* generating, not after the board turns out to be unreadable. It sits
		// at the foot of the screen, next to the other caveat about starting a puzzle, rather than pushing the
		// picker itself down the page. The check is against the device's real width, so the same 16x16 warns on
		// a compact phone and stays silent on a tablet - the size alone is not the problem, the size here is.
		if (isCrampedOnThisScreen(size)) {
			SmallScreenWarning(size)
		}
	}

	info?.let { topic ->
		InfoDialog(
			title = stringResource(topic.titleRes),
			body = stringResource(topic.bodyRes) + "\n\n" + when (topic) {
				InfoTopic.SIZE -> stringResource(R.string.generator_info_current, sizeLabel(size)) + "\n" + sizeDetail(size)
				InfoTopic.VARIANT -> stringResource(R.string.generator_info_current, variantLabel(variant)) + "\n" + variantDetail(variant)
				InfoTopic.DIFFICULTY -> stringResource(R.string.generator_info_current, difficultyLabel(difficulty)) + "\n" + difficultyDetail(difficulty)
			},
			onDismiss = { info = null }
		)
	}
}

/**
 * Whether this grid would be uncomfortably small on the device actually in hand (generator item 7).
 *
 * [MIN_COMFORTABLE_CELL_DP] is the width a cell needs for its digit and its notes to stay readable without
 * zooming. Below 12x12 nothing warns: a 9x9 clears the bar on every phone, so a warning there would only be
 * noise. The board takes the screen width minus the screen's own horizontal padding.
 */
@Composable
private fun isCrampedOnThisScreen(size: GridSize): Boolean {
	if (size.n() < WARN_FROM_EDGE_LENGTH) return false
	val availableWidth = LocalConfiguration.current.screenWidthDp - SCREEN_HORIZONTAL_PADDING_DP
	return availableWidth < size.n() * MIN_COMFORTABLE_CELL_DP
}

/** Only the two large sizes can be cramped; 4x4 through 9x9 fit any phone this app runs on. */
private const val WARN_FROM_EDGE_LENGTH = 12

/**
 * Roughly the narrowest a cell can be and still hold a legible digit *and* a 4x4 block of notes without
 * zooming. Deliberately generous: at 12x12 a typical phone lands a little under it, which is exactly the
 * case worth warning about, while a tablet clears it at both sizes and stays quiet.
 */
private const val MIN_COMFORTABLE_CELL_DP = 36

/** What the play screen's own horizontal padding takes off the board's width. */
private const val SCREEN_HORIZONTAL_PADDING_DP = 24

@Composable
private fun SmallScreenWarning(size: GridSize, modifier: Modifier = Modifier) {
	Surface(
		modifier = modifier.fillMaxWidth().padding(top = 16.dp),
		shape = RoundedCornerShape(14.dp),
		color = MaterialTheme.colorScheme.tertiaryContainer,
		contentColor = MaterialTheme.colorScheme.onTertiaryContainer
	) {
		Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
			Icon(
				imageVector = Icons.Filled.Warning,
				contentDescription = null,
				modifier = Modifier.size(18.dp)
			)
			Text(
				text = stringResource(R.string.generator_small_screen_warning, size.n()),
				style = MaterialTheme.typography.bodySmall,
				modifier = Modifier.padding(start = 8.dp)
			)
		}
	}
}

private enum class InfoTopic(val titleRes: Int, val bodyRes: Int) {
	SIZE(R.string.label_size, R.string.generator_info_size),
	VARIANT(R.string.label_variant, R.string.generator_info_variant),
	DIFFICULTY(R.string.label_difficulty, R.string.generator_info_difficulty)
}

/**
 * One labelled dropdown plus its info button - the repeated unit of this screen.
 *
 * New-puzzle item 1: the trigger is a [DropdownTrigger], so it is the same width, height and gradient as
 * the "Generate and play" button below it. All three carry the same accent as that button on purpose -
 * they are one form, and giving each row its own colour here would read as three unrelated actions.
 */
@Composable
private fun <T> OptionDropdown(
	label: String,
	selectedLabel: String,
	options: List<T>,
	optionLabel: @Composable (T) -> String,
	onSelect: (T) -> Unit,
	onInfo: () -> Unit,
	modifier: Modifier = Modifier
) {
	Column(modifier = modifier.fillMaxWidth()) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(label, style = MaterialTheme.typography.labelLarge)
			IconButton(onClick = onInfo, modifier = Modifier.size(28.dp)) {
				Icon(
					imageVector = Icons.Filled.Info,
					contentDescription = stringResource(R.string.action_info),
					tint = MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(18.dp)
				)
			}
		}

		DropdownTrigger(
			selectedLabel = selectedLabel,
			options = options,
			optionLabel = optionLabel,
			onSelect = onSelect,
			accent = GENERATOR_ACCENT
		)
	}
}

/** The one accent this whole screen uses - both dropdowns and the start button (new-puzzle items 1 and 2). */
private val GENERATOR_ACCENT = ActionAccent.INDIGO

@Composable
private fun sizeLabel(size: GridSize): String = "${size.n()}×${size.n()}"

@Composable
private fun variantLabel(variant: Variant): String = when (variant) {
	Variant.CLASSIC -> stringResource(R.string.variant_classic)
	Variant.CHAOS -> stringResource(R.string.variant_chaos)
	else -> variant.name.lowercase().replaceFirstChar(Char::uppercase)
}

@Composable
private fun difficultyLabel(difficulty: Difficulty): String =
	if (difficulty.isLisa) stringResource(R.string.difficulty_lisa)
	else stringResource(R.string.difficulty_tier, difficulty.index())

@Composable
private fun sizeDetail(size: GridSize): String = stringResource(R.string.generator_info_size_detail, size.n(), size.n() * size.n())

@Composable
private fun variantDetail(variant: Variant): String = when (variant) {
	Variant.CLASSIC -> stringResource(R.string.generator_info_variant_classic)
	Variant.CHAOS -> stringResource(R.string.generator_info_variant_chaos)
	else -> stringResource(R.string.generator_info_variant)
}

@Composable
private fun difficultyDetail(difficulty: Difficulty): String =
	if (difficulty.isLisa) stringResource(R.string.generator_info_difficulty_lisa)
	else stringResource(R.string.generator_info_difficulty_tier, difficulty.index())
