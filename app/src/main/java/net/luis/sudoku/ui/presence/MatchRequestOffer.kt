package net.luis.sudoku.ui.presence

import net.luis.sudoku.data.remote.dto.MatchRequestResponse

/**
 * Which of the requests the server is currently serving should be on screen (feature-spec §9.7).
 *
 * A pure rule rather than a branch inside the heartbeat loop, because it is the whole of what makes a
 * not-consumed-on-read protocol usable and it has three cases worth stating separately:
 *
 * - what is already showing keeps showing, while the server still reports it. Replacing it every beat
 *   would reset the banner under the player's finger.
 * - a request the player has already answered is never offered again. The server keeps serving it until
 *   the dismissal lands, so without this the banner for one just declined would come straight back.
 * - otherwise the oldest unanswered one is offered, and only that one: two banners stacked over a running,
 *   timed game would bury the game.
 *
 * @param current what is on screen now, if anything
 * @param served every request the server reported this beat, oldest first
 * @param answered ids the player has already accepted or declined in this process
 */
internal fun nextRequestToOffer(
	current: MatchRequestResponse?,
	served: List<MatchRequestResponse>,
	answered: Set<String>
): MatchRequestResponse? {
	if (current != null && served.any { it.id == current.id } && current.id !in answered) {
		return current
	}
	return served.firstOrNull { it.id !in answered }
}
