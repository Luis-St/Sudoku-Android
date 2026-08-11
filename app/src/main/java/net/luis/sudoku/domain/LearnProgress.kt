package net.luis.sudoku.domain

import net.luis.sudoku.data.local.entity.LearnProgressEntity
import net.luis.sudoku.learn.LearnContent
import net.luis.sudoku.learn.LearnTechniques
import net.luis.sudoku.solver.Technique

/**
 * What one exercise of a technique's training looks like to the player.
 *
 * [PARTIAL] is the one that has to be drawn differently from the others. It means the exercise was finished
 * without using the technique it teaches: the next exercise opens, but the achievement stays unearned, and a
 * player who cannot see that distinction is left with a technique that refuses to complete for no visible
 * reason.
 */
enum class SubLevelState { LOCKED, OPEN, PARTIAL, SOLVED }

/**
 * One technique's training, as the levels screen shows it.
 *
 * @param states the state of every exercise, level by level, [LearnContent.SUB_LEVELS] per level
 */
data class TechniqueProgress(val technique: Technique, val states: List<List<SubLevelState>>) {

	/** Whether every exercise has been solved with the technique, which is what earns the achievement. */
	val isMastered: Boolean = states.all { level -> level.all { it == SubLevelState.SOLVED } }

	/** Whether anything at all has been done here, which is what separates "not started" from a part-done row. */
	val isStarted: Boolean = states.any { level -> level.any { it == SubLevelState.PARTIAL || it == SubLevelState.SOLVED } }

	/** How many exercises are finished, counting a partial: what the list screen's "%1$d of %2$d" says. */
	val finished: Int = states.sumOf { level -> level.count { it == SubLevelState.PARTIAL || it == SubLevelState.SOLVED } }

	/** Whether a level can be entered at all. */
	fun isLevelOpen(level: Int): Boolean = states[level - 1].any { it != SubLevelState.LOCKED }

	fun stateOf(level: Int, subLevel: Int): SubLevelState = states[level - 1][subLevel]
}

/**
 * Turns the stored rows into the four states the screens draw.
 *
 * Only finished exercises are stored, so locked and open are derived here rather than written down - two
 * places holding the same fact is two places for it to disagree. The rule is the plainest one that still
 * makes the training a progression: an exercise opens once the one before it is finished, and a level opens
 * once the level before it is finished, where **finished counts a partial**. A partial is a completed
 * exercise that earned no achievement, not a failed one, so barring the player from continuing over it would
 * punish them twice for the same thing.
 */
object LearnProgressRules {

	fun progressOf(technique: Technique, rows: List<LearnProgressEntity>): TechniqueProgress {
		val stored = rows.associateBy { it.level to it.subLevel }
		val states = mutableListOf<List<SubLevelState>>()
		var previousLevelFinished = true
		for (level in 1..LearnContent.LEVELS) {
			val levelStates = mutableListOf<SubLevelState>()
			var previousFinished = previousLevelFinished
			for (subLevel in 0 until LearnContent.SUB_LEVELS) {
				val row = stored[level to subLevel]
				val state = when {
					row?.state == LearnProgressEntity.SOLVED -> SubLevelState.SOLVED
					row?.state == LearnProgressEntity.PARTIAL -> SubLevelState.PARTIAL
					previousFinished -> SubLevelState.OPEN
					else -> SubLevelState.LOCKED
				}
				levelStates.add(state)
				previousFinished = state == SubLevelState.SOLVED || state == SubLevelState.PARTIAL
			}
			previousLevelFinished = levelStates.all { it == SubLevelState.SOLVED || it == SubLevelState.PARTIAL }
			states.add(levelStates)
		}
		return TechniqueProgress(technique, states)
	}

	/**
	 * How many techniques have been mastered, of how many there are.
	 *
	 * The denominator is read from the shared core rather than written here: the taught set has already lost
	 * one technique and may regain five, and an "all techniques" achievement that quietly became incomplete
	 * would be a bug nobody could see.
	 */
	fun masteredCount(progress: Collection<TechniqueProgress>): Int = progress.count { it.isMastered }

	fun techniqueCount(): Int = LearnTechniques.count()
}
