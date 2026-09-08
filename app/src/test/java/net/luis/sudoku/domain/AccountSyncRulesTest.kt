package net.luis.sudoku.domain

import net.luis.sudoku.data.local.CurrencyState
import net.luis.sudoku.difficulty.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Covers [AccountSyncRules] - which side of a two-device account wins, and when.
 *
 * These are the cases that made a linked device look broken: it had nothing of its own to show, and
 * everything it did have it was willing to push over what the account already held.
 */
class AccountSyncRulesTest {

	private val today = LocalDate.of(2026, 8, 10)

	private fun record(
		date: LocalDate? = today,
		solved: Boolean = false,
		streak: Int = 0,
		lastCompletedDate: LocalDate? = null,
		restorableMissedDays: Int = 0,
		restorableUntil: LocalDate? = null
	) = DailyRecord(
		date = date,
		solved = solved,
		attempts = 0,
		solvedElapsedMillis = null,
		streak = streak,
		lastCompletedDate = lastCompletedDate,
		restorableMissedDays = restorableMissedDays,
		restorableUntil = restorableUntil,
		activeDifficulty = Difficulty.FIVE,
		pendingDifficulty = null,
		pendingEffectiveDate = null
	)

	// --- currency ---

	@Test
	fun currencyToReport_aDeviceThatHasEarnedNothing_reportsNothing() {
		// The freshly linked device, and the second device generally. Offering its own number is how a
		// balance spent on the other device would be credited straight back: the server takes the larger.
		assertNull(AccountSyncRules.currencyToReport(CurrencyState(300L, 0, null, reconciledBalance = 300L)))
	}

	@Test
	fun currencyToReport_aBalanceMintedOffline_isOffered() {
		assertEquals(340L, AccountSyncRules.currencyToReport(CurrencyState(340L, 0, null, reconciledBalance = 300L)))
	}

	@Test
	fun currencyToReport_aBalanceBelowTheLastReconciled_reportsNothing() {
		// Spent elsewhere and already adopted, or simply behind. Either way this device has nothing to add.
		assertNull(AccountSyncRules.currencyToReport(CurrencyState(200L, 0, null, reconciledBalance = 300L)))
	}

	@Test
	fun currencyToReport_aDeviceThatHasNeverSynced_offersWhatItMinted() {
		// reconciledBalance is 0 until a server answers, so an offline-only history is offered whole - which
		// is the behaviour that existed before any of this and must not regress.
		assertEquals(120L, AccountSyncRules.currencyToReport(CurrencyState(120L, 0, null)))
	}

	// --- the daily difficulty ---

	@Test
	fun adoptDailyDifficulty_aDeviceWithNoDailyForToday_takesTheTierNow() {
		// The freshly linked device. Queuing it for tomorrow would mean the two devices play different
		// grids today, for no gain: there is no attempt in progress here to protect.
		val adopted = AccountSyncRules.adoptDailyDifficulty(record(date = null), Difficulty.TEN, today)

		assertEquals(Difficulty.TEN, adopted?.activeDifficulty)
		assertNull(adopted?.pendingDifficulty)
	}

	@Test
	fun adoptDailyDifficulty_aDeviceAlreadyOnTodaysDaily_queuesItForTomorrow() {
		// Today's tier was fixed when the day began (server-spec 8.1), and the player may be mid-puzzle.
		val adopted = AccountSyncRules.adoptDailyDifficulty(record(date = today), Difficulty.TEN, today)

		assertEquals(Difficulty.FIVE, adopted?.activeDifficulty)
		assertEquals(Difficulty.TEN, adopted?.pendingDifficulty)
		assertEquals(today.plusDays(1), adopted?.pendingEffectiveDate)
	}

	@Test
	fun adoptDailyDifficulty_aTierThisDeviceAlreadyChose_writesNothing() {
		// Including one that is only queued here: re-adopting it would keep rewriting the record on every
		// sync, and every write wakes the collectors reading it.
		val queued = record(date = today).copy(pendingDifficulty = Difficulty.TEN, pendingEffectiveDate = today.plusDays(1))

		assertNull(AccountSyncRules.adoptDailyDifficulty(queued, Difficulty.TEN, today))
	}

	// --- streak ---

    @Test
	fun mergeStreak_aFreshlyLinkedDevice_takesTheAccountsStreak() {
		val merged = AccountSyncRules.mergeStreak(record(), remoteCurrent = 12, remoteLastCompleted = today, today = today)

		assertEquals(12, merged.streak)
		assertEquals(today, merged.lastCompletedDate)
	}

	@Test
	fun mergeStreak_aLocalStreakTheServerHasNotVerified_isNotTakenAway() {
		// Days solved while the server was unreachable. StreakPublisher offers them; adopting the shorter
		// count here would delete them in between the offer and its acceptance.
		val merged = AccountSyncRules.mergeStreak(
			record(streak = 9, lastCompletedDate = today),
			remoteCurrent = 4,
			remoteLastCompleted = today.minusDays(5),
			today = today
		)

		assertEquals(9, merged.streak)
		assertEquals(today, merged.lastCompletedDate)
	}

	@Test
	fun mergeStreak_aDailyTheAccountCompletedToday_marksTodaySolvedHere() {
		// Otherwise the second device offers a daily the account has already played, and the submission it
		// produces is refused.
		val merged = AccountSyncRules.mergeStreak(record(), remoteCurrent = 3, remoteLastCompleted = today, today = today)

		assertTrue(merged.solved)
	}

	@Test
	fun mergeStreak_aRecordLeftOverFromAnotherDay_isNotMarkedSolved() {
		// `solved` is read against the record's own date, so setting it here would mark yesterday's board
		// done rather than today's.
		val merged = AccountSyncRules.mergeStreak(
			record(date = today.minusDays(1)),
			remoteCurrent = 3,
			remoteLastCompleted = today,
			today = today
		)

		assertFalse(merged.solved)
	}

	@Test
	fun mergeStreak_aServerThatKnowsNothing_changesNothing() {
		// A device signed in to an account with no verified dailies must keep what it has, not be zeroed.
		val local = record(streak = 6, solved = true, lastCompletedDate = today)

		assertEquals(local, AccountSyncRules.mergeStreak(local, remoteCurrent = 0, remoteLastCompleted = null, today = today))
	}

	@Test
	fun mergeStreak_aBreakTheServerStillOffersToRepair_isCarriedIntoTheRecord() {
		// Issue 2.2.0/6: the home card warns about the closing window, and this is where it learns of it.
		val merged = AccountSyncRules.mergeStreak(
			record(streak = 1, lastCompletedDate = today),
			remoteCurrent = 1,
			remoteLastCompleted = today,
			today = today,
			remoteRestorableMissedDays = 2,
			remoteRestorableUntil = today.plusDays(6)
		)

		assertEquals(2, merged.restorableMissedDays)
		assertEquals(today.plusDays(6), merged.restorableUntil)
	}

	@Test
	fun mergeStreak_aBreakRepairedOnAnotherDevice_isClearedHere() {
		// Adopted outright rather than merged upward, or this device would go on offering a restore that
		// has already been paid for elsewhere.
		val merged = AccountSyncRules.mergeStreak(
			record(streak = 8, lastCompletedDate = today, restorableMissedDays = 2, restorableUntil = today.plusDays(4)),
			remoteCurrent = 8,
			remoteLastCompleted = today,
			today = today
		)

		assertEquals(0, merged.restorableMissedDays)
		assertNull(merged.restorableUntil)
	}

	// --- issue 2.2.2/1: two runs are merged as runs, not as a number and a date ---

	@Test
	fun mergeStreak_aRunThatRestartedTodayAndAnOlderBrokenOne_keepsTheLiveRun() {
		// The reported inflation. The account's run ended two days ago, this device solved today after
		// missing a day - taking the larger count with today's date on it invents a 22 day run that then
		// gets published back to the server as one.
		val merged = AccountSyncRules.mergeStreak(
			record(streak = 1, solved = true, lastCompletedDate = today),
			remoteCurrent = 22,
			remoteLastCompleted = today.minusDays(2),
			today = today
		)

		assertEquals(1, merged.streak)
		assertEquals(today, merged.lastCompletedDate)
	}

	@Test
	fun mergeStreak_twoRunsThatMeet_countTheDaysTheyCoverBetweenThem() {
		// The everyday case: the account verified through yesterday, this device solved today offline.
		val merged = AccountSyncRules.mergeStreak(
			record(streak = 1, solved = true, lastCompletedDate = today),
			remoteCurrent = 22,
			remoteLastCompleted = today.minusDays(1),
			today = today
		)

		assertEquals(23, merged.streak)
		assertEquals(today, merged.lastCompletedDate)
	}

	@Test
	fun mergeRuns_aRunWithNoAnchor_keepsTheLongerCount() {
		// A record written before `lastCompletedDate` existed cannot be placed on the calendar, so there is
		// nothing to contradict it with.
		assertEquals(9 to today, AccountSyncRules.mergeRuns(9, null, 4, today))
	}

	@Test
	fun mergeRuns_aRunInsideAnother_isNotAddedToIt() {
		assertEquals(10 to today, AccountSyncRules.mergeRuns(3, today, 10, today))
	}
}
