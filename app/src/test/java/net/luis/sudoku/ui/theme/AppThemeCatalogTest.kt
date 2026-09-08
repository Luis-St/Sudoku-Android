package net.luis.sudoku.ui.theme

import androidx.compose.material3.ColorScheme
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
	fun everyTheme_surfaceRampIsEitherPinnedOrOrdered() {
		// Account item 1 was a role the theme never assigned, which kept Material's baseline lavender. That
		// cannot happen any more - `chromePalette` builds the scheme through `ColorScheme`'s own constructor
		// with every role named, so a forgotten one is a compile error rather than a wrong colour on a phone.
		//
		// What is left to check is that the ramp a theme *does* supply means something. There are exactly two
		// honest answers: every tone the same, for a theme that separates a popup from the page with its
		// outline, or a ramp that moves in one direction, for a theme that separates them with tone. A ramp
		// that goes up, down and up again is a list of five colours somebody typed.
		for (theme in this.themes) {
			for ((mode, chrome) in listOf("light" to theme.chromeLight, "dark" to theme.chromeDark)) {
				val scheme = chrome.colorScheme
				val ramp = listOf(
					scheme.surfaceContainerLowest,
					scheme.surfaceContainerLow,
					scheme.surfaceContainer,
					scheme.surfaceContainerHigh,
					scheme.surfaceContainerHighest
				).map(::luminance)
				val rising = ramp.zipWithNext().all { (a, b) -> b >= a }
				val falling = ramp.zipWithNext().all { (a, b) -> b <= a }
				assertTrue("$theme.id/$mode: the surface ramp is neither pinned nor ordered", rising || falling)
			}
		}
	}

	@Test
	fun everyTheme_everyRoleIsOpaqueAndSpecified() {
		// A role left `Unspecified` renders as transparent black, and a role that picked up an alpha renders
		// as whatever is behind it - both of which look like a drawing bug on a phone and neither of which is
		// one. Nothing in a scheme is meant to be see-through; the two things that are, a scrim and a state
		// layer, get their alpha where they are drawn rather than here.
		for (theme in this.themes) {
			for ((mode, chrome) in listOf("light" to theme.chromeLight, "dark" to theme.chromeDark)) {
				for ((role, color) in roles(chrome.colorScheme)) {
					val where = theme.id + "/" + mode + "/" + role
					assertNotEquals(where, Color.Unspecified, color)
					assertEquals(where, 1f, color.alpha, 0f)
				}
			}
		}
	}

	@Test
	fun everyTheme_onPairsClearWcagAa() {
		// The measurement the design's own palette tables are written against. A pair that misses it is not a
		// matter of taste - it is text somebody cannot read, and it is invisible in review because the person
		// reviewing is looking at a bright monitor indoors.
		//
		// 4.5:1 is the AA floor for body text, which is the size most of these carry. The pairs are named
		// rather than derived, because "on" is a naming convention and not something the type system knows:
		// `onSurfaceVariant` pairs with `surfaceVariant` and `inversePrimary` pairs with `inverseSurface`,
		// and nothing in the class says so.
		for (theme in this.themes) {
			for ((mode, chrome) in listOf("light" to theme.chromeLight, "dark" to theme.chromeDark)) {
				val s = chrome.colorScheme
				val pairs = listOf(
					"primary" to (s.primary to s.onPrimary),
					"primaryContainer" to (s.primaryContainer to s.onPrimaryContainer),
					"secondary" to (s.secondary to s.onSecondary),
					"secondaryContainer" to (s.secondaryContainer to s.onSecondaryContainer),
					"tertiary" to (s.tertiary to s.onTertiary),
					"tertiaryContainer" to (s.tertiaryContainer to s.onTertiaryContainer),
					"error" to (s.error to s.onError),
					"errorContainer" to (s.errorContainer to s.onErrorContainer),
					"background" to (s.background to s.onBackground),
					"surface" to (s.surface to s.onSurface),
					"surfaceVariant" to (s.surfaceVariant to s.onSurfaceVariant),
					"inverseSurface" to (s.inverseSurface to s.inverseOnSurface),
					"inverseSurface/inversePrimary" to (s.inverseSurface to s.inversePrimary),
					"primaryFixed" to (s.primaryFixed to s.onPrimaryFixed),
					"secondaryFixed" to (s.secondaryFixed to s.onSecondaryFixed),
					"tertiaryFixed" to (s.tertiaryFixed to s.onTertiaryFixed)
				)
				for ((role, pair) in pairs) {
					val ratio = contrast(pair.first, pair.second)
					val where = theme.id + "/" + mode + "/" + role + " measures " + ratio + ":1, below AA"
					assertTrue(where, ratio >= WCAG_AA)
				}
			}
		}
	}

	@Test
	fun everyTheme_everySurfaceToneKeepsItsTextReadable() {
		// The ramp is drawn under body copy at every step of it - a card at `container`, a dialog at
		// `containerLow`, a menu at `containerHigh` - and all of them are written on in `onSurface`. A theme
		// that stretched its ramp one tone too far would leave exactly one of those unreadable.
		for (theme in this.themes) {
			for ((mode, chrome) in listOf("light" to theme.chromeLight, "dark" to theme.chromeDark)) {
				val s = chrome.colorScheme
				val tones = listOf(
					"surfaceDim" to s.surfaceDim,
					"surfaceBright" to s.surfaceBright,
					"surfaceContainerLowest" to s.surfaceContainerLowest,
					"surfaceContainerLow" to s.surfaceContainerLow,
					"surfaceContainer" to s.surfaceContainer,
					"surfaceContainerHigh" to s.surfaceContainerHigh,
					"surfaceContainerHighest" to s.surfaceContainerHighest
				)
				for ((role, tone) in tones) {
					val ratio = contrast(tone, s.onSurface)
					val where = theme.id + "/" + mode + "/" + role + " measures " + ratio + ":1 against onSurface"
					assertTrue(where, ratio >= WCAG_AA)
				}
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
				// A real edge, not merely a different number: a popup one thousandth of a tone off the page
				// is the signature of a role filled in from the wrong constant, and it looks identical on a
				// screen to one that was never filled in at all.
				val hasOwnEdge = Math.abs(luminance(scheme.surfaceContainerHigh) - luminance(scheme.surface)) >= MIN_POPUP_STEP
				assertTrue("${theme.id}/$mode", matchesPage || hasOwnEdge)
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

		/** WCAG AA for body text. Every "on" pair in the design's own tables is quoted at or above this. */
		const val WCAG_AA = 4.5

		/** How far a popup has to sit from the page before the step counts as deliberate rather than as slop. */
		const val MIN_POPUP_STEP = 0.005

		/** Every role in the scheme, named, so a failure says which one rather than which index. */
		fun roles(s: ColorScheme): List<Pair<String, Color>> = listOf(
			"primary" to s.primary, "onPrimary" to s.onPrimary,
			"primaryContainer" to s.primaryContainer, "onPrimaryContainer" to s.onPrimaryContainer,
			"inversePrimary" to s.inversePrimary,
			"secondary" to s.secondary, "onSecondary" to s.onSecondary,
			"secondaryContainer" to s.secondaryContainer, "onSecondaryContainer" to s.onSecondaryContainer,
			"tertiary" to s.tertiary, "onTertiary" to s.onTertiary,
			"tertiaryContainer" to s.tertiaryContainer, "onTertiaryContainer" to s.onTertiaryContainer,
			"background" to s.background, "onBackground" to s.onBackground,
			"surface" to s.surface, "onSurface" to s.onSurface,
			"surfaceVariant" to s.surfaceVariant, "onSurfaceVariant" to s.onSurfaceVariant,
			"surfaceTint" to s.surfaceTint,
			"inverseSurface" to s.inverseSurface, "inverseOnSurface" to s.inverseOnSurface,
			"error" to s.error, "onError" to s.onError,
			"errorContainer" to s.errorContainer, "onErrorContainer" to s.onErrorContainer,
			"outline" to s.outline, "outlineVariant" to s.outlineVariant,
			"scrim" to s.scrim,
			"surfaceBright" to s.surfaceBright, "surfaceDim" to s.surfaceDim,
			"surfaceContainer" to s.surfaceContainer,
			"surfaceContainerHigh" to s.surfaceContainerHigh,
			"surfaceContainerHighest" to s.surfaceContainerHighest,
			"surfaceContainerLow" to s.surfaceContainerLow,
			"surfaceContainerLowest" to s.surfaceContainerLowest,
			"primaryFixed" to s.primaryFixed, "primaryFixedDim" to s.primaryFixedDim,
			"onPrimaryFixed" to s.onPrimaryFixed, "onPrimaryFixedVariant" to s.onPrimaryFixedVariant,
			"secondaryFixed" to s.secondaryFixed, "secondaryFixedDim" to s.secondaryFixedDim,
			"onSecondaryFixed" to s.onSecondaryFixed, "onSecondaryFixedVariant" to s.onSecondaryFixedVariant,
			"tertiaryFixed" to s.tertiaryFixed, "tertiaryFixedDim" to s.tertiaryFixedDim,
			"onTertiaryFixed" to s.onTertiaryFixed, "onTertiaryFixedVariant" to s.onTertiaryFixedVariant
		)

		/**
		 * WCAG relative luminance, written out rather than taken from `Color.luminance()` - that one is an
		 * Android framework call and this is a plain JVM test.
		 */
		fun luminance(color: Color): Double {
			fun channel(v: Float): Double {
				val c = v.toDouble()
				return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
			}
			return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
		}

		fun contrast(a: Color, b: Color): Double {
			val la = luminance(a)
			val lb = luminance(b)
			return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
		}
	}
}
