package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.difficulty.DifficultyBands
import net.luis.sudoku.grid.GridSize

/**
 * Which difficulty bands a picker may offer at a given size, and what a selection becomes when the size
 * changes underneath it.
 *
 * A grid size does not reach every band, and the reachable set is not a run from one upwards: a 4x4 grid is
 * band 1 and nothing else, and a 6x6 makes 1, 2, 3, 7 and 8 but never 4, 5 or 6 - its boxes make locked
 * candidates so common that it steps straight from band 3 to band 7. These are measured facts held by
 * shared-core ([DifficultyBands.supported]), not a ceiling this app can guess at, so the pickers ask rather
 * than filter by index.
 *
 * Offering an unreachable band is not a cosmetic fault. The generator snaps the request onto the nearest
 * band it can actually produce, so a player who picks tier 5 on a 6x6 board is handed tier 3 and told
 * nothing about it - the setting simply appears to be ignored.
 */
object DifficultyOptions {

	private val BANDS = DifficultyBands.defaults()

	/** The bands [size] can produce, in ascending order, ready to hand to a dropdown. */
	fun supportedAt(size: GridSize): List<Difficulty> = BANDS.supported(size).sortedBy(Difficulty::index)

	/** As [supportedAt], without Lisa - every multiplayer mode rejects it (feature-spec §4.3). */
	fun multiplayerSupportedAt(size: GridSize): List<Difficulty> = supportedAt(size).filterNot(Difficulty::isLisa)

	/**
	 * [selected] if [size] can produce it, otherwise the nearest band it can, preferring the easier of two
	 * equally close ones - shared-core's own rule, so a picker and the generator can never disagree.
	 */
	fun snap(size: GridSize, selected: Difficulty): Difficulty = BANDS.nearestSupported(size, selected)

	/**
	 * As [snap], but never lands on Lisa - the multiplayer pickers' version.
	 *
	 * Only reachable at a size whose nearest supported band *is* Lisa, which cannot happen at any size the
	 * app offers; the fallback is the hardest numbered band available rather than nothing at all.
	 */
	fun snapForMultiplayer(size: GridSize, selected: Difficulty): Difficulty {
		val snapped = snap(size, selected)
		if (!snapped.isLisa) return snapped
		return multiplayerSupportedAt(size).lastOrNull() ?: Difficulty.ONE
	}
}
