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
	 * Another co-op participant's selected cell (feature-spec §10.3) - defaulted so existing palettes don't
	 * need updating.
	 *
	 * Multiplayer-game item 4: **green, not yellow.** It was `0xFFFFD54F`, a shade off [hintCandidate]'s
	 * `0xFFFFC400`, so a cell somebody else had selected was for practical purposes the hint colour on a
	 * screen with no hint button - the owner's report was cells "marked yellow as hint but nothing
	 * happened". Two different facts about a cell must not be two shades of one hue; this is the same green
	 * that means "present" everywhere else in the app (`OnlineGreen`).
	 */
	val presence: Color = Color(0xFF9FE0A8),
	/**
	 * The cell a pending hint is offering (game item 4).
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
			// Teal, not the app's indigo: every ink on this board is a near-black violet, and the marked digit
			// has to be a different *hue* to be spotted at a glance rather than a darker shade of the rest.
			sameValuePen = Color(0xFF00767C),
			sameValuePencil = Color(0xFF00767C),
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
			selectedCell = Color(0xFFF3D9D9),
			peerHighlight = Color(0xFFFBEDED),
			// Lisa's every ink is a red, so the marked digit goes to the other side of the wheel outright.
			sameValuePen = Color(0xFF1A6B8C),
			sameValuePencil = Color(0xFF1A6B8C),
			conflict = Color(0xFFFFB4AB),
			hintCandidate = Color(0xFFFFC400),
			tintHighlight = Color(0xFF9B2B2B)
		),
		dark = BoardPalette(
			gridLine = Color(0xFF6B4A4A),
			regionLine = Color(0xFFE0A8A8),
			given = Color(0xFFF3D9D9),
			penEntry = Color(0xFFE0A8A8),
			pencilMark = Color(0xFFA87A7A),
			error = Color(0xFFFFB4AB),
			selectedCell = Color(0xFF5C1A1A),
			peerHighlight = Color(0xFF3A1414),
			sameValuePen = Color(0xFF8FD3EF),
			sameValuePencil = Color(0xFF8FD3EF),
			conflict = Color(0xFF93000A),
			hintCandidate = Color(0xFF9A6E00),
			tintHighlight = Color(0xFFE0A8A8)
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
