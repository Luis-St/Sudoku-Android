package net.luis.sudoku.domain

import net.luis.sudoku.core.GameSession
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** feature-spec §5.6: auto-candidate mode fills a cell with every digit not already taken by a peer. */
class CandidateCalculatorTest {

	private fun session() = GameSession.generate(PuzzleKey.of(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, 1L))

	@Test
	fun legalDigits_excludesEveryPeersValue() {
		val session = session()
		val index = (0 until session.cellCount).first { !session.snapshot(it).given }

		val takenByPeers = session.peersOf(index)
			.mapNotNull { peer -> session.snapshot(peer).value.takeIf { it != 0 } }
			.toSet()

		val legal = CandidateCalculator.legalDigits(session, index)
		for (digit in 1..session.edgeLength) {
			val isLegal = (legal shr digit) and 1 == 1
			assertEquals("digit $digit legality", digit !in takenByPeers, isLegal)
		}
	}

	@Test
	fun legalDigits_neverIncludesZeroOrOutOfRangeBits() {
		val session = session()
		val index = (0 until session.cellCount).first { !session.snapshot(it).given }

		val legal = CandidateCalculator.legalDigits(session, index)

		assertFalse((legal shr 0) and 1 == 1)
		assertFalse((legal shr (session.edgeLength + 1)) and 1 == 1)
	}
}
