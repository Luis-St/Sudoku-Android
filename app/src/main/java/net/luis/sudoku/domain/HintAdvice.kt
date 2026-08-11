package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.learn.LearnTechniques
import net.luis.sudoku.solver.Technique

/**
 * What a hint can tell the player besides the cell it is pointing at.
 *
 * A hint that only reveals a digit answers one cell and teaches nothing about the next one. Naming the
 * technique that solves the cell turns the same hint into a way forward, and the wiki page behind the name
 * is where the player finds out how it works.
 */
sealed interface HintAdvice {

	/**
	 * The player's own pencil marks contradict the board: a cell lists a candidate that a peer already holds
	 * as a placed digit.
	 *
	 * Reported **before** any technique, and this is the whole reason the check exists. Every technique is an
	 * argument about the candidate set, so naming one over a candidate set the player has filled in wrongly
	 * teaches them to apply it to a position that is not there, and the conclusion it reaches will be wrong
	 * for reasons that have nothing to do with the technique.
	 */
	data class WrongPencilMarks(val cells: List<Int>) : HintAdvice

	/**
	 * The technique that solves the cell the hint is pointing at.
	 *
	 * [teachable] is false for the handful of techniques the learn area does not cover, which are still named
	 * but have no page to open.
	 */
	data class Named(val technique: Technique, val teachable: Boolean) : HintAdvice
}

/**
 * Works out what to tell the player alongside a hint.
 *
 * The pencil marks come first, always. Only once they agree with the board is the technique worth naming.
 */
object HintAdviser {

	/**
	 * @param technique the technique the hint engine used to reach its cell
	 * @return the wrong pencil marks if there are any, otherwise the technique by name
	 */
	fun adviceFor(session: GameSession, technique: Technique): HintAdvice {
		val wrong = wrongPencilMarks(session)
		if (wrong.isNotEmpty()) {
			return HintAdvice.WrongPencilMarks(wrong)
		}
		return HintAdvice.Named(technique, LearnTechniques.isTaught(technique))
	}

	/**
	 * Every empty cell carrying a candidate that some peer already holds as a placed digit.
	 *
	 * Only impossible candidates count as wrong. A *missing* pencil mark is not an error: a player who writes
	 * their marks by hand is under no obligation to write all of them, and treating a half-filled cell as a
	 * mistake would report almost every board in the game.
	 */
	fun wrongPencilMarks(session: GameSession): List<Int> {
		val wrong = mutableListOf<Int>()
		for (index in 0 until session.cellCount) {
			val snapshot = session.snapshot(index)
			if (!snapshot.empty || snapshot.pencilMarks == 0) {
				continue
			}

			if (snapshot.pencilMarks and CandidateCalculator.legalDigits(session, index).inv() != 0) {
				wrong.add(index)
			}
		}
		return wrong
	}
}
