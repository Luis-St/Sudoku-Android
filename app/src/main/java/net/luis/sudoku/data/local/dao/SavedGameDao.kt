package net.luis.sudoku.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.luis.sudoku.data.local.entity.SavedGameEntity

@Dao
interface SavedGameDao {

	@Query("SELECT * FROM saved_games WHERE slot = :slot")
	suspend fun get(slot: String): SavedGameEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(entity: SavedGameEntity)

	@Query("DELETE FROM saved_games WHERE slot = :slot")
	suspend fun delete(slot: String)
}
