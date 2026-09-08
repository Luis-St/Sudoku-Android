package net.luis.sudoku.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The type scale against the table it was drawn from, role by role.
 *
 * A scale is fifteen rows of four numbers, and every way of getting it wrong is silent. A line height copied
 * from the row above sets the text a hair too tight and nothing crashes. A tracking value left at zero on
 * `labelSmall` runs eleven-point letters together and nothing crashes. A role left off entirely keeps
 * Material's own baseline, which is a *different typeface* - and that does not crash either; it renders one
 * label in Roboto in the middle of an app in Archivo, on whichever screen happens to use that role.
 *
 * So the numbers are pinned here rather than trusted. The failure this catches is not a bug in the app, it is
 * a transcription error in a wall of digits nobody can proofread by eye.
 */
class TypographyScaleTest {

	private val typography = Typography

	@Test
	fun displayLarge_matchesTheScale() = assertRole(this.typography.displayLarge, 57, 64, FontWeight.Normal, (-0.25).sp)

	@Test
	fun displayMedium_matchesTheScale() = assertRole(this.typography.displayMedium, 45, 52, FontWeight.Normal, 0.sp)

	@Test
	fun displaySmall_matchesTheScale() = assertRole(this.typography.displaySmall, 36, 44, FontWeight.Normal, 0.sp)

	@Test
	fun headlineLarge_matchesTheScale() = assertRole(this.typography.headlineLarge, 32, 40, FontWeight.SemiBold, 0.sp)

	@Test
	fun headlineMedium_matchesTheScale() = assertRole(this.typography.headlineMedium, 28, 36, FontWeight.SemiBold, 0.sp)

	@Test
	fun headlineSmall_matchesTheScale() = assertRole(this.typography.headlineSmall, 24, 32, FontWeight.SemiBold, 0.sp)

	@Test
	fun titleLarge_matchesTheScale() = assertRole(this.typography.titleLarge, 22, 28, FontWeight.SemiBold, 0.sp)

	@Test
	fun titleMedium_matchesTheScale() = assertRole(this.typography.titleMedium, 16, 24, FontWeight.SemiBold, 0.15.sp)

	@Test
	fun titleSmall_matchesTheScale() = assertRole(this.typography.titleSmall, 14, 20, FontWeight.SemiBold, 0.1.sp)

	@Test
	fun bodyLarge_matchesTheScale() = assertRole(this.typography.bodyLarge, 16, 24, FontWeight.Normal, 0.5.sp)

	@Test
	fun bodyMedium_matchesTheScale() = assertRole(this.typography.bodyMedium, 14, 20, FontWeight.Normal, 0.25.sp)

	@Test
	fun bodySmall_matchesTheScale() = assertRole(this.typography.bodySmall, 12, 16, FontWeight.Normal, 0.4.sp)

	@Test
	fun labelLarge_matchesTheScale() = assertRole(this.typography.labelLarge, 14, 20, FontWeight.SemiBold, 0.1.sp)

	@Test
	fun labelMedium_matchesTheScale() = assertRole(this.typography.labelMedium, 12, 16, FontWeight.SemiBold, 0.5.sp)

	@Test
	fun labelSmall_matchesTheScale() = assertRole(this.typography.labelSmall, 11, 16, FontWeight.SemiBold, 0.5.sp)

	@Test
	fun everyRole_usesTheAppsOwnFamily() {
		// The role nobody assigned is the one this is for. Material fills an unset role with its own baseline
		// style, whose family is `null` - which renders as the platform default, not as the app's font.
		for ((role, style) in this.roles()) {
			assertNotNull("$role has no font family, so it renders in the platform default", style.fontFamily)
			assertEquals(role, this.typography.bodyLarge.fontFamily, style.fontFamily)
		}
	}

	@Test
	fun everyRole_usesOneOfTheTwoWeights() {
		// 400 and 600 and nothing between. A 500 is not a third option, it is a weight the shipped font files
		// do not contain, so the platform synthesises it - and a synthesised weight is a different shape on a
		// Pixel than it is on a Samsung.
		for ((role, style) in this.roles()) {
			val weight = style.fontWeight
			assertTrue(
				"$role is ${weight?.weight}, which is neither 400 nor 600",
				weight == FontWeight.Normal || weight == FontWeight.SemiBold
			)
		}
	}

	@Test
	fun everyRole_leavesRoomForItsOwnText() {
		// A line height below the font size crops descenders, and the way this happens is a row of the table
		// transcribed one line up.
		for ((role, style) in this.roles()) {
			assertTrue(
				"$role sets ${style.fontSize} in a ${style.lineHeight} line",
				style.lineHeight.value >= style.fontSize.value
			)
		}
	}

	@Test
	fun boardStyles_areTheScalesOneException() {
		// The board's digits are sized from the measured cell rather than from a role, so what these carry
		// that matters is the family and the weight - see [BoardTextStyles].
		assertEquals(this.typography.bodyLarge.fontFamily, BoardTextStyles.given.fontFamily)
		assertEquals(this.typography.bodyLarge.fontFamily, BoardTextStyles.entry.fontFamily)
		assertEquals(this.typography.bodyLarge.fontFamily, BoardTextStyles.note.fontFamily)
		assertEquals(FontWeight.SemiBold, BoardTextStyles.given.fontWeight)
		assertEquals(FontWeight.Normal, BoardTextStyles.entry.fontWeight)
		assertEquals(FontWeight.Normal, BoardTextStyles.note.fontWeight)
		// A note sits nine to a cell, so it is the one style that has to be markedly smaller than a digit.
		assertTrue(BoardTextStyles.note.fontSize.value < BoardTextStyles.entry.fontSize.value / 2f)
	}

	private fun assertRole(style: TextStyle, size: Int, lineHeight: Int, weight: FontWeight, tracking: TextUnit) {
		assertEquals(size.sp, style.fontSize)
		assertEquals(lineHeight.sp, style.lineHeight)
		assertEquals(weight, style.fontWeight)
		assertEquals(tracking, style.letterSpacing)
	}

	private fun roles(): List<Pair<String, TextStyle>> = listOf(
		"displayLarge" to this.typography.displayLarge,
		"displayMedium" to this.typography.displayMedium,
		"displaySmall" to this.typography.displaySmall,
		"headlineLarge" to this.typography.headlineLarge,
		"headlineMedium" to this.typography.headlineMedium,
		"headlineSmall" to this.typography.headlineSmall,
		"titleLarge" to this.typography.titleLarge,
		"titleMedium" to this.typography.titleMedium,
		"titleSmall" to this.typography.titleSmall,
		"bodyLarge" to this.typography.bodyLarge,
		"bodyMedium" to this.typography.bodyMedium,
		"bodySmall" to this.typography.bodySmall,
		"labelLarge" to this.typography.labelLarge,
		"labelMedium" to this.typography.labelMedium,
		"labelSmall" to this.typography.labelSmall
	)
}
