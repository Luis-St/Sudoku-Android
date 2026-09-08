package net.luis.sudoku.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import net.luis.sudoku.R

/**
 * Archivo, at two weights and nothing else.
 *
 * **400 and 600, never anything between or beyond.** That is not a supply problem - the family ships nine
 * weights - it is the rule that makes hierarchy legible: a scale with four weights has three near misses in
 * it, and a reader cannot tell a 500 from a 600 at 14sp on a phone. Everything that has to outrank something
 * else does it with weight *and* colour instead, which is why a `titleMedium` in `onSurface` reads as more
 * important than a `bodyLarge` in `onSurfaceVariant` at exactly the same size.
 *
 * Both files are static instances cut from the variable original at `wght` 400 and 600 with `wdth` pinned to
 * 100, so no axis is left for the platform to interpret differently between two Android versions. Licence in
 * `ARCHIVO-OFL.txt` at the repository root.
 */
private val Archivo = FontFamily(
	Font(R.font.archivo_regular, FontWeight.Normal),
	Font(R.font.archivo_semibold, FontWeight.SemiBold)
)

/**
 * The full Material scale, every role given a size, a line height, a weight and a tracking value.
 *
 * All fifteen, rather than the two or three a screen happens to use today: an unassigned role keeps
 * Material's own baseline - Roboto at Material's metrics - and the failure that produces is a single label
 * somewhere in the app rendering in a different typeface from everything around it, which nothing catches
 * because nothing crashes. It is the same shape of bug as the unassigned `surfaceContainerHigh` that made a
 * dialog arrive lavender-grey (account item 1), and it gets the same answer: assign all of them.
 *
 * The tracking values are the part that is easy to drop and hard to notice. Display sizes take negative
 * tracking because letterforms at 57sp look loose at zero; body and label sizes take positive tracking
 * because the same forms at 11sp run together. A scale copied without them is a scale that is subtly wrong
 * at both ends and correct only in the middle.
 *
 * The board's own digits are deliberately not here (see [BoardTextStyles]).
 */
val Typography = Typography(
	displayLarge = archivo(57, 64, FontWeight.Normal, (-0.25).sp),
	displayMedium = archivo(45, 52, FontWeight.Normal, 0.sp),
	displaySmall = archivo(36, 44, FontWeight.Normal, 0.sp),
	headlineLarge = archivo(32, 40, FontWeight.SemiBold, 0.sp),
	headlineMedium = archivo(28, 36, FontWeight.SemiBold, 0.sp),
	headlineSmall = archivo(24, 32, FontWeight.SemiBold, 0.sp),
	titleLarge = archivo(22, 28, FontWeight.SemiBold, 0.sp),
	titleMedium = archivo(16, 24, FontWeight.SemiBold, 0.15.sp),
	titleSmall = archivo(14, 20, FontWeight.SemiBold, 0.1.sp),
	bodyLarge = archivo(16, 24, FontWeight.Normal, 0.5.sp),
	bodyMedium = archivo(14, 20, FontWeight.Normal, 0.25.sp),
	bodySmall = archivo(12, 16, FontWeight.Normal, 0.4.sp),
	labelLarge = archivo(14, 20, FontWeight.SemiBold, 0.1.sp),
	labelMedium = archivo(12, 16, FontWeight.SemiBold, 0.5.sp),
	labelSmall = archivo(11, 16, FontWeight.SemiBold, 0.5.sp)
)

private fun archivo(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: TextUnit) = TextStyle(
	fontFamily = Archivo,
	fontWeight = weight,
	fontSize = size.sp,
	lineHeight = lineHeight.sp,
	letterSpacing = letterSpacing
)

/**
 * The three styles a board is drawn with, and the one exception to the scale above.
 *
 * A digit in a cell is not text in a layout: its size is decided by how big the cell is, so the play board
 * and the lesson board both compute it from the measured cell rather than reading a role. What they take
 * from here is everything *else* about the glyph - the family and the weight that say "given" or "entered"
 * - which is what stops a board from being the one surface in the app in a different typeface.
 *
 * The sizes named here are the design's own for a 9x9 at the target frame, and are what a board falls back
 * to when it has no measurement to scale from.
 */
object BoardTextStyles {

	/** A clue: the same size as an entry and heavier, which is the whole of how the two are told apart. */
	val given = archivo(22, 24, FontWeight.SemiBold, 0.sp)

	/** A digit the player placed. */
	val entry = archivo(22, 24, FontWeight.Normal, 0.sp)

	/** One pencil mark, in the 3x3 sub-grid inside a cell. */
	val note = archivo(10, 12, FontWeight.Normal, 0.sp)
}
