package net.luis.sudoku.domain

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import net.luis.sudoku.data.local.DailyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Test class for [StreakBreakNotice]. A fresh instance stands for a fresh launch, which is the whole
 * behaviour under test: the notice is news once and then stops being news.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreakBreakNoticeTest {

	private val until = LocalDate.of(2026, 8, 31)

	private fun newStore(): DailyStore {
		val file = java.io.File.createTempFile("daily", ".preferences_pb", RuntimeEnvironment.getApplication().cacheDir)
		return DailyStore(PreferenceDataStoreFactory.create { file })
	}

	@Test
	fun shouldShow_onTheLaunchThatFirstSeesABreak_isTrueAndRemembersIt() = runBlocking {
		val store = newStore()

		assertTrue(StreakBreakNotice(store).shouldShow(until))
		assertEquals(until, store.current().restoreNoticeSeenFor)
	}

	@Test
	fun shouldShow_askedAgainInTheSameLaunch_staysTrue() = runBlocking {
		// The daily record is a flow and the write above produces one more emission of it, so the card asks
		// again immediately - and must not blink out of existence between the two.
		val notice = StreakBreakNotice(newStore())
		notice.shouldShow(until)

		assertTrue(notice.shouldShow(until))
	}

	@Test
	fun shouldShow_onTheNextLaunch_isFalse() = runBlocking {
		val store = newStore()
		StreakBreakNotice(store).shouldShow(until)

		assertFalse(StreakBreakNotice(store).shouldShow(until))
	}

	@Test
	fun shouldShow_aBreakThatHappenedAfterTheAcknowledgedOne_isAnnouncedAgain() = runBlocking {
		val store = newStore()
		StreakBreakNotice(store).shouldShow(until)

		assertTrue(StreakBreakNotice(store).shouldShow(until.plusDays(9)))
	}

	@Test
	fun shouldShow_withNothingToRestore_isFalseAndWritesNothing() = runBlocking {
		val store = newStore()

		assertFalse(StreakBreakNotice(store).shouldShow(null))
		assertEquals(null, store.current().restoreNoticeSeenFor)
	}
}
