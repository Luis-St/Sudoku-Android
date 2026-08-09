package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** feature-spec §6a: earning, the daily cap, the daily bonus, and spending. */
class CurrencyControllerTest {

	private val today = LocalDate.of(2026, 7, 27)

	private fun controller(balance: Long = 0, earnedToday: Int = 0, earnDate: LocalDate? = today) =
		CurrencyController(balance, earnedToday, earnDate) { this.today }

	@Test
	fun awardForNormalSolve_onANineByNine_scalesWithTheTierWeight() {
		val controller = controller()

		val awarded = controller.awardForNormalSolve(3, 9)

		assertEquals(9L, awarded)
		assertEquals(9L, controller.balance)
	}

	@Test
	fun awardForNormalSolve_lisa_onANineByNine_isThirty() {
		val controller = controller()

		assertEquals(30L, controller.awardForNormalSolve(Difficulty.LISA.index(), 9))
	}

	/**
	 * The fifteen tier rework moved Lisa from index 6 to index 15. The award is linear in the tier weight,
	 * not in the index, precisely so the two ends of the scale keep paying what they always paid instead of
	 * inflating 2.5x. These two are the anchors the server calibrates against, so they must not move.
	 */
	@Test
	fun awardForNormalSolve_theEndsOfTheScaleAreUnchangedByTheRework() {
		assertEquals(5L, controller().awardForNormalSolve(Difficulty.ONE.index(), 9))
		assertEquals(30L, controller().awardForNormalSolve(Difficulty.LISA.index(), 9))
	}

	/** Drift from `CurrencyService.baseAward` gets an honest offline player clamped on sync. */
	@Test
	fun awardForNormalSolve_mirrorsTheServerAwardAtEveryTierOnANineByNine() {
		// Printed from CurrencyService.baseAward(tier, NINE) on the server, not recomputed from the formula.
		val expected = listOf(5L, 7L, 9L, 10L, 12L, 14L, 16L, 18L, 19L, 21L, 23L, 25L, 26L, 28L, 30L)

		expected.forEachIndexed { offset, amount ->
			assertEquals("tier ${offset + 1}", amount, controller().awardForNormalSolve(offset + 1, 9))
		}
	}

	@Test
	fun awardForNormalSolve_neverPaysLessForAHarderTier() {
		val awards = (1..Difficulty.LISA.index()).map { controller().awardForNormalSolve(it, 9) }

		assertEquals(awards.sorted(), awards)
	}

	@Test
	fun awardForNormalSolve_scalesWithTheGrid() {
		// 9 on a 9x9, times the size factor, rounded half up.
		assertEquals(3L, controller().awardForNormalSolve(3, 4))
		assertEquals(5L, controller().awardForNormalSolve(3, 6))
		assertEquals(9L, controller().awardForNormalSolve(3, 9))
		assertEquals(13L, controller().awardForNormalSolve(3, 12))
		assertEquals(19L, controller().awardForNormalSolve(3, 16))
	}

	@Test
	fun awardForNormalSolve_aSmallGridPaysLessThanALargeOne_atEveryTier() {
		for (difficultyIndex in 1..Difficulty.LISA.index()) {
			val small = controller().awardForNormalSolve(difficultyIndex, 4)
			val large = controller().awardForNormalSolve(difficultyIndex, 16)

			assertTrue("tier $difficultyIndex", small < large)
		}
	}

	@Test
	fun awardForNormalSolve_onAnUnsupportedEdgeLength_throws() {
		assertThrows(IllegalArgumentException::class.java) { controller().awardForNormalSolve(3, 5) }
	}

	@Test
	fun awardForNormalSolve_capsAtTenPerDay() {
		val controller = controller(earnedToday = 10)

		val awarded = controller.awardForNormalSolve(3, 9)

		assertEquals(0L, awarded)
		assertEquals(0L, controller.balance)
	}

	@Test
	fun awardForNormalSolve_theTenthGameStillEarns_theEleventhDoesNot() {
		val controller = controller(earnedToday = 9)

		assertEquals(5L, controller.awardForNormalSolve(1, 9))
		assertEquals(0L, controller.awardForNormalSolve(1, 9))
	}

	@Test
	fun awardForNormalSolve_onANewDay_resetsTheCap() {
		val controller = controller(earnedToday = 10, earnDate = today.minusDays(1))

		val awarded = controller.awardForNormalSolve(2, 9)

		assertEquals(7L, awarded)
		assertEquals(1, controller.currentNormalGamesEarnedToday)
	}

	@Test
	fun awardForDailySolve_isOutsideTheCapAndAddsTheBonus() {
		val controller = controller(earnedToday = 10)

		val awarded = controller.awardForDailySolve(3, 9)

		assertEquals(29L, awarded) // 9 for the grid + 20
		assertEquals(29L, controller.balance)
	}

	@Test
	fun awardForDailySolve_scalesTheBaseButNotTheBonus() {
		// 13 for the grid (9 * 1.5, rounded half up) plus the flat 20.
		assertEquals(33L, controller().awardForDailySolve(3, 12))
	}

	@Test
	fun spend_succeedsWhenAffordableAndFailsOtherwise() {
		val controller = controller(balance = 10)

		assertFalse(controller.spend(11))
		assertEquals(10L, controller.balance)

		assertTrue(controller.spend(10))
		assertEquals(0L, controller.balance)
	}

	@Test
	fun applyServerBalance_overwritesLocalBalanceSilently() {
		val controller = controller(balance = 999)

		controller.applyServerBalance(7)

		assertEquals(7L, controller.balance)
	}
}
