package net.luis.sudoku.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.luis.sudoku.data.local.CurrencyStore
import net.luis.sudoku.data.local.DailyStore
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.ApiException
import net.luis.sudoku.domain.StreakBreakNotice
import net.luis.sudoku.domain.StreakRestoreCalculator
import net.luis.sudoku.domain.StreakRestorePreview
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The home screen's own state: the daily summary card (streak, whether today is already solved), the
 * currency balance, and the streak-restore flow that UI item 11 moved off the game screen.
 *
 * Restore is server-only - restore points are minted and spent server-side (server-spec §9.8), so the
 * card offers it only when signed in and otherwise just shows the local streak.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
	private val dailyStore: DailyStore,
	private val currencyStore: CurrencyStore,
	private val serverConfigStore: ServerConfigStore,
	private val apiClient: ApiClient,
	private val streakBreakNotice: StreakBreakNotice
) : ViewModel() {

	var streak by mutableStateOf(0)
		private set

	var dailySolvedToday by mutableStateOf(false)
		private set

	/**
	 * The break the server still offers to repair, and how many days are left to do it (issue 2.2.0/6).
	 *
	 * On the card rather than only inside the restore dialog, because a player who never opens that dialog
	 * is exactly the one who loses the window: nothing used to say a break had happened, what it would cost
	 * to undo, or that the offer expires. Announced on the launch that first learns of the break and no
	 * later one - see [StreakBreakNotice], which is what makes this 0 again on the next start.
	 */
	var restorableMissedDays by mutableStateOf(0)
		private set

	var restoreDaysLeft by mutableStateOf<Int?>(null)
		private set

	var currencyBalance by mutableStateOf(0L)
		private set

	/** Whether the restore entry point exists at all - signed in, regardless of eligibility. */
	var restoreAvailable by mutableStateOf(false)
		private set

	var restorePreview by mutableStateOf<StreakRestorePreview?>(null)
		private set

	var busy by mutableStateOf(false)
		private set

	var errorMessage by mutableStateOf<String?>(null)
		private set

	/** The [ApiException.code] behind [errorMessage], so the dialog can show copy rather than the raw failure. */
	var errorCode by mutableStateOf<String?>(null)
		private set

	/**
	 * An unreachable server, which is not an error to report but a thing that cannot be done right now.
	 *
	 * Kept apart from [errorMessage] because the two read differently: the server answering "no" is worth
	 * showing, while a connection that never happened only means the player has to come back later, and a
	 * socket message means nothing to them.
	 */
	var restoreOffline by mutableStateOf(false)
		private set

	init {
		// Collected rather than read once. The streak and the balance are account state now, which means
		// something other than this screen changes them: AccountSync adopts what another device earned, and
		// it runs on the heartbeat - so a one-shot read would leave the card showing the old numbers for as
		// long as the player stayed on the home screen.
		this.viewModelScope.launch {
			this@HomeViewModel.dailyStore.record.collect { record ->
				this@HomeViewModel.streak = record.streak
				this@HomeViewModel.dailySolvedToday = record.solved && record.date == today()
				val daysLeft = StreakRestoreCalculator.daysLeftToRestore(record.restorableUntil, today())
				// An expired window is not an offer: the record keeps what the server last said, and the
				// next heartbeat clears it, but the card must not go on inviting a restore in between.
				val open = record.restorableMissedDays > 0 && (daysLeft == null || daysLeft > 0) &&
					this@HomeViewModel.streakBreakNotice.shouldShow(record.restorableUntil)
				this@HomeViewModel.restorableMissedDays = if (open) record.restorableMissedDays else 0
				this@HomeViewModel.restoreDaysLeft = if (open) daysLeft else null
			}
		}
		this.viewModelScope.launch {
			this@HomeViewModel.currencyStore.state.collect { this@HomeViewModel.currencyBalance = it.balance }
		}
		this.viewModelScope.launch {
			this@HomeViewModel.serverConfigStore.config.collect { this@HomeViewModel.restoreAvailable = it.isAuthenticated }
		}
	}

	private suspend fun today(): LocalDate {
		val config = this.serverConfigStore.current()
		return config.cachedTimezone?.let { LocalDate.now(ZoneId.of(it)) } ?: LocalDate.now()
	}

	/** Eligibility is fetched only when the player actually asks, never on every home-screen visit. */
	fun openStreakRestore() {
		this.viewModelScope.launch {
			val config = this@HomeViewModel.serverConfigStore.current()
			val baseUrl = config.serverUrl ?: return@launch
			val token = config.sessionToken ?: return@launch
			this@HomeViewModel.busy = true
			try {
				val streak = this@HomeViewModel.apiClient.dailyStreak(baseUrl, token)
				val today = config.cachedTimezone?.let { LocalDate.now(ZoneId.of(it)) } ?: LocalDate.now()
				val restorableUntil = streak.restorableUntil?.let(LocalDate::parse)
				// The server's own count wins where it reports one: since issue 2.2.0/6 it also remembers a
				// break the player has already solved past, which no distance from the last completed date
				// can show. The local gap stays as the fallback for a server that predates that.
				val missedDays = streak.restorableMissedDays.takeIf { it > 0 }
					?: StreakRestoreCalculator.missedDays(streak.lastCompletedDate?.let(LocalDate::parse), today)
				this@HomeViewModel.restorePreview = StreakRestorePreview(
					missedDays = missedDays,
					cost = StreakRestoreCalculator.rhubarbCost(missedDays),
					restorePoints = streak.restorePoints,
					longest = streak.longest,
					balance = this@HomeViewModel.currencyStore.current().balance,
					daysLeft = StreakRestoreCalculator.daysLeftToRestore(restorableUntil, today)
				)

				// The card reads the same two fields, and this is a fresher answer than the last heartbeat's.
				val record = this@HomeViewModel.dailyStore.current()
				this@HomeViewModel.dailyStore.save(
					record.copy(restorableMissedDays = streak.restorableMissedDays, restorableUntil = restorableUntil)
				)
			} catch (e: ApiException) {
				this@HomeViewModel.errorMessage = e.message ?: e.code
				this@HomeViewModel.errorCode = e.code
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// An unreachable server fails with an IOException long before there is an ErrorResponse to turn
				// into an ApiException, and that used to escape this coroutine and take the whole app down.
				// Said as "not now, try later" rather than as a failure, since nothing went wrong on the
				// player's side and there is nothing for them to fix.
				this@HomeViewModel.restoreOffline = true
			} finally {
				this@HomeViewModel.busy = false
			}
		}
	}

	fun dismissRestorePreview() {
		this.restorePreview = null
	}

	/** The POST response is authoritative for the streak itself; the balance it cost is read back. */
	fun restoreStreak() {
		this.viewModelScope.launch {
			val config = this@HomeViewModel.serverConfigStore.current()
			val baseUrl = config.serverUrl ?: return@launch
			val token = config.sessionToken ?: return@launch
			this@HomeViewModel.busy = true
			try {
				val streak = this@HomeViewModel.apiClient.restoreDailyStreak(baseUrl, token)
				val record = this@HomeViewModel.dailyStore.current()
				// The collector repaints `streak` from this write - it is no longer set by hand, or the
				// store and the screen could disagree about a number that now comes from two places. The
				// repaired break goes with it, so the card stops offering what has just been paid for.
				this@HomeViewModel.dailyStore.save(
					record.copy(
						streak = streak.current,
						restorableMissedDays = streak.restorableMissedDays,
						restorableUntil = streak.restorableUntil?.let(LocalDate::parse)
					)
				)

				// A read, not a sync: the restore has just *spent* Rhubarb, so reporting the balance this
				// device still remembers is precisely how the cost would be handed straight back.
				val serverBalance = this@HomeViewModel.apiClient.currencyBalance(baseUrl, token).balance
				this@HomeViewModel.currencyStore.adoptServerBalance(serverBalance)

				this@HomeViewModel.restorePreview = null
			} catch (e: ApiException) {
				this@HomeViewModel.errorMessage = e.message ?: e.code
				this@HomeViewModel.errorCode = e.code
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				// Same stance as above. The preview closes with it: nothing was spent, and its numbers came
				// from a server that is no longer answering, so pressing the button again would only repeat
				// this. Reopening it is what "try again later" means.
				this@HomeViewModel.restorePreview = null
				this@HomeViewModel.restoreOffline = true
			} finally {
				this@HomeViewModel.busy = false
			}
		}
	}

	fun dismissError() {
		this.errorMessage = null
		this.errorCode = null
	}

	fun dismissRestoreOffline() {
		this.restoreOffline = false
	}
}
