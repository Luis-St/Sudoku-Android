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
 * They are the colours the same-value mark uses (owner's call: reuse what the board already has), which is
 * why the mark itself moves while the beta is on: a marked pen digit takes the *pencil's* ink and a marked
 * note the *pen's*, so it still differs from the ink it replaces - see `CellView`. Nothing else on the
 * board is affected, since neither hue is the mistake red, the hint yellow or either hint-note colour.
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

	/** The ink for [mode], or `null` while the beta is off - the caller then keeps its own colour. */
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
		 * other colour the board assigns a meaning to. The light blue is the app's own sky accent
		 * ([GradientSkyEnd]), which is that same hue at the value a white cell needs.
		 *
		 * One colour per *mode* rather than per light/dark: what the ink says is which of the two the player
		 * is writing in, and a colour that changed with the theme would say it differently on each.
		 */
		val LIGHT = InkColors(
			enabled = true,
			pen = InkOrange,
			pencil = GradientSkyEnd,
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

/** Its dark-mode counterpart, and the pencil's ink on a dark board. */
private val InkBlueDark = Color(0xFF4DD9E0)

/** [InkColors.OFF] unless the beta is on - see `SudokuAndroidTheme`. */
val LocalInkColors = staticCompositionLocalOf { InkColors.OFF }
