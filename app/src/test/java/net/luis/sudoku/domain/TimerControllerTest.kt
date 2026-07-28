package net.luis.sudoku.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerControllerTest {

	private class FakeClock {
		var now = 0L
		fun get() = this.now
	}

	@Test
	fun elapsedMillis_accumulatesOnlyWhileRunning() {
		val clock = FakeClock()
		val timer = TimerController(clock::get)

		timer.start()
		clock.now = 1000
		assertEquals(1000, timer.elapsedMillis())

		timer.pause()
		clock.now = 5000 // time passes while paused - must not count
		assertEquals(1000, timer.elapsedMillis())
		assertFalse(timer.isRunning)

		timer.start()
		clock.now = 6000
		assertEquals(2000, timer.elapsedMillis())
		assertTrue(timer.isRunning)
	}

	@Test
	fun restore_setsElapsedAndStaysPaused() {
		val clock = FakeClock()
		val timer = TimerController(clock::get)

		timer.restore(42_000)

		assertEquals(42_000, timer.elapsedMillis())
		assertFalse(timer.isRunning)
	}

	@Test
	fun start_isIdempotent() {
		val clock = FakeClock()
		val timer = TimerController(clock::get)

		timer.start()
		clock.now = 500
		timer.start() // must not reset the running-since point
		clock.now = 1000

		assertEquals(1000, timer.elapsedMillis())
	}
}
