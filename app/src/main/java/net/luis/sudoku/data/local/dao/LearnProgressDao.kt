package net.luis.sudoku.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import net.luis.sudoku.data.local.entity.LearnProgressEntity

@Dao
interface LearnProgressDao {

	@Upsert
	suspend fun upsert(entity: LearnProgressEntity)

	@Upsert
	suspend fun upsertAll(entities: List<LearnProgressEntity>)

	@Query("SELECT * FROM learn_progress")
	suspend fun all(): List<LearnProgressEntity>

	/**
	 * Every row, as a stream, so the wiki list and the stats counter both follow a finished exercise without
	 * having to be told to reload.
	 */
	@Query("SELECT * FROM learn_progress")
	fun observeAll(): Flow<List<LearnProgressEntity>>

	@Query("SELECT * FROM learn_progress WHERE technique = :technique")
	suspend fun forTechnique(technique: String): List<LearnProgressEntity>

	@Query("SELECT * FROM learn_progress WHERE uploaded = 0")
	suspend fun notUploaded(): List<LearnProgressEntity>

	@Query("UPDATE learn_progress SET uploaded = 1 WHERE technique = :technique AND level = :level AND subLevel = :subLevel")
	suspend fun markUploaded(technique: String, level: Int, subLevel: Int)

	@Query("DELETE FROM learn_progress WHERE technique = :technique")
	suspend fun clearTechnique(technique: String)

	@Query("DELETE FROM learn_progress")
	suspend fun clear()
}
