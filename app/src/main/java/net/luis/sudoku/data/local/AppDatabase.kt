package net.luis.sudoku.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import net.luis.sudoku.data.local.dao.PendingDailyResultDao
import net.luis.sudoku.data.local.dao.SavedGameDao
import net.luis.sudoku.data.local.dao.StatisticsDao
import net.luis.sudoku.data.local.entity.GameResultEntity
import net.luis.sudoku.data.local.entity.PendingDailyResultEntity
import net.luis.sudoku.data.local.entity.SavedGameEntity

@Database(
	entities = [SavedGameEntity::class, GameResultEntity::class, PendingDailyResultEntity::class],
	version = 2,
	exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
	abstract fun savedGameDao(): SavedGameDao
	abstract fun statisticsDao(): StatisticsDao
	abstract fun pendingDailyResultDao(): PendingDailyResultDao
}
