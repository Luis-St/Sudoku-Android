package net.luis.sudoku.domain

import net.luis.sudoku.difficulty.Difficulty
import net.luis.sudoku.difficulty.DifficultyBands
import net.luis.sudoku.grid.GridSize
import net.luis.sudoku.grid.Variant

/**
 * Which difficulty bands a picker may offer for a given size and variant, and what a selection becomes when
 * either changes underneath it.
 *
 * A grid does not reach every band, and the reachable set is not a run from one upwards: a 4x4 grid is band 1
 * and nothing else, and a 6x6 makes 1, 2, 3, 7 and 8 but never 4, 5 or 6 - its boxes make locked candidates so
 * common that it steps straight from band 3 to band 7. These are measured facts held by shared-core
 * ([DifficultyBands.supported]), not a ceiling this app can guess at, so the pickers ask rather than filter by
 * index.
 *
 * **The variant is part of the question, not a detail.** A 16x16 jigsaw reaches band 8 and no further, while a
 * 16x16 classic reaches all fifteen: a jigsaw grid has the Law of Leftovers available to it, which is enough to
 * solve the hard boards too easily to rate where they were asked to. A picker that asked by size alone would
 * offer seven bands the board cannot produce.
 *
 * Offering an unreachable band is not a cosmetic fault. The generator snaps the request onto the nearest band it
 * can actually produce, so a player who picks tier 5 on a 6x6 board is handed tier 3 and told nothing about it -
 * the setting simply appears to be ignored.
 */
object DifficultyOptions {

	private val BANDS = DifficultyBands.defaults()

	/** The bands [size] can produce in [variant], in ascending order, ready to hand to a dropdown. */
	fun supportedAt(size: GridSize, variant: Variant): List<Difficulty> =
		BANDS.supported(size, variant).sortedBy(Difficulty::index)

	/** As [supportedAt], without Lisa - every multiplayer mode rejects it (feature-spec §4.3). */
	fun multiplayerSupportedAt(size: GridSize, variant: Variant): List<Difficulty> =
		supportedAt(size, variant).filterNot(Difficulty::isLisa)

	/**
	 * [selected] if this grid can produce it, otherwise the nearest band it can, preferring the easier of two
	 * equally close ones - shared-core's own rule, so a picker and the generator can never disagree.
	 */
	fun snap(size: GridSize, variant: Variant, selected: Difficulty): Difficulty =
		BANDS.nearestSupported(size, variant, selected)

	/**
	 * As [snap], but never lands on Lisa - the multiplayer pickers' version.
	 *
	 * Only reachable at a grid whose nearest supported band *is* Lisa, which cannot happen at any size the app
	 * offers; the fallback is the hardest numbered band available rather than nothing at all.
	 */
	fun snapForMultiplayer(size: GridSize, variant: Variant, selected: Difficulty): Difficulty {
		val snapped = snap(size, variant, selected)
		if (!snapped.isLisa) return snapped
		return multiplayerSupportedAt(size, variant).lastOrNull() ?: Difficulty.ONE
	}
}
