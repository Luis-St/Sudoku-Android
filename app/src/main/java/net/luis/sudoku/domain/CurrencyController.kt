package net.luis.sudoku.domain

import java.time.LocalDate

/**
 * Rhubarb earning and balance (feature-spec §6a). Pure arithmetic over an in-memory snapshot; persistence
 * is [net.luis.sudoku.data.local.CurrencyStore]'s job, injecting `today` for testability without a clock.
 */
class CurrencyController(
	initialBalance: Long,
	initialNormalGamesEarnedToday: Int,
	initialEarnDate: LocalDate?,
	private val today: () -> LocalDate = LocalDate::now
) {
	var balance: Long = initialBalance
		private set

	private var normalGamesEarnedToday = initialNormalGamesEarnedToday
	private var earnDate = initialEarnDate

	val currentNormalGamesEarnedToday: Int get() = rolledOverCount()
	val currentEarnDate: LocalDate get() = this.today()

	private fun rolledOverCount(): Int {
		val now = this.today()
		return if (this.earnDate == now) this.normalGamesEarnedToday else 0
	}

	/** First 10 normal games per day earn currency; failures earn nothing (§6a). @return the amount awarded. */
	fun awardForNormalSolve(difficultyIndex: Int, edgeLength: Int): Long {
		val now = this.today()
		if (this.earnDate != now) {
			this.earnDate = now
			this.normalGamesEarnedToday = 0
		}
		if (this.normalGamesEarnedToday >= NORMAL_GAMES_CAP_PER_DAY) return 0

		this.normalGamesEarnedToday++
		val amount = baseAward(difficultyIndex, edgeLength)
		this.balance += amount
		return amount
	}

	/** The daily is outside the cap and always earns when solved - the caller gates "once per day" (A7). */
	fun awardForDailySolve(difficultyIndex: Int, edgeLength: Int): Long {
		// The bonus is flat on purpose: it pays for turning up on the day, not for the grid, and the daily's
		// size is the server's choice rather than the player's.
		val amount = baseAward(difficultyIndex, edgeLength) + DAILY_BONUS
		this.balance += amount
		return amount
	}

	/** @return false, spending nothing, if [amount] exceeds the balance. */
	fun spend(amount: Long): Boolean {
		if (amount < 0 || amount > this.balance) return false
		this.balance -= amount
		return true
	}

	/** Server-reported balance always wins on connect (§6a) - no user-facing "adjusted" message. */
	fun applyServerBalance(balance: Long) {
		this.balance = balance
	}

	companion object {
		const val NORMAL_GAMES_CAP_PER_DAY = 10
		const val DAILY_BONUS = 20L
		const val PER_DIFFICULTY_INDEX = 5L

		/**
		 * How much a solved grid is worth relative to a 9x9, in tenths (§6a).
		 *
		 * A tier-3 4x4 is a minute of work and a tier-3 16x16 is an evening of it, so paying both
		 * `5 x difficultyIndex` made the small grids the efficient way to farm the daily cap of ten games.
		 * The factors sit between the edge length ratio (`n / 9`, which underpays the big grids) and the
		 * cell count ratio (`n^2 / 81`, which pays 3.2x for a 16x16 and a fifth of the rate for a 4x4).
		 *
		 * 9x9 is the baseline at 1.0, so every award on the default size is exactly what it was before.
		 */
		private val SIZE_FACTOR_TENTHS = mapOf(4 to 4, 6 to 6, 9 to 10, 12 to 15, 16 to 22)

		/**
		 * The award for one solved grid, before the daily bonus: the difficulty rate scaled by the size
		 * factor, rounded half up so a tier does not silently pay a Rhubarb less than the factor says.
		 *
		 * @throws IllegalArgumentException If [edgeLength] is not one of the five supported sizes - the same
		 *   stance as `GridSize.ofEdgeLength`, since an unknown size has no honest price
		 */
		fun baseAward(difficultyIndex: Int, edgeLength: Int): Long {
			val factor = requireNotNull(SIZE_FACTOR_TENTHS[edgeLength]) { "No supported grid size with edge length $edgeLength" }
			return (PER_DIFFICULTY_INDEX * difficultyIndex * factor + 5) / 10
		}
	}
}
