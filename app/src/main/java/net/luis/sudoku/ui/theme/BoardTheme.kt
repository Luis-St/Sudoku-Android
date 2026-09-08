package net.luis.sudoku.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Cosmetic colors for the board itself - separate from Material's light/dark [androidx.compose.material3.ColorScheme]
 * (system setting, feature-spec 12/§328 - light and dark only). Nothing else overrides them: the difficulty
 * never changes how the board is drawn.
 *
 * The board half of an [AppTheme]: a theme supplies one of these per mode, and every board renderer -
 * the play screen, all three match screens and the learn area's - reads [LocalBoardPalette] and nothing
 * else, so a purchased look reaches all six without any of them knowing a shop exists.
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
	/**
	 * A note a hint is *proposing*, drawn in the cell rather than written into it (game item 19).
	 *
	 * Green for one that is missing and red for one that cannot be right, and both deliberately softer than
	 * [error] or any ink on the board: at that point in a hint nothing has been decided yet, and a
	 * full-strength colour on a ninth of a cell would read as the board having been changed already. They
	 * still have to be legible at that size, which is why they are muted rather than genuinely pale.
	 *
	 * The colour is the whole mark. A note carries no ring, box or outline of its own: a shape drawn around a
	 * glyph that is already a ninth of a cell is bigger than the glyph, and these two hues appear nowhere else
	 * in the note grid, so nothing else is needed to tell a proposal from something the player wrote.
	 */
	val hintMarkMissing: Color = Color(0xFF2E7D52),
	/** The other half of [hintMarkMissing]: a noted digit a peer already holds, so it cannot be right. */
	val hintMarkWrong: Color = Color(0xFFC4443E),
	/** A cell the player entered a wrong digit into, on the end-of-game summary board (game item 7). */
	val summaryMistake: Color = Color(0xFFFFB3B3),
	/**
	 * The digit drawn *on* [summaryMistake] - the wrong number a multiplayer mistake leaves standing in the
	 * cell (issue 2.2.0/6).
	 *
	 * It has its own entry because [summaryMistake] is the one board colour that is the same pale pink in
	 * light and dark, so the ink on it cannot come from [error], which is a deep red on a light board and a
	 * pale one on a dark board. In dark mode that pale red *was* the mark's own pink, and the digit the peer
	 * got wrong was drawn in the colour of the cell it sat in: there was nothing to see. Light mode was
	 * always right, and this is exactly the red it used.
	 */
	val summaryMistakeInk: Color = Color(0xFFBA1A1A),
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

/** The board half of [AppThemeCatalog.CLASSIC], in light mode. */
val ClassicBoardLight = BoardPalette(
	gridLine = Color(0xFFB0AEB8),
	regionLine = Color(0xFF3A3646),
	given = Color(0xFF1C1B1F),
	// Deliberately identical to [given]: a placed digit is a placed digit, and the owner does not want
	// the board to say who put it there. The only recolouring a value glyph ever gets is the
	// same-value mark ([sameValuePen]) and the mistake red ([error]).
	penEntry = Color(0xFF1C1B1F),
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
	// ([ClassicRegionTintsLight]), and orange is opposite the coolest of them rather than a lighter
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
	hintMarkMissing = Color(0xFF2E7D52),
	hintMarkWrong = Color(0xFFC4443E),
	tintHighlight = Color(0xFF4C4ED9)
)

/** The same board in dark mode. */
val ClassicBoardDark = BoardPalette(
	gridLine = Color(0xFF4A4658),
	regionLine = Color(0xFFCCC2DC),
	given = Color(0xFFE8E2F5),
	// Same as [given], like light mode. This used to be the Material violet #D0BCFF, which made an
	// entered digit visibly a different colour from a clue on a dark board only.
	penEntry = Color(0xFFE8E2F5),
	pencilMark = Color(0xFF9C96AC),
	error = Color(0xFFFFB4AB),
	selectedCell = Color(0xFF3A3646),
	peerHighlight = Color(0xFF2B2836),
	// The light mode's teal, lifted for a dark board. It has to separate by hue from the near-white
	// #E8E2F5 every value glyph is drawn in; a light indigo or violet would read as the same ink.
	sameValuePen = Color(0xFF4DD9E0),
	sameValuePencil = Color(0xFF4DD9E0),
	conflict = Color(0xFF93000A),
	// Deep amber, not the light mode's bright yellow: on a near-black board a full-strength yellow cell
	// is a lamp, and the pencil marks left in the cell would have to be black to survive it.
	hintCandidate = Color(0xFF9A6E00),
	// Lifted for a dark board, like every other ink here: the light mode's greens and reds are dark
	// pigments and vanish into a near-black cell.
	hintMarkMissing = Color(0xFF7FD3A3),
	hintMarkWrong = Color(0xFFEFA19B),
	// Light on a dark board: the tints there are near-black, so the highlight has to lift them, not
	// darken them further.
	tintHighlight = Color(0xFFC1C1FF)
)

// Lisa used to have a board look of its own here, a red variant the game screen swapped in for the
// duration of a Lisa puzzle. It is gone at the owner's instruction: Lisa differs in how it plays (two
// lives, no hints, no auto candidates - see `ModifierSet.LISA`), not in how the board is drawn, so it
// renders with whatever board theme the player has selected, exactly like every other difficulty.

/**
 * Per-region cell tints for chaos puzzles (game item 1). A jigsaw region is only marked by its outline
 * otherwise, and at 12x12 and up tracing an outline across the grid is most of the work of reading the
 * board; a fill says which region a cell is in at a glance.
 *
 * Pastel on purpose: these sit *under* the digits and under every highlight, so they have to stay far
 * enough from both the text color and the selection/peer/conflict colors to never be mistaken for one.
 * Adjacent regions can still land on neighbouring hues, which is fine - the region outline is still drawn.
 *
 * A theme's list rather than a fixed table (see [AppTheme.regionTintsLight]), read through [regionTint].
 */
val ClassicRegionTintsLight = listOf(
	Color(0xFFFDE2E4), Color(0xFFE2F0CB), Color(0xFFDCE8FA), Color(0xFFFFF1CC),
	Color(0xFFEADCF8), Color(0xFFD6F2EF), Color(0xFFFAE1D0), Color(0xFFE4F7DC),
	Color(0xFFF6DDEB), Color(0xFFDDEEF6), Color(0xFFF3EFD3), Color(0xFFE0E2F5),
	Color(0xFFD9F0DE), Color(0xFFFBE4F0), Color(0xFFE8EFD8), Color(0xFFDCE9F2)
)

/** [ClassicRegionTintsLight] at the value a near-black board needs. */
val ClassicRegionTintsDark = listOf(
	Color(0xFF3A2A2E), Color(0xFF2B3527), Color(0xFF26303F), Color(0xFF3A3325),
	Color(0xFF322940), Color(0xFF223533), Color(0xFF3A2F26), Color(0xFF2A3A2C),
	Color(0xFF382A34), Color(0xFF253440), Color(0xFF353325), Color(0xFF2A2B3C),
	Color(0xFF243528), Color(0xFF3A2A36), Color(0xFF31382A), Color(0xFF263440)
)

/**
 * The board half of [AppThemeCatalog.EMBER], in light mode.
 *
 * Every value here is a role from Ember's own scheme rather than a colour picked for the board, which is
 * the rule that keeps the grid part of the app instead of a picture sitting in it. The three that are not
 * are named below, each with what it is answering.
 */
val EmberBoardLight = BoardPalette(
	gridLine = EmberOutlineVariantLight,
	regionLine = EmberOnSurfaceVariantLight,
	given = EmberOnSurfaceLight,
	// Identical to [given], as in every theme: whether a digit was given or entered is deliberately not
	// something the board says (see [ClassicBoardLight]). This is the one place Ember departs from the
	// spec it was drawn from, which paints an entry in `primary` - that is an owner ruling and it outranks
	// a palette.
	penEntry = EmberOnSurfaceLight,
	pencilMark = EmberOnSurfaceVariantLight,
	error = EmberErrorLight,
	// `secondaryContainer`, where the spec says `primaryContainer`. On a red accent the primary and error
	// families are the same hue two tone steps apart, so a selected cell and a conflicting one would differ
	// by less than a phone in sunlight can show. The spec's own answer is a second, non-chromatic cue - a
	// 2dp inset ring on the conflict - which is a change to the shared board renderer and so to every theme
	// at once. Moving the selection 22 degrees off instead is a change to this line. If the ring is ever
	// drawn, this should go back to `primaryContainer`.
	selectedCell = EmberSecondaryContainerLight,
	peerHighlight = EmberSurfacesLight.containerHigh,
	// The same job Classic's orange does and the same two measurements: it has to separate in *value* from
	// the near-black ink it replaces (5.1:1 here) and still hold its own on the pale cell under it (3.2:1,
	// the floor for a glyph this large and this bold). Ember's own tertiary gold is the right hue and the
	// wrong value - at tone 40 it sits 2.6:1 from the ink and reads as "dark" rather than as "marked" - so
	// this is that gold lifted until the first number is met.
	sameValuePen = Color(0xFFC97A16),
	sameValuePencil = Color(0xFFC97A16),
	conflict = EmberErrorContainerLight,
	// Ember's tertiary at full chroma. A hint is the one thing on the board that has to be found before it
	// can be judged, so it is allowed the loudest tone in the palette.
	hintCandidate = Color(0xFFF0B429),
	// Green for missing and red for wrong, shared with Classic and deliberately not themed: these two say
	// what a proposal *is*, and a theme that recoloured them would be renaming the proposal.
	hintMarkMissing = Color(0xFF2E7D52),
	hintMarkWrong = Color(0xFFC4443E),
	summaryMistake = EmberErrorContainerLight,
	summaryMistakeInk = EmberErrorLight,
	summaryHint = EmberTertiaryContainerLight,
	tintHighlight = EmberPrimaryLight
)

/** The same board in dark mode. */
val EmberBoardDark = BoardPalette(
	gridLine = EmberOutlineVariantDark,
	regionLine = EmberOnSurfaceVariantDark,
	given = EmberOnSurfaceDark,
	penEntry = EmberOnSurfaceDark,
	// `outline`, not `onSurfaceVariant`: on a dark board the variant role is a near-white and a note drawn
	// in it competes with the digit above it. A pencil mark is a thing the player wrote down to think with,
	// and it has to sit below the value in the reading order.
	pencilMark = EmberOutlineDark,
	error = EmberErrorDark,
	// See the light board.
	selectedCell = EmberSecondaryContainerDark,
	peerHighlight = EmberSurfacesDark.containerHigh,
	// Here the inks are already light, so the mark separates by hue alone and the gold can stay a gold -
	// the value problem the light board has does not exist on a near-black cell.
	sameValuePen = Color(0xFFEFC44F),
	sameValuePencil = Color(0xFFEFC44F),
	conflict = EmberErrorContainerDark,
	// Deep, not bright: a full-strength yellow cell on a near-black board is a lamp, and the notes left in
	// the cell would have to be black to survive it.
	hintCandidate = Color(0xFF7A5B00),
	hintMarkMissing = Color(0xFF7FD3A3),
	hintMarkWrong = Color(0xFFEFA19B),
	// The same pale red in both modes, like Classic's, which is why the ink on it is the light mode's deep
	// red in both as well (issue 2.2.0/6).
	summaryMistake = EmberErrorContainerLight,
	summaryMistakeInk = EmberErrorLight,
	summaryHint = EmberTertiaryContainerDark,
	tintHighlight = EmberPrimaryDark
)

/**
 * Ember's chaos region tints (game item 1).
 *
 * The one place the theme's own discipline has to give way. Sixteen regions have to be told apart at a
 * glance, and sixteen tones of one warm accent cannot do that - past about four steps the eye stops reading
 * them as different regions and starts reading them as a gradient. So these rotate through the wheel like
 * Classic's do, but at roughly half the chroma and biased warm, which keeps them a family rather than a
 * second palette shouting over the first.
 */
val EmberRegionTintsLight = listOf(
	Color(0xFFF7DED6), Color(0xFFF6E3CB), Color(0xFFF1E8C6), Color(0xFFE7E9CB),
	Color(0xFFD9E8D2), Color(0xFFD5E7E0), Color(0xFFD8E3EC), Color(0xFFE1DDEB),
	Color(0xFFEFDCE4), Color(0xFFF5E0CE), Color(0xFFECE7D0), Color(0xFFDEE9D6),
	Color(0xFFD3E4E6), Color(0xFFE5E0E7), Color(0xFFF2E1DA), Color(0xFFE9E4D8)
)

/** [EmberRegionTintsLight] at the value a near-black board needs. */
val EmberRegionTintsDark = listOf(
	Color(0xFF3A2823), Color(0xFF3A3024), Color(0xFF37351F), Color(0xFF2E3623),
	Color(0xFF26362A), Color(0xFF223631), Color(0xFF24303C), Color(0xFF2C2A3A),
	Color(0xFF372634), Color(0xFF3B2E22), Color(0xFF34321F), Color(0xFF283529),
	Color(0xFF213338), Color(0xFF302D38), Color(0xFF3A2C26), Color(0xFF333024)
)

val LocalBoardPalette = staticCompositionLocalOf { ClassicBoardLight }

/**
 * Whether the app is *drawing* dark right now.
 *
 * Provided by the theme rather than read off the system, because the player's own light/dark choice
 * (settings item 7) overrides the system one: a screen that asks `isSystemInDarkTheme()` paints its light
 * colours onto a dark app for every player who set dark mode on a light phone. The learn area is where that
 * showed, since its role fills are a palette per mode rather than a tint of the scheme.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }
