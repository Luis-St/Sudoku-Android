package net.luis.sudoku.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import net.luis.sudoku.data.local.entity.PendingDailyResultEntity

@Dao
interface PendingDailyResultDao {

	@Insert
	suspend fun insert(entity: PendingDailyResultEntity)

	@Query("SELECT * FROM pending_daily_results ORDER BY id ASC")
	suspend fun all(): List<PendingDailyResultEntity>

	@Delete
	suspend fun delete(entity: PendingDailyResultEntity)
}
