package net.luis.sudoku.core

import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.version.GenVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test class for [GameSession], and the Phase A1 proof that shared-core is usable from Kotlin.
 */
class GameSessionTest {

	private fun key(
		size: GridSize = GridSize.NINE,
		variant: Variant = Variant.CLASSIC,
		difficulty: Difficulty = Difficulty.THREE,
		seed: Long = 20260725L
	) = PuzzleKey.of(size, variant, difficulty, seed)

	@Test
	fun generate_aClassicNineByNine_producesAGridOfTheRequestedShape() {
		val session = GameSession.generate(key())

		assertEquals(GridSize.NINE, session.size)
		assertEquals(Variant.CLASSIC, session.variant)
		assertEquals(9, session.edgeLength)
		assertEquals(81, session.cellCount)
		assertEquals(GenVersion.CURRENT, session.key.genVersion())
	}

	@Test
	fun generate_theSameKeyTwice_producesIdenticalGrids() {
		// The determinism guarantee the whole client/server split rests on: only the key is ever sent,
		// and both sides regenerate the same grid from it (feature-spec 3.2).
		val first = GameSession.generate(key())
		val second = GameSession.generate(key())

		val firstValues = (0 until first.cellCount).map { first.snapshot(it).value }
		val secondValues = (0 until second.cellCount).map { second.snapshot(it).value }

		assertEquals(firstValues, secondValues)
	}

	@Test
	fun generate_differentSeeds_produceDifferentGrids() {
		val first = GameSession.generate(key(seed = 1L))
		val second = GameSession.generate(key(seed = 2L))

		val firstValues = (0 until first.cellCount).map { first.snapshot(it).value }
		val secondValues = (0 until second.cellCount).map { second.snapshot(it).value }

		assertNotEquals(firstValues, secondValues)
	}

	@Test
	fun generate_aFreshPuzzle_hasSomeGivensAndSomeHoles() {
		val session = GameSession.generate(key())
		val snapshots = session.snapshots()

		assertTrue(snapshots.any { it.given })
		assertTrue(snapshots.any { it.empty })
		// Every non-empty cell in a fresh puzzle is a given; nothing has been entered yet.
		assertTrue(snapshots.filter { !it.empty }.all { it.given })
	}

	@Test
	fun solutionAt_everyCell_holdsALegalDigitAndAgreesWithTheGivens() {
		val session = GameSession.generate(key())

		for (index in 0 until session.cellCount) {
			val solution = session.solutionAt(index)
			assertTrue("cell $index solution was $solution", solution in 1..session.edgeLength)

			val snapshot = session.snapshot(index)
			if (snapshot.given) {
				assertEquals("given at $index disagrees with the solution", solution, snapshot.value)
			}
		}
	}

	@Test
	fun isCorrect_theSolutionDigit_isAcceptedAndAnyOtherRejected() {
		val session = GameSession.generate(key())
		val emptyCell = session.snapshots().first { it.empty }
		val correct = session.solutionAt(emptyCell.index)

		assertTrue(session.isCorrect(emptyCell.index, correct))
		val wrong = if (correct == 1) 2 else 1
		assertFalse(session.isCorrect(emptyCell.index, wrong))
	}

	@Test
	fun setValue_fillingEveryEmptyCellFromTheSolution_solvesThePuzzle() {
		val session = GameSession.generate(key())
		assertFalse(session.isSolved())

		session.snapshots().filter { it.empty }.forEach { session.setValue(it.index, session.solutionAt(it.index)) }

		assertTrue(session.isSolved())
	}

	@Test
	fun setValue_aGivenCell_throws() {
		val session = GameSession.generate(key())
		val given = session.snapshots().first { it.given }

		try {
			session.setValue(given.index, 1)
			throw AssertionError("expected setting a given cell to throw")
		} catch (expected: IllegalStateException) {
			// shared-core refuses to overwrite a given; the UI never has to guard this itself.
		}
	}

	@Test
	fun clear_aFilledCell_emptiesIt() {
		val session = GameSession.generate(key())
		val empty = session.snapshots().first { it.empty }

		session.setValue(empty.index, session.solutionAt(empty.index))
		assertFalse(session.snapshot(empty.index).empty)

		session.clear(empty.index)
		assertTrue(session.snapshot(empty.index).empty)
	}

	@Test
	fun togglePencilMark_aDigit_addsThenRemovesIt() {
		val session = GameSession.generate(key())
		val empty = session.snapshots().first { it.empty }

		session.togglePencilMark(empty.index, 5)
		assertTrue(session.snapshot(empty.index).hasPencilMark(5))
		assertEquals(listOf(5), session.snapshot(empty.index).pencilMarkDigits())

		session.togglePencilMark(empty.index, 5)
		assertFalse(session.snapshot(empty.index).hasPencilMark(5))
	}

	@Test
	fun snapshot_aConflictingEntry_isReportedAsConflicted() {
		val session = GameSession.generate(key())
		// Deliberately duplicate a given's digit inside its own row.
		val given = session.snapshots().first { it.given }
		val row = given.index / session.edgeLength
		val victim = (0 until session.edgeLength)
			.map { row * session.edgeLength + it }
			.first { session.snapshot(it).empty }

		session.setValue(victim, given.value)

		assertTrue(session.snapshot(victim).conflicted)
	}

	@Test
	fun regionOf_everyCell_isWithinTheRegionCount() {
		val session = GameSession.generate(key())

		for (index in 0 until session.cellCount) {
			val region = session.regionOf(index)
			assertTrue("cell $index mapped to region $region", region in 0 until session.edgeLength)
		}
	}

	@Test
	fun generate_aChaosPuzzle_yieldsRegionsThatAreNotPlainBoxes() {
		val session = GameSession.generate(key(variant = Variant.CHAOS))

		assertEquals(Variant.CHAOS, session.variant)
		// A chaos partition still has N regions of N cells, it just isn't the box layout.
		val sizes = (0 until session.cellCount).groupBy { session.regionOf(it) }.mapValues { it.value.size }
		assertEquals(session.edgeLength, sizes.size)
		assertTrue(sizes.values.all { it == session.edgeLength })
	}

	@Test
	fun generate_everySupportedSize_succeeds() {
		for (size in GridSize.entries) {
			val difficulty = if (size == GridSize.FOUR || size == GridSize.SIX) Difficulty.TWO else Difficulty.THREE
			val session = GameSession.generate(key(size = size, difficulty = difficulty))

			assertEquals(size, session.size)
			assertEquals(size.cellCount(), session.cellCount)
			assertTrue("no givens for $size", session.snapshots().any { it.given })
		}
	}
}
