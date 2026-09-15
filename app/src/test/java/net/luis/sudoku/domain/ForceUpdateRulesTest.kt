package net.luis.sudoku.domain

import net.luis.sudoku.data.local.entity.LearnProgressEntity
import net.luis.sudoku.data.remote.dto.LearnProgressEntry
import net.luis.sudoku.data.remote.dto.StatsEntryResponse
import net.luis.sudoku.difficulty.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Covers [ForceUpdateRules] - what a forced resync writes, and the small set of values it refuses.
 *
 * The two halves of the contract pull in opposite directions and both are tested here. A value that is
 * merely *worse* than the one this device holds has to be applied silently, because repairing a device that
 * is holding too much is the entire feature; a value that could not be true of any account has to be left
 * alone, because that is a broken or newer server rather than an instruction.
 */
class ForceUpdateRulesTest {

	private val today = LocalDate.of(2026, 9, 10)

	private fun record(
		date: LocalDate? = today,
		solved: Boolean = false,
		solvedElapsedMillis: Long? = null,
		streak: Int = 0,
		lastCompletedDate: LocalDate? = null,
		restorableMissedDays: Int = 0,
		restorableUntil: LocalDate? = null
	) = DailyRecord(
		date = date,
		solved = solved,
		attempts = 0,
		solvedElapsedMillis = solvedElapsedMillis,
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
	fun balance_aSmallerBalanceThanTheDeviceHolds_isAccepted() {
		// The case the whole mechanism exists for: the device is holding Rhubarb the account does not have.
		assertEquals(120L, ForceUpdateRules.balance(120L))
	}

	@Test
	fun balance_zero_isAccepted() {
		assertEquals(0L, ForceUpdateRules.balance(0L))
	}

	@Test
	fun balance_negative_isRefused() {
		assertNull(ForceUpdateRules.balance(-1L))
	}

	// --- streak ---

	@Test
	fun adoptStreak_aShorterRunThanTheDeviceHolds_isAdopted() {
		val adopted = ForceUpdateRules.adoptStreak(
			record(streak = 40, lastCompletedDate = today),
			remoteCurrent = 3,
			remoteLastCompleted = today.minusDays(1),
			today = today,
			remoteRestorableMissedDays = 0,
			remoteRestorableUntil = null
		)

		assertEquals(3, adopted.streak)
		assertEquals(today.minusDays(1), adopted.lastCompletedDate)
	}

	@Test
	fun adoptStreak_zero_isAdopted() {
		val adopted = ForceUpdateRules.adoptStreak(
			record(streak = 12, lastCompletedDate = today),
			remoteCurrent = 0,
			remoteLastCompleted = null,
			today = today,
			remoteRestorableMissedDays = 0,
			remoteRestorableUntil = null
		)

		assertEquals(0, adopted.streak)
		assertNull(adopted.lastCompletedDate)
	}

	@Test
	fun adoptStreak_aNegativeCount_leavesTheRunAlone() {
		val adopted = ForceUpdateRules.adoptStreak(
			record(streak = 12, lastCompletedDate = today),
			remoteCurrent = -4,
			remoteLastCompleted = today.minusDays(2),
			today = today,
			remoteRestorableMissedDays = 0,
			remoteRestorableUntil = null
		)

		assertEquals(12, adopted.streak)
		// The anchor goes with it: a count and the day it ends on only mean anything together.
		assertEquals(today, adopted.lastCompletedDate)
	}

	@Test
	fun adoptStreak_aCompletionDateInTheFuture_leavesTheRunAlone() {
		val adopted = ForceUpdateRules.adoptStreak(
			record(streak = 12, lastCompletedDate = today),
			remoteCurrent = 3,
			remoteLastCompleted = today.plusDays(1),
			today = today,
			remoteRestorableMissedDays = 0,
			remoteRestorableUntil = null
		)

		assertEquals(12, adopted.streak)
		assertEquals(today, adopted.lastCompletedDate)
	}

	@Test
	fun adoptStreak_anAccountThatCompletedToday_marksTodaySolved() {
		val adopted = ForceUpdateRules.adoptStreak(
			record(solved = false),
			remoteCurrent = 5,
			remoteLastCompleted = today,
			today = today,
			remoteRestorableMissedDays = 0,
			remoteRestorableUntil = null
		)

		assertTrue(adopted.solved)
	}

	@Test
	fun adoptStreak_anAccountThatHasNotCompletedToday_marksTodayUnsolved() {
		// The direction the ordinary merge deliberately cannot go: a solve the server never accepted is
		// exactly the drift a resync is called in to undo, so the daily is offered again.
		val adopted = ForceUpdateRules.adoptStreak(
			record(solved = true, solvedElapsedMillis = 90_000L, streak = 9, lastCompletedDate = today),
			remoteCurrent = 2,
			remoteLastCompleted = today.minusDays(1),
			today = today,
			remoteRestorableMissedDays = 0,
			remoteRestorableUntil = null
		)

		assertFalse(adopted.solved)
		assertNull(adopted.solvedElapsedMillis)
	}

	@Test
	fun adoptStreak_aRecordFromAnEarlierDay_keepsItsOwnSolvedFlag() {
		// The flag is read against the record's date, so writing it here would mark *that* day done.
		val adopted = ForceUpdateRules.adoptStreak(
			record(date = today.minusDays(3), solved = true),
			remoteCurrent = 1,
			remoteLastCompleted = today,
			today = today,
			remoteRestorableMissedDays = 0,
			remoteRestorableUntil = null
		)

		assertTrue(adopted.solved)
	}

	@Test
	fun adoptStreak_aRestoreSpentElsewhere_clearsTheLocalOffer() {
		val adopted = ForceUpdateRules.adoptStreak(
			record(restorableMissedDays = 2, restorableUntil = today.plusDays(4)),
			remoteCurrent = 1,
			remoteLastCompleted = today,
			today = today,
			remoteRestorableMissedDays = 0,
			remoteRestorableUntil = null
		)

		assertEquals(0, adopted.restorableMissedDays)
		assertNull(adopted.restorableUntil)
	}

	@Test
	fun adoptStreak_aNegativeMissedDayCount_readsAsNothingToRestore() {
		val adopted = ForceUpdateRules.adoptStreak(
			record(restorableMissedDays = 2, restorableUntil = today.plusDays(4)),
			remoteCurrent = 1,
			remoteLastCompleted = today,
			today = today,
			remoteRestorableMissedDays = -3,
			remoteRestorableUntil = today.plusDays(4)
		)

		assertEquals(0, adopted.restorableMissedDays)
	}

	@Test
	fun adoptStreak_aRestoreWindowThatHasClosed_isDropped() {
		val adopted = ForceUpdateRules.adoptStreak(
			record(),
			remoteCurrent = 1,
			remoteLastCompleted = today,
			today = today,
			remoteRestorableMissedDays = 2,
			remoteRestorableUntil = today.minusDays(1)
		)

		assertNull(adopted.restorableUntil)
	}

	// --- daily difficulty ---

	@Test
	fun difficulty_aBandThisBuildHas_isTaken() {
		assertEquals(Difficulty.ofIndex(7), ForceUpdateRules.difficulty(7))
	}

	@Test
	fun difficulty_aBandFromANewerServer_isRefused() {
		assertNull(ForceUpdateRules.difficulty(99))
	}

	@Test
	fun difficulty_aBandBelowTheFirst_isRefused() {
		assertNull(ForceUpdateRules.difficulty(0))
	}

	// --- learn progress ---

	@Test
	fun learnRows_whatTheAccountHolds_arrivesMarkedUploaded() {
		val rows = ForceUpdateRules.learnRows(
			listOf(LearnProgressEntry("NAKED_SINGLE", 1, 0, LearnProgressEntity.SOLVED)),
			now = 1_000L
		)

		assertEquals(1, rows.size)
		assertEquals("NAKED_SINGLE", rows[0].technique)
		assertEquals(LearnProgressEntity.SOLVED, rows[0].state)
		// Straight from the server, so offering it back would say nothing new.
		assertTrue(rows[0].uploaded)
	}

	@Test
	fun learnRows_aPartialWhereTheDeviceHasASolve_isKept() {
		// The merge rule the ordinary sync follows is off here: the account is right, even when it is worse.
		val rows = ForceUpdateRules.learnRows(
			listOf(LearnProgressEntry("NAKED_SINGLE", 1, 0, LearnProgressEntity.PARTIAL)),
			now = 1_000L
		)

		assertEquals(LearnProgressEntity.PARTIAL, rows.single().state)
	}

	@Test
	fun learnRows_aTechniqueThisBuildDoesNotHave_isDropped() {
		assertTrue(
			ForceUpdateRules.learnRows(
				listOf(LearnProgressEntry("TIME_TRAVEL", 1, 0, LearnProgressEntity.SOLVED)),
				now = 1_000L
			).isEmpty()
		)
	}

	@Test
	fun learnRows_anExerciseOutsideTheTraining_isDropped() {
		val entries = listOf(
			LearnProgressEntry("NAKED_SINGLE", 0, 0, LearnProgressEntity.SOLVED),
			LearnProgressEntry("NAKED_SINGLE", 4, 0, LearnProgressEntity.SOLVED),
			LearnProgressEntry("NAKED_SINGLE", 1, 3, LearnProgressEntity.SOLVED),
			LearnProgressEntry("NAKED_SINGLE", 1, -1, LearnProgressEntity.SOLVED)
		)

		assertTrue(ForceUpdateRules.learnRows(entries, now = 1_000L).isEmpty())
	}

	@Test
	fun learnRows_aResetMarker_isDropped() {
		// Purely local bookkeeping: it means "this device has a reset the server has not been told about".
		assertTrue(
			ForceUpdateRules.learnRows(
				listOf(LearnProgressEntry("NAKED_SINGLE", LearnProgressEntity.RESET_LEVEL, 0, LearnProgressEntity.RESET)),
				now = 1_000L
			).isEmpty()
		)
	}

	@Test
	fun learnRows_oneBadRow_doesNotCostTheGoodOnes() {
		val entries = listOf(
			LearnProgressEntry("NAKED_SINGLE", 1, 0, LearnProgressEntity.SOLVED),
			LearnProgressEntry("TIME_TRAVEL", 1, 0, LearnProgressEntity.SOLVED),
			LearnProgressEntry("SIMPLE_COLOURING", 2, 1, LearnProgressEntity.PARTIAL)
		)

		assertEquals(2, ForceUpdateRules.learnRows(entries, now = 1_000L).size)
	}

	// --- statistics ---

	private fun tier(
		size: Int = 9,
		variant: String? = "CLASSIC",
		difficulty: Int = 5,
		gamesPlayed: Int = 4,
		solved: Int = 3,
		failed: Int = 1,
		bestTimeMs: Long? = 90_000L,
		averageTimeMs: Long? = 120_000L,
		hintsUsed: Int = 2
	) = StatsEntryResponse(size, variant, difficulty, gamesPlayed, solved, failed, bestTimeMs, averageTimeMs, hintsUsed)

	@Test
	fun statsRows_aTierTheAccountHolds_isMirrored() {
		val row = ForceUpdateRules.statsRows(listOf(tier())).single()

		assertEquals(9, row.size)
		assertEquals("CLASSIC", row.variant)
		assertEquals(5, row.difficulty)
		assertEquals(4, row.gamesPlayed)
		assertEquals(90_000L, row.bestTimeMs)
	}

	@Test
	fun statsRows_aTierWithNoVariant_isStoredAsTheEmptyString() {
		// Null cannot be part of a primary key, and the store maps it back on the way out.
		assertEquals("", ForceUpdateRules.statsRows(listOf(tier(variant = null))).single().variant)
	}

	@Test
	fun statsRows_totalsBelowWhatTheDevicePlayed_areMirroredAnyway() {
		val row = ForceUpdateRules.statsRows(listOf(tier(gamesPlayed = 0, solved = 0, failed = 0, hintsUsed = 0))).single()

		assertEquals(0, row.gamesPlayed)
	}

	@Test
	fun statsRows_aSizeThisBuildDoesNotHave_isDropped() {
		assertTrue(ForceUpdateRules.statsRows(listOf(tier(size = 7))).isEmpty())
	}

	@Test
	fun statsRows_aVariantThisBuildDoesNotHave_isDropped() {
		assertTrue(ForceUpdateRules.statsRows(listOf(tier(variant = "HYPER"))).isEmpty())
	}

	@Test
	fun statsRows_aBandThisBuildDoesNotHave_isDropped() {
		assertTrue(ForceUpdateRules.statsRows(listOf(tier(difficulty = 99))).isEmpty())
	}

	@Test
	fun statsRows_negativeCountersOrTimes_areDropped() {
		assertTrue(ForceUpdateRules.statsRows(listOf(tier(gamesPlayed = -1))).isEmpty())
		assertTrue(ForceUpdateRules.statsRows(listOf(tier(solved = -1))).isEmpty())
		assertTrue(ForceUpdateRules.statsRows(listOf(tier(failed = -1))).isEmpty())
		assertTrue(ForceUpdateRules.statsRows(listOf(tier(hintsUsed = -1))).isEmpty())
		assertTrue(ForceUpdateRules.statsRows(listOf(tier(bestTimeMs = -1L))).isEmpty())
		assertTrue(ForceUpdateRules.statsRows(listOf(tier(averageTimeMs = -1L))).isEmpty())
	}

	@Test
	fun statsRows_aTierWithNoTimesYet_isKept() {
		val row = ForceUpdateRules.statsRows(listOf(tier(bestTimeMs = null, averageTimeMs = null))).single()

		assertNull(row.bestTimeMs)
		assertNull(row.averageTimeMs)
	}

	@Test
	fun statsRows_oneBadTier_doesNotCostTheGoodOnes() {
		assertEquals(1, ForceUpdateRules.statsRows(listOf(tier(), tier(size = 7))).size)
	}
}
