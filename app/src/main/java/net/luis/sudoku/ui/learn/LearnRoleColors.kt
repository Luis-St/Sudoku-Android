package net.luis.sudoku.ui.learn

import androidx.compose.ui.graphics.Color
import net.luis.sudoku.solver.CellRole

/**
 * The colour each part of a pattern is drawn in.
 *
 * Its own palette rather than an addition to the board's: these are not a board look the player can buy or
 * change, they are the vocabulary a lesson is drawn with, and they have to mean the same thing on every
 * technique. A fish's base lines and a chain's true end being the same colour on two different pages would
 * teach two different things with one hue.
 *
 * The pairs are what matter most. Base against cover, floor against roof, and true against false have to be
 * told apart at a glance and while colour-blind, so each pair is separated in lightness as well as in hue.
 */
object LearnRoleColors {

	private val LIGHT = mapOf(
		// The two halves of a set argument: a warm base against a cool cover.
		CellRole.BASE to Color(0xFFFFE0B2),
		CellRole.COVER to Color(0xFFB3E5FC),
		// The plain pattern cell, for a subset or a set that has no more specific part to play.
		CellRole.PATTERN to Color(0xFFE1D4F7),
		// A wing hangs off its pivot, so the pivot is the stronger of the two.
		CellRole.PIVOT to Color(0xFFFFCC80),
		CellRole.WING to Color(0xFFFFF0C2),
		// The fin is the exception that narrows a conclusion, so it is the one colour that stands apart.
		CellRole.FIN to Color(0xFFF8BBD0),
		// A deadly pattern's two halves: the bare floor against the roof that saves it.
		CellRole.FLOOR to Color(0xFFCFD8DC),
		CellRole.ROOF to Color(0xFFB2DFDB),
		// The two ends of every chain and colouring assumption. Green for what the argument takes as true,
		// red-grey for what it takes as false, which is the one pair a player reads without being told.
		CellRole.LINK_ON to Color(0xFFC5E1A5),
		CellRole.LINK_OFF to Color(0xFFEFB8B8),
		// The conclusion, in the same yellow the game already uses for "look here".
		CellRole.TARGET to Color(0xFFFFC400),
		// Shown only to explain, so it recedes.
		CellRole.CONTEXT to Color(0xFFECEFF1)
	)

	/**
	 * The same vocabulary for a dark board, and not the light one darkened.
	 *
	 * Two things set the level. Each fill has to be a colour rather than another shade of the board, which
	 * the old set was not: several of them sat within a few points of the board's own #131318 and of the
	 * peer highlight, so a coloured cell read as a slightly different black. And every fill carries the
	 * board's near-white digits and its pencil marks on top, so none of them may climb to where a light
	 * glyph stops being legible.
	 */
	private val DARK = mapOf(
		CellRole.BASE to Color(0xFF5A421A),
		CellRole.COVER to Color(0xFF1B4257),
		// Clear of the selected cell (#3A3646), which is the one grey a learn board also draws.
		CellRole.PATTERN to Color(0xFF43336B),
		CellRole.PIVOT to Color(0xFF7A5410),
		CellRole.WING to Color(0xFF4A431F),
		CellRole.FIN to Color(0xFF6B2447),
		CellRole.FLOOR to Color(0xFF384049),
		CellRole.ROOF to Color(0xFF1F4A44),
		CellRole.LINK_ON to Color(0xFF2F5A2A),
		CellRole.LINK_OFF to Color(0xFF5E2A2A),
		// The board's own "look here" amber, exactly as in light mode. Deep rather than bright for the
		// reason the play board gives it: a full-strength yellow cell on a near-black board is a lamp.
		CellRole.TARGET to Color(0xFF9A6E00),
		CellRole.CONTEXT to Color(0xFF333040)
	)

	fun of(role: CellRole, dark: Boolean): Color =
		(if (dark) DARK else LIGHT).getValue(role)

	/**
	 * The ink a digit standing on a coloured cell is written in.
	 *
	 * A role fill replaces the board's background under the glyph, and the board's inks are chosen against
	 * that background and nothing else. Left alone, a near-white given lands on a pale lavender pattern cell
	 * and disappears, and the placed digit's orange lands on the target's yellow, which is the same colour
	 * twice. One ink per mode, held against every fill in that mode, is what keeps a lesson readable on all
	 * twelve of them.
	 */
	fun inkOn(dark: Boolean): Color =
		if (dark) Color(0xFFF4F1FA) else Color(0xFF171221)

	/**
	 * The same vocabulary as a **stroke**, for a board that cannot give the cell's fill away (issue 2.2.2/2).
	 *
	 * The play board's cell colours are already spoken for - selected, peer, conflict, mistake, hint - so a
	 * hint that shows a technique's pattern there outlines the cells instead of filling them. The hues have to
	 * stay the same ones the lesson used, or a player who learned that the warm colour is the base set would
	 * be looking at a different language on the board where they need it.
	 *
	 * Which is why this is the *other* mode's fill: the light palette is a set of pale colours tuned to be
	 * legible with dark ink on a light ground, and the dark palette a set of deep ones tuned against a
	 * near-black board. Swap them and each becomes what a stroke needs - the deep colour draws on a light
	 * board, the pale one on a dark board - at the same hue, with nothing new to keep in step.
	 */
	fun outlineOf(role: CellRole, dark: Boolean): Color = of(role, !dark)

	/** The outline a focused row, column or region is drawn with. */
	fun unitOutline(dark: Boolean): Color =
		if (dark) Color(0xFFC1C1FF) else Color(0xFF4C4ED9)
}
