package net.luis.sudoku.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import net.luis.sudoku.data.local.entity.ServerStatsEntity

@Dao
interface ServerStatsDao {

	@Query("SELECT * FROM server_stats ORDER BY size ASC, variant ASC, difficulty ASC")
	suspend fun all(): List<ServerStatsEntity>

	@Upsert
	suspend fun upsertAll(rows: List<ServerStatsEntity>)

	@Query("DELETE FROM server_stats")
	suspend fun clear()

	/**
	 * Makes the mirror say exactly what the server just said, tiers the server no longer reports included.
	 *
	 * One transaction, because the two halves are only correct together: a clear that lands without its
	 * insert would leave the stats screen empty, and an insert without its clear would keep a tier the
	 * account no longer has.
	 */
	@Transaction
	suspend fun replaceAll(rows: List<ServerStatsEntity>) {
		this.clear()
		if (rows.isNotEmpty()) this.upsertAll(rows)
	}
}
