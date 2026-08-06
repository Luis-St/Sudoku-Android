package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession

/**
 * Auto-candidate mode (feature-spec §5.6): "the app fills and maintains all pencil marks automatically."
 * Pure constraint check - a digit is a legal candidate for an empty cell if no peer (row/column/region)
 * already holds it as a pen value. Unavailable under Lisa (§4.3) - [ModifierSet.autoCandidateModeAvailable]
 * is the gate the caller checks, this class doesn't know about difficulty at all.
 */
object CandidateCalculator {

	/** @return the bitmask of digits still legal for the (assumed empty, non-given) cell at [index]. */
	fun legalDigits(session: GameSession, index: Int): Int {
		val edgeLength = session.edgeLength
		var taken = 0
		for (peer in session.peersOf(index)) {
			val value = session.snapshot(peer).value
			if (value != 0) taken = taken or (1 shl value)
		}
		var legal = 0
		for (digit in 1..edgeLength) {
			if (taken and (1 shl digit) == 0) legal = legal or (1 shl digit)
		}
		return legal
	}
}
