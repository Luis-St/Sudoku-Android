package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession

/**
 * Solution-based mistake checking (feature-spec §6): an entry is wrong when it contradicts the known
 * solution, not merely when it breaks a local constraint. A wrong pen entry is never written to
 * [GameSession] at all - "the board only ever retains correct values" is then true by construction,
 * which is also why no erase action for pen values needs to exist. The caller (`GameViewModel`) shows
 * the wrong digit transiently (red, then auto-removed) purely in UI state.
 */
class MistakeChecker(private val session: GameSession) {
	fun isMistake(index: Int, digit: Int): Boolean = !this.session.isCorrect(index, digit)
}
