package net.luis.sudoku.ui.game

import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.DifficultyOptions
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which waits the loading screen warns about.
 *
 * The offline fallback may build all fifteen bands, so the longest wait it can hand a player is the price of
 * that ruling rather than a fault. What makes it acceptable is that it is announced: a ten-second silence
 * under a spinner reads as a frozen app.
 */
class PuzzleLoadingTest {

	private fun loading(size: GridSize, difficulty: Difficulty, onDevice: Boolean = true) =
		PuzzleLoading(size, Variant.CLASSIC, difficulty, onDevice)

	@Test
	fun slowOnDevice_sixteenAtAnyBand_warns() {
		// Not a shortcut: measured cost at 16x16 is not monotone in the band (Q6, handoff §5). Band 6
		// averages 25.7 s and band 4 takes 1.6 s, while bands 12 and 15 are under two seconds, so a
		// "band N and above" rule would stay silent on two of the three worst cases.
		for (difficulty in Difficulty.values()) {
			assertTrue("$difficulty", loading(GridSize.SIXTEEN, difficulty).slowOnDevice)
		}
	}

	@Test
	fun slowOnDevice_nineOnlyFromTheChainBands_warns() {
		assertTrue(loading(GridSize.NINE, Difficulty.ELEVEN).slowOnDevice)
		assertTrue(loading(GridSize.NINE, Difficulty.LISA).slowOnDevice)
		assertFalse(loading(GridSize.NINE, Difficulty.TEN).slowOnDevice)
		assertFalse(loading(GridSize.NINE, Difficulty.FIVE).slowOnDevice)
	}

	@Test
	fun slowOnDevice_twelveAtEveryBand_staysQuiet() {
		// 12x12 measured as the strongest size of all: 10 to 339 ms average, 1.5 s worst. An earlier
		// threshold lumped it in with 16x16 and warned from band 8, which was simply wrong.
		for (difficulty in Difficulty.values()) {
			assertFalse("$difficulty", loading(GridSize.TWELVE, difficulty).slowOnDevice)
		}
	}

	/**
	 * The small grids are the vacuous-pass trap: a predicate that answered "slow" for every band would still
	 * pass the two tests above, and every 4x4 game, which is band 1 and instant, would carry a warning that
	 * the wait is long.
	 */
	@Test
	fun slowOnDevice_smallGridAtEveryBandItReaches_isNeverSlow() {
		for (size in listOf(GridSize.FOUR, GridSize.SIX)) {
			for (difficulty in DifficultyOptions.supportedAt(size, Variant.CLASSIC)) {
				assertFalse("$size band ${difficulty.index()}", loading(size, difficulty).slowOnDevice)
			}
		}
	}

	/** A fetched puzzle is over before the spinner settles, whatever band it is. */
	@Test
	fun slowOnDevice_puzzleComesFromTheServer_isNeverSlow() {
		assertFalse(loading(GridSize.SIXTEEN, Difficulty.LISA, onDevice = false).slowOnDevice)
	}

	/**
	 * The initial restore starts its wait before the saved row has been read, so the puzzle is genuinely
	 * unknown for a moment. Guessing "slow" there would put the warning on games that never earn it.
	 */
	@Test
	fun slowOnDevice_puzzleNotKnownYet_isNeverSlow() {
		assertFalse(PuzzleLoading(onDevice = true).slowOnDevice)
		assertFalse(PuzzleLoading(size = GridSize.SIXTEEN, onDevice = true).slowOnDevice)
	}
}
