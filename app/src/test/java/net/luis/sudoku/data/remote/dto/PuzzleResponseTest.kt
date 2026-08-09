package net.luis.sudoku.data.remote.dto

import kotlinx.serialization.json.Json
import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The v2 puzzle payload, and mainly the thing that made v2 necessary: the difficulty integer is the real
 * `1..15` band index now. To a v1 client a `6` meant Lisa; here it means tier 6, and Lisa is 15.
 */
class PuzzleResponseTest {

	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun toPuzzleKey_atTier15_isLisa() {
		val response = PuzzleResponse(genVersion = 2, size = 9, variant = "CLASSIC", difficulty = 15, seed = "42")

		val key = response.toPuzzleKey()

		assertEquals(Difficulty.LISA, key.difficulty())
		assertEquals(15, key.difficulty().index())
		assertEquals(GridSize.NINE, key.size())
		assertEquals(Variant.CLASSIC, key.variant())
		assertEquals(42L, key.seed())
	}

	@Test
	fun toPuzzleKey_atTier6_isTier6AndNoLongerLisa() {
		val key = PuzzleResponse(genVersion = 2, size = 9, difficulty = 6, seed = "1").toPuzzleKey()

		assertEquals(Difficulty.SIX, key.difficulty())
	}

	@Test
	fun toPuzzleKey_readsEveryBand() {
		(1..15).forEach { index ->
			val key = PuzzleResponse(genVersion = 2, size = 9, difficulty = index, seed = "1").toPuzzleKey()
			assertEquals(index, key.difficulty().index())
		}
	}

	@Test
	fun toPuzzleKey_withABandThatDoesNotExist_isRejectedRatherThanGuessed() {
		val response = PuzzleResponse(genVersion = 2, size = 9, difficulty = 16, seed = "1")

		assertThrows(IllegalArgumentException::class.java) { response.toPuzzleKey() }
	}

	@Test
	fun seed_travelsAsAStringBecauseA64BitValueDoesNotSurviveAJsonDouble() {
		val response = this.json.decodeFromString<PuzzleResponse>(
			"""{"genVersion":2,"size":9,"variant":"CLASSIC","difficulty":15,"seed":"9007199254740993"}"""
		)

		assertEquals(9007199254740993L, response.toPuzzleKey().seed())
	}

	@Test
	fun givens_areOptional_becauseAnOlderServerSendsNone() {
		val response = this.json.decodeFromString<PuzzleResponse>("""{"genVersion":2,"size":9,"difficulty":7}""")

		assertNull(response.givens)
		assertEquals(Variant.CLASSIC, response.toPuzzleKey().variant())
		assertEquals(0L, response.toPuzzleKey().seed())
	}

	@Test
	fun envelope_readsThePuzzleOutOfTheV2Wrapper() {
		val envelope = this.json.decodeFromString<PuzzleEnvelopeResponse>(
			"""{"puzzle":{"genVersion":2,"size":6,"variant":"CLASSIC","difficulty":8,"seed":"5","givens":"BgAA"}}"""
		)

		assertEquals(Difficulty.EIGHT, envelope.puzzle!!.toPuzzleKey().difficulty())
		assertEquals("BgAA", envelope.puzzle!!.givens)
	}
}
