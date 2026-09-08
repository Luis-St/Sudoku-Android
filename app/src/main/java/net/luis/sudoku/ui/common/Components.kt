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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import net.luis.sudoku.ui.theme.Accent
import net.luis.sudoku.ui.theme.LocalAppShapes
import net.luis.sudoku.ui.theme.accentBrush
import net.luis.sudoku.ui.theme.appFocusRing

/**
 * Shared building blocks for the UI. The house style is **outlined, not filled**: primary actions get a
 * hairline outline and the accent gradient only where emphasis is genuinely needed ([GradientButton]), so
 * a screen full of actions doesn't turn into a wall of solid color blocks.
 *
 * Nothing here holds a colour, a radius, a border width or an elevation of its own. Every one of those
 * comes from `MaterialTheme.colorScheme` or from [net.luis.sudoku.ui.theme.AppShapes], which a theme
 * supplies - so a purchased look reaches every button, panel, popup and switch in the app without any of
 * these functions knowing which theme is on. A component that asked *which* theme it was drawing would
 * have to be edited for every theme ever added; a component that reads named roles never does.
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
	iconIsArtwork: Boolean = false,
	/**
	 * Game item 2 (2.1.0): an outline of the caller's own, for the buttons that sit **on a board**.
	 *
	 * The play screens draw theirs in the text ink rather than the scheme's soft outline - a board is a page
	 * of ruled lines and digits, and a grey hairline that reads as a button everywhere else disappears into
	 * it. `null` everywhere else, which is the outline every other screen has always had.
	 */
	borderColor: Color? = null
) {
	OutlinedButton(
		onClick = onClick,
		modifier = modifier.appFocusRing(LocalAppShapes.current.controlCorner),
		enabled = enabled,
		shape = raisedShape(),
		colors = ButtonDefaults.outlinedButtonColors(
			// Opaque, not transparent: a shadow cast by a see-through container shows through it as a
			// grey haze instead of reading as lift.
			containerColor = MaterialTheme.colorScheme.surface,
			contentColor = MaterialTheme.colorScheme.onSurface
		),
		elevation = raisedElevation(),
		border = controlBorder(enabled, borderColor),
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
		modifier = modifier
			.appFocusRing(LocalAppShapes.current.controlCorner)
			.shadow(elevation = if (enabled) LocalAppShapes.current.elevation else 0.dp, shape = raisedShape()),
		enabled = enabled,
		shape = raisedShape(),
		colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
		border = controlBorder(enabled),
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
	accent: Accent? = null
) {
	if (selected) {
		GradientButton(text = text, onClick = onClick, accent = accent, modifier = modifier, fillWidth = false)
	} else {
		OutlinedActionButton(text = text, onClick = onClick, modifier = modifier)
	}
}

/** The corner radius shared by every raised control (visual item 5) - the theme's, not a constant. */
@Composable
@ReadOnlyComposable
private fun raisedShape() = RoundedCornerShape(LocalAppShapes.current.controlCorner)

/** How far off the page a raised control sits - enough to read as lift, not so far it looks detached. */
@Composable
private fun raisedElevation() = ButtonDefaults.buttonElevation(
	defaultElevation = LocalAppShapes.current.elevation,
	pressedElevation = LocalAppShapes.current.pressedElevation,
	disabledElevation = 0.dp
)

/**
 * A control's own outline: the theme's width, in [color] (or the scheme's outline), faded when disabled.
 *
 * The fade is applied to whichever colour is in use rather than swapping in a grey, so a board button
 * greys out exactly like every other one rather than staying at full strength once its count runs out.
 */
@Composable
@ReadOnlyComposable
private fun controlBorder(enabled: Boolean, color: Color? = null): BorderStroke {
	val shapes = LocalAppShapes.current
	return BorderStroke(
		shapes.borderWidth,
		(color ?: MaterialTheme.colorScheme.outline)
			.copy(alpha = if (enabled) shapes.borderAlpha else shapes.disabledBorderAlpha)
	)
}

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
	accent: Accent? = null,
	/** `false` for a button that has to size itself to its label - the pen/pencil toggles (visual item 3). */
	fillWidth: Boolean = true
) {
	val shape = raisedShape()
	val brush = accent?.gradient()?.brush() ?: accentBrush()
	Box(
		modifier = modifier
			// Before the clip, so the ring is drawn outside the shape rather than trimmed to it.
			.appFocusRing(LocalAppShapes.current.controlCorner)
			.shadow(elevation = if (enabled) LocalAppShapes.current.elevation else 0.dp, shape = shape)
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
 * The icon-only member of the gradient family, for a single decisive action that has no room for its label.
 *
 * Filled rather than outlined, and deliberately: [OutlinedIconActionButton] is the neutral icon control, so
 * an icon that is *the* thing to press next has to differ from it by more than which glyph it carries.
 */
@Composable
fun GradientIconActionButton(
	icon: ImageVector,
	contentDescription: String?,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	accent: Accent? = null
) {
	val shape = raisedShape()
	val brush = accent?.gradient()?.brush() ?: accentBrush()
	Box(
		modifier = modifier
			// See [GradientButton]: before the clip, or the ring is trimmed to the button.
			.appFocusRing(LocalAppShapes.current.controlCorner)
			.shadow(elevation = LocalAppShapes.current.elevation, shape = shape)
			.clip(shape)
			.background(brush),
		contentAlignment = Alignment.Center
	) {
		IconButton(
			onClick = onClick,
			// White for the same reason [GradientButton] uses it: the accents are fixed saturated colours
			// that do not follow the scheme, so a scheme ink would flip and vanish into the gradient.
			colors = IconButtonDefaults.iconButtonColors(
				containerColor = Color.Transparent,
				contentColor = Color.White
			),
			modifier = Modifier.fillMaxSize()
		) {
			Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
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
	accent: Accent? = null,
	/**
	 * Settings item 1: draw the trigger as an [OutlinedActionButton] rather than a [GradientButton].
	 *
	 * The generator's pickers *are* the screen - three gradient triggers and a start button, and the
	 * gradient is what makes them read as the choices being made. A settings screen is a list of quiet
	 * rows, and three saturated gradient bars in it drowned out the one button on the page that leads
	 * somewhere ("Server and account"). Same width, height and menu either way; only the fill differs.
	 */
	outlined: Boolean = false
) {
	var expanded by remember { mutableStateOf(false) }
	// The menu is placed against the trigger's own measured width so it can never be narrower than the
	// button that opened it - DropdownMenu otherwise sizes itself to its widest item.
	var widthPx by remember { mutableIntStateOf(0) }
	val density = LocalDensity.current

	Box(modifier = modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }) {
		if (outlined) {
			OutlinedActionButton(
				text = selectedLabel,
				onClick = { expanded = true },
				icon = Icons.Filled.KeyboardArrowDown,
				modifier = Modifier.fillMaxWidth()
			)
		} else {
			GradientButton(
				text = selectedLabel,
				onClick = { expanded = true },
				icon = Icons.Filled.KeyboardArrowDown,
				accent = accent,
				modifier = Modifier.fillMaxWidth()
			)
		}
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
			// Generator item 1: the same plain surface the info dialog uses. Material's default menu is a
			// tonally elevated container, which lands as a grey-lavender panel next to a white popup on the
			// very same screen.
			shape = raisedShape(),
			border = popupBorder(),
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

/**
 * A container's outline: a card, a panel, a dialog. Quieter than a control's, since it is not pressable.
 */
@Composable
@ReadOnlyComposable
fun containerBorder(): BorderStroke {
	val shapes = LocalAppShapes.current
	return BorderStroke(shapes.borderWidth, MaterialTheme.colorScheme.outline.copy(alpha = shapes.containerBorderAlpha))
}

/** A popup's outline, between the two: a menu has an edge to find, but is not a thing to press. */
@Composable
@ReadOnlyComposable
fun popupBorder(): BorderStroke {
	val shapes = LocalAppShapes.current
	return BorderStroke(shapes.borderWidth, MaterialTheme.colorScheme.outline.copy(alpha = shapes.popupBorderAlpha))
}

/** A titled, outlined container - the unit every settings/stats section is built from. */
@Composable
fun SectionCard(
	title: String?,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
) {
	val shapes = LocalAppShapes.current
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = RoundedCornerShape(shapes.containerCorner),
		// `surfaceContainer`, which is the role a card is *defined* as sitting on, rather than `surface`,
		// which is the page. The two are the same colour under a theme that pins its ramp - Classic's light
		// mode is white either way - and they are one deliberate tone apart under a theme that does not,
		// which is the whole point of a card having a role of its own.
		color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = shapes.containerAlpha),
		// Explicit, because Material3 derives contentColor via contentColorFor(color) and an
		// alpha-modified surface matches no scheme role - it resolves to Unspecified and the text lands
		// black, which is invisible in dark mode.
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = containerBorder()
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
 * A connection that is not answering, said in place rather than in a popup.
 *
 * Settings item 1's principle, applied wherever it comes up: losing a connection is the ordinary case on a
 * phone, and a modal for it interrupts whatever the player was doing and has to be dismissed before
 * anything else can be touched - while the screen underneath usually still works perfectly well with what
 * it already has.
 *
 * The *server* being unreachable is reported once, globally, by the warning next to the players button in
 * the top bar (`MainActivity`), driven by the presence heartbeat - the only thing that talks to the server
 * continuously and so the only thing that can answer without asking a question of its own. This composable
 * is for the narrower cases a screen knows about and the heartbeat does not, such as a co-op match socket
 * dropping while the match itself is still running.
 *
 * `Image`, never `Icon`: the warning is full-colour artwork and `Icon` would flatten it to a silhouette
 * (see the drawable-*dpi rasters).
 */
@Composable
fun ServerUnreachableNotice(text: String, modifier: Modifier = Modifier) {
	Row(
		modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Image(
			painter = androidx.compose.ui.res.painterResource(net.luis.sudoku.R.drawable.ic_warning),
			contentDescription = null,
			modifier = Modifier.size(20.dp)
		)
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(start = 8.dp)
		)
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
				.height(PROGRESS_BAR_HEIGHT)
				// Fully rounded, not the theme's small corner: a track this thin with a 4dp radius is a
				// rectangle with the corners nicked off, and the fill inside it ends in a straight edge.
				.clip(CircleShape)
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
 * dropdown menu (generator item 1). Two popups on the same screen must not be two different shades.
 *
 * Account item 1: this reads `surfaceContainerHigh` rather than `surface` now, and the theme pins that role
 * (with the other four container tones) to the app's own surface colour. Naming the role Material actually
 * defaults dialogs and menus to is what makes the popups that *do not* call this - twenty-one bare
 * `AlertDialog`s across the app, the link-code one among them - come out the same colour as the three that
 * do, instead of the lavender-grey of Material's unstyled baseline palette.
 */
@Composable
fun dialogContainerColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow

@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
	AppDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { Text(body) },
		confirmButton = { AppTextButton(text = stringResourceOk(), onClick = onDismiss) }
	)
}

@Composable
private fun stringResourceOk(): String =
	androidx.compose.ui.res.stringResource(net.luis.sudoku.R.string.action_ok)
