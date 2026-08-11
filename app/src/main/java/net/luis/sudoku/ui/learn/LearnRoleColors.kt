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

	private val DARK = mapOf(
		CellRole.BASE to Color(0xFF4A3A1E),
		CellRole.COVER to Color(0xFF1E3A4A),
		CellRole.PATTERN to Color(0xFF352A4A),
		CellRole.PIVOT to Color(0xFF5A431C),
		CellRole.WING to Color(0xFF3E3520),
		CellRole.FIN to Color(0xFF4A2233),
		CellRole.FLOOR to Color(0xFF2C3238),
		CellRole.ROOF to Color(0xFF1F3A36),
		CellRole.LINK_ON to Color(0xFF2C4423),
		CellRole.LINK_OFF to Color(0xFF4A2626),
		CellRole.TARGET to Color(0xFF9A6E00),
		CellRole.CONTEXT to Color(0xFF2A2E33)
	)

	fun of(role: CellRole, dark: Boolean): Color =
		(if (dark) DARK else LIGHT).getValue(role)

	/** The outline a focused row, column or region is drawn with. */
	fun unitOutline(dark: Boolean): Color =
		if (dark) Color(0xFFC1C1FF) else Color(0xFF4C4ED9)
}
