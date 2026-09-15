package net.luis.sudoku.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.luis.sudoku.data.local.dao.LearnProgressDao
import net.luis.sudoku.data.local.dao.PendingDailyResultDao
import net.luis.sudoku.data.local.dao.SavedGameDao
import net.luis.sudoku.data.local.dao.ServerStatsDao
import net.luis.sudoku.data.local.dao.StatisticsDao
import net.luis.sudoku.data.local.entity.GameResultEntity
import net.luis.sudoku.data.local.entity.LearnProgressEntity
import net.luis.sudoku.data.local.entity.PendingDailyResultEntity
import net.luis.sudoku.data.local.entity.SavedGameEntity
import net.luis.sudoku.data.local.entity.ServerStatsEntity

@Database(
	entities = [SavedGameEntity::class, GameResultEntity::class, PendingDailyResultEntity::class, LearnProgressEntity::class, ServerStatsEntity::class],
	version = 7,
	exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
	abstract fun savedGameDao(): SavedGameDao
	abstract fun statisticsDao(): StatisticsDao
	abstract fun pendingDailyResultDao(): PendingDailyResultDao
	abstract fun learnProgressDao(): LearnProgressDao
	abstract fun serverStatsDao(): ServerStatsDao
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

/**
 * Drops the saved **chaos** games, and only those, for generator 3.
 *
 * The givens column added in version 4 is what normally makes a save survive a generator change, and for a
 * classic board it does exactly that: the region layout is the fixed box layout of the size, so the same
 * givens describe the same board under any generator. A jigsaw board is not described by its givens alone.
 * Its layout is grown from the key's own random stream, that stream is derived from the key *including* its
 * `genVersion`, and a save stores no `genVersion` - so the key is rebuilt at whatever version is current and
 * generator 3 grows a different jigsaw for it. The stored givens then belong to regions that no longer exist:
 * at best the rebuild fails its uniqueness check and the player silently gets a fresh board, at worst their
 * pen values and pencil marks are replayed into cells that mean something else.
 *
 * So the chaos rows go and the classic rows stay. Losing an unfinished jigsaw across one upgrade is a far
 * smaller loss than one that comes back scrambled, and there is nothing stored that could reconstruct the old
 * layout. Finished games, statistics, streaks and currency live in other tables and are untouched.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("DELETE FROM saved_games WHERE variant = 'CHAOS'")
	}
}

/**
 * Adds the learn area's progress table.
 *
 * Written out rather than left to destructive migration for the same reason `game_results` was: by the time
 * a later version arrives this table holds every technique the player has mastered, and there is nothing
 * anywhere else to rebuild it from. It is created empty, which is exactly right - no progress is the honest
 * starting state for a feature that did not exist before this version.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"CREATE TABLE IF NOT EXISTS learn_progress (" +
				"technique TEXT NOT NULL, " +
				"level INTEGER NOT NULL, " +
				"subLevel INTEGER NOT NULL, " +
				"state TEXT NOT NULL, " +
				"updatedAt INTEGER NOT NULL, " +
				"uploaded INTEGER NOT NULL DEFAULT 0, " +
				"PRIMARY KEY(technique, level, subLevel))"
		)
	}
}

/**
 * Adds the mirror of the server's per-tier statistics.
 *
 * Written out rather than left to destructive migration, like every version since 3 - not for this table's
 * own sake, which is a cache the next sync refills, but for the three around it: `game_results`,
 * `learn_progress` and `saved_games` are all irreplaceable and a destructive migration would take them
 * with it. Created empty, which is honest - the device has not been told the account's totals yet.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"CREATE TABLE IF NOT EXISTS server_stats (" +
				"size INTEGER NOT NULL, " +
				"variant TEXT NOT NULL, " +
				"difficulty INTEGER NOT NULL, " +
				"gamesPlayed INTEGER NOT NULL, " +
				"solved INTEGER NOT NULL, " +
				"failed INTEGER NOT NULL, " +
				"bestTimeMs INTEGER, " +
				"averageTimeMs INTEGER, " +
				"hintsUsed INTEGER NOT NULL, " +
				"PRIMARY KEY(size, variant, difficulty))"
		)
	}
}
