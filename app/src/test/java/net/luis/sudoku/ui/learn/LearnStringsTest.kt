package net.luis.sudoku.ui.learn

import net.luis.sudoku.learn.LearnTechniques
import net.luis.sudoku.solver.Technique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers [stringsOrNull] against the core's own taught set.
 *
 * The two have to agree in both directions, and since issue 2.3.0/2 something depends on it: a hint decides
 * whether it has a technique step to show by asking [LearnTechniques.isTaught], and then renders the copy.
 * A taught technique without copy is a crash on the game screen; copy for an untaught one is a lesson the
 * learn area never lists and nothing can reach.
 */
class LearnStringsTest {

	@Test
	fun `every taught technique has copy`() {
		for (technique in LearnTechniques.taught()) {
			assertNotNull("$technique is taught and needs copy", stringsOrNull(technique))
		}
	}

	@Test
	fun `no untaught technique has copy`() {
		for (technique in Technique.entries) {
			if (LearnTechniques.isTaught(technique)) {
				continue
			}
			assertNull("$technique is not taught and must have no copy", stringsOrNull(technique))
		}
	}

	@Test
	fun `the copy set is exactly the taught set`() {
		val withCopy = Technique.entries.filter { stringsOrNull(it) != null }

		assertEquals(LearnTechniques.taught().toSet(), withCopy.toSet())
		assertEquals(LearnTechniques.count(), withCopy.size)
	}

	@Test
	fun `asking for an untaught technique by name is still an error`() {
		// The learn area's own screens list only what is taught, so reaching one of these there is a
		// programming error and stays one - it is the *hint* that has to cope, and it asks stringsOrNull.
		val untaught = Technique.entries.first { !LearnTechniques.isTaught(it) }

		try {
			stringsOf(untaught)
			throw AssertionError("stringsOf accepted $untaught")
		} catch (expected: IllegalArgumentException) {
			assertEquals("The learn area does not teach $untaught", expected.message)
		}
	}
}
