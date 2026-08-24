package net.luis.sudoku.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import net.luis.sudoku.domain.InputMode

/**
 * Beta feature: **pen and pencil get an ink of their own**, on the board and on everything that puts a
 * digit there.
 *
 * Off by default and opt-in from the settings screen (see `PreferenceSettings.betaDualInk`), which is why
 * this is a composition local carrying an [enabled] flag rather than two more fields on [BoardPalette]:
 * every consumer keeps its existing colours untouched while the beta is off, so nothing about the default
 * board changes shape while the feature is being tried out.
 *
 * Deliberately *not* part of a board theme. A board theme is a look the player buys; this is a statement
 * about which of the two input modes is in use, and it has to read the same on every theme - so it is one
 * fixed pair, and the pair is the board's own orange and blue rather than two colours invented for it.
 *
 * They are the colours the same-value mark uses (owner's call: reuse what the board already has).
 *
 * Since 2.2.0 the pen's ink is on **every** digit it placed, not only on the locked one (owner's call, beta
 * item 2): a placed digit is always orange, and the same-value mark is carried by the bold weight alone -
 * see `CellView`. Givens are not the pen's, so they keep the palette's own colour.
 *
 * **The pencil's is not** (owner's amendment to the same item): the blue is on the notes of the locked digit
 * only, where it stands in for the palette's same-value mark, and every other note stays grey. A cell holds
 * up to sixteen notes against one value, so colouring them all made the ink the loudest thing on the board
 * and erased the very mark the player scans the notes for.
 *
 * Nothing else on the board is affected, since neither hue is the mistake red, the hint yellow or either
 * hint-note colour.
 */
data class InkColors(
	val enabled: Boolean,
	/** The ink a placed digit is written in, and the accent of everything that places one. */
	val pen: Color,
	/** The same for a note. */
	val pencil: Color,
	val penAccent: ActionAccent,
	val pencilAccent: ActionAccent
) {

	/**
	 * The ink for [mode], or `null` while the beta is off - the caller then keeps its own colour.
	 *
	 * *Which* glyphs it lands on is the caller's to decide, and the two modes differ: see the class comment.
	 */
	fun inkOf(mode: InputMode): Color? = when {
		!this.enabled -> null
		mode == InputMode.PEN -> this.pen
		else -> this.pencil
	}

	/** The gradient a mode's controls light up in. [MODE_ACCENT] for both while the beta is off. */
	fun accentOf(mode: InputMode): ActionAccent = when {
		!this.enabled -> MODE_ACCENT
		mode == InputMode.PEN -> this.penAccent
		else -> this.pencilAccent
	}

	companion object {

		/** The accent both toggles have always used, and still do while the beta is off. */
		val MODE_ACCENT = ActionAccent.INDIGO

		/** The beta switched off: what every screen drew before it existed. */
		val OFF = InkColors(
			enabled = false,
			pen = Color.Unspecified,
			pencil = Color.Unspecified,
			penAccent = MODE_ACCENT,
			pencilAccent = MODE_ACCENT
		)

		/**
		 * The board's **own** orange and blue, not a new pair.
		 *
		 * Both already exist in the classic palette as the same-value mark - the orange is what a marked digit
		 * is drawn in on a light board, the blue what it is drawn in on a dark one - and each was picked there
		 * for exactly the property the ink needs: to stay legible on its background and to separate from every
		 * other colour the board assigns a meaning to. The light board's blue is [InkBlueLight], which is that
		 * same statement at the strength a white cell and a note-sized glyph need.
		 *
		 * One colour per *mode* rather than per light/dark: what the ink says is which of the two the player
		 * is writing in, and a colour that changed with the theme would say it differently on each. That is
		 * also why the pen's orange now reaches the same-value mark on a *given* while the beta is on: the
		 * dark palette marks those in its own teal, which on a dark board is the pencil's colour, so the two
		 * modes were saying the same thing there while light mode said them apart.
		 */
		val LIGHT = InkColors(
			enabled = true,
			pen = InkOrange,
			pencil = InkBlueLight,
			penAccent = ActionAccent.AMBER,
			pencilAccent = ActionAccent.SKY
		)

		/** The same two, each at the value a near-black cell needs - the blue is the dark board's own. */
		val DARK = InkColors(
			enabled = true,
			pen = InkOrange,
			pencil = InkBlueDark,
			penAccent = ActionAccent.AMBER,
			pencilAccent = ActionAccent.SKY
		)

		fun of(dark: Boolean): InkColors = if (dark) DARK else LIGHT
	}
}

/** The classic palette's light-mode same-value mark, which is where the pen's ink comes from. */
private val InkOrange = Color(0xFFEF6C00)

/**
 * The pencil's ink on a light board: a saturated blue that is clearly *stronger* than the grey the other
 * notes are drawn in (owner's call, twice).
 *
 * The two colours it replaced both failed against `BoardPalette.pencilMark` (#6F6A7C), and each failed the
 * opposite way. [GradientSkyEnd] #1F5FC4 sits at practically the grey's own lightness (5.2:1 on white
 * against the grey's 5.2:1), so the marked note changed hue and nothing else. [GradientSkyStart] #2B8FE0 is
 * brighter, which made it *weaker* than the grey beside it (3.4:1) - a mark that fades is not a mark.
 *
 * This is 9.0:1 on a white cell: darker and fully saturated where the grey is neither, so it separates by
 * lightness and by chroma at once, which is what a glyph a ninth of a cell wide needs to be picked out of a
 * grid of them. It stays legible on the pale chaos region tints for the same reason.
 */
private val InkBlueLight = Color(0xFF0033CC)

/** Its dark-mode counterpart, and the pencil's ink on a dark board. */
private val InkBlueDark = Color(0xFF4DD9E0)

/** [InkColors.OFF] unless the beta is on - see `SudokuAndroidTheme`. */
val LocalInkColors = staticCompositionLocalOf { InkColors.OFF }

/**
 * Beta item 8 of 2.2.0: whether the row, column and box highlight covers every cell holding the selected
 * number rather than only the tapped one. False unless the beta is on - see `SudokuAndroidTheme`.
 *
 * A composition local next to [LocalInkColors] and for the same reason: the board is drawn by the play
 * screen and by all three match screens, and none of them is where this belongs. The rule itself is
 * `net.luis.sudoku.domain.PeerHighlightRules`; this is only how the switch reaches it.
 */
val LocalEveryOccurrencePeers = staticCompositionLocalOf { false }
