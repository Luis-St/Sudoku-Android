package net.luis.sudoku.data.local

import android.content.ContentValues
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import net.luis.sudoku.core.GameSession
import net.luis.sudoku.core.testPuzzleProvider
import net.luis.sudoku.data.local.entity.SavedGameEntity
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.UndoStack
import net.luis.sudoku.domain.toPersisted
import net.luis.sudoku.generation.PuzzleGenerator
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.sharecode.GivensCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The saved game's givens: written on save, preferred on load, and the reason version 4 throws the old rows
 * away.
 *
 * This is the defect the column exists for. The generator does **not** branch on a key's `genVersion`, so a
 * save written under generator 1 and reopened under generator 2 comes back as a different grid with the
 * player's digits replayed into cells that mean something else. A key is a recipe and recipes changed; the
 * givens are the dish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedGameGivensTest {

	private lateinit var database: AppDatabase
	private lateinit var store: SavedGameStore

	@Before
	fun setUp() {
		this.database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		this.store = SavedGameStore(this.database.savedGameDao(), testPuzzleProvider())
	}

	@After
	fun tearDown() {
		this.database.close()
	}

	private fun key(seed: Long) = PuzzleKey.of(GridSize.FOUR, Variant.CLASSIC, Difficulty.ONE, seed)

	@Test
	fun save_writesTheGivensAlongsideTheKey() = runBlocking {
		val session = GameSession.generate(key(1L))

		this@SavedGameGivensTest.store.save(SaveSlot.NORMAL, session, UndoStack(), 0L, 5, 0)

		val stored = this@SavedGameGivensTest.database.savedGameDao().get(SaveSlot.NORMAL.name)!!
		assertNotNull(stored.givens)
		assertEquals(session.encodedGivens(), stored.givens)
	}

	@Test
	fun saveThenLoad_bringsBackTheSameGridAndTheSameProgress() = runBlocking {
		val session = GameSession.generate(key(1L))
		val emptyCell = (0 until session.cellCount).first { session.snapshot(it).empty }
		session.setValue(emptyCell, session.solutionAt(emptyCell))

		this@SavedGameGivensTest.store.save(SaveSlot.NORMAL, session, UndoStack(), 4_000L, 4, 1)
		val loaded = this@SavedGameGivensTest.store.load(SaveSlot.NORMAL)!!

		assertEquals(session.encodedGivens(), loaded.session.encodedGivens())
		assertEquals(session.snapshot(emptyCell).value, loaded.session.snapshot(emptyCell).value)
	}

	@Test
	fun load_prefersTheStoredGivensOverWhatTheKeyWouldRegenerate() = runBlocking {
		// The stand-in for a generator change: a row whose givens and whose key describe different grids.
		// Only a load that reads the givens can come back with the stored one, which is what makes a save
		// survive a `GenVersion` bump at all.
		val storedGivens = GivensCodec.encode(PuzzleGenerator.generate(key(999L)).puzzle())
		val regenerated = GivensCodec.encode(PuzzleGenerator.generate(key(1L)).puzzle())
		assertNotEquals("the two seeds must produce different grids for this test to prove anything", regenerated, storedGivens)
		this@SavedGameGivensTest.database.savedGameDao().upsert(entity(key(1L), storedGivens))

		val loaded = this@SavedGameGivensTest.store.load(SaveSlot.NORMAL)!!

		assertEquals(storedGivens, loaded.session.encodedGivens())
	}

	@Test
	fun load_withoutGivens_stillRestoresFromTheKeyAlone() = runBlocking {
		// The pre-2.0.0 shape. MIGRATION_3_4 deletes these rows so no player ever meets one, but the path is
		// kept honest rather than left to throw.
		this@SavedGameGivensTest.database.savedGameDao().upsert(entity(key(1L), givens = null))

		val loaded = this@SavedGameGivensTest.store.load(SaveSlot.NORMAL)!!

		assertEquals(GivensCodec.encode(PuzzleGenerator.generate(key(1L)).puzzle()), loaded.session.encodedGivens())
	}

	private fun entity(key: PuzzleKey, givens: String?) = SavedGameEntity(
		slot = SaveSlot.NORMAL.name,
		size = key.size().name,
		variant = key.variant().name,
		difficulty = key.difficulty().name,
		seed = key.seed(),
		valuesJson = "[${List(key.size().cellCount()) { 0 }.joinToString(",")}]",
		pencilMarksJson = "[${List(key.size().cellCount()) { 0 }.joinToString(",")}]",
		elapsedMillis = 0L,
		livesRemaining = 5,
		hintsUsed = 0,
		undoStackJson = kotlinx.serialization.json.Json.encodeToString(UndoStack().toPersisted()),
		givens = givens
	)
}

/**
 * The saved-game migrations, run against a real version-3 database file and carried all the way to the
 * current version.
 *
 * For 3 to 4, two things have to hold and the second is the awkward one. The column has to arrive, and every
 * existing row has to be gone: a pre-2.0.0 save has no givens and cannot be restored correctly under
 * generator 2, so keeping it would mean handing the player a scrambled board rather than an empty slot.
 *
 * For 4 to 5 the deletion is *selective*, which is the part worth a test of its own: generator 3 regrows a
 * different jigsaw for the same key, so chaos saves have to go while classic saves - whose layout is the
 * fixed box layout and therefore generator-independent - have to survive.
 *
 * Room opening the migrated file afterwards is the other half of the proof in both cases: it validates the
 * result against the exported schema, so a wrong `ALTER` fails here rather than on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedGameMigrationTest {

	private val name = "migration-3-4-test.db"

	@Before
	fun setUp() {
		RuntimeEnvironment.getApplication().deleteDatabase(this.name)
	}

	@After
	fun tearDown() {
		RuntimeEnvironment.getApplication().deleteDatabase(this.name)
	}

	@Test
	fun migration3To4_addsGivensAndDropsEveryPreExistingSave() {
		createVersion3Database()

		val database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java, this.name)
			.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
			.allowMainThreadQueries()
			.build()

		try {
			// The row written at version 3 is gone, and the column it lacked is there for the next one.
			assertNull(runBlocking { database.savedGameDao().get(SaveSlot.NORMAL.name) })
			runBlocking {
				database.savedGameDao().upsert(
					SavedGameEntity(
						slot = SaveSlot.NORMAL.name,
						size = "FOUR",
						variant = "CLASSIC",
						difficulty = "ONE",
						seed = 1L,
						valuesJson = "[]",
						pencilMarksJson = "[]",
						elapsedMillis = 0L,
						livesRemaining = 5,
						hintsUsed = 0,
						undoStackJson = kotlinx.serialization.json.Json.encodeToString(UndoStack().toPersisted()),
						givens = "AAAA"
					)
				)
				assertEquals("AAAA", database.savedGameDao().get(SaveSlot.NORMAL.name)!!.givens)
			}
		} finally {
			database.close()
		}
	}

	@Test
	fun migration4To5_dropsTheChaosSavesAndKeepsTheClassicOnes() {
		createVersion3Database()

		val database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java, this.name)
			.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
			.allowMainThreadQueries()
			.build()

		try {
			runBlocking {
				database.savedGameDao().upsert(save(SaveSlot.NORMAL, "CLASSIC"))
				database.savedGameDao().upsert(save(SaveSlot.DAILY, "CHAOS"))

				// The migration has already run by now, so it is re-run by hand against the same rows: what is
				// being proven is the statement, not the moment it fires.
				MIGRATION_4_5.migrate(database.openHelper.writableDatabase)

				assertNotNull(
					"a classic save is described by its givens alone and must survive a generator bump",
					database.savedGameDao().get(SaveSlot.NORMAL.name)
				)
				assertNull(
					"a chaos save's layout is regrown from the key, so generator 3 would restore it onto a different jigsaw",
					database.savedGameDao().get(SaveSlot.DAILY.name)
				)
			}
		} finally {
			database.close()
		}
	}

	private fun save(slot: SaveSlot, variant: String) = SavedGameEntity(
		slot = slot.name,
		size = "NINE",
		variant = variant,
		difficulty = "ONE",
		seed = 1L,
		valuesJson = "[]",
		pencilMarksJson = "[]",
		elapsedMillis = 0L,
		livesRemaining = 5,
		hintsUsed = 0,
		undoStackJson = kotlinx.serialization.json.Json.encodeToString(UndoStack().toPersisted()),
		givens = "AAAA"
	)

	/** The exact version-3 schema, taken from `app/schemas/.../3.json`, plus one saved game to be thrown away. */
	private fun createVersion3Database() {
		val callback = object : SupportSQLiteOpenHelper.Callback(3) {

			override fun onCreate(db: SupportSQLiteDatabase) {
				db.execSQL(
					"CREATE TABLE IF NOT EXISTS `saved_games` (`slot` TEXT NOT NULL, `size` TEXT NOT NULL, `variant` TEXT NOT NULL, " +
						"`difficulty` TEXT NOT NULL, `seed` INTEGER NOT NULL, `valuesJson` TEXT NOT NULL, `pencilMarksJson` TEXT NOT NULL, " +
						"`elapsedMillis` INTEGER NOT NULL, `livesRemaining` INTEGER NOT NULL, `hintsUsed` INTEGER NOT NULL, " +
						"`undoStackJson` TEXT NOT NULL, PRIMARY KEY(`slot`))"
				)
				db.execSQL(
					"CREATE TABLE IF NOT EXISTS `game_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `size` TEXT NOT NULL, " +
						"`variant` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `won` INTEGER NOT NULL, `elapsedMillis` INTEGER NOT NULL, " +
						"`hintsUsed` INTEGER NOT NULL, `livesLost` INTEGER NOT NULL, `hardestTechnique` TEXT, `timestamp` INTEGER NOT NULL, " +
						"`clientId` TEXT NOT NULL DEFAULT '', `uploaded` INTEGER NOT NULL DEFAULT 0)"
				)
				db.execSQL("CREATE INDEX IF NOT EXISTS `index_game_results_uploaded` ON `game_results` (`uploaded`)")
				db.execSQL(
					"CREATE TABLE IF NOT EXISTS `pending_daily_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, " +
						"`difficulty` INTEGER NOT NULL, `outcome` TEXT NOT NULL, `elapsedMs` INTEGER NOT NULL, `mistakes` INTEGER NOT NULL, " +
						"`hintsUsed` INTEGER NOT NULL, `solveOrderJson` TEXT NOT NULL)"
				)
				// Room refuses to open a database it cannot identify, so the version-3 identity hash from the
				// exported schema has to be there exactly as Room itself would have written it.
				db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
				db.execSQL("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$VERSION_3_IDENTITY_HASH')")

				val values = ContentValues().apply {
					put("slot", SaveSlot.NORMAL.name)
					put("size", "NINE")
					put("variant", "CLASSIC")
					put("difficulty", "THREE")
					put("seed", 1L)
					put("valuesJson", "[]")
					put("pencilMarksJson", "[]")
					put("elapsedMillis", 0L)
					put("livesRemaining", 5)
					put("hintsUsed", 0)
					put("undoStackJson", kotlinx.serialization.json.Json.encodeToString(UndoStack().toPersisted()))
				}
				db.insert("saved_games", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
			}

			override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
		}

		val configuration = SupportSQLiteOpenHelper.Configuration
			.builder(RuntimeEnvironment.getApplication())
			.name(this.name)
			.callback(callback)
			.build()
		val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
		helper.writableDatabase.close()
		helper.close()
	}

	private companion object {

		/** `app/schemas/net.luis.sudoku.data.local.AppDatabase/3.json`, field `identityHash`. */
		const val VERSION_3_IDENTITY_HASH = "1759b61f591783b5031d6b8964b80779"
	}
}
