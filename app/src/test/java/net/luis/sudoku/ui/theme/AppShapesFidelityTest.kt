package net.luis.sudoku.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shape tokens against the Material values the app actually shipped with.
 *
 * The house components wrap Material's own controls, and a wrapper that restates a value silently replaces
 * a default. Three of them were caught doing exactly that: a dialog is `CornerExtraLarge` (28dp), a text
 * field `CornerExtraSmall` (4dp) and a chip `CornerSmall` (8dp), and folding all three into the app's
 * control radius reshaped forty-odd controls that nobody had asked to change.
 *
 * So the values are pinned here. This is not a claim that a theme may never move them - a theme may set
 * every one - it is a claim about [AppShapes]'s *defaults*, which are what the app looks like today and
 * what "no visual change" has to mean when a component is adopted.
 */
class AppShapesFidelityTest {

	private val shapes = AppShapes()

	@Test
	fun dialogCorner_isMaterialsExtraLarge() {
		assertEquals(28.dp, this.shapes.dialogCorner)
	}

	@Test
	fun fieldCorner_isMaterialsExtraSmall() {
		assertEquals(4.dp, this.shapes.fieldCorner)
	}

	@Test
	fun chipCorner_isMaterialsSmall() {
		assertEquals(8.dp, this.shapes.chipCorner)
	}

	@Test
	fun controlAndContainerCorners_areTheAppsOwn() {
		// These two are the app's, not Material's - the bespoke buttons and cards were always drawn by hand.
		assertEquals(14.dp, this.shapes.controlCorner)
		assertEquals(18.dp, this.shapes.containerCorner)
	}

	@Test
	fun aDialogIsRounderThanACard_whichIsRounderThanAField() {
		// The ordering is the design statement, and it is what collapsing the families destroyed: a popup
		// floats over the page, a card sits in it, and a field is a place to type rather than a thing to press.
		val order = listOf(this.shapes.fieldCorner, this.shapes.chipCorner, this.shapes.controlCorner, this.shapes.containerCorner, this.shapes.dialogCorner)
		assertEquals(order.sortedBy { it.value }, order)
	}

	@Test
	fun everyCatalogThemeKeepsThatOrdering() {
		for (theme in AppThemeCatalog.ALL) {
			val s = theme.shapes
			assertEquals(theme.id, s.fieldCorner.value <= s.containerCorner.value, true)
			assertEquals(theme.id, s.containerCorner.value <= s.dialogCorner.value, true)
		}
	}
}
