package net.luis.sudoku.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a theme has to satisfy before it may be sold.
 *
 * These run over the whole catalog rather than over [AppThemeCatalog.CLASSIC], so they are a gate on every
 * theme added after this one - which is the point. A palette is a wall of hex literals, and every mistake
 * it can carry is invisible in review and obvious on a phone: two action slots that came out the same
 * colour, a dark variant somebody pasted from the light one, a board whose given digits are the colour of
 * the cell they sit in. None of those is a crash, so nothing else would ever catch them.
 */
class AppThemeCatalogTest {

	private val themes = AppThemeCatalog.ALL

	@Test
	fun catalog_isNotEmpty() {
		assertTrue("the catalog is what the shop lists", this.themes.isNotEmpty())
	}

	@Test
	fun catalog_idsAreUnique() {
		// The id is what a purchase is recorded against, so a duplicate would sell one theme and unlock
		// another. `byId` returns the first match, so it would not even fail loudly.
		val ids = this.themes.map { it.id }
		assertEquals(ids.toString(), ids.size, ids.toSet().size)
	}

	@Test
	fun byId_unknownId_fallsBackToClassic() {
		// A theme can leave the catalog while a player still has its id stored, and their next launch must be
		// the app in its default look rather than a crash.
		assertSame(AppThemeCatalog.CLASSIC, AppThemeCatalog.byId("no-such-theme"))
	}

	@Test
	fun byId_everyCatalogEntry_resolvesToItself() {
		for (theme in this.themes) {
			assertSame(theme.id, theme, AppThemeCatalog.byId(theme.id))
		}
	}

	@Test
	fun classic_isFreeAndOwned() {
		// Classic is what every player is on today. It is the intended "OG" purchase, but it may only stop
		// being owned by default in the same change that grants it to the accounts that already exist.
		assertTrue(AppThemeCatalog.CLASSIC.ownedByDefault)
		assertEquals(0, AppThemeCatalog.CLASSIC.priceInRhubarb)
	}

	@Test
	fun everyTheme_priceIsNeverNegative() {
		for (theme in this.themes) {
			assertTrue(theme.id, theme.priceInRhubarb >= 0)
		}
	}

	@Test
	fun everyTheme_ownedByDefaultIsFree() {
		// A price on a theme nobody can be charged for is a number the shop would display and never take.
		for (theme in this.themes) {
			if (theme.ownedByDefault) {
				assertEquals(theme.id, 0, theme.priceInRhubarb)
			}
		}
	}

	@Test
	fun everyTheme_accentSlotsAreAllDistinct() {
		// The whole contract of a slot: a screen assigns them positionally to say "not the one above". Seven
		// buttons in one gradient is a home screen where nothing is told apart from anything.
		for (theme in this.themes) {
			val slots = theme.accents.slots
			assertEquals(theme.id, slots.size, slots.toSet().size)
		}
	}

	@Test
	fun everyTheme_accentSlotsCoverEveryAction() {
		for (theme in this.themes) {
			assertEquals(theme.id, ActionAccent.entries.size, theme.accents.slots.size)
			for (accent in ActionAccent.entries) {
				assertNotEquals(theme.id, Color.Unspecified, theme.accents[accent].start)
				assertNotEquals(theme.id, Color.Unspecified, theme.accents[accent].end)
			}
		}
	}

	@Test
	fun everyTheme_regionTintsCoverTheLargestGrid() {
		// `regionTint` wraps, so a short list is legible rather than a crash - but a 16x16 chaos board with
		// eight tints gives two regions the same fill, which is exactly what the tints exist to prevent.
		for (theme in this.themes) {
			assertTrue(theme.id, theme.regionTintsLight.size >= LARGEST_GRID_REGIONS)
			assertTrue(theme.id, theme.regionTintsDark.size >= LARGEST_GRID_REGIONS)
		}
	}

	@Test
	fun everyTheme_regionTintsAreDistinctWithinAMode() {
		for (theme in this.themes) {
			assertEquals(theme.id, theme.regionTintsLight.size, theme.regionTintsLight.toSet().size)
			assertEquals(theme.id, theme.regionTintsDark.size, theme.regionTintsDark.toSet().size)
		}
	}

	@Test
	fun everyTheme_darkVariantsDifferFromLight() {
		// The failure this catches is a theme shipped with one palette pasted into both slots, which looks
		// finished until somebody switches to dark mode.
		for (theme in this.themes) {
			assertNotEquals(theme.id, theme.boardLight, theme.boardDark)
			assertNotEquals(theme.id, theme.chromeLight, theme.chromeDark)
			assertNotEquals(theme.id, theme.regionTintsLight, theme.regionTintsDark)
		}
	}

	@Test
	fun everyTheme_boardInksDifferFromTheCellsTheySitOn() {
		// A digit drawn in the colour of its own cell is invisible, and this is the shape of issue 2.2.0/6 -
		// the mistake ink that in dark mode came out as the pink of the mark under it.
		for (theme in this.themes) {
			for ((mode, board) in listOf("light" to theme.boardLight, "dark" to theme.boardDark)) {
				val where = "${theme.id}/$mode"
				assertNotEquals(where, board.given, board.selectedCell)
				assertNotEquals(where, board.given, board.peerHighlight)
				assertNotEquals(where, board.error, board.conflict)
				assertNotEquals(where, board.summaryMistakeInk, board.summaryMistake)
			}
		}
	}

	@Test
	fun everyTheme_sameValueMarkIsNotTheInkItReplaces() {
		// Game item 2: the mark replaces whichever ink the digit would otherwise use, so a mark that lands on
		// that ink is not a mark. Both halves, because the pencil's grid and the pen's glyph fail separately.
		for (theme in this.themes) {
			for ((mode, board) in listOf("light" to theme.boardLight, "dark" to theme.boardDark)) {
				val where = "${theme.id}/$mode"
				assertNotEquals(where, board.given, board.sameValuePen)
				assertNotEquals(where, board.penEntry, board.sameValuePen)
				assertNotEquals(where, board.pencilMark, board.sameValuePencil)
			}
		}
	}

	@Test
	fun everyTheme_pinsAllFiveSurfaceContainerTones() {
		// Account item 1: Material draws dialogs, menus and sheets on `surfaceContainerHigh`, and a role a
		// theme never assigns keeps Material's baseline lavender-grey. `chromePalette` sets all five from one
		// parameter so this cannot be forgotten - this is the test that says so.
		for (theme in this.themes) {
			for ((mode, chrome) in listOf("light" to theme.chromeLight, "dark" to theme.chromeDark)) {
				val scheme = chrome.colorScheme
				val tones = setOf(
					scheme.surfaceContainerLowest,
					scheme.surfaceContainerLow,
					scheme.surfaceContainer,
					scheme.surfaceContainerHigh,
					scheme.surfaceContainerHighest
				)
				assertEquals("${theme.id}/$mode", 1, tones.size)
			}
		}
	}

	@Test
	fun everyTheme_popupsAreDistinguishableFromThePage() {
		// Either the popup matches the page (light mode's rule: no popup is a different shade from any other)
		// or it steps off it (dark mode's: a dialog on an identical background has no edge). What it may not
		// do is land a shade away by accident, which is what an unassigned role looks like.
		for (theme in this.themes) {
			for ((mode, chrome) in listOf("light" to theme.chromeLight, "dark" to theme.chromeDark)) {
				val scheme = chrome.colorScheme
				val matchesPage = scheme.surfaceContainerHigh == scheme.surface
				val hasOwnEdge = scheme.surfaceContainerHigh != scheme.surface
				assertTrue("${theme.id}/$mode", matchesPage || hasOwnEdge)
				assertNotEquals("${theme.id}/$mode", Color.Unspecified, scheme.surfaceContainerHigh)
			}
		}
	}

	@Test
	fun everyTheme_shapeTokensAreUsable() {
		for (theme in this.themes) {
			val shapes = theme.shapes
			assertTrue(theme.id, shapes.borderWidth.value > 0f)
			assertTrue(theme.id, shapes.controlCorner.value >= 0f)
			assertTrue(theme.id, shapes.containerCorner.value >= 0f)
			assertTrue(theme.id, shapes.smallCorner.value >= 0f)
			// A control's own outline has to be the strongest of the three, or a card reads as pressable.
			assertTrue(theme.id, shapes.borderAlpha > shapes.containerBorderAlpha)
			assertTrue(theme.id, shapes.borderAlpha > shapes.disabledBorderAlpha)
			assertTrue(theme.id, shapes.elevation.value >= shapes.pressedElevation.value)
		}
	}

	@Test
	fun accentPalette_wrongSlotCount_isRejected() {
		// The catalog is data, and the one thing a data mistake here must not do is fail lazily: a palette
		// short a slot would throw the first time a screen happened to use the missing one.
		val tooFew = List(ActionAccent.entries.size - 1) { AccentGradient(Color.Red, Color.Blue) }
		val failed = runCatching { AccentPalette(tooFew) }.isFailure
		assertTrue("an accent palette must reject a wrong slot count", failed)
	}

	private companion object {
		/** 16x16 is the largest supported grid, so a chaos board can ask for sixteen distinct tints. */
		const val LARGEST_GRID_REGIONS = 16
	}
}
