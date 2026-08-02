package net.luis.sudoku.ui.presence

import net.luis.sudoku.data.remote.dto.MatchRequestResponse

/**
 * Which of the requests the server is currently serving should be in the popup (feature-spec §9.7).
 *
 * A pure rule rather than a branch inside the heartbeat loop, because it is the whole of what makes a
 * not-consumed-on-read protocol usable and it has three cases worth stating separately:
 *
 * - what is already showing keeps showing, while the server still reports it. Replacing it every beat
 *   would reset the popup under the player's finger.
 * - a suppressed request is never popped again. The server keeps serving it until the dismissal lands, so
 *   without this the popup for one just declined - or one that already had its few seconds on screen -
 *   would come straight back on the next beat.
 * - otherwise the oldest unsuppressed one is offered, and only that one: two popups stacked over a running,
 *   timed game would bury the game.
 *
 * @param current what is on screen now, if anything
 * @param served every request the server reported this beat, oldest first
 * @param suppressed ids that must not pop: answered in this process, or already shown once and timed out.
 *   A timed-out request is only suppressed *as a popup* - it stays pending, which is what the badge on the
 *   players button and the join button on the requester's profile are for.
 */
internal fun nextRequestToOffer(
	current: MatchRequestResponse?,
	served: List<MatchRequestResponse>,
	suppressed: Set<String>
): MatchRequestResponse? {
	if (current != null && served.any { it.id == current.id } && current.id !in suppressed) {
		return current
	}
	return served.firstOrNull { it.id !in suppressed }
}
