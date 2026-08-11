package net.luis.sudoku.domain

import net.luis.sudoku.data.local.entity.LearnProgressEntity
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.learn.LearnTechniques
import net.luis.sudoku.solver.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [LearnProgressRules] - which exercises the player can reach, and which technique counts as mastered.
 *
 * Only finished exercises are stored, so locked and open are worked out here every time they are shown. That
 * is the whole reason these cases are worth pinning: get the derivation wrong and the training either opens
 * everything at once or refuses to open anything.
 */
class LearnProgressRulesTest {

	private val technique = Technique.NAKED_SINGLE

	private fun row(level: Int, subLevel: Int, state: String) = LearnProgressEntity(
		technique = technique.name,
		level = level,
		subLevel = subLevel,
		state = state,
		updatedAt = 0L
	)

	private fun solved(level: Int, subLevel: Int) = row(level, subLevel, LearnProgressEntity.SOLVED)

	private fun partial(level: Int, subLevel: Int) = row(level, subLevel, LearnProgressEntity.PARTIAL)

	private fun progressOf(vararg rows: LearnProgressEntity) = LearnProgressRules.progressOf(technique, rows.toList())

	@Test
	fun `nothing done opens only the first exercise`() {
		val progress = progressOf()

		assertEquals(SubLevelState.OPEN, progress.stateOf(1, 0))
		assertEquals(SubLevelState.LOCKED, progress.stateOf(1, 1))
		assertEquals(SubLevelState.LOCKED, progress.stateOf(2, 0))
		assertFalse(progress.isStarted)
		assertFalse(progress.isMastered)
		assertEquals(0, progress.finished)
	}

	@Test
	fun `solving one exercise opens the next`() {
		val progress = progressOf(solved(1, 0))

		assertEquals(SubLevelState.SOLVED, progress.stateOf(1, 0))
		assertEquals(SubLevelState.OPEN, progress.stateOf(1, 1))
		assertEquals(SubLevelState.LOCKED, progress.stateOf(1, 2))
		assertTrue(progress.isStarted)
		assertEquals(1, progress.finished)
	}

	@Test
	fun `a partial opens the next exercise too`() {
		val progress = progressOf(partial(1, 0))

		assertEquals(SubLevelState.PARTIAL, progress.stateOf(1, 0))
		assertEquals(SubLevelState.OPEN, progress.stateOf(1, 1))
		assertEquals(1, progress.finished)
	}

	@Test
	fun `a level opens once the one before it is finished`() {
		val partway = progressOf(solved(1, 0), solved(1, 1))
		assertFalse(partway.isLevelOpen(2))

		val finished = progressOf(solved(1, 0), solved(1, 1), solved(1, 2))
		assertTrue(finished.isLevelOpen(2))
		assertEquals(SubLevelState.OPEN, finished.stateOf(2, 0))
		assertFalse(finished.isLevelOpen(3))
	}

	@Test
	fun `a level finished with a partial still opens the next one`() {
		val progress = progressOf(solved(1, 0), partial(1, 1), solved(1, 2))

		assertTrue(progress.isLevelOpen(2))
		assertEquals(SubLevelState.OPEN, progress.stateOf(2, 0))
	}

	@Test
	fun `an exercise done out of order is shown as done`() {
		// Nothing writes this today, but a synced row from another device can arrive on its own, and a state
		// that says LOCKED over a solve the player has already earned would be a lie.
		val progress = progressOf(solved(2, 2))

		assertEquals(SubLevelState.SOLVED, progress.stateOf(2, 2))
		assertEquals(SubLevelState.LOCKED, progress.stateOf(2, 0))
	}

	@Test
	fun `every exercise solved is mastered`() {
		val rows = mutableListOf<LearnProgressEntity>()
		for (level in 1..LearnContent.LEVELS) {
			for (subLevel in 0 until LearnContent.SUB_LEVELS) {
				rows.add(solved(level, subLevel))
			}
		}

		val progress = LearnProgressRules.progressOf(technique, rows)

		assertTrue(progress.isMastered)
		assertEquals(LearnContent.EXERCISES_PER_TECHNIQUE, progress.finished)
		assertEquals(1, LearnProgressRules.masteredCount(listOf(progress)))
	}

	@Test
	fun `one partial anywhere withholds mastery`() {
		val rows = mutableListOf<LearnProgressEntity>()
		for (level in 1..LearnContent.LEVELS) {
			for (subLevel in 0 until LearnContent.SUB_LEVELS) {
				rows.add(if (level == 2 && subLevel == 1) partial(level, subLevel) else solved(level, subLevel))
			}
		}

		val progress = LearnProgressRules.progressOf(technique, rows)

		// Every exercise is finished and every level is open, and the achievement is still unearned. That is
		// the case the levels screen has to draw differently, or it looks like a bug.
		assertEquals(LearnContent.EXERCISES_PER_TECHNIQUE, progress.finished)
		assertFalse(progress.isMastered)
		assertEquals(0, LearnProgressRules.masteredCount(listOf(progress)))
	}

	@Test
	fun `the technique count comes from the shared core`() {
		assertEquals(LearnTechniques.count(), LearnProgressRules.techniqueCount())
	}
}
