package net.luis.sudoku.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.luis.sudoku.data.local.dao.LearnProgressDao
import net.luis.sudoku.data.local.entity.LearnProgressEntity
import net.luis.sudoku.domain.LearnProgressRules
import net.luis.sudoku.domain.TechniqueProgress
import net.luis.sudoku.learn.LearnTechniques
import net.luis.sudoku.solver.Technique
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The player's progress through the learn area, local first.
 *
 * Local first is not a fallback here: the whole area works offline, so the database is the truth and the
 * server is a copy of it that a second device can read. Nothing on these screens ever waits for a request.
 */
@Singleton
class LearnProgressStore @Inject constructor(
	private val dao: LearnProgressDao
) {

	/**
	 * Records how an exercise went.
	 *
	 * A solve never loses to a partial. The same exercise can be finished repeatedly - a partial is explicitly
	 * retryable, and a fresh puzzle can be generated for it - so a later attempt that goes worse than an
	 * earlier one must not take the achievement back off the player.
	 */
	suspend fun record(technique: Technique, level: Int, subLevel: Int, solvedWithTechnique: Boolean) {
		val existing = this.dao.forTechnique(technique.name)
			.firstOrNull { it.level == level && it.subLevel == subLevel }
		if (existing?.state == LearnProgressEntity.SOLVED && !solvedWithTechnique) {
			return
		}

		val state = if (solvedWithTechnique) LearnProgressEntity.SOLVED else LearnProgressEntity.PARTIAL
		if (existing?.state == state) {
			return
		}
		this.dao.upsert(
			LearnProgressEntity(
				technique = technique.name,
				level = level,
				subLevel = subLevel,
				state = state,
				updatedAt = System.currentTimeMillis(),
				uploaded = false
			)
		)
	}

	suspend fun progressOf(technique: Technique): TechniqueProgress =
		LearnProgressRules.progressOf(technique, this.dao.forTechnique(technique.name))

	/** Every taught technique's progress, including the untouched ones, so a caller can render the whole list. */
	suspend fun all(): List<TechniqueProgress> = group(this.dao.all())

	fun observeAll(): Flow<List<TechniqueProgress>> = this.dao.observeAll().map { group(it) }

	/**
	 * Clears one technique's training, which is the only reset there is.
	 *
	 * Scoped to a technique rather than offered for the whole area at once: a player asking for this wants to
	 * do a technique again, and one control that clears forty-one of them is a control that eventually clears
	 * the wrong one. The rows are deleted rather than marked, so the technique reads as untouched, exactly as
	 * it did before it was ever opened.
	 */
	suspend fun reset(technique: Technique) = this.dao.clearTechnique(technique.name)

	suspend fun notUploaded(): List<LearnProgressEntity> = this.dao.notUploaded()

	suspend fun markUploaded(row: LearnProgressEntity) =
		this.dao.markUploaded(row.technique, row.level, row.subLevel)

	/**
	 * Takes on what the server holds, keeping whichever of the two is further along.
	 *
	 * Merging by better state rather than by newer row is the whole rule: two devices are both allowed to
	 * work offline, so a stale partial arriving after a solve must not overwrite it and silently un-earn an
	 * achievement the player has already been shown.
	 */
	suspend fun merge(rows: List<LearnProgressEntity>) {
		val local = this.dao.all().associateBy { Triple(it.technique, it.level, it.subLevel) }
		val better = rows.filter { row ->
			val current = local[Triple(row.technique, row.level, row.subLevel)]
			current == null || (current.state != LearnProgressEntity.SOLVED && row.state == LearnProgressEntity.SOLVED)
		}
		if (better.isNotEmpty()) {
			// Marked uploaded: these came from the server, so telling it about them again says nothing new.
			this.dao.upsertAll(better.map { it.copy(uploaded = true) })
		}
	}

	private fun group(rows: List<LearnProgressEntity>): List<TechniqueProgress> {
		val byTechnique = rows.groupBy { it.technique }
		return LearnTechniques.taught().map { technique ->
			LearnProgressRules.progressOf(technique, byTechnique[technique.name].orEmpty())
		}
	}
}
