package net.luis.sudoku.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.luis.sudoku.ui.theme.ActionAccent
import net.luis.sudoku.ui.theme.accentBrush

/**
 * Shared building blocks for the refactored UI. The house style is **outlined, not filled**: primary
 * actions get a hairline outline and the accent gradient only where emphasis is genuinely needed
 * ([GradientButton]), so a screen full of actions doesn't turn into a wall of solid color blocks.
 */

/**
 * The default action button: outlined, and **raised** (visual item 5). A hairline outline on the page's
 * own background left these reading as inert boxes rather than as things to press, so they now sit on an
 * opaque surface with a real shadow - the outline still carries the light-blue accent, the elevation is
 * what says "button".
 */
@Composable
fun OutlinedActionButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	icon: ImageVector? = null,
	iconPainter: Painter? = null,
	/** `true` when [iconPainter] is a full-color drawable rather than a tintable glyph - see `ButtonLabel`. */
	iconIsArtwork: Boolean = false
) {
	OutlinedButton(
		onClick = onClick,
		modifier = modifier,
		enabled = enabled,
		shape = RAISED_SHAPE,
		colors = ButtonDefaults.outlinedButtonColors(
			// Opaque, not transparent: a shadow cast by a see-through container shows through it as a
			// grey haze instead of reading as lift.
			containerColor = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface
		),
		elevation = raisedElevation(),
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.7f else 0.25f)),
		contentPadding = ACTION_BUTTON_PADDING
	) {
		ButtonLabel(text, icon, iconPainter, iconIsArtwork)
	}
}

/**
 * The icon-only member of the same family - undo and redo (game item 2), raised exactly like
 * [OutlinedActionButton] so a row mixing the two doesn't mix two button languages (visual item 5).
 */
@Composable
fun OutlinedIconActionButton(
	iconPainter: Painter,
	contentDescription: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true
) {
	OutlinedIconButton(
		onClick = onClick,
		// IconButton takes no elevation parameter, so the lift is drawn by the modifier instead - same
		// depth as raisedElevation(), so both button shapes cast one shadow.
		modifier = modifier.shadow(elevation = if (enabled) 3.dp else 0.dp, shape = RAISED_SHAPE),
		enabled = enabled,
		shape = RAISED_SHAPE,
		colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.7f else 0.25f)),
		content = {
			Icon(painter = iconPainter, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
		}
	)
}

/**
 * A two-state action - pen vs pencil (visual item 3). It is a *button*, not a chip: the play screen's
 * other controls are all [OutlinedActionButton]s and [GradientButton]s, and Material's `FilterChip`
 * brought its own height, corner radius and selection colours into the middle of that row.
 *
 * Selected is the gradient fill, unselected is the raised outline - the same pair every other screen uses
 * for "this one is active".
 */
@Composable
fun ToggleActionButton(
	text: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	accent: ActionAccent? = null
) {
	if (selected) {
		GradientButton(text = text, onClick = onClick, accent = accent, modifier = modifier, fillWidth = false)
	} else {
		OutlinedActionButton(text = text, onClick = onClick, modifier = modifier)
	}
}

/** The corner radius shared by every raised control (visual item 5). */
private val RAISED_SHAPE = RoundedCornerShape(14.dp)

/** How far off the page a raised control sits - enough to read as lift, not so far it looks detached. */
@Composable
private fun raisedElevation() = ButtonDefaults.buttonElevation(
	defaultElevation = 3.dp,
	pressedElevation = 1.dp,
	disabledElevation = 0.dp
)

/**
 * A gradient-filled action button. [accent] picks which gradient (design item 2, home item 1); leaving it
 * `null` uses the theme's own accent sweep, which is what the single emphasised action on a screen wants.
 *
 * Always [ACTION_BUTTON_PADDING] tall and full width, so a screen can stack these and a [DropdownTrigger]
 * together and have every row line up (new-puzzle item 1).
 */
@Composable
fun GradientButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	icon: ImageVector? = null,
	iconPainter: Painter? = null,
	/** `true` when [iconPainter] is a full-color drawable rather than a tintable glyph - see `ButtonLabel`. */
	iconIsArtwork: Boolean = false,
	accent: ActionAccent? = null,
	/** `false` for a button that has to size itself to its label - the pen/pencil toggles (visual item 3). */
	fillWidth: Boolean = true
) {
	val shape = RAISED_SHAPE
	val brush = accent?.brush() ?: accentBrush()
	Box(
		modifier = modifier
			.shadow(elevation = if (enabled) 3.dp else 0.dp, shape = shape)
			.clip(shape)
			.then(if (enabled) Modifier.background(brush) else Modifier)
	) {
		Button(
			onClick = onClick,
			enabled = enabled,
			shape = shape,
			elevation = null,
			colors = ButtonDefaults.buttonColors(
				containerColor = androidx.compose.ui.graphics.Color.Transparent,
				// White, not onPrimary: the accents are fixed saturated colors that do not follow the scheme,
				// so onPrimary would flip to a dark ink in dark mode and vanish into the gradient.
				contentColor = androidx.compose.ui.graphics.Color.White
			),
			contentPadding = ACTION_BUTTON_PADDING,
			modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier
		) {
			ButtonLabel(text, icon, iconPainter, iconIsArtwork)
		}
	}
}

/**
 * The one content padding every full-width action shares - an outlined button, a gradient button and a
 * dropdown trigger all have to measure the same or a column of them looks ragged (new-puzzle item 1).
 */
private val ACTION_BUTTON_PADDING = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

/**
 * A dropdown that looks exactly like a [GradientButton] and opens a menu as wide as itself (new-puzzle
 * item 1). Material's own `ExposedDropdownMenuBox` is deliberately not used: it is a text field, so it
 * carries a field's height, label slot and focus behaviour, none of which can be made to match a button.
 */
@Composable
fun <T> DropdownTrigger(
	selectedLabel: String,
	options: List<T>,
	optionLabel: @Composable (T) -> String,
	onSelect: (T) -> Unit,
	modifier: Modifier = Modifier,
	accent: ActionAccent? = null
) {
	var expanded by remember { mutableStateOf(false) }
	// The menu is placed against the trigger's own measured width so it can never be narrower than the
	// button that opened it - DropdownMenu otherwise sizes itself to its widest item.
	var widthPx by remember { mutableIntStateOf(0) }
	val density = LocalDensity.current

	Box(modifier = modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }) {
		GradientButton(
			text = selectedLabel,
			onClick = { expanded = true },
			icon = Icons.Filled.KeyboardArrowDown,
			accent = accent,
			modifier = Modifier.fillMaxWidth()
		)
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
			// Generator item 1: the same plain surface the info dialog uses. Material's default menu is a
			// tonally elevated container, which lands as a grey-lavender panel next to a white popup on the
			// very same screen.
			containerColor = dialogContainerColor(),
			shape = RoundedCornerShape(14.dp),
			border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
			modifier = Modifier.width(with(density) { widthPx.toDp() })
		) {
			options.forEach { option ->
				val text = optionLabel(option)
				DropdownMenuItem(
					text = { Text(text) },
					onClick = {
						onSelect(option)
						expanded = false
					}
				)
			}
		}
	}
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?, iconPainter: Painter?, iconIsArtwork: Boolean) {
	Row(verticalAlignment = Alignment.CenterVertically) {
		when {
			icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 0.dp))
			// Artwork draws through Image, not Icon: Icon paints the drawable as a single-color mask, which
			// throws away every color in a full-color source and leaves a silhouette.
			iconPainter != null && iconIsArtwork ->
				Image(painter = iconPainter, contentDescription = null, modifier = Modifier.size(20.dp))
			iconPainter != null -> Icon(iconPainter, contentDescription = null, modifier = Modifier.size(18.dp))
		}
		if (icon != null || iconPainter != null) {
			Box(modifier = Modifier.size(8.dp))
		}
		Text(text, style = MaterialTheme.typography.labelLarge)
	}
}

/** A titled, outlined container - the unit every settings/stats section is built from. */
@Composable
fun SectionCard(
	title: String?,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
) {
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(18.dp),
		color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
		// Explicit, because Material3 derives contentColor via contentColorFor(color) and an
		// alpha-modified surface matches no scheme role - it resolves to Unspecified and the text lands
		// black, which is invisible in dark mode.
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
	) {
		Column(modifier = Modifier.padding(16.dp)) {
			if (title != null) {
				Text(
					text = title,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.padding(bottom = 12.dp)
				)
			}
			content()
		}
	}
}

/**
 * A labelled horizontal progress bar. Drawn by hand rather than with `LinearProgressIndicator` so the
 * fill can carry the accent gradient and the track can stay a hairline-outlined pill.
 */
@Composable
fun ProgressRow(
	label: String,
	value: String,
	fraction: Float,
	modifier: Modifier = Modifier
) {
	Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
		Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
			Text(label, style = MaterialTheme.typography.bodyMedium)
			Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
		}
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 6.dp)
				.height(8.dp)
				.clip(RoundedCornerShape(4.dp))
				.background(MaterialTheme.colorScheme.surfaceVariant)
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth(fraction.coerceIn(0f, 1f))
					.fillMaxSize()
					.background(accentBrush())
			)
		}
	}
}

/**
 * The per-option help the generator screen needs (UI item 6): a small `i` that explains what the option
 * does *and* what the currently selected value means.
 *
 * New-puzzle item 3: plain white, not the tinted elevated surface Material gives a dialog by default. The
 * content is background detail, so it should recede rather than announce itself.
 */
/**
 * The one surface every popup shares - the info dialog, the share dialog (share item 2) and the generator's
 * dropdown menu (generator item 1). Plain surface, not Material's tonally elevated container: two popups on
 * the same screen must not be two different shades of white.
 */
@Composable
fun dialogContainerColor(): Color = MaterialTheme.colorScheme.surface

@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		containerColor = dialogContainerColor(),
		titleContentColor = MaterialTheme.colorScheme.onSurface,
		textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
		title = { Text(title) },
		text = { Text(body) },
		confirmButton = { TextButton(onClick = onDismiss) { Text(stringResourceOk()) } }
	)
}

@Composable
private fun stringResourceOk(): String =
	androidx.compose.ui.res.stringResource(net.luis.sudoku.R.string.action_ok)
