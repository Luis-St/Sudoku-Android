package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a difficulty picker may offer, and what a selection becomes when the size or the variant changes
 * under it.
 *
 * Two cases are worth having tests for. A 6x6 grid's reachable bands have a *hole* in them, so a picker built
 * on a ceiling would offer four bands that grid cannot produce. And a 16x16 grid answers differently per
 * variant - jigsaw stops at band 8, classic reaches all fifteen - so a picker built on the size alone would
 * offer a jigsaw player seven bands their board cannot make. In both cases the player who picks one is quietly
 * handed a different puzzle, since the generator snaps the request onto what it can build.
 */
class DifficultyOptionsTest {

	/** Every (size, variant) pair that exists, since chaos does not exist at 4x4. */
	private fun grids(): List<Pair<GridSize, Variant>> =
		GridSize.values().flatMap { size ->
			Variant.values().filter { it.isSupportedAt(size) }.map { size to it }
		}

	@Test
	fun supportedAt_fourByFour_isBandOneAlone() {
		assertEquals(listOf(Difficulty.ONE), DifficultyOptions.supportedAt(GridSize.FOUR, Variant.CLASSIC))
	}

	@Test
	fun supportedAt_sixBySix_hasAGapRatherThanACeiling() {
		assertEquals(
			listOf(Difficulty.ONE, Difficulty.TWO, Difficulty.THREE, Difficulty.SEVEN, Difficulty.EIGHT),
			DifficultyOptions.supportedAt(GridSize.SIX, Variant.CLASSIC)
		)
	}

	@Test
	fun supportedAt_nineByNine_offersAllFifteen() {
		val supported = DifficultyOptions.supportedAt(GridSize.NINE, Variant.CLASSIC)

		assertEquals(15, supported.size)
		assertEquals(Difficulty.ONE, supported.first())
		assertEquals(Difficulty.LISA, supported.last())
	}

	@Test
	fun supportedAt_sixteenByChaos_stopsAtBandEight() {
		// The measured cliff: a 16x16 jigsaw lands its target 17 times in 32 at band 8 and single digits above
		// it, because the Law of Leftovers solves the hard boards too easily to rate where they were asked to.
		val chaos = DifficultyOptions.supportedAt(GridSize.SIXTEEN, Variant.CHAOS)

		assertEquals(8, chaos.size)
		assertEquals(Difficulty.EIGHT, chaos.last())
		assertEquals(15, DifficultyOptions.supportedAt(GridSize.SIXTEEN, Variant.CLASSIC).size)
	}

	@Test
	fun supportedAt_isAscending() {
		grids().forEach { (size, variant) ->
			val indices = DifficultyOptions.supportedAt(size, variant).map(Difficulty::index)
			assertEquals("bands must be offered in ascending order at $size/$variant", indices.sorted(), indices)
		}
	}

	@Test
	fun snap_keepsASupportedBandUntouched() {
		assertEquals(Difficulty.SEVEN, DifficultyOptions.snap(GridSize.SIX, Variant.CLASSIC, Difficulty.SEVEN))
		assertEquals(Difficulty.FIVE, DifficultyOptions.snap(GridSize.NINE, Variant.CLASSIC, Difficulty.FIVE))
	}

	@Test
	fun snap_movesAnUnreachableBandOntoTheNearestOne() {
		// Five is two from three and two from seven; the easier of the two wins, because a player who asked
		// for a band this grid cannot make is better served by an easier puzzle than a harder one.
		assertEquals(Difficulty.THREE, DifficultyOptions.snap(GridSize.SIX, Variant.CLASSIC, Difficulty.FIVE))
		assertEquals(Difficulty.THREE, DifficultyOptions.snap(GridSize.SIX, Variant.CLASSIC, Difficulty.FOUR))
		assertEquals(Difficulty.SEVEN, DifficultyOptions.snap(GridSize.SIX, Variant.CLASSIC, Difficulty.SIX))
	}

	@Test
	fun snap_atSixteenByChaos_bringsHardBandsDownToEight() {
		// The same selection means different things on the two 16x16 boards, which is the whole reason the
		// variant is a parameter.
		assertEquals(Difficulty.EIGHT, DifficultyOptions.snap(GridSize.SIXTEEN, Variant.CHAOS, Difficulty.THIRTEEN))
		assertEquals(Difficulty.THIRTEEN, DifficultyOptions.snap(GridSize.SIXTEEN, Variant.CLASSIC, Difficulty.THIRTEEN))
	}

	@Test
	fun snap_atFourByFour_collapsesEveryBandOntoOne() {
		Difficulty.values().forEach { band ->
			assertEquals(Difficulty.ONE, DifficultyOptions.snap(GridSize.FOUR, Variant.CLASSIC, band))
		}
	}

	@Test
	fun snap_alwaysLandsOnSomethingTheGridCanProduce() {
		grids().forEach { (size, variant) ->
			val supported = DifficultyOptions.supportedAt(size, variant)
			Difficulty.values().forEach { band ->
				assertTrue(
					"snapping $band at $size/$variant must land in the supported set",
					DifficultyOptions.snap(size, variant, band) in supported
				)
			}
		}
	}

	@Test
	fun multiplayerSupportedAt_neverOffersLisa() {
		grids().forEach { (size, variant) ->
			assertFalse(
				"Lisa is rejected by every multiplayer mode",
				DifficultyOptions.multiplayerSupportedAt(size, variant).any(Difficulty::isLisa)
			)
		}
		assertEquals(14, DifficultyOptions.multiplayerSupportedAt(GridSize.NINE, Variant.CLASSIC).size)
	}

	@Test
	fun snapForMultiplayer_neverLandsOnLisa() {
		grids().forEach { (size, variant) ->
			Difficulty.values().forEach { band ->
				val snapped = DifficultyOptions.snapForMultiplayer(size, variant, band)
				assertFalse("$band at $size/$variant snapped onto Lisa", snapped.isLisa)
				assertTrue(snapped in DifficultyOptions.multiplayerSupportedAt(size, variant))
			}
		}
	}

	@Test
	fun snapForMultiplayer_atFourByFour_isBandOne() {
		assertEquals(Difficulty.ONE, DifficultyOptions.snapForMultiplayer(GridSize.FOUR, Variant.CLASSIC, Difficulty.FOURTEEN))
	}
}
