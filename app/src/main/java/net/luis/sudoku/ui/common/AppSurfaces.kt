package net.luis.sudoku.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.luis.sudoku.ui.theme.LocalAppShapes

/**
 * The two containers a theme has to reach: the popup and the plain panel.
 *
 * They exist for one reason. A raw `AlertDialog` or `Surface` takes its shape and container tone from
 * Material's own defaults, and a theme cannot reach a default. That was survivable while there was one
 * look - the app already pins the scheme roles Material draws popups on (account item 1), which is why
 * twenty-two bare dialogs came out white rather than lavender-grey - but a *shape* has no scheme role to
 * pin. A theme wanting square corners would have changed every bespoke component in `Components.kt` and
 * none of these, and shipped two design languages on the same screen.
 *
 * The ordinary controls are next door in `AppControls.kt`; the bespoke action buttons are in
 * `Components.kt`. Same rule across all three: read named roles, never ask which theme is on.
 */

/**
 * The one popup surface, and the one place a dialog's shape and outline are decided.
 *
 * `AlertDialog` is kept underneath rather than rebuilt, because it carries the scrim, the predictive-back
 * handling and the button ordering, none of which is a design decision.
 */
@Composable
fun AppDialog(
	onDismissRequest: () -> Unit,
	confirmButton: @Composable () -> Unit,
	modifier: Modifier = Modifier,
	dismissButton: (@Composable () -> Unit)? = null,
	icon: (@Composable () -> Unit)? = null,
	title: (@Composable () -> Unit)? = null,
	text: (@Composable () -> Unit)? = null
) {
	AlertDialog(
		onDismissRequest = onDismissRequest,
		confirmButton = confirmButton,
		modifier = modifier,
		dismissButton = dismissButton,
		icon = icon,
		title = title,
		text = text,
		shape = RoundedCornerShape(LocalAppShapes.current.dialogCorner),
		containerColor = dialogContainerColor(),
		titleContentColor = MaterialTheme.colorScheme.onSurface,
		textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
	)
}

/**
 * How large a thing a panel is - which is the only decision behind its corner radius.
 *
 * Two values rather than a `Dp`, because "how round is this" is not a call site's judgment to make. The app
 * used five radii before the theme existed, and nobody could have said which of them was right for a new
 * box; these say what the box *is*, and the theme decides what that looks like.
 */
enum class PanelShape {

	/** A card, a tile, a dialog: something with content of its own inside it. */
	CARD,

	/** An inline strip - a caption, a warning, a row in a list. Sits at the control radius. */
	INLINE
}

/**
 * A plain themed panel: the outlined box a screen puts content in when it is not a titled [SectionCard] -
 * a hub tile, a caption strip, a learn card, a warning.
 *
 * @param color defaults to `surfaceContainer`, which is the role a container is defined as sitting on - not
 *   `surface`, which is the page it sits *in*. Pass one only where the panel means something a container
 *   does not, such as a warning or a solved exercise
 * @param outlined `false` for a panel that carries its meaning in its fill, or that sits on a card which
 *   already has an outline - two hairlines a pixel apart read as a rendering fault rather than as two
 *   containers
 * @param border a stroke of the caller's own, for the one case an outline has to say something the theme's
 *   does not (a locked row fading its own edge). Ignored when [outlined] is `false`
 */
@Composable
fun AppPanel(
	modifier: Modifier = Modifier,
	shape: PanelShape = PanelShape.CARD,
	color: Color = MaterialTheme.colorScheme.surfaceContainer,
	contentColor: Color = MaterialTheme.colorScheme.onSurface,
	outlined: Boolean = true,
	border: BorderStroke? = null,
	contentPadding: PaddingValues = PaddingValues(0.dp),
	content: @Composable () -> Unit
) {
	val shapes = LocalAppShapes.current
	val corner = when (shape) {
		PanelShape.CARD -> shapes.containerCorner
		PanelShape.INLINE -> shapes.controlCorner
	}
	Surface(
		modifier = modifier,
		shape = RoundedCornerShape(corner),
		color = color,
		// Explicit for the same reason [SectionCard] is explicit: Material derives a content colour via
		// contentColorFor(color), and a colour that matches no scheme role resolves to Unspecified, which
		// lands the text black and invisible in dark mode.
		contentColor = contentColor,
		border = if (outlined) (border ?: containerBorder()) else null
	) {
		// `propagateMinConstraints`, because `Surface` sets it and this Box sits between the two. Left at the
		// default the Box relaxes the minimum width, and any child that fills its parent by *not* asking -
		// a Column with only padding on it, which is most of them - stops filling the panel and shrinks to
		// its own content.
		Box(modifier = Modifier.padding(contentPadding), propagateMinConstraints = true) { content() }
	}
}
