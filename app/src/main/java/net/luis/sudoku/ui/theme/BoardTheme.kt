package net.luis.sudoku.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Cosmetic colors for the board itself - separate from Material's light/dark [androidx.compose.material3.ColorScheme]
 * (system setting, feature-spec 12/§328 - light and dark only) and from Lisa's distinct board look
 * (difficulty-driven, feature-spec 4.3). This is the seam the owner wants for the currency shop: for now
 * [BoardThemeCatalog] has exactly one entry and it is free and always owned. Adding a purchasable one
 * later is a new catalog entry plus an unlock check against the player's Rhubarb balance - not a
 * restructuring of the board, which only ever reads [LocalBoardPalette].
 */
data class BoardPalette(
	val gridLine: Color,
	val regionLine: Color,
	val given: Color,
	val penEntry: Color,
	val pencilMark: Color,
	val error: Color,
	val selectedCell: Color,
	val peerHighlight: Color,
	/**
	 * Game item 2: the **ink a placed digit is written in** when it is the locked value. Not a cell fill and
	 * not a shape around the glyph - both of those mark the cell, and what the player is scanning for is
	 * where the number is. At 12x12 and up a board of washed cells left nothing else readable at all.
	 *
	 * It therefore has to separate from [given] *and* [penEntry] in the same theme, since it replaces
	 * whichever of the two the digit would otherwise use - a marked digit that lands on the colour an
	 * unmarked one already has is not a mark.
	 */
	val sameValuePen: Color,
	/** Game item 2, the notes half: the same, for the locked digit inside a pencil-mark grid. */
	val sameValuePencil: Color,
	val conflict: Color,
	/**
	 * The cell a pending hint is offering (game item 4), and in co-op the one cell the whole group is being
	 * asked to look at.
	 *
	 * There used to be a second, green highlight next to this one for another participant's *selected* cell.
	 * It is gone at the owner's request: a mark on every tap marked cells nothing had happened to, and it had
	 * already been confused for this one once (it was `0xFFFFD54F`, a shade off this yellow, and read as "a
	 * hint that does nothing"). The lesson survives the feature - one hue, one meaning.
	 *
	 * Its own colour rather than the selection's: a hint is a one-shot "look here" that the player has to
	 * find *before* deciding whether to spend it, and sharing the selection colour meant it read as "you
	 * tapped this". Applied opaque, never composited onto a chaos region tint the way the selection is -
	 * a highlight that lets the tint through is exactly what cannot be picked out of sixteen tinted
	 * regions, and there is nothing to preserve underneath a cell that is about to be filled in.
	 */
	val hintCandidate: Color = Color(0xFFFFC400),
	/** A cell the player entered a wrong digit into, on the end-of-game summary board (game item 7). */
	val summaryMistake: Color = Color(0xFFFFB3B3),
	/** A cell the player spent a hint on, on the end-of-game summary board (game item 7). */
	val summaryHint: Color = Color(0xFFFFE08A),
	/**
	 * The accent a selection or a row/column highlight is composited from **on a chaos board** (chaos item 8).
	 *
	 * On a plain board the pastel [selectedCell] and [peerHighlight] read fine against white. On a chaos board
	 * they land on a region tint of the same weight and disappear, so there the highlight is this colour laid
	 * over the tint at a fixed alpha instead - one saturated hue that steps away from every pastel tint, while
	 * still letting the region show through underneath.
	 */
	val tintHighlight: Color = Color(0xFF4C4ED9)
)

/** A single purchasable (or, for now, only free) board look, in light and dark variants. */
data class BoardTheme(
	val id: String,
	val displayName: String, // placeholder until the A11 localization pass; not a string resource yet
	val priceInRhubarb: Int,
	val ownedByDefault: Boolean,
	val light: BoardPalette,
	val dark: BoardPalette
)

object BoardThemeCatalog {

	val CLASSIC = BoardTheme(
		id = "classic",
		displayName = "Classic",
		priceInRhubarb = 0,
		ownedByDefault = true,
		light = BoardPalette(
			gridLine = Color(0xFFB0AEB8),
			regionLine = Color(0xFF3A3646),
			given = Color(0xFF1C1B1F),
			penEntry = Color(0xFF3A3646),
			pencilMark = Color(0xFF6F6A7C),
			error = Color(0xFFBA1A1A),
			selectedCell = Color(0xFFE4DFF7),
			peerHighlight = Color(0xFFF1EEFB),
			// Orange, after two teals failed at the same job. Every ink on this board is a near-black violet, so
			// the marked digit has to differ in *value* and in hue at once, and the teal family cannot do the
			// first: #00767C sat 3.3:1 from [given] and #0097A7 4.7:1, both still reported as "black". Blue and
			// violet are dark by construction, so moving that way costs the very contrast being asked for.
			//
			// This is 5.4:1 from the black inks - the step that was missing - and 3.1:1 on the white cell, which
			// is the floor for the bold, large glyph a marked digit always is. It is also the one hue that stays
			// legible on the chaos region tints: those are pale pinks, blues, greens and yellows
			// ([ChaosRegionColors.LIGHT]), and orange is opposite the coolest of them rather than a lighter
			// version of any, where the teals landed close to the blue and green tints they had to sit on.
			//
			// It does land near [hintCandidate]'s yellow when a hint is offered on the marked digit's cell, which
			// is accepted: that cell is one cell, and it is about to be filled in.
			//
			// Dark mode was never affected and keeps its own lifted teal - there the inks are already light, so
			// the mark separates by hue alone.
			sameValuePen = Color(0xFFEF6C00),
			sameValuePencil = Color(0xFFEF6C00),
			conflict = Color(0xFFFFDAD6),
			hintCandidate = Color(0xFFFFC400),
			tintHighlight = Color(0xFF4C4ED9)
		),
		dark = BoardPalette(
			gridLine = Color(0xFF4A4658),
			regionLine = Color(0xFFCCC2DC),
			given = Color(0xFFE8E2F5),
			penEntry = Color(0xFFD0BCFF),
			pencilMark = Color(0xFF9C96AC),
			error = Color(0xFFFFB4AB),
			selectedCell = Color(0xFF3A3646),
			peerHighlight = Color(0xFF2B2836),
			// The light mode's teal, lifted for a dark board. A light indigo would sit right on top of
			// penEntry's #D0BCFF and the mark would be invisible.
			sameValuePen = Color(0xFF4DD9E0),
			sameValuePencil = Color(0xFF4DD9E0),
			conflict = Color(0xFF93000A),
			// Deep amber, not the light mode's bright yellow: on a near-black board a full-strength yellow cell
			// is a lamp, and the pencil marks left in the cell would have to be black to survive it.
			hintCandidate = Color(0xFF9A6E00),
			// Light on a dark board: the tints there are near-black, so the highlight has to lift them, not
			// darken them further.
			tintHighlight = Color(0xFFC1C1FF)
		)
	)

	/**
	 * Lisa's distinct board look (feature-spec §4.3) - not purchasable, not in [ALL]: it's driven by the
	 * selected difficulty, the same axis [BoardPalette.conflict] etc. serve for the shop, but a different
	 * concern entirely (recognizability, not cosmetic choice).
	 */
	val LISA = BoardTheme(
		id = "lisa",
		displayName = "Lisa",
		priceInRhubarb = 0,
		ownedByDefault = true,
		light = BoardPalette(
			gridLine = Color(0xFF8A7A7A),
			regionLine = Color(0xFF5C1A1A),
			given = Color(0xFF1C0E0E),
			penEntry = Color(0xFF5C1A1A),
			pencilMark = Color(0xFF7A5A5A),
			error = Color(0xFFBA1A1A),
			// Classic's selection, at the owner's instruction, and for the same reason the marked digit below
			// is Classic's orange: a tap means the same thing on every board, so it looks the same on every
			// board. Lisa stays recognizable through its inks, lines and region tint, which is what the red
			// board was for - the selection was never the part carrying that.
			selectedCell = Color(0xFFE4DFF7),
			peerHighlight = Color(0xFFF1EEFB),
			// Classic's orange, at the owner's instruction: the marked digit means the same thing on every
			// board, so it is the same colour on every board and a player moving between difficulties is not
			// relearning what the mark looks like.
			//
			// This board's inks are reds rather than Classic's violet-blacks, so orange is a nearer hue here
			// than it is there - the separation rests on value (5.4:1 from [given]'s near-black red, and the
			// glyph is bold) rather than on the hue distance Classic gets for free. It was a cyan for exactly
			// that reason; one colour for one meaning won.
			sameValuePen = Color(0xFFEF6C00),
			sameValuePencil = Color(0xFFEF6C00),
			conflict = Color(0xFFFFB4AB),
			hintCandidate = Color(0xFFFFC400),
			// Classic's accent too: this is the same selection, composited over a chaos region tint instead of
			// drawn on white, so a Lisa-specific red here would reintroduce the difference the fills just lost.
			tintHighlight = Color(0xFF4C4ED9)
		),
		dark = BoardPalette(
			gridLine = Color(0xFF6B4A4A),
			regionLine = Color(0xFFE0A8A8),
			given = Color(0xFFF3D9D9),
			penEntry = Color(0xFFE0A8A8),
			pencilMark = Color(0xFFA87A7A),
			error = Color(0xFFFFB4AB),
			// Classic's, as in light mode. The lifted violet reads as a selection against this board's dark
			// reds as readily as it does against Classic's, and it is the same colour the player already knows.
			selectedCell = Color(0xFF3A3646),
			peerHighlight = Color(0xFF2B2836),
			sameValuePen = Color(0xFF4DD9E0),
			sameValuePencil = Color(0xFF4DD9E0),
			conflict = Color(0xFF93000A),
			hintCandidate = Color(0xFF9A6E00),
			tintHighlight = Color(0xFFC1C1FF)
		)
	)

	/** Catalog order is shop display order. Only [CLASSIC] exists until A5/A6 wire up the shop. */
	val ALL = listOf(CLASSIC)

	fun byId(id: String): BoardTheme = ALL.firstOrNull { it.id == id } ?: CLASSIC
}

/**
 * Per-region cell tints for chaos puzzles (game item 1). A jigsaw region is only marked by its outline
 * today, and at 12x12 and up tracing an outline across the grid is most of the work of reading the board;
 * a fill says which region a cell is in at a glance.
 *
 * Pastel on purpose: these sit *under* the digits and under every highlight, so they have to stay far
 * enough from both the text color and the selection/peer/conflict colors to never be mistaken for one.
 * Adjacent regions can still land on neighbouring hues, which is fine - the region outline is still drawn.
 */
object ChaosRegionColors {

	private val LIGHT = listOf(
		Color(0xFFFDE2E4), Color(0xFFE2F0CB), Color(0xFFDCE8FA), Color(0xFFFFF1CC),
		Color(0xFFEADCF8), Color(0xFFD6F2EF), Color(0xFFFAE1D0), Color(0xFFE4F7DC),
		Color(0xFFF6DDEB), Color(0xFFDDEEF6), Color(0xFFF3EFD3), Color(0xFFE0E2F5),
		Color(0xFFD9F0DE), Color(0xFFFBE4F0), Color(0xFFE8EFD8), Color(0xFFDCE9F2)
	)

	private val DARK = listOf(
		Color(0xFF3A2A2E), Color(0xFF2B3527), Color(0xFF26303F), Color(0xFF3A3325),
		Color(0xFF322940), Color(0xFF223533), Color(0xFF3A2F26), Color(0xFF2A3A2C),
		Color(0xFF382A34), Color(0xFF253440), Color(0xFF353325), Color(0xFF2A2B3C),
		Color(0xFF243528), Color(0xFF3A2A36), Color(0xFF31382A), Color(0xFF263440)
	)

	/** The tint for a region index, wrapping if a future size ever exceeds the list. */
	fun of(region: Int, dark: Boolean): Color {
		val palette = if (dark) DARK else LIGHT
		return palette[region.mod(palette.size)]
	}
}

val LocalBoardPalette = staticCompositionLocalOf { BoardThemeCatalog.CLASSIC.light }
