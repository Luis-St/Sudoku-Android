package net.luis.sudoku.ui.presence

import net.luis.sudoku.data.remote.dto.MatchRequestResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The server serves a match request over and over until it is dismissed - it is not consumed by being
 * read, so that a client killed between receiving one and showing it does not lose it. [nextRequestToOffer]
 * is what turns that repetition into one stable banner, and these pin the three cases it has to get right.
 */
class MatchRequestOfferTest {

	private fun request(id: String) = MatchRequestResponse(id = id, matchId = "m-$id", inviteToken = "t-$id")

	@Test
	fun nothingIsOfferedWhenTheServerServesNothing() {
		assertNull(nextRequestToOffer(current = null, served = emptyList(), answered = emptySet()))
	}

	@Test
	fun theOldestRequestIsOffered() {
		val served = listOf(request("first"), request("second"))

		assertEquals("first", nextRequestToOffer(current = null, served = served, answered = emptySet())?.id)
	}

	@Test
	fun whatIsAlreadyShowingKeepsShowing() {
		val current = request("first")
		val served = listOf(request("first"), request("second"))

		// Re-offering the oldest every beat would reset the banner under the player's finger, and swapping it
		// for a newer one would move the buttons mid-tap.
		assertEquals(current.id, nextRequestToOffer(current, served, answered = emptySet())?.id)
	}

	@Test
	fun anAnsweredRequestIsNotOfferedAgainWhileTheServerStillServesIt() {
		val answered = request("first")
		val served = listOf(answered)

		// The dismissal has not reached the server yet, or failed to. Without this the banner comes straight
		// back on the next beat and the player has to decline the same request repeatedly.
		assertNull(nextRequestToOffer(current = null, served = served, answered = setOf("first")))
	}

	@Test
	fun answeringTheShowingRequestMovesOnToTheNextOne() {
		val current = request("first")
		val served = listOf(request("first"), request("second"))

		assertEquals("second", nextRequestToOffer(current, served, answered = setOf("first"))?.id)
	}

	@Test
	fun aRequestTheServerHasStoppedServingIsDropped() {
		val current = request("expired")
		val served = listOf(request("fresh"))

		// It expired or the match ended: the offer has to follow the server, not outlive it.
		assertEquals("fresh", nextRequestToOffer(current, served, answered = emptySet())?.id)
	}

	@Test
	fun aRequestTheServerHasStoppedServingLeavesNothingBehind() {
		val current = request("expired")

		assertNull(nextRequestToOffer(current, served = emptyList(), answered = emptySet()))
	}
}
