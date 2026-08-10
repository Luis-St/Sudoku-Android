package net.luis.sudoku.core

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.domain.DifficultyOptions
import net.luis.sudoku.generation.PuzzleGenerator
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import net.luis.sudoku.key.PuzzleKey
import net.luis.sudoku.sharecode.GivensCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * [PuzzleProvider]'s decision table, which is the whole point of the class: prefer the server's finished
 * grid, and generate locally when there is no grid to be had.
 *
 * The two outcomes are told apart by the *givens* rather than by the key, because that is exactly where the
 * bug would hide: a provider that ignored the payload and regenerated from the key would still return a
 * puzzle with the right key, the right size and the right band, and only the grid itself would be wrong.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PuzzleProviderTest {

	private val size = GridSize.FOUR
	private val variant = Variant.CLASSIC
	private val difficulty = Difficulty.ONE

	private fun key(seed: Long) = PuzzleKey.of(this.size, this.variant, this.difficulty, seed)

	/** A real, uniquely solvable grid for [size] - `fromGivens` refuses anything less, and rightly. */
	private fun givensOf(seed: Long): String = GivensCodec.encode(PuzzleGenerator.generate(key(seed)).puzzle())

	private fun puzzleJson(seed: Long, givens: String?): String {
		val givensField = givens?.let { ""","givens":"$it"""" } ?: ""
		return """{"puzzle":{"genVersion":2,"size":${this.size.n()},"variant":"CLASSIC","difficulty":${this.difficulty.index()},"seed":"$seed"$givensField}}"""
	}

	@Test
	fun forNewGame_serverSendsGivens_buildsThatExactGrid() = runBlocking {
		// Givens from one seed, key from another: only a provider that actually used the payload can come
		// back with the first grid.
		val sent = givensOf(seed = 999L)
		assertNotEquals("the two seeds must produce different grids for this test to prove anything", givensOf(seed = 1L), sent)
		val requests = mutableListOf<HttpRequestData>()
		val provider = testPuzzleProvider(puzzleJson(seed = 1L, givens = sent), requests)
		val origins = mutableListOf<PuzzleOrigin>()

		val session = provider.forNewGame(this@PuzzleProviderTest.size, this@PuzzleProviderTest.variant, this@PuzzleProviderTest.difficulty) { origins.add(it) }

		assertEquals(sent, session.encodedGivens())
		assertEquals(key(1L), session.key)
		assertEquals(listOf(PuzzleOrigin.SERVER), origins)
		assertEquals("/api/v2/puzzles", requests.single().url.encodedPath)
		assertEquals(HttpMethod.Post, requests.single().method)
	}

	@Test
	fun forNewGame_serverSendsNoGivens_generatesTheServersKeyLocally() = runBlocking {
		val provider = testPuzzleProvider(puzzleJson(seed = 7L, givens = null))
		val origins = mutableListOf<PuzzleOrigin>()

		val session = provider.forNewGame(this@PuzzleProviderTest.size, this@PuzzleProviderTest.variant, this@PuzzleProviderTest.difficulty) { origins.add(it) }

		// The server still decided the seed, so the puzzle is the one it would verify against - it just cost
		// this device the generation.
		assertEquals(key(7L), session.key)
		assertEquals(givensOf(seed = 7L), session.encodedGivens())
		assertEquals(listOf(PuzzleOrigin.DEVICE), origins)
	}

	@Test
	fun forNewGame_serverUnreachable_generatesLocallyAtTheRequestedShape() = runBlocking {
		val provider = unreachableServerPuzzleProvider()
		val origins = mutableListOf<PuzzleOrigin>()

		val session = provider.forNewGame(this@PuzzleProviderTest.size, this@PuzzleProviderTest.variant, this@PuzzleProviderTest.difficulty) { origins.add(it) }

		assertEquals(this@PuzzleProviderTest.size, session.key.size())
		assertEquals(this@PuzzleProviderTest.variant, session.key.variant())
		assertEquals(this@PuzzleProviderTest.difficulty, session.key.difficulty())
		assertEquals(listOf(PuzzleOrigin.DEVICE), origins)
	}

	@Test
	fun forNewGame_noServerConfigured_neverAsksAndGeneratesLocally() = runBlocking {
		val provider = testPuzzleProvider()
		val origins = mutableListOf<PuzzleOrigin>()

		val session = provider.forNewGame(this@PuzzleProviderTest.size, this@PuzzleProviderTest.variant, this@PuzzleProviderTest.difficulty) { origins.add(it) }

		assertEquals(this@PuzzleProviderTest.difficulty, session.key.difficulty())
		assertEquals(listOf(PuzzleOrigin.DEVICE), origins)
	}

	@Test
	fun forKey_withGivens_decodesRatherThanGenerates() = runBlocking {
		val provider = testPuzzleProvider()
		val sent = givensOf(seed = 999L)
		val origins = mutableListOf<PuzzleOrigin>()

		val session = provider.forKey(key(1L), sent) { origins.add(it) }

		assertEquals(sent, session.encodedGivens())
		assertEquals(listOf(PuzzleOrigin.SERVER), origins)
	}

	@Test
	fun forKey_withGivensThatDoNotDecode_fallsBackToGenerating() = runBlocking {
		// Nothing off the wire is taken on trust: a payload that is not a grid is worth no more than none.
		val provider = testPuzzleProvider()
		val origins = mutableListOf<PuzzleOrigin>()

		val session = provider.forKey(key(1L), "!!!not base64!!!") { origins.add(it) }

		assertEquals(givensOf(seed = 1L), session.encodedGivens())
		assertTrue(origins.contains(PuzzleOrigin.DEVICE))
	}

	@Test
	fun forKey_withGivensForTheWrongSize_fallsBackToGenerating() = runBlocking {
		val provider = testPuzzleProvider()
		val nineByNine = GivensCodec.encode(
			PuzzleGenerator.generate(PuzzleKey.of(GridSize.NINE, Variant.CLASSIC, Difficulty.ONE, 3L)).puzzle()
		)

		val session = provider.forKey(key(1L), nineByNine)

		assertEquals(this@PuzzleProviderTest.size, session.key.size())
		assertEquals(givensOf(seed = 1L), session.encodedGivens())
	}

	@Test
	fun forNewGame_serverKeyIsMalformed_discardsItsGivensTooRatherThanPairingThemWithALocalKey() = runBlocking {
		// Key and givens describe one grid together. A response whose key will not parse used to be half
		// kept: the key was replaced with a freshly minted local one and the givens were passed through
		// anyway. On CLASSIC, whose layout does not come from the key, those givens decode and validate
		// perfectly - so the session would show the server's board while recording a seed that never
		// produced it, and nothing downstream could tell.
		val sent = givensOf(seed = 999L)
		val malformed = """{"puzzle":{"genVersion":2,"size":${this@PuzzleProviderTest.size.n()},"variant":"NOT_A_VARIANT","difficulty":${this@PuzzleProviderTest.difficulty.index()},"seed":"1","givens":"$sent"}}"""
		val provider = testPuzzleProvider(malformed)
		val origins = mutableListOf<PuzzleOrigin>()

		val session = provider.forNewGame(this@PuzzleProviderTest.size, this@PuzzleProviderTest.variant, this@PuzzleProviderTest.difficulty) { origins.add(it) }

		assertNotEquals("the discarded key's givens must not survive it", sent, session.encodedGivens())
		assertEquals(givensOf(session.key.seed()), session.encodedGivens())
		assertEquals(listOf(PuzzleOrigin.DEVICE), origins)
	}

	@Test
	fun forKey_withGivensThatFailTheUniquenessCheck_reportsDeviceOnlyOnce() = runBlocking {
		// The origin is what the loading screen tells the player they are waiting for, so it must be said
		// once and be true. Reporting SERVER before the check and DEVICE after it failed flashed "fetched"
		// and then "generating" for exactly the case where the wait is real.
		val decoded = GivensCodec.decode(givensOf(seed = 999L))
		// A well-formed payload for the right size that is not a uniquely solvable puzzle: emptied entirely,
		// so it decodes cleanly and then fails fromGivens.
		val empty = GivensCodec.encode(this@PuzzleProviderTest.size, IntArray(decoded.size))
		val origins = mutableListOf<PuzzleOrigin>()

		val session = testPuzzleProvider().forKey(key(1L), empty) { origins.add(it) }

		assertEquals(listOf(PuzzleOrigin.DEVICE), origins)
		assertEquals(givensOf(seed = 1L), session.encodedGivens())
	}

	@Test
	fun forNewGame_serverIsReachableButSlowerThanTheTimeout_generatesLocallyInstead() = runBlocking {
		// A server generating an unpooled band inline is reachable, so no transport error ever arrives - just
		// silence. Without a timeout on this one call the player watches a loading screen for as long as the
		// server takes, and then still waits for the local generation the device could have started at once.
		val wouldHaveBeenServed = givensOf(seed = 999L)
		val provider = slowServerPuzzleProvider(10.seconds, puzzleJson(seed = 999L, givens = wouldHaveBeenServed))
		val origins = mutableListOf<PuzzleOrigin>()

		val session = provider.forNewGame(this@PuzzleProviderTest.size, this@PuzzleProviderTest.variant, this@PuzzleProviderTest.difficulty) { origins.add(it) }

		// The answer that never arrived in time is a perfectly good one, so anything but the local grid here
		// would mean the request was waited out rather than abandoned.
		assertNotEquals(wouldHaveBeenServed, session.encodedGivens())
		assertEquals(this@PuzzleProviderTest.difficulty, session.key.difficulty())
		assertEquals(listOf(PuzzleOrigin.DEVICE), origins)
	}

	/**
	 * The offline policy, pinned. All fifteen bands are permitted offline by ruling (see
	 * [PuzzleProvider.OFFLINE_BANDS]), so the failure this exists to catch is somebody deciding later that the
	 * slow bands are not worth generating on a phone and quietly cutting the range: that change looks harmless
	 * and is not, because the generator snaps an unbuildable request onto the nearest band instead of refusing
	 * it, so a capped fallback hands the player a different tier without a word.
	 *
	 * The expected set is written out rather than derived from [Difficulty], so a narrowing fails here instead
	 * of narrowing both sides of the assertion at once.
	 */
	@Test
	fun offlineBands_areAllFifteen() {
		assertEquals(
			setOf(
				Difficulty.ONE, Difficulty.TWO, Difficulty.THREE, Difficulty.FOUR, Difficulty.FIVE,
				Difficulty.SIX, Difficulty.SEVEN, Difficulty.EIGHT, Difficulty.NINE, Difficulty.TEN,
				Difficulty.ELEVEN, Difficulty.TWELVE, Difficulty.THIRTEEN, Difficulty.FOURTEEN, Difficulty.LISA
			),
			PuzzleProvider.OFFLINE_BANDS
		)
		// The offline range narrows nothing a picker can offer: every band any size supports is in it, so no
		// selection a player can make becomes unbuildable the moment the server goes away.
		for (size in GridSize.values()) {
			assertTrue("$size", PuzzleProvider.OFFLINE_BANDS.containsAll(DifficultyOptions.supportedAt(size)))
		}
	}

	@Test
	fun forNewGame_serverUnreachableAtTheTopBand_stillGeneratesThatBandOnDevice() = runBlocking {
		// The behavioural half of the policy, and the one case a restricted fallback would have refused: the
		// hardest band there is, on a size that genuinely reaches it, with no server to ask. Asserting the band
		// on the returned key rather than the request is what catches a silent snap down to something cheaper.
		val provider = unreachableServerPuzzleProvider()
		val origins = mutableListOf<PuzzleOrigin>()

		val session = provider.forNewGame(GridSize.NINE, Variant.CLASSIC, Difficulty.LISA) { origins.add(it) }

		assertEquals(Difficulty.LISA, session.key.difficulty())
		assertEquals(GridSize.NINE, session.key.size())
		assertEquals(listOf(PuzzleOrigin.DEVICE), origins)
	}

	@Test
	fun restore_withGivens_replaysOntoTheStoredGridRatherThanARegeneratedOne() = runBlocking {
		val provider = testPuzzleProvider()
		val stored = givensOf(seed = 999L)
		val decoded = GivensCodec.decode(stored)
		val emptyCell = decoded.indices.first { decoded[it] == 0 }
		val values = IntArray(this@PuzzleProviderTest.size.cellCount())
		values[emptyCell] = 1

		val session = provider.restore(key(1L), stored, values, IntArray(this@PuzzleProviderTest.size.cellCount()))

		assertEquals(stored, session.encodedGivens())
		assertEquals(1, session.snapshot(emptyCell).value)
	}
}
