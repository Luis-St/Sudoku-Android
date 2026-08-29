package net.luis.sudoku.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A whole look the player can select, and eventually buy: chrome, board, accents, region tints and the
 * shape language, in a light and a dark variant.
 *
 * This is the seam the currency shop hangs off (feature-spec §566). Everything that decides what the app
 * looks like is reachable from here and from nothing else, which is the property that makes a new theme a
 * catalog entry rather than an edit spread over seventy screens: no composable outside this package holds
 * a colour of its own, and the four things that used to be compile-time constants - the two
 * [ColorScheme]s, the action-button gradients, the chaos region tints and the corner radii - are fields on
 * a theme now.
 *
 * Deliberately **not** on a theme, and each for a reason the codebase has already paid for once:
 * - [InkColors], because the dual-ink beta says *which input mode* wrote a digit, and a statement that
 *   changed colour per theme would say it differently on every one.
 * - [net.luis.sudoku.ui.learn.LearnRoleColors], because those are the vocabulary a lesson is drawn with.
 * - The light/dark choice itself, which is the player's ([net.luis.sudoku.data.local.ThemeMode]); a theme
 *   supplies both variants and never decides which is showing.
 */
@Immutable
data class AppTheme(
	val id: String,
	/** Placeholder until the A11 localization pass; not a string resource yet. */
	val displayName: String,
	val priceInRhubarb: Int,
	val ownedByDefault: Boolean,
	val chromeLight: ChromePalette,
	val chromeDark: ChromePalette,
	val boardLight: BoardPalette,
	val boardDark: BoardPalette,
	/**
	 * One palette, not one per mode: an action accent keeps the same colours in light and dark, because
	 * white-on-saturated reads the same either way and re-tinting would make the same button change
	 * identity when the player flips the mode. See [ActionAccent].
	 */
	val accents: AccentPalette,
	val regionTintsLight: List<Color>,
	val regionTintsDark: List<Color>,
	val shapes: AppShapes = AppShapes()
) {

	fun chrome(dark: Boolean): ChromePalette = if (dark) this.chromeDark else this.chromeLight

	fun board(dark: Boolean): BoardPalette = if (dark) this.boardDark else this.boardLight

	fun regionTints(dark: Boolean): List<Color> = if (dark) this.regionTintsDark else this.regionTintsLight
}

/**
 * The app chrome: Material's own [ColorScheme] plus the background wash and accent sweep that no Material
 * role covers.
 *
 * Built through [chromePalette] rather than assembled by hand, so a new theme cannot repeat account item
 * 1 - the bug where a dialog arrived lavender-grey because Material draws popups on `surfaceContainerHigh`
 * and the theme had only ever assigned `surface`. That builder pins all five container tones from one
 * parameter, so the role a theme forgets does not exist.
 */
@Immutable
data class ChromePalette(
	val colorScheme: ColorScheme,
	val gradients: AppGradients
)

/**
 * Builds a [ChromePalette] from the roles a theme actually has an opinion about.
 *
 * Material's own scheme has forty-odd roles and this takes eighteen; the rest are derived or pinned here.
 * That is the point - a theme author picks pigments, not a Material specification.
 *
 * @param surfaceContainer what every popup is drawn on - dialogs, menus and sheets. All five of Material's
 *   container tones are set to this single value: light themes want them equal to the page so no popup is a
 *   different shade from any other, dark themes want them one step up so a dialog has a visible edge, and
 *   *either* answer is wrong if only some of the five carry it.
 */
fun chromePalette(
	dark: Boolean,
	primary: Color,
	onPrimary: Color,
	primaryContainer: Color,
	onPrimaryContainer: Color,
	secondary: Color,
	onSecondary: Color,
	secondaryContainer: Color,
	onSecondaryContainer: Color,
	tertiary: Color,
	onTertiary: Color,
	tertiaryContainer: Color,
	onTertiaryContainer: Color,
	background: Color,
	onBackground: Color,
	surface: Color,
	onSurface: Color,
	surfaceVariant: Color,
	onSurfaceVariant: Color,
	surfaceContainer: Color,
	outline: Color,
	error: Color,
	onError: Color,
	gradientTop: Color,
	gradientBottom: Color,
	accentStart: Color,
	accentEnd: Color
): ChromePalette {
	val base = if (dark) darkColorScheme() else lightColorScheme()
	return ChromePalette(
		colorScheme = base.copy(
			primary = primary,
			onPrimary = onPrimary,
			primaryContainer = primaryContainer,
			onPrimaryContainer = onPrimaryContainer,
			secondary = secondary,
			onSecondary = onSecondary,
			secondaryContainer = secondaryContainer,
			onSecondaryContainer = onSecondaryContainer,
			tertiary = tertiary,
			onTertiary = onTertiary,
			tertiaryContainer = tertiaryContainer,
			onTertiaryContainer = onTertiaryContainer,
			background = background,
			onBackground = onBackground,
			surface = surface,
			onSurface = onSurface,
			surfaceVariant = surfaceVariant,
			onSurfaceVariant = onSurfaceVariant,
			surfaceContainerLowest = surfaceContainer,
			surfaceContainerLow = surfaceContainer,
			surfaceContainer = surfaceContainer,
			surfaceContainerHigh = surfaceContainer,
			surfaceContainerHighest = surfaceContainer,
			outline = outline,
			error = error,
			onError = onError
		),
		gradients = AppGradients(
			backgroundTop = gradientTop,
			backgroundBottom = gradientBottom,
			accentStart = accentStart,
			accentEnd = accentEnd
		)
	)
}

/**
 * Gradient stops for the app shell. Kept as plain colors rather than ready-made [Brush]es so a caller can
 * build a vertical, horizontal or radial brush from the same pair without the theme guessing.
 */
@Immutable
data class AppGradients(
	val backgroundTop: Color,
	val backgroundBottom: Color,
	val accentStart: Color,
	val accentEnd: Color
)

/** A single gradient fill: the two stops an action button is painted with. */
@Immutable
data class AccentGradient(val start: Color, val end: Color) {

	fun brush(): Brush = Brush.horizontalGradient(listOf(this.start, this.end))
}

/**
 * Where a gradient-filled control gets its fill.
 *
 * Two implementations, and the split is the point. [ActionAccent] is a **slot the theme fills**, for the
 * ordinary case where a gradient means nothing beyond "not the button above". [FixedAccent] is a fill
 * that must **not** follow the theme, for the few controls whose colour is a statement: the dual-ink mode
 * toggles, which have to stay the exact orange and blue the ink writes in, on every theme there will ever
 * be (see [InkColors]).
 *
 * Before this existed both went through one enum of named colours, so recolouring a theme's buttons would
 * silently have moved the pen toggle away from the pen's own ink with nothing to catch it.
 */
sealed interface Accent {

	@Composable
	fun gradient(): AccentGradient
}

/** An [Accent] pinned to one fill, immune to the theme - see the interface. */
@Immutable
data class FixedAccent(private val fill: AccentGradient) : Accent {

	constructor(start: Color, end: Color) : this(AccentGradient(start, end))

	@Composable
	override fun gradient(): AccentGradient = this.fill
}

/**
 * The seven gradients a theme offers action buttons, in slot order - see [ActionAccent].
 *
 * A list rather than seven named fields, because the slots have no individual meaning: what a theme owes
 * is seven fills that can be told apart from one another, not seven specific hues.
 */
@Immutable
data class AccentPalette(val slots: List<AccentGradient>) {

	init {
		require(this.slots.size == ActionAccent.entries.size) {
			"An accent palette needs exactly ${ActionAccent.entries.size} slots, got ${this.slots.size}"
		}
	}

	operator fun get(accent: ActionAccent): AccentGradient = this.slots[accent.ordinal]
}

/**
 * Which of the theme's action gradients a button wears (design item 2, home item 1).
 *
 * **A slot, not a colour.** These used to be named after their pigments (`INDIGO`, `AMBER`, ...) with the
 * colours baked into the constants, which was honest while there was one theme and a lie the moment a
 * second one recoloured them. They are deliberately *not* Material roles either: a scheme role means
 * "primary action", "error", "container" - a role a caller can be wrong about - whereas these mean nothing
 * beyond "tell this button apart from the one above it", so a screen assigns them positionally.
 *
 * In [AppThemeCatalog.CLASSIC] the slots are, in order: indigo, amber, rose, teal, violet, sky and the
 * learn area's lime. A theme may reassign every one of them; what it may not do is give two slots the same
 * fill, since a home screen of seven identical bars is the state this type exists to prevent.
 */
enum class ActionAccent : Accent {

	SLOT_1,
	SLOT_2,
	SLOT_3,
	SLOT_4,
	SLOT_5,
	SLOT_6,
	/** The learn area's own (learn item 1) - every learn screen uses this one slot, so they match. */
	SLOT_7;

	/** The fill this slot carries under the theme in scope. */
	@Composable
	override fun gradient(): AccentGradient = LocalAccents.current[this]
}

/**
 * The shape language: how round a control is, how heavy its outline is, how far it lifts off the page.
 *
 * Its own type rather than more fields on a palette, because these are the half of a look that is not a
 * colour, and they are exactly what "the same app in a different design" changes - a theme can be flat and
 * square with a heavy border, or round and raised with none, without touching a single pigment.
 *
 * The defaults are the house style the app shipped with: **outlined, not filled**, hairline borders in the
 * scheme's own outline colour, and a small real shadow that says "button" where a border alone read as an
 * inert box (visual item 5).
 */
@Immutable
data class AppShapes(
	/** Buttons, dropdown triggers and the menus they open - every raised control shares this radius. */
	val controlCorner: Dp = 14.dp,
	/** Cards, panels and dialogs: the containers a control sits *in*, one step rounder than the control. */
	val containerCorner: Dp = 18.dp,
	/** Progress tracks, swatches and anything else too small to carry [controlCorner]. */
	val smallCorner: Dp = 4.dp,
	/**
	 * A popup. Rounder than a card on purpose, and Material's own value: a dialog floats over the page
	 * rather than sitting in it, and the extra radius is most of what says so.
	 */
	val dialogCorner: Dp = 28.dp,
	/**
	 * A text field. Squarer than a control on purpose, and Material's own value: a field is a place to type
	 * rather than a thing to press, and a rounded box the size of a button reads as the second.
	 */
	val fieldCorner: Dp = 4.dp,
	/** A chip - between a field and a control, which is Material's value and the size a chip actually is. */
	val chipCorner: Dp = 8.dp,
	val borderWidth: Dp = 1.dp,
	/** An enabled control's own outline. */
	val borderAlpha: Float = 0.7f,
	/** The same outline on a disabled control - faded with whatever colour is in use, never greyed out. */
	val disabledBorderAlpha: Float = 0.25f,
	/** A container's outline: a card, a panel, a popup. Quieter than a control's, since it is not pressable. */
	val containerBorderAlpha: Float = 0.25f,
	/** A popup's outline, between the two: a menu has an edge to find, but is not a thing to press. */
	val popupBorderAlpha: Float = 0.4f,
	/** How far off the page a raised control sits - enough to read as lift, not so far it looks detached. */
	val elevation: Dp = 3.dp,
	val pressedElevation: Dp = 1.dp,
	/** How opaque a [net.luis.sudoku.ui.common.SectionCard] is over the background wash. */
	val containerAlpha: Float = 0.6f
)

object AppThemeCatalog {

	/**
	 * The look the app shipped with, and the one every player currently has.
	 *
	 * Free and owned by default *today*. When the shop lands this is the intended "OG" theme, which means
	 * it stops being owned by default and is granted instead to every account that already existed - a
	 * player must never open the app to find the board they have been using is now behind a price.
	 */
	val CLASSIC = AppTheme(
		id = "classic",
		displayName = "Classic",
		priceInRhubarb = 0,
		ownedByDefault = true,
		chromeLight = chromePalette(
			dark = false,
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
			// Plain white, equal to the page: in light mode the point is that no popup is a different shade
			// from any other.
			surfaceContainer = SurfaceLight,
			outline = OutlineLight,
			error = ErrorLight,
			onError = OnErrorLight,
			gradientTop = BackgroundGradientTopLight,
			gradientBottom = BackgroundGradientBottomLight,
			accentStart = IndigoPrimaryLight,
			accentEnd = TealSecondaryLight
		),
		chromeDark = chromePalette(
			dark = true,
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
			// One tone up from the page, unlike light mode: a dialog painted [SurfaceDark] on a [SurfaceDark]
			// page has no visible edge.
			surfaceContainer = SurfaceContainerDark,
			outline = OutlineDark,
			error = ErrorDark,
			onError = OnErrorDark,
			gradientTop = BackgroundGradientTopDark,
			gradientBottom = BackgroundGradientBottomDark,
			accentStart = IndigoPrimaryDark,
			accentEnd = TealSecondaryDark
		),
		boardLight = ClassicBoardLight,
		boardDark = ClassicBoardDark,
		// Each pair is a hue and a neighbouring hue, never a hue and its complement, so a button reads as one
		// colour with depth rather than two colours fighting.
		accents = AccentPalette(
			listOf(
				AccentGradient(GradientIndigoStart, GradientIndigoEnd),
				AccentGradient(GradientAmberStart, GradientAmberEnd),
				AccentGradient(GradientRoseStart, GradientRoseEnd),
				AccentGradient(GradientTealStart, GradientTealEnd),
				AccentGradient(GradientVioletStart, GradientVioletEnd),
				AccentGradient(GradientSkyStart, GradientSkyEnd),
				AccentGradient(GradientLimeStart, GradientLimeEnd)
			)
		),
		regionTintsLight = ClassicRegionTintsLight,
		regionTintsDark = ClassicRegionTintsDark
	)

	/** Catalog order is shop display order. */
	val ALL = listOf(CLASSIC)

	fun byId(id: String): AppTheme = ALL.firstOrNull { it.id == id } ?: CLASSIC
}

val LocalAccents = staticCompositionLocalOf { AppThemeCatalog.CLASSIC.accents }

val LocalAppShapes = staticCompositionLocalOf { AppShapes() }

val LocalAppGradients = staticCompositionLocalOf { AppThemeCatalog.CLASSIC.chromeLight.gradients }

/**
 * The chaos region tints in scope (game item 1) - see [regionTint].
 *
 * A list on the theme rather than a fixed `object`, because a board whose cells are tinted is most of what
 * a chaos puzzle looks like, and a theme that recoloured the grid but not the regions would be two themes
 * on one screen.
 */
val LocalRegionTints = staticCompositionLocalOf { AppThemeCatalog.CLASSIC.regionTintsLight }

/** The tint for a region index, wrapping if a future size ever exceeds the theme's list. */
@Composable
@ReadOnlyComposable
fun regionTint(region: Int): Color {
	val tints = LocalRegionTints.current
	return tints[region.mod(tints.size)]
}
