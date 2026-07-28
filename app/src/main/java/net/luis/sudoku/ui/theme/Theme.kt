package net.luis.sudoku.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import net.luis.sudoku.data.local.ThemeMode

private val LightColorScheme = lightColorScheme(
	primary = IndigoPrimaryLight,
	onPrimary = IndigoOnPrimaryLight,
	primaryContainer = IndigoContainerLight,
	onPrimaryContainer = IndigoOnContainerLight,
	secondary = TealSecondaryLight,
	onSecondary = TealOnSecondaryLight,
	secondaryContainer = TealContainerLight,
	onSecondaryContainer = TealOnContainerLight,
	tertiary = AmberTertiaryLight,
	onTertiary = AmberOnTertiaryLight,
	tertiaryContainer = AmberContainerLight,
	onTertiaryContainer = AmberOnContainerLight,
	background = BackgroundLight,
	onBackground = OnBackgroundLight,
	surface = SurfaceLight,
	onSurface = OnSurfaceLight,
	surfaceVariant = SurfaceVariantLight,
	onSurfaceVariant = OnSurfaceVariantLight,
	outline = OutlineLight,
	error = ErrorLight,
	onError = OnErrorLight
)

private val DarkColorScheme = darkColorScheme(
	primary = IndigoPrimaryDark,
	onPrimary = IndigoOnPrimaryDark,
	primaryContainer = IndigoContainerDark,
	onPrimaryContainer = IndigoOnContainerDark,
	secondary = TealSecondaryDark,
	onSecondary = TealOnSecondaryDark,
	secondaryContainer = TealContainerDark,
	onSecondaryContainer = TealOnContainerDark,
	tertiary = AmberTertiaryDark,
	onTertiary = AmberOnTertiaryDark,
	tertiaryContainer = AmberContainerDark,
	onTertiaryContainer = AmberOnContainerDark,
	background = BackgroundDark,
	onBackground = OnBackgroundDark,
	surface = SurfaceDark,
	onSurface = OnSurfaceDark,
	surfaceVariant = SurfaceVariantDark,
	onSurfaceVariant = OnSurfaceVariantDark,
	outline = OutlineDark,
	error = ErrorDark,
	onError = OnErrorDark
)

/**
 * Gradient stops for the app shell. Kept as plain colors rather than ready-made [Brush]es so a caller
 * can build a vertical, horizontal or radial brush from the same pair without the theme guessing.
 */
data class AppGradients(
	val backgroundTop: Color,
	val backgroundBottom: Color,
	val accentStart: Color,
	val accentEnd: Color
)

val LocalAppGradients = staticCompositionLocalOf {
	AppGradients(
		backgroundTop = BackgroundGradientTopLight,
		backgroundBottom = BackgroundGradientBottomLight,
		accentStart = IndigoPrimaryLight,
		accentEnd = TealSecondaryLight
	)
}

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

/**
 * The gradient an action button paints itself with (design item 2, home item 1).
 *
 * These are deliberately *not* derived from the color scheme. A scheme role means "primary action",
 * "error", "container" - a role a caller can be wrong about. These mean nothing beyond "tell this button
 * apart from the one above it", so they are a flat list a screen assigns positionally, and they keep the
 * same colors in light and dark: white-on-saturated reads the same either way, and re-tinting them per mode
 * would make the same button change identity when the player flips the theme.
 */
enum class ActionAccent(val start: Color, val end: Color) {
	INDIGO(GradientIndigoStart, GradientIndigoEnd),
	AMBER(GradientAmberStart, GradientAmberEnd),
	ROSE(GradientRoseStart, GradientRoseEnd),
	TEAL(GradientTealStart, GradientTealEnd),
	VIOLET(GradientVioletStart, GradientVioletEnd),
	SKY(GradientSkyStart, GradientSkyEnd);

	fun brush(): Brush = Brush.horizontalGradient(listOf(this.start, this.end))
}

@Composable
fun Modifier.appBackground(): Modifier = this.background(appBackgroundBrush())

/**
 * [themeMode] is the player's own light/dark/system choice (settings item 7). Purchasable board themes
 * stack *above* it: each supplies its own light and dark [BoardPalette], so buying a theme never decides
 * which mode the app is in.
 *
 * Dynamic color is deliberately gone - the app has a deliberate brand palette now, and Material You
 * would replace it with the wallpaper's colors on every device.
 */
@Composable
fun SudokuAndroidTheme(
	themeMode: ThemeMode = ThemeMode.SYSTEM,
	boardTheme: BoardTheme = BoardThemeCatalog.CLASSIC,
	content: @Composable () -> Unit
) {
	val darkTheme = when (themeMode) {
		ThemeMode.SYSTEM -> isSystemInDarkTheme()
		ThemeMode.LIGHT -> false
		ThemeMode.DARK -> true
	}

	val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
	val boardPalette = if (darkTheme) boardTheme.dark else boardTheme.light
	val gradients = if (darkTheme) {
		AppGradients(BackgroundGradientTopDark, BackgroundGradientBottomDark, IndigoPrimaryDark, TealSecondaryDark)
	} else {
		AppGradients(BackgroundGradientTopLight, BackgroundGradientBottomLight, IndigoPrimaryLight, TealSecondaryLight)
	}

	CompositionLocalProvider(
		LocalBoardPalette provides boardPalette,
		LocalAppGradients provides gradients
	) {
		MaterialTheme(
			colorScheme = colorScheme,
			typography = Typography,
			content = content
		)
	}
}
