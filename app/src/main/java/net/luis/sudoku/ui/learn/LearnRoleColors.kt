package net.luis.sudoku.ui.learn

import androidx.compose.ui.graphics.Color
import net.luis.sudoku.domain.DiagramTone
import net.luis.sudoku.ui.theme.BoardPalette

/**
 * The colours a technique's diagram is drawn in, on the lesson board and in a hint on the play board alike.
 *
 * Five fills rather than one per role (see [DiagramTone]), and none of them new: every one is a colour the app
 * already gives a meaning close to this one, so the diagram borrows a vocabulary instead of adding a sixth.
 *
 * - the pattern is the pale blue the lesson board has always drawn a cover set in;
 * - the second set is the orange of a wing's pivot, the warm half against the cool one;
 * - what only supports the argument is the lesson's receding grey;
 * - a cell losing a candidate is the board's own conflict red, the colour of "this cannot be";
 * - the solved cell is the green the lesson has always drawn an assumed-true link in.
 *
 * The lines are the board's accent ([BoardPalette.tintHighlight]) and a struck candidate its error red, both
 * read straight from the palette.
 */
object LearnRoleColors {

	private val LIGHT = mapOf(
		DiagramTone.PATTERN to Color(0xFFB3E5FC),
		DiagramTone.SECONDARY to Color(0xFFFFCC80),
		DiagramTone.CONTEXT to Color(0xFFECEFF1),
		DiagramTone.TARGET to Color(0xFFC5E1A5)
	)

	/**
	 * The same vocabulary for a dark board, and not the light one darkened: each fill carries the board's
	 * near-white digits and its pencil marks on top, so none of them may climb to where a light glyph stops
	 * being legible, and none may sink to another shade of the board's own near-black.
	 */
	private val DARK = mapOf(
		DiagramTone.PATTERN to Color(0xFF1B4257),
		DiagramTone.SECONDARY to Color(0xFF7A5410),
		DiagramTone.CONTEXT to Color(0xFF333040),
		DiagramTone.TARGET to Color(0xFF2F5A2A)
	)

	/** The fill for [tone]; the eliminated red is the palette's conflict colour, so it follows the board theme. */
	fun of(tone: DiagramTone, dark: Boolean, palette: BoardPalette): Color =
		if (tone == DiagramTone.ELIMINATED) palette.conflict else (if (dark) DARK else LIGHT).getValue(tone)

	/**
	 * The ink a digit standing on a coloured cell is written in.
	 *
	 * A fill replaces the board's background under the glyph, and the board's inks are chosen against that
	 * background and nothing else. One ink per mode, held against every fill in that mode, is what keeps a
	 * lesson readable on all of them.
	 */
	fun inkOn(dark: Boolean): Color =
		if (dark) Color(0xFFF4F1FA) else Color(0xFF171221)

	/** The outline a focused row, column or region is drawn with. */
	fun unitOutline(dark: Boolean): Color =
		if (dark) Color(0xFFC1C1FF) else Color(0xFF4C4ED9)
}
