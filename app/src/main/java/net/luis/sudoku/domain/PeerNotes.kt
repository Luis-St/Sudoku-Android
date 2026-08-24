package net.luis.sudoku.domain

/**
 * feature-spec §5.6's auto-clear-peers, for the modes whose notes live in a **map** instead of in the
 * session's own cells: duel keeps them privately per device (server-spec §10.5) and co-op holds the
 * group's shared set, which arrives cell by cell over the socket.
 *
 * Multiplayer item 1 of 2.2.0. Single-player gets the same rule from [BoardEditor], which no multiplayer
 * mode goes through - a pen entry there is a `PLACE` frame and the digit only reaches the board when the
 * server says so. That is why the notes were left standing in every mode but single-player: the board
 * filled up while the note grids all down the row, column and region still offered a digit that was
 * already sitting on it.
 *
 * The peers are passed in rather than derived here so this stays a pure function over the note map;
 * `GameSession.peersOf` is what every caller hands it.
 */
object PeerNotes {

	/**
	 * @param notes cell index -> pencil-mark bitmask, in the shape [net.luis.sudoku.core.CellSnapshot.pencilMarks] uses
	 * @param cell the cell the digit was placed in - its own notes go too, since a placed digit is not a
	 *   question any more and the cell is drawn as its value from here on
	 * @param peers every cell sharing [cell]'s row, column or region
	 * @param digit the digit that was placed, and is now impossible in all of [peers]
	 * @return [notes] without [cell] and without [digit] anywhere it has just become impossible, with any
	 *   cell left holding nothing dropped rather than kept as an empty mask
	 */
	fun cleared(notes: Map<Int, Int>, cell: Int, peers: Set<Int>, digit: Int): Map<Int, Int> {
		val bit = (1 shl digit).inv()
		return buildMap {
			for ((index, mask) in notes) {
				if (index == cell) continue
				val updated = if (index in peers) mask and bit else mask
				if (updated != 0) put(index, updated)
			}
		}
	}
}
