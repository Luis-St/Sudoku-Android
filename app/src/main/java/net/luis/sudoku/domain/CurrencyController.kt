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
	fun awardForNormalSolve(difficultyIndex: Int): Long {
		val now = this.today()
		if (this.earnDate != now) {
			this.earnDate = now
			this.normalGamesEarnedToday = 0
		}
		if (this.normalGamesEarnedToday >= NORMAL_GAMES_CAP_PER_DAY) return 0

		this.normalGamesEarnedToday++
		val amount = 5L * difficultyIndex
		this.balance += amount
		return amount
	}

	/** The daily is outside the cap and always earns when solved - the caller gates "once per day" (A7). */
	fun awardForDailySolve(difficultyIndex: Int): Long {
		val amount = 5L * difficultyIndex + DAILY_BONUS
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
	}
}
