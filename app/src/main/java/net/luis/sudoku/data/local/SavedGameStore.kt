package net.luis.sudoku.data.local

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.luis.sudoku.core.GameSession
import net.luis.sudoku.data.local.dao.SavedGameDao
import net.luis.sudoku.data.local.entity.SavedGameEntity
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.PersistedUndoStack
import net.luis.sudoku.domain.UndoStack
import net.luis.sudoku.domain.restoreFrom
import net.luis.sudoku.domain.toPersisted
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import javax.inject.Inject

/** Exactly two slots (feature-spec §7): a normal game and a paused daily, kept separate on purpose. */
enum class SaveSlot { NORMAL, DAILY }

data class SavedGame(
	val session: GameSession,
	val undoStack: UndoStack,
	val elapsedMillis: Long,
	val livesRemaining: Int,
	val hintsUsed: Int
)

/**
 * Loads/saves the two persistence slots. Auto-resume on app start (§7) is just "load NORMAL if present" -
 * the ViewModel decides what to do when it's absent.
 */
class SavedGameStore @Inject constructor(private val dao: SavedGameDao) {

	suspend fun save(slot: SaveSlot, session: GameSession, undoStack: UndoStack, elapsedMillis: Long, livesRemaining: Int, hintsUsed: Int) {
		this.dao.upsert(
			SavedGameEntity(
				slot = slot.name,
				size = session.size.name,
				variant = session.variant.name,
				difficulty = session.key.difficulty().name,
				seed = session.key.seed(),
				valuesJson = Json.encodeToString(session.values().toList()),
				pencilMarksJson = Json.encodeToString(session.pencilMarksArray().toList()),
				elapsedMillis = elapsedMillis,
				livesRemaining = livesRemaining,
				hintsUsed = hintsUsed,
				undoStackJson = Json.encodeToString(undoStack.toPersisted())
			)
		)
	}

	suspend fun load(slot: SaveSlot): SavedGame? {
		val entity = this.dao.get(slot.name) ?: return null
		val key = PuzzleKey.of(
			GridSize.valueOf(entity.size),
			Variant.valueOf(entity.variant),
			Difficulty.valueOf(entity.difficulty),
			entity.seed
		)
		val values = Json.decodeFromString<List<Int>>(entity.valuesJson).toIntArray()
		val pencilMarks = Json.decodeFromString<List<Int>>(entity.pencilMarksJson).toIntArray()
		val session = GameSession.restore(key, values, pencilMarks)
		val undoStack = UndoStack().apply { restoreFrom(Json.decodeFromString<PersistedUndoStack>(entity.undoStackJson)) }
		return SavedGame(session, undoStack, entity.elapsedMillis, entity.livesRemaining, entity.hintsUsed)
	}

	suspend fun clear(slot: SaveSlot) = this.dao.delete(slot.name)
}
