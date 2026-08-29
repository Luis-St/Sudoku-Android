package net.luis.sudoku.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import net.luis.sudoku.data.local.ThemeMode

/** The app's background wash - one call instead of every screen repeating the same [Brush]. */
@Composable
@ReadOnlyComposable
fun appBackgroundBrush(): Brush {
	val gradients = LocalAppGradients.current
	return Brush.verticalGradient(listOf(gradients.backgroundTop, gradients.backgroundBottom))
}

/** The accent sweep used on primary buttons and progress fills. */
@Composable
@ReadOnlyComposable
fun accentBrush(): Brush {
	val gradients = LocalAppGradients.current
	return Brush.horizontalGradient(listOf(gradients.accentStart, gradients.accentEnd))
}

@Composable
fun Modifier.appBackground(): Modifier = this.background(appBackgroundBrush())

/**
 * Whether [themeMode] currently *renders* dark - the stored preference resolved against the system setting.
 *
 * The theme itself needs this, and so does the header's light/dark toggle (game item 3), which has to name
 * and draw the mode it would switch to. Both asking the same function is what stops the toggle showing a sun
 * on a light screen the first time somebody leaves the mode on `SYSTEM`.
 */
@Composable
@ReadOnlyComposable
fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
	ThemeMode.SYSTEM -> isSystemInDarkTheme()
	ThemeMode.LIGHT -> false
	ThemeMode.DARK -> true
}

/**
 * The one place a look is turned into something a screen can read.
 *
 * [themeMode] is the player's own light/dark/system choice (settings item 7). A purchasable [AppTheme]
 * stacks *above* it: it supplies a light and a dark variant of everything, so buying a theme never decides
 * which mode the app is in, and switching mode never decides which theme is on.
 *
 * Everything is provided as a composition local rather than passed down, because the alternative is
 * threading a palette through seventy screens, and four of them draw a board. Screens read named roles -
 * `MaterialTheme.colorScheme`, [LocalBoardPalette], [LocalAppShapes], [ActionAccent.gradient] - and never
 * ask *which* theme is on. That is what makes a new theme a catalog entry: a component that switched on
 * the theme id would have to be edited for every theme ever added.
 *
 * Dynamic color is deliberately gone - the app has a deliberate brand palette now, and Material You would
 * replace it with the wallpaper's colors on every device.
 */
@Composable
fun SudokuAndroidTheme(
	themeMode: ThemeMode = ThemeMode.SYSTEM,
	theme: AppTheme = AppThemeCatalog.CLASSIC,
	/**
	 * Beta item 1: pen and pencil in inks of their own (see [InkColors]). Provided here rather than read
	 * from the settings store by each screen, because it has to reach the board, the number pad and the
	 * mode toggles at once, and two of those are drawn by four screens each.
	 */
	dualInk: Boolean = false,
	/**
	 * Beta item 8 of 2.2.0: the peer highlight follows every occurrence of the selected number
	 * (see [net.luis.sudoku.domain.PeerHighlightRules]). Provided here for the same reason [dualInk] is -
	 * four screens draw a board, and none of them owns this decision.
	 */
	everyOccurrencePeers: Boolean = false,
	content: @Composable () -> Unit
) {
	val darkTheme = isDarkTheme(themeMode)
	val chrome = theme.chrome(darkTheme)

	CompositionLocalProvider(
		LocalBoardPalette provides theme.board(darkTheme),
		LocalInkColors provides if (dualInk) InkColors.of(darkTheme) else InkColors.OFF,
		LocalEveryOccurrencePeers provides everyOccurrencePeers,
		LocalAppGradients provides chrome.gradients,
		LocalAccents provides theme.accents,
		LocalRegionTints provides theme.regionTints(darkTheme),
		LocalAppShapes provides theme.shapes,
		LocalDarkTheme provides darkTheme
	) {
		MaterialTheme(
			colorScheme = chrome.colorScheme,
			typography = Typography,
			content = content
		)
	}
}
