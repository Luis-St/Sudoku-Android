package net.luis.sudoku.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.luis.sudoku.data.local.ServerConfigStore
import net.luis.sudoku.data.remote.ApiClient
import net.luis.sudoku.data.remote.dto.PuzzleResponse
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.sharecode.GivensCodec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Where a puzzle came from, which is the one thing about the wait that is worth telling the player.
 *
 * [SERVER] means the grid arrived finished and was decoded, which is over before a spinner can settle.
 * [DEVICE] means this phone is running the generator itself: the same code the server runs, only on a phone
 * and at fifteen bands, where the hardest of them cost seconds. That is a wait with a reason, so the loading
 * screen says so.
 */
enum class PuzzleOrigin { SERVER, DEVICE }

/**
 * The single place a [GameSession] is built, and the only place [net.luis.sudoku.generation.PuzzleGenerator]
 * is called from outside a test.
 *
 * It exists for two reasons, and the first is thread confinement. Generation used to be called straight from
 * six view-model call sites and from the saved-game store, all on the main thread; at five bands that was
 * merely slow, and at fifteen it freezes the UI for seconds at a time. Every suspending entry point here
 * hops to [Dispatchers.Default] exactly once, at this boundary rather than at each call site, so there is
 * one rule to keep rather than seven.
 *
 * The second is the givens. The server generates and rates a puzzle once and ships the finished grid
 * (`GivensCodec`), and rebuilding from that costs milliseconds against up to a second of local generation.
 * So the order is: ask the server, decode what it sends, and generate locally only when there is nothing to
 * decode. Local generation is not a degraded puzzle - it is the same generator the server runs, and it may
 * claim all fifteen bands ([OFFLINE_BANDS]) - it is only the slow way to the same place, which is why callers
 * are told about it through [PuzzleOrigin] rather than left watching an unexplained spinner.
 */
@Singleton
class PuzzleProvider @Inject constructor(
	private val apiClient: ApiClient,
	private val serverConfigStore: ServerConfigStore
) {

	/**
	 * A fresh single-player puzzle at the requested shape.
	 *
	 * Asks `POST /api/v2/puzzles` first. A server that answers decides the seed, so the puzzle is the one it
	 * would verify against; a server that cannot be reached, or is not configured at all, leaves the key to
	 * be minted here exactly as a purely offline install has always minted it, with a random seed.
	 *
	 * @param onOrigin called before the expensive step with how this puzzle is about to be built
	 */
	suspend fun forNewGame(
		size: GridSize,
		variant: Variant,
		difficulty: Difficulty,
		onOrigin: (PuzzleOrigin) -> Unit = {}
	): GameSession = withContext(Dispatchers.Default) {
		val response = requestFromServer(size, variant, difficulty)
		// Key and givens stand or fall together. A response whose key will not parse is discarded whole: its
		// givens describe the grid *that* key names, and pairing them with a freshly minted local key would
		// hand CLASSIC a board that decodes and validates perfectly while the seed recorded beside it never
		// produced it. CHAOS would merely fail the layout check and regenerate; CLASSIC would not notice.
		val serverKey = response?.toPuzzleKeyOrNull()
		val key = serverKey ?: PuzzleKey.of(size, variant, difficulty, Random.nextLong())
		build(key, response?.givens.takeIf { serverKey != null }, onOrigin)
	}

	/**
	 * The puzzle a key already names, with [givens] when whatever handed the key over also handed over the
	 * grid - the daily, a match snapshot, a saved game.
	 *
	 * No request is made: the caller has the key, and there is no route that turns one back into a puzzle.
	 * Absent or unusable givens therefore mean local generation.
	 */
	suspend fun forKey(key: PuzzleKey, givens: String? = null, onOrigin: (PuzzleOrigin) -> Unit = {}): GameSession =
		withContext(Dispatchers.Default) { build(key, givens, onOrigin) }

	/** The puzzle a v2 response describes, falling back to the key it carries when it carries no givens. */
	suspend fun forResponse(puzzle: PuzzleResponse, onOrigin: (PuzzleOrigin) -> Unit = {}): GameSession =
		withContext(Dispatchers.Default) { build(puzzle.toPuzzleKey(), puzzle.givens, onOrigin) }

	/**
	 * A saved game put back onto the board it was played on.
	 *
	 * [givens] is what makes the restore honest across a generator change - see [GameSession.restore].
	 */
	suspend fun restore(
		key: PuzzleKey,
		givens: String?,
		values: IntArray,
		pencilMarks: IntArray,
		onOrigin: (PuzzleOrigin) -> Unit = {}
	): GameSession = withContext(Dispatchers.Default) {
		val decoded = decode(key, givens)
		onOrigin(if (decoded != null) PuzzleOrigin.SERVER else PuzzleOrigin.DEVICE)
		GameSession.restore(key, values, pencilMarks, decoded)
	}

	/**
	 * The local half, for a caller that is **already** off the main thread and already holds the givens.
	 *
	 * That is the multiplayer case and only the multiplayer case: a `MATCH_STATE` arrives on the socket's own
	 * [Dispatchers.Default] thread carrying the key and the grid together, and the handler applies the whole
	 * snapshot inside one `Snapshot.withMutableSnapshot` so the board can never be composed half-updated.
	 * Suspending in the middle of that would break the atomicity it depends on, and there is nothing to
	 * suspend *for* - the network round trip already happened, this is a decode.
	 */
	fun localSession(key: PuzzleKey, givens: String? = null, onOrigin: (PuzzleOrigin) -> Unit = {}): GameSession =
		build(key, givens, onOrigin)

	private fun build(key: PuzzleKey, givens: String?, onOrigin: (PuzzleOrigin) -> Unit): GameSession {
		val decoded = decode(key, givens)
		if (decoded != null) {
			// Not taken on trust even so: fromGivens proves the grid uniquely solvable, and a payload that
			// fails that is worth nothing - so the generator produces an honest puzzle instead.
			//
			// Reported only once it has succeeded. Announcing SERVER before the check and DEVICE after it
			// failed would flash "fetched" and then "generating" on the loading screen for the one case where
			// the player most deserves a straight answer about which wait they are in.
			val session = try {
				GameSession.fromGivens(key, decoded)
			} catch (e: IllegalArgumentException) {
				null // Fall through to generation below.
			}
			if (session != null) {
				onOrigin(PuzzleOrigin.SERVER)
				return session
			}
		}
		// Nothing is filtered here, and that is the ruling rather than an omission: see [OFFLINE_BANDS].
		onOrigin(PuzzleOrigin.DEVICE)
		return GameSession.generate(key)
	}

	/** Null for absent givens and for a payload that cannot be read - both mean "generate it here". */
	private fun decode(key: PuzzleKey, givens: String?): IntArray? {
		if (givens.isNullOrBlank()) return null
		return try {
			GivensCodec.decode(givens).takeIf { it.size == key.size().cellCount() }
		} catch (e: IllegalArgumentException) {
			null
		}
	}

	/**
	 * Silent on every failure, deliberately: an unreachable server is the case the offline fallback exists
	 * for, not something to refuse a puzzle over. A device with no server configured, or signed out of one,
	 * never asks at all.
	 */
	private suspend fun requestFromServer(size: GridSize, variant: Variant, difficulty: Difficulty): PuzzleResponse? {
		return try {
			val config = this.serverConfigStore.current()
			val baseUrl = config.serverUrl ?: return null
			val token = config.sessionToken ?: return null
			this.apiClient.requestPuzzle(baseUrl, token, size.n(), variant.name, difficulty.index()).puzzle
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			null
		}
	}

	/** A malformed key from the wire is worth no more than no answer at all. */
	private fun PuzzleResponse.toPuzzleKeyOrNull(): PuzzleKey? = try {
		this.toPuzzleKey()
	} catch (e: IllegalArgumentException) {
		null
	}

	companion object {

		/**
		 * Every band the offline fallback is allowed to produce, which is every band there is: all fifteen,
		 * Lisa included, at every grid size that band is reachable at.
		 *
		 * This is a decision, not a leftover. When generation moved onto the server the question was left open
		 * (DIFFICULTY-15-HANDOFF §4, Q4b) whether the fallback should be cut back to the cheap bands, because
		 * the hard tail is genuinely slow to build on a phone: measured on a desktop JVM, 9x9 chaos peaks near
		 * 4.7 s and 16x16 band 11 near 10.8 s, and a phone is several times slower again. The owner ruled for
		 * all fifteen on 2026-08-09: a player who picked a tier and then lost their connection would otherwise
		 * be told the tier they chose is unavailable, and a refused puzzle is a worse failure than a slow one.
		 * The fallback is also rare now, since it only runs when the server cannot be reached or sends no
		 * givens.
		 *
		 * The rejected alternative was a restricted offline range, and the trap it carries is that the
		 * restriction is invisible: the generator snaps an unsupported request onto the nearest band it will
		 * build, so a capped fallback does not refuse tier 13, it silently hands out tier 9 and says nothing.
		 * There is deliberately no flag and no filter to go with this constant - the whole point is that there
		 * is nothing to configure - so the price is paid in waiting instead, and the wait is explained to the
		 * player by [net.luis.sudoku.ui.game.PuzzleLoading.slowOnDevice].
		 *
		 * What a *size* can produce is a separate and older limit, measured by shared-core and surfaced by
		 * [net.luis.sudoku.domain.DifficultyOptions]: a 4x4 grid is band 1 whether the puzzle is fetched or
		 * built here. This constant narrows nothing beyond that.
		 */
		val OFFLINE_BANDS: Set<Difficulty> = Difficulty.values().toSet()
	}
}
