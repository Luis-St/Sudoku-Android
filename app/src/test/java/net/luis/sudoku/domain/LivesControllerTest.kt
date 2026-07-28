package net.luis.sudoku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivesControllerTest {

	@Test
	fun loseLife_decrementsUntilZero() {
		val lives = LivesController(maxLives = 2)

		assertFalse(lives.loseLife())
		assertEquals(1, lives.remaining)
		assertTrue(lives.loseLife())
		assertEquals(0, lives.remaining)
		assertTrue(lives.isDead)
	}

	@Test
	fun loseLife_atZero_staysAtZero() {
		val lives = LivesController(maxLives = 1)
		lives.loseLife()

		assertTrue(lives.loseLife())
		assertEquals(0, lives.remaining)
	}

	@Test
	fun restore_clampsToMax() {
		val lives = LivesController(maxLives = 5)
		lives.restore(9)

		assertEquals(5, lives.remaining)
	}
}
