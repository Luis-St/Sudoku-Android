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
	version = 4,
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

/**
 * Gives `saved_games` the column that makes a save survive a generator change, and drops every row that
 * predates it.
 *
 * The deletion is the point, not collateral damage. A save written before this column holds a key and
 * nothing else, and the generator does not branch on the key's `genVersion` - so under generator 2 that key
 * builds a *different grid*, and restoring onto it replays the player's pen values and pencil marks into
 * cells that mean something else entirely. There is no way to recover the original board from what was
 * stored, so the honest outcome is that the in-progress game is gone: a puzzle the player has to start again
 * is a far smaller loss than one that silently comes back scrambled, and only games left unfinished across
 * this one upgrade are affected. Finished games, statistics, streaks and currency all live in other tables
 * and are untouched.
 *
 * `TEXT` and nullable, because `ALTER TABLE ... ADD COLUMN` cannot add a `NOT NULL` column without a default
 * and there is no honest default for a puzzle's givens.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE saved_games ADD COLUMN givens TEXT")
		db.execSQL("DELETE FROM saved_games")
	}
}
