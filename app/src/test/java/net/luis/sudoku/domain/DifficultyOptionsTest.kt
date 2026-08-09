package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a difficulty picker may offer, and what a selection becomes when the size changes under it.
 *
 * The 6x6 case is the one worth having a test for: its reachable bands have a *hole* in them, so a picker
 * built on a ceiling would offer four bands that grid cannot produce. A player who picks one of them is
 * quietly handed a different puzzle, since the generator snaps the request onto what it can build.
 */
class DifficultyOptionsTest {

	@Test
	fun supportedAt_fourByFour_isBandOneAlone() {
		assertEquals(listOf(Difficulty.ONE), DifficultyOptions.supportedAt(GridSize.FOUR))
	}

	@Test
	fun supportedAt_sixBySix_hasAGapRatherThanACeiling() {
		assertEquals(
			listOf(Difficulty.ONE, Difficulty.TWO, Difficulty.THREE, Difficulty.SEVEN, Difficulty.EIGHT),
			DifficultyOptions.supportedAt(GridSize.SIX)
		)
	}

	@Test
	fun supportedAt_nineByNine_offersAllFifteen() {
		val supported = DifficultyOptions.supportedAt(GridSize.NINE)

		assertEquals(15, supported.size)
		assertEquals(Difficulty.ONE, supported.first())
		assertEquals(Difficulty.LISA, supported.last())
	}

	@Test
	fun supportedAt_isAscending() {
		GridSize.values().forEach { size ->
			val indices = DifficultyOptions.supportedAt(size).map(Difficulty::index)
			assertEquals("bands must be offered in ascending order at $size", indices.sorted(), indices)
		}
	}

	@Test
	fun snap_keepsASupportedBandUntouched() {
		assertEquals(Difficulty.SEVEN, DifficultyOptions.snap(GridSize.SIX, Difficulty.SEVEN))
		assertEquals(Difficulty.FIVE, DifficultyOptions.snap(GridSize.NINE, Difficulty.FIVE))
	}

	@Test
	fun snap_movesAnUnreachableBandOntoTheNearestOne() {
		// Five is two from three and two from seven; the easier of the two wins, because a player who asked
		// for a band this grid cannot make is better served by an easier puzzle than a harder one.
		assertEquals(Difficulty.THREE, DifficultyOptions.snap(GridSize.SIX, Difficulty.FIVE))
		assertEquals(Difficulty.THREE, DifficultyOptions.snap(GridSize.SIX, Difficulty.FOUR))
		assertEquals(Difficulty.SEVEN, DifficultyOptions.snap(GridSize.SIX, Difficulty.SIX))
	}

	@Test
	fun snap_atFourByFour_collapsesEveryBandOntoOne() {
		Difficulty.values().forEach { band ->
			assertEquals(Difficulty.ONE, DifficultyOptions.snap(GridSize.FOUR, band))
		}
	}

	@Test
	fun snap_alwaysLandsOnSomethingTheSizeCanProduce() {
		GridSize.values().forEach { size ->
			val supported = DifficultyOptions.supportedAt(size)
			Difficulty.values().forEach { band ->
				assertTrue("snapping $band at $size must land in the supported set", DifficultyOptions.snap(size, band) in supported)
			}
		}
	}

	@Test
	fun multiplayerSupportedAt_neverOffersLisa() {
		GridSize.values().forEach { size ->
			assertFalse("Lisa is rejected by every multiplayer mode", DifficultyOptions.multiplayerSupportedAt(size).any(Difficulty::isLisa))
		}
		assertEquals(14, DifficultyOptions.multiplayerSupportedAt(GridSize.NINE).size)
	}

	@Test
	fun snapForMultiplayer_neverLandsOnLisa() {
		GridSize.values().forEach { size ->
			Difficulty.values().forEach { band ->
				val snapped = DifficultyOptions.snapForMultiplayer(size, band)
				assertFalse("$band at $size snapped onto Lisa", snapped.isLisa)
				assertTrue(snapped in DifficultyOptions.multiplayerSupportedAt(size))
			}
		}
	}

	@Test
	fun snapForMultiplayer_atFourByFour_isBandOne() {
		assertEquals(Difficulty.ONE, DifficultyOptions.snapForMultiplayer(GridSize.FOUR, Difficulty.FOURTEEN))
	}
}
