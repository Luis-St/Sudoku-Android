package net.luis.sudoku.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import net.luis.sudoku.ui.theme.LocalAppShapes
import net.luis.sudoku.ui.theme.appFocusRing

/**
 * The house set of ordinary controls: the switch, the chips, the text field, the quiet text button, the
 * icon button, the divider and the two progress indicators.
 *
 * `Components.kt` holds the app's *bespoke* controls - the outlined and gradient action buttons, which are
 * built from scratch because Material has nothing shaped like them. These are the opposite case: Material
 * has exactly the right control and only its defaults are wrong. Wrapping is still the answer, because a
 * default is the one thing a theme cannot reach - a raw `Switch` takes Material's baseline colours and a
 * raw `OutlinedTextField` its baseline shape, and neither has a scheme role to pin.
 *
 * Everything here reads named roles and [net.luis.sudoku.ui.theme.AppShapes]; nothing asks which theme is
 * on. They carry the current look unchanged, so adopting one is never a visual change on its own.
 */

/** Material's own switch track is a 52x32 pill, so this is what "fully rounded" means for one. */
private val SWITCH_TRACK_CORNER = 16.dp

/** The same, for the 48dp circle an [AppIconButton]'s touch target is. */
private val ICON_BUTTON_CORNER = 24.dp

/** Every progress track in the app, labelled or bare - see [AppProgressBar] and `ProgressRow`. */
val PROGRESS_BAR_HEIGHT = 6.dp

/** A themed switch, so all four settings screens agree on what "on" looks like. */
@Composable
fun AppSwitch(
	checked: Boolean,
	onCheckedChange: ((Boolean) -> Unit)?,
	modifier: Modifier = Modifier,
	enabled: Boolean = true
) {
	Switch(
		checked = checked,
		onCheckedChange = onCheckedChange,
		// A switch is already a pill, so the ring follows it round rather than boxing it.
		modifier = modifier.appFocusRing(SWITCH_TRACK_CORNER),
		enabled = enabled,
		colors = SwitchDefaults.colors(
			checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
			checkedTrackColor = MaterialTheme.colorScheme.primary,
			uncheckedThumbColor = MaterialTheme.colorScheme.outline,
			// `surfaceVariant`, not `surfaceContainerHighest`. They were the same colour while a theme pinned
			// every container tone to one value, and stopped being the same the moment one shipped a real
			// ramp: on a themed ramp `containerHighest` is the tone a *popup* sits on, which is the wrong
			// thing for an off switch to borrow.
			uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
			uncheckedBorderColor = MaterialTheme.colorScheme.outline
		)
	)
}

/**
 * A themed selection chip - the match setup screen's size, variant and difficulty pickers.
 *
 * Still a chip and not a [ToggleActionButton]: these appear in wrapping rows of five to fifteen options,
 * where a full-height button per option is a screen of buttons. The play screen's two-state controls are
 * the other case and stay buttons (visual item 3).
 */
@Composable
fun AppFilterChip(
	selected: Boolean,
	onClick: () -> Unit,
	label: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true
) {
	val shapes = LocalAppShapes.current
	FilterChip(
		selected = selected,
		onClick = onClick,
		label = label,
		modifier = modifier.appFocusRing(shapes.chipCorner),
		enabled = enabled,
		shape = RoundedCornerShape(shapes.chipCorner),
		// Selected is `primaryContainer`, where Material's own default is `secondaryContainer`. A selected
		// chip is the same statement as a selected anything else in this app, and secondary is the role that
		// means progress and currency here - a picked difficulty drawn in it read as a status, not a choice.
		colors = FilterChipDefaults.filterChipColors(
			labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
			selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
			selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
			selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
		),
		border = FilterChipDefaults.filterChipBorder(
			enabled = enabled,
			selected = selected,
			borderWidth = shapes.borderWidth,
			selectedBorderWidth = shapes.borderWidth
		)
	)
}

/** The non-selectable member of the same family: a chip that is an action, not a state. */
@Composable
fun AppAssistChip(
	onClick: () -> Unit,
	label: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	leadingIcon: (@Composable () -> Unit)? = null
) {
	val shapes = LocalAppShapes.current
	AssistChip(
		onClick = onClick,
		label = label,
		modifier = modifier.appFocusRing(shapes.chipCorner),
		enabled = enabled,
		leadingIcon = leadingIcon,
		shape = RoundedCornerShape(shapes.chipCorner),
		// The same label role an unselected filter chip uses: these two sit in the same rows and an assist
		// chip drawn a step darker reads as the selected one.
		colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.onSurfaceVariant),
		border = AssistChipDefaults.assistChipBorder(enabled = enabled, borderWidth = shapes.borderWidth)
	)
}

/**
 * The app's one text field: the invite code, the display name, the email, the device label, the share code.
 *
 * Material's outlined field is kept underneath rather than rebuilt - it carries the floating label
 * animation, the error and supporting-text slots and the IME plumbing, none of which is a design decision.
 * What it does not carry is this app's corner radius or hairline weight, which is why it is wrapped.
 *
 * @param supportingText a note under the field, shown in the error colour once [isError] is set - so a
 *   field that explains itself and a field that is complaining are the same control, not two
 */
@Composable
fun AppTextField(
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	readOnly: Boolean = false,
	isError: Boolean = false,
	singleLine: Boolean = true,
	supportingText: String? = null,
	keyboardType: KeyboardType = KeyboardType.Text,
	capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
	autoCorrect: Boolean = true,
	trailingIcon: (@Composable () -> Unit)? = null
) {
	val shapes = LocalAppShapes.current
	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
		modifier = modifier,
		enabled = enabled,
		readOnly = readOnly,
		label = { Text(label) },
		trailingIcon = trailingIcon,
		supportingText = supportingText?.let { { Text(it) } },
		isError = isError,
		singleLine = singleLine,
		keyboardOptions = KeyboardOptions(
			capitalization = capitalization,
			autoCorrectEnabled = autoCorrect,
			keyboardType = keyboardType
		),
		shape = RoundedCornerShape(shapes.fieldCorner),
		// The value is `bodyLarge`, which is what a field's own text is in the scale - and is *not* what an
		// unstyled field uses. `OutlinedTextField` takes its text style from `LocalTextStyle`, which is
		// whatever the enclosing screen happened to be writing in, so a field inside a `bodySmall` note used
		// to render its value at 12sp.
		textStyle = MaterialTheme.typography.bodyLarge,
		// The container is a real tone rather than transparent. On a theme with a surface ramp that is what
		// separates a field from the page it sits on; on one that pins the ramp it comes out exactly as it
		// did before, which is why this is safe to set for every theme.
		colors = OutlinedTextFieldDefaults.colors(
			focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
			unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
			disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
			errorContainerColor = MaterialTheme.colorScheme.surfaceContainer
		)
	)
}

/**
 * The quiet button: a dialog's confirm and dismiss, and the one-tap actions that sit inside body text.
 *
 * Deliberately not an [OutlinedActionButton]. A dialog's own buttons are read *after* its question, in a
 * place nothing else competes for, so an outline and a shadow there add weight where none is needed - and
 * two raised buttons in a row is the layout that makes a player press the wrong one.
 *
 * @param destructive draws the label in the error colour, for a leave, a kick or a delete. It is only the
 *   ink: the button keeps its position and size, because a destructive action that is also the biggest
 *   thing in the dialog is a trap
 */
@Composable
fun AppTextButton(
	text: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	destructive: Boolean = false
) {
	TextButton(
		onClick = onClick,
		modifier = modifier.appFocusRing(LocalAppShapes.current.controlCorner),
		enabled = enabled,
		colors = ButtonDefaults.textButtonColors(
			contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
		)
	) {
		Text(text)
	}
}

/**
 * A bare icon control - the top bar's actions, and the small `i` and refresh glyphs inside a row.
 *
 * The unfilled member of the icon family: [OutlinedIconActionButton] is the one that reads as a button on
 * a page, and [GradientIconActionButton] the one that is *the* thing to press. This is neither, which is
 * what a top bar wants - a bar of raised buttons is a second toolbar.
 *
 * Takes an [ImageVector] or a [Painter] and never both, because a full-colour drawable must be drawn
 * through `Image` rather than `Icon` (which paints it as a single-colour mask) - see `ButtonLabel`'s note.
 *
 * @param tint defaults to [LocalContentColor], which is exactly what a bare `Icon` uses, so the glyph takes
 *   the colour of whatever it sits in. Naming a scheme role here instead would be wrong in the one place
 *   most of these live: a `TopAppBar` draws its navigation icon in `onSurface` and its **actions** in
 *   `onSurfaceVariant` (`AppBarTokens.LeadingIconColor` against `TrailingIconColor`), so a fixed `onSurface`
 *   silently darkens every action in the bar.
 */
@Composable
fun AppIconButton(
	contentDescription: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	icon: ImageVector? = null,
	iconPainter: Painter? = null,
	iconSize: Dp = 24.dp,
	tint: androidx.compose.ui.graphics.Color = LocalContentColor.current
) {
	// A bare icon button's touch target is a 48dp circle, so its ring is one too: half the target is the
	// radius that makes a rounded rectangle into a circle.
	IconButton(onClick = onClick, modifier = modifier.appFocusRing(ICON_BUTTON_CORNER), enabled = enabled) {
		when {
			icon != null -> Icon(icon, contentDescription, Modifier.size(iconSize), tint)
			iconPainter != null -> Icon(iconPainter, contentDescription, Modifier.size(iconSize), tint)
		}
	}
}

/**
 * The hairline between two rows of a list.
 *
 * A divider and a container outline are the same statement at different strengths, so both come off
 * [net.luis.sudoku.ui.theme.AppShapes] - a theme that draws heavy borders and hairline dividers is saying
 * two different things about the same idea.
 */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
	val shapes = LocalAppShapes.current
	HorizontalDivider(
		modifier = modifier,
		thickness = shapes.borderWidth,
		color = MaterialTheme.colorScheme.outline.copy(alpha = shapes.containerBorderAlpha)
	)
}

/** A spinner, in the theme's accent rather than Material's baseline violet. */
@Composable
fun AppSpinner(modifier: Modifier = Modifier, size: Dp? = null) {
	CircularProgressIndicator(
		modifier = if (size != null) modifier.size(size) else modifier,
		color = MaterialTheme.colorScheme.primary,
		// Transparent, which is Material's own default for the *indeterminate* ring. A coloured track here
		// is a full circle drawn behind the sweep - not a quieter spinner, a different control.
		trackColor = Color.Transparent
	)
}

/**
 * A whole screen that is waiting: the spinner, centred, filling what it is given.
 *
 * Its own function because seven screens had written the same centred `Box` around a bare indicator, and
 * seven copies of a layout is how two of them end up differing.
 */
@Composable
fun AppLoadingScreen(modifier: Modifier = Modifier) {
	Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		AppSpinner()
	}
}

/**
 * A bare progress bar - for the places that need a plain indicator rather than the labelled [ProgressRow]
 * (a race opponent's row, the example screen's step counter).
 */
@Composable
fun AppProgressBar(progress: () -> Float, modifier: Modifier = Modifier) {
	LinearProgressIndicator(
		progress = progress,
		// 6dp rather than Material's 4: a bar this app draws is usually the only thing saying how far along
		// something is, and four device-independent pixels of it is a hairline on a modern phone.
		modifier = modifier.height(PROGRESS_BAR_HEIGHT),
		color = MaterialTheme.colorScheme.primary,
		trackColor = MaterialTheme.colorScheme.surfaceVariant,
		strokeCap = StrokeCap.Round
	)
}
