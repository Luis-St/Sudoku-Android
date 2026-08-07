package net.luis.sudoku.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.luis.sudoku.data.local.dao.PendingDailyResultDao
import net.luis.sudoku.data.local.dao.SavedGameDao
import net.luis.sudoku.data.local.dao.StatisticsDao
import net.luis.sudoku.data.local.entity.GameResultEntity
import net.luis.sudoku.data.local.entity.PendingDailyResultEntity
import net.luis.sudoku.data.local.entity.SavedGameEntity

@Database(
	entities = [SavedGameEntity::class, GameResultEntity::class, PendingDailyResultEntity::class],
	version = 3,
	exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
	abstract fun savedGameDao(): SavedGameDao
	abstract fun statisticsDao(): StatisticsDao
	abstract fun pendingDailyResultDao(): PendingDailyResultDao
}

/**
 * Gives `game_results` the two columns the per-game upload needs (server-spec §9): the id each game is
 * uploaded under, and whether the server already has it.
 *
 * **Written out rather than left to destructive migration**, unlike the versions before it: this table is
 * the player's whole history, and dropping it to add two columns would delete the very statistics the
 * change exists to keep in sync.
 *
 * Existing rows are marked uploaded. They were either included in the one-shot `POST /stats/sync` when
 * this device linked, in which case the server has them and sending them again would double them, or the
 * device has never had a server, in which case there is nothing to send them to and a later sync will
 * carry them as part of the bulk merge. Their [GameResultEntity.clientId] stays empty, which is safe
 * precisely because no request will ever carry them.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE game_results ADD COLUMN clientId TEXT NOT NULL DEFAULT ''")
		db.execSQL("ALTER TABLE game_results ADD COLUMN uploaded INTEGER NOT NULL DEFAULT 0")
		db.execSQL("UPDATE game_results SET uploaded = 1")
		db.execSQL("CREATE INDEX IF NOT EXISTS index_game_results_uploaded ON game_results (uploaded)")
	}
}
