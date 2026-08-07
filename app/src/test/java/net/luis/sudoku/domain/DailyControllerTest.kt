package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** feature-spec §8: daily derivation, locking, retries, and the streak's increment/break rules. */
class DailyControllerTest {

	private val day1 = LocalDate.of(2026, 7, 27)

	private fun controllerOn(date: LocalDate) = DailyController(serverId = "local") { date }

	@Test
	fun keyFor_isDeterministic_sameDateSameDifficulty_sameKey() {
		val controller = controllerOn(day1)

		val a = controller.keyFor(day1, GridSize.NINE, Difficulty.THREE)
		val b = controller.keyFor(day1, GridSize.NINE, Difficulty.THREE)

		assertEquals(a, b)
	}

	@Test
	fun keyFor_differentDifficulty_isADifferentPuzzle() {
		val controller = controllerOn(day1)

		val three = controller.keyFor(day1, GridSize.NINE, Difficulty.THREE)
		val four = controller.keyFor(day1, GridSize.NINE, Difficulty.FOUR)

		assertFalse(three == four)
	}

	@Test
	fun keyFor_differentDate_isADifferentSeed() {
		val controller = controllerOn(day1)

		val today = controller.keyFor(day1, GridSize.NINE, Difficulty.THREE)
		val tomorrow = controller.keyFor(day1.plusDays(1), GridSize.NINE, Difficulty.THREE)

		assertFalse(today.seed() == tomorrow.seed())
	}

	@Test
	fun rollover_freshRecord_setsTodayAndDefaultDifficulty() {
		val controller = controllerOn(day1)

		val rolled = controller.rollover(DailyRecord.INITIAL)

		assertEquals(day1, rolled.date)
		assertFalse(rolled.solved)
		assertEquals(0, rolled.attempts)
		assertEquals(Difficulty.THREE, rolled.activeDifficulty)
	}

	@Test
	fun rollover_sameDayTwice_isANoOp() {
		val controller = controllerOn(day1)
		val rolled = controller.rollover(DailyRecord.INITIAL)

		val rolledAgain = controller.rollover(rolled)

		assertEquals(rolled, rolledAgain)
	}

	@Test
	fun rollover_dayEndedSolved_streakSurvives() {
		val today = controllerOn(day1).rollover(DailyRecord.INITIAL)
		val solvedToday = controllerOn(day1).recordSuccess(today, 60_000L)

		val tomorrow = controllerOn(day1.plusDays(1)).rollover(solvedToday)

		assertEquals(1, tomorrow.streak)
		assertFalse(tomorrow.solved)
	}

	@Test
	fun rollover_dayEndedUnsolved_breaksTheStreak() {
		val today = controllerOn(day1).rollover(DailyRecord.INITIAL)
		val solvedToday = controllerOn(day1).recordSuccess(today, 60_000L) // streak = 1

		val tomorrowController = controllerOn(day1.plusDays(1))
		val tomorrow = tomorrowController.rollover(solvedToday) // streak survives, not solved today
		val dayAfter = controllerOn(day1.plusDays(2)).rollover(tomorrow) // tomorrow ended unsolved

		assertEquals(0, dayAfter.streak)
	}

	@Test
	fun canPlay_isFalseOnceSolved() {
		val controller = controllerOn(day1)
		val today = controller.rollover(DailyRecord.INITIAL)

		assertTrue(controller.canPlay(today))
		val solved = controller.recordSuccess(today, 1000L)
		assertFalse(controller.canPlay(solved))
	}

	@Test
	fun setDifficulty_takesEffectOnlyFromTomorrow() {
		val controller = controllerOn(day1)
		val today = controller.rollover(DailyRecord.INITIAL)

		val changed = controller.setDifficulty(today, Difficulty.FIVE)

		assertEquals(Difficulty.THREE, controller.effectiveDifficulty(changed)) // unchanged today
		val tomorrow = controllerOn(day1.plusDays(1)).rollover(changed)
		assertEquals(Difficulty.FIVE, tomorrow.activeDifficulty)
	}

	@Test
	fun recordAttemptStart_incrementsAttempts() {
		val controller = controllerOn(day1)
		val today = controller.rollover(DailyRecord.INITIAL)

		val first = controller.recordAttemptStart(today)
		val second = controller.recordAttemptStart(first)

		assertEquals(2, second.attempts)
	}

	// --- lastCompletedDate, the anchor StreakPublisher sends (server-spec §8.3) ---

	@Test
	fun recordSuccess_anchorsTheStreakToTheDaySolved() {
		val today = controllerOn(day1).rollover(DailyRecord.INITIAL)

		val solved = controllerOn(day1).recordSuccess(today, 60_000L)

		assertEquals(day1, solved.lastCompletedDate)
	}

	@Test
	fun rollover_dayEndedSolved_carriesTheAnchorForward() {
		val today = controllerOn(day1).rollover(DailyRecord.INITIAL)
		val solvedToday = controllerOn(day1).recordSuccess(today, 60_000L)

		val tomorrow = controllerOn(day1.plusDays(1)).rollover(solvedToday)

		assertEquals(day1, tomorrow.lastCompletedDate)
	}

	@Test
	fun rollover_dayEndedUnsolved_clearsTheAnchorWithTheStreak() {
		val today = controllerOn(day1).rollover(DailyRecord.INITIAL)
		val solvedToday = controllerOn(day1).recordSuccess(today, 60_000L)
		val skipped = controllerOn(day1.plusDays(1)).rollover(solvedToday)

		val afterSkip = controllerOn(day1.plusDays(2)).rollover(skipped)

		assertEquals(0, afterSkip.streak)
		assertEquals(null, afterSkip.lastCompletedDate)
	}

	@Test
	fun recordSuccess_onConsecutiveDays_movesTheAnchorWithTheStreak() {
		val day1Record = controllerOn(day1).recordSuccess(controllerOn(day1).rollover(DailyRecord.INITIAL), 60_000L)
		val day2 = day1.plusDays(1)

		val day2Record = controllerOn(day2).recordSuccess(controllerOn(day2).rollover(day1Record), 60_000L)

		assertEquals(2, day2Record.streak)
		assertEquals(day2, day2Record.lastCompletedDate)
	}
}
