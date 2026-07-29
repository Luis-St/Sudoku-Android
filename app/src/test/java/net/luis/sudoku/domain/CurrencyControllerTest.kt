package net.luis.sudoku.domain

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
	fun awardForNormalSolve_onANineByNine_isFiveTimesDifficultyIndex() {
		val controller = controller()

		val awarded = controller.awardForNormalSolve(3, 9)

		assertEquals(15L, awarded)
		assertEquals(15L, controller.balance)
	}

	@Test
	fun awardForNormalSolve_lisaIndexSix_onANineByNine_isThirty() {
		val controller = controller()

		assertEquals(30L, controller.awardForNormalSolve(6, 9))
	}

	@Test
	fun awardForNormalSolve_scalesWithTheGrid() {
		// 5 * 3 = 15 on a 9x9, times the size factor, rounded half up.
		assertEquals(6L, controller().awardForNormalSolve(3, 4))
		assertEquals(9L, controller().awardForNormalSolve(3, 6))
		assertEquals(15L, controller().awardForNormalSolve(3, 9))
		assertEquals(23L, controller().awardForNormalSolve(3, 12))
		assertEquals(33L, controller().awardForNormalSolve(3, 16))
	}

	@Test
	fun awardForNormalSolve_aSmallGridPaysLessThanALargeOne_atEveryTier() {
		for (difficultyIndex in 1..6) {
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

		assertEquals(10L, awarded)
		assertEquals(1, controller.currentNormalGamesEarnedToday)
	}

	@Test
	fun awardForDailySolve_isOutsideTheCapAndAddsTheBonus() {
		val controller = controller(earnedToday = 10)

		val awarded = controller.awardForDailySolve(3, 9)

		assertEquals(35L, awarded) // 5*3 + 20
		assertEquals(35L, controller.balance)
	}

	@Test
	fun awardForDailySolve_scalesTheBaseButNotTheBonus() {
		// 23 for the grid (5*3 * 1.5, rounded half up) plus the flat 20.
		assertEquals(43L, controller().awardForDailySolve(3, 12))
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
