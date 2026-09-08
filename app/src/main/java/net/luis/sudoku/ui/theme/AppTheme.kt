package net.luis.sudoku.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * The surface family: the seven tones Material draws a page, a card and a popup on.
 *
 * Its own type because these are not seven independent choices - they are one ramp, and the only two
 * sensible answers to it are "every tone the same" and "a real ramp with the steps in the right order". A
 * theme that sets three of them and forgets four has picked neither.
 *
 * @param dim the page at its darkest, [bright] at its lightest. Nothing in this app reads them yet, which is
 *   exactly why they are here: a role nothing reads is a role nobody notices is still Material's lavender.
 */
@Immutable
data class SurfaceTones(
	val dim: Color,
	val bright: Color,
	val containerLowest: Color,
	val containerLow: Color,
	val container: Color,
	val containerHigh: Color,
	val containerHighest: Color
) {

	companion object {

		/**
		 * Every tone the same, which is the answer for a theme that separates a popup from the page with an
		 * outline rather than with tone - see [AppThemeCatalog.CLASSIC].
		 */
		fun pinned(tone: Color) = SurfaceTones(tone, tone, tone, tone, tone, tone, tone)
	}
}

/**
 * One accent's **fixed** roles: the four tones Material keeps identical in light and dark.
 *
 * They exist for surfaces that must not flip when the mode does - a shared card in a light sheet over a dark
 * app, and the expressive components built on that idea. Nothing in this app draws one today. They are still
 * set, for the same reason [SurfaceTones.dim] is: an unassigned role does not stay blank, it stays Material's
 * own baseline, and the day something does reach for one it will arrive in the wrong palette entirely.
 *
 * A theme does not invent these. They are tones it already owns: [fixed] is its light container, [fixedDim]
 * the dark mode's accent, [onFixed] the light on-container, and [onFixedVariant] the dark mode's container.
 */
@Immutable
data class FixedTones(
	val fixed: Color,
	val fixedDim: Color,
	val onFixed: Color,
	val onFixedVariant: Color
)

/**
 * Builds a [ChromePalette] from the roles a theme actually has an opinion about.
 *
 * Material's scheme has forty-seven roles. This takes twenty-nine and derives the rest, which is the point -
 * a theme author picks pigments, not a Material specification. What it will not do is leave one unset: every
 * role reaching [ColorScheme] here comes from the theme or from another of the theme's own values, because an
 * unassigned role keeps Material's baseline lavender and shows up as one component in the wrong palette long
 * after the theme was signed off. That is account item 1, and `AppThemeCatalogTest` is where it is now caught.
 *
 * @param surfaces the page-and-popup ramp - see [SurfaceTones]. Light themes tend to pin it, so no popup is a
 *   different shade from any other; dark themes tend to step it, so a dialog has a visible edge.
 * @param surfaceTint what Material tints an elevated surface with. Defaults to [primary], which is Material's
 *   own rule and the right one: the tint is the accent bleeding through, not a colour of its own.
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
	error: Color,
	onError: Color,
	errorContainer: Color,
	onErrorContainer: Color,
	background: Color,
	onBackground: Color,
	surface: Color,
	onSurface: Color,
	surfaceVariant: Color,
	onSurfaceVariant: Color,
	surfaces: SurfaceTones,
	outline: Color,
	outlineVariant: Color,
	inverseSurface: Color,
	inverseOnSurface: Color,
	/** The *other* mode's accent, which is what makes a snackbar's action readable on an inverted surface. */
	inversePrimary: Color,
	primaryFixed: FixedTones,
	secondaryFixed: FixedTones,
	tertiaryFixed: FixedTones,
	gradientTop: Color,
	gradientBottom: Color,
	accentStart: Color,
	accentEnd: Color,
	surfaceTint: Color = primary,
	scrim: Color = Color.Black
): ChromePalette = ChromePalette(
	colorScheme = ColorScheme(
		primary = primary,
		onPrimary = onPrimary,
		primaryContainer = primaryContainer,
		onPrimaryContainer = onPrimaryContainer,
		inversePrimary = inversePrimary,
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
		surfaceTint = surfaceTint,
		inverseSurface = inverseSurface,
		inverseOnSurface = inverseOnSurface,
		error = error,
		onError = onError,
		errorContainer = errorContainer,
		onErrorContainer = onErrorContainer,
		outline = outline,
		outlineVariant = outlineVariant,
		scrim = scrim,
		surfaceBright = surfaces.bright,
		surfaceDim = surfaces.dim,
		surfaceContainer = surfaces.container,
		surfaceContainerHigh = surfaces.containerHigh,
		surfaceContainerHighest = surfaces.containerHighest,
		surfaceContainerLow = surfaces.containerLow,
		surfaceContainerLowest = surfaces.containerLowest,
		primaryFixed = primaryFixed.fixed,
		primaryFixedDim = primaryFixed.fixedDim,
		onPrimaryFixed = primaryFixed.onFixed,
		onPrimaryFixedVariant = primaryFixed.onFixedVariant,
		secondaryFixed = secondaryFixed.fixed,
		secondaryFixedDim = secondaryFixed.fixedDim,
		onSecondaryFixed = secondaryFixed.onFixed,
		onSecondaryFixedVariant = secondaryFixed.onFixedVariant,
		tertiaryFixed = tertiaryFixed.fixed,
		tertiaryFixedDim = tertiaryFixed.fixedDim,
		onTertiaryFixed = tertiaryFixed.onFixed,
		onTertiaryFixedVariant = tertiaryFixed.onFixedVariant
	),
	gradients = AppGradients(
		backgroundTop = gradientTop,
		backgroundBottom = gradientBottom,
		accentStart = accentStart,
		accentEnd = accentEnd
	)
)

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
	val containerAlpha: Float = 0.6f,
	/**
	 * The ring drawn around whatever holds keyboard or D-pad focus - see [Modifier.appFocusRing].
	 *
	 * Its own tokens rather than the platform default, because the platform default is *nothing* on a phone:
	 * Compose draws no focus indication of its own, so a player on a hardware keyboard, a TV remote or a
	 * switch-access device has no way to tell which control they are on. Three density-independent pixels of
	 * ring at two of offset is the smallest that reads as deliberate at arm's length.
	 */
	val focusRingWidth: Dp = 3.dp,
	val focusRingOffset: Dp = 2.dp
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
			error = ErrorLight,
			onError = OnErrorLight,
			errorContainer = ErrorContainerLight,
			onErrorContainer = OnErrorContainerLight,
			background = BackgroundLight,
			onBackground = OnBackgroundLight,
			surface = SurfaceLight,
			onSurface = OnSurfaceLight,
			surfaceVariant = SurfaceVariantLight,
			onSurfaceVariant = OnSurfaceVariantLight,
			// Plain white, every tone of it: in light mode the point is that no popup is a different shade
			// from any other, and this theme separates one from the page with its outline instead.
			surfaces = SurfaceTones.pinned(SurfaceLight),
			outline = OutlineLight,
			outlineVariant = OutlineVariantLight,
			inverseSurface = InverseSurfaceLight,
			inverseOnSurface = InverseOnSurfaceLight,
			inversePrimary = IndigoPrimaryDark,
			primaryFixed = ClassicPrimaryFixed,
			secondaryFixed = ClassicSecondaryFixed,
			tertiaryFixed = ClassicTertiaryFixed,
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
			error = ErrorDark,
			onError = OnErrorDark,
			errorContainer = ErrorContainerDark,
			onErrorContainer = OnErrorContainerDark,
			background = BackgroundDark,
			onBackground = OnBackgroundDark,
			surface = SurfaceDark,
			onSurface = OnSurfaceDark,
			surfaceVariant = SurfaceVariantDark,
			onSurfaceVariant = OnSurfaceVariantDark,
			// One tone up from the page, unlike light mode: a dialog painted [SurfaceDark] on a [SurfaceDark]
			// page has no visible edge. Still one tone for all of them, which is Classic's rule.
			surfaces = SurfaceTones.pinned(SurfaceContainerDark),
			outline = OutlineDark,
			outlineVariant = OutlineVariantDark,
			inverseSurface = InverseSurfaceDark,
			inverseOnSurface = InverseOnSurfaceDark,
			inversePrimary = IndigoPrimaryLight,
			primaryFixed = ClassicPrimaryFixed,
			secondaryFixed = ClassicSecondaryFixed,
			tertiaryFixed = ClassicTertiaryFixed,
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

	/**
	 * A calm, utilitarian Material You look built on a single warm accent (`#AD1F00`).
	 *
	 * Where Classic is three families - indigo for identity, teal for progress, amber for emphasis - this is
	 * one, read at four points around the wheel. That is the whole difference, and it changes what carries
	 * hierarchy: Classic tells two things apart by giving them different hues, Ember has no second hue to
	 * spend and tells them apart by weight, tone and space instead. Nothing on a screen changes position for
	 * it; a theme is not allowed to move anything.
	 *
	 * Three deliberate departures from the palette it was drawn from, each with its reason at the line that
	 * makes it: the board's entry ink (an owner ruling), the selected cell (see [EmberBoardLight]) and the
	 * action gradients (see [EmberGradientPrimaryStart]).
	 *
	 * Free and owned by default while it is the app's own look. It becomes a priced catalog entry on the day
	 * the shop can record a purchase - which is a server change, not this line (see
	 * [net.luis.sudoku.ui.shop.ShopScreen]).
	 */
	val EMBER = AppTheme(
		id = "ember",
		displayName = "Ember",
		priceInRhubarb = 0,
		ownedByDefault = true,
		chromeLight = chromePalette(
			dark = false,
			primary = EmberPrimaryLight,
			onPrimary = EmberOnPrimaryLight,
			primaryContainer = EmberPrimaryContainerLight,
			onPrimaryContainer = EmberOnPrimaryContainerLight,
			secondary = EmberSecondaryLight,
			onSecondary = EmberOnSecondaryLight,
			secondaryContainer = EmberSecondaryContainerLight,
			onSecondaryContainer = EmberOnSecondaryContainerLight,
			tertiary = EmberTertiaryLight,
			onTertiary = EmberOnTertiaryLight,
			tertiaryContainer = EmberTertiaryContainerLight,
			onTertiaryContainer = EmberOnTertiaryContainerLight,
			error = EmberErrorLight,
			onError = EmberOnErrorLight,
			errorContainer = EmberErrorContainerLight,
			onErrorContainer = EmberOnErrorContainerLight,
			background = EmberBackgroundLight,
			onBackground = EmberOnBackgroundLight,
			surface = EmberSurfaceLight,
			onSurface = EmberOnSurfaceLight,
			surfaceVariant = EmberSurfaceVariantLight,
			onSurfaceVariant = EmberOnSurfaceVariantLight,
			// A real ramp, unlike Classic's one pinned tone. This theme spends no shadow at all (see its
			// [AppShapes]), so tone is the only thing a card or a popup has to stand on, and the ramp is the
			// thing that does the standing.
			surfaces = EmberSurfacesLight,
			outline = EmberOutlineLight,
			outlineVariant = EmberOutlineVariantLight,
			inverseSurface = EmberInverseSurfaceLight,
			inverseOnSurface = EmberInverseOnSurfaceLight,
			inversePrimary = EmberPrimaryDark,
			primaryFixed = EmberPrimaryFixed,
			secondaryFixed = EmberSecondaryFixed,
			tertiaryFixed = EmberTertiaryFixed,
			gradientTop = EmberBackgroundGradientTopLight,
			gradientBottom = EmberBackgroundGradientBottomLight,
			accentStart = EmberGradientPrimaryStart,
			accentEnd = EmberGradientPrimaryEnd
		),
		chromeDark = chromePalette(
			dark = true,
			primary = EmberPrimaryDark,
			onPrimary = EmberOnPrimaryDark,
			primaryContainer = EmberPrimaryContainerDark,
			onPrimaryContainer = EmberOnPrimaryContainerDark,
			secondary = EmberSecondaryDark,
			onSecondary = EmberOnSecondaryDark,
			secondaryContainer = EmberSecondaryContainerDark,
			onSecondaryContainer = EmberOnSecondaryContainerDark,
			tertiary = EmberTertiaryDark,
			onTertiary = EmberOnTertiaryDark,
			tertiaryContainer = EmberTertiaryContainerDark,
			onTertiaryContainer = EmberOnTertiaryContainerDark,
			error = EmberErrorDark,
			onError = EmberOnErrorDark,
			errorContainer = EmberErrorContainerDark,
			onErrorContainer = EmberOnErrorContainerDark,
			background = EmberBackgroundDark,
			onBackground = EmberOnBackgroundDark,
			surface = EmberSurfaceDark,
			onSurface = EmberOnSurfaceDark,
			surfaceVariant = EmberSurfaceVariantDark,
			onSurfaceVariant = EmberOnSurfaceVariantDark,
			// The dark ramp steps *up* in lightness with elevation, where the light one steps down. That is
			// not symmetry for its own sake: elevation means "closer to the light" in both, and on a
			// near-black page closer to the light is lighter.
			surfaces = EmberSurfacesDark,
			outline = EmberOutlineDark,
			outlineVariant = EmberOutlineVariantDark,
			inverseSurface = EmberInverseSurfaceDark,
			inverseOnSurface = EmberInverseOnSurfaceDark,
			inversePrimary = EmberPrimaryLight,
			primaryFixed = EmberPrimaryFixed,
			secondaryFixed = EmberSecondaryFixed,
			tertiaryFixed = EmberTertiaryFixed,
			gradientTop = EmberBackgroundGradientTopDark,
			gradientBottom = EmberBackgroundGradientBottomDark,
			// The dark sweep starts one tone lower than the light one. The dark scheme's primary is already
			// a tone-80 salmon, and a sweep starting there would be a pale bar with dark text on a near-black
			// page, which is the loudest thing on the screen for no reason.
			accentStart = EmberGradientPrimaryDeepStart,
			accentEnd = EmberGradientPrimaryDeepEnd
		),
		boardLight = EmberBoardLight,
		boardDark = EmberBoardDark,
		accents = AccentPalette(
			listOf(
				AccentGradient(EmberGradientPrimaryStart, EmberGradientPrimaryEnd),
				AccentGradient(EmberGradientTertiaryStart, EmberGradientTertiaryEnd),
				AccentGradient(EmberGradientSecondaryStart, EmberGradientSecondaryEnd),
				AccentGradient(EmberGradientNeutralStart, EmberGradientNeutralEnd),
				AccentGradient(EmberGradientPrimaryDeepStart, EmberGradientPrimaryDeepEnd),
				AccentGradient(EmberGradientTertiaryDeepStart, EmberGradientTertiaryDeepEnd),
				AccentGradient(EmberGradientSecondaryDeepStart, EmberGradientSecondaryDeepEnd)
			)
		),
		regionTintsLight = EmberRegionTintsLight,
		regionTintsDark = EmberRegionTintsDark,
		// Rounder and flatter than the house default, which is the half of this look that is not a colour.
		// Elevation is tonal here: a container is told from the page by its tone and its corner, never by a
		// shadow, so the lift that says "button" in Classic is spent on nothing and set to zero. The three
		// Material radii the app has always used - the 28dp dialog, the 4dp field, the 8dp chip - move to
		// this theme's own scale, which is what [AppShapesFidelityTest] pins the *defaults* against so that
		// a theme moving them stays a decision rather than an accident.
		shapes = AppShapes(
			controlCorner = 16.dp,
			containerCorner = 16.dp,
			smallCorner = 4.dp,
			dialogCorner = 28.dp,
			fieldCorner = 12.dp,
			chipCorner = 12.dp,
			borderWidth = 1.dp,
			borderAlpha = 0.55f,
			disabledBorderAlpha = 0.2f,
			containerBorderAlpha = 0.18f,
			popupBorderAlpha = 0.3f,
			elevation = 0.dp,
			pressedElevation = 0.dp,
			containerAlpha = 0.6f
		)
	)

	/** Catalog order is shop display order. */
	val ALL = listOf(EMBER, CLASSIC)

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

/**
 * The focus ring, drawn *outside* the control it belongs to.
 *
 * Outside rather than inset, and this is the whole reason it is a hand-drawn ring instead of a border: an
 * inset ring eats into the control it marks, so a focused button is drawn a few pixels smaller than an
 * unfocused one and the row it sits in shivers as focus moves along it. Drawing past the bounds keeps every
 * control exactly the size it was and costs nothing, because nothing in this app clips a button's parent.
 *
 * It reads [androidx.compose.ui.focus.FocusState.hasFocus] rather than `isFocused`, so a composite control -
 * a chip with its own label, a dropdown trigger wrapping a button - lights up as one thing when the piece
 * inside it takes focus, instead of drawing nothing because the focus landed one level down.
 *
 * @param corner the radius of the control underneath. The ring's own radius is this plus how far out it sits,
 *   which is what keeps the two curves concentric instead of merely close.
 */
@Composable
fun Modifier.appFocusRing(corner: Dp): Modifier {
	val shapes = LocalAppShapes.current
	val color = MaterialTheme.colorScheme.primary
	var focused by remember { mutableStateOf(false) }
	return this
		.onFocusChanged { focused = it.hasFocus }
		.drawWithContent {
			drawContent()
			if (!focused) return@drawWithContent
			val stroke = shapes.focusRingWidth.toPx()
			// The ring is stroked *on* its path, so half of it falls either side: the path has to sit at the
			// offset plus half the width for the inner edge to land exactly `offset` away from the control.
			val out = shapes.focusRingOffset.toPx() + stroke / 2f
			drawRoundRect(
				color = color,
				topLeft = Offset(-out, -out),
				size = Size(this.size.width + out * 2f, this.size.height + out * 2f),
				cornerRadius = CornerRadius(corner.toPx() + out),
				style = Stroke(width = stroke)
			)
		}
}

/**
 * The state layers: how strongly a control tints itself while it is hovered, focused, pressed or dragged.
 *
 * Material's own values are close to these but not equal, and "close" is the problem - a pressed state at
 * 12% next to one at 10% is not two designs, it is one design with a mistake in it. Setting them here sets
 * them for every Material control in the app at once, which is the only way this stays true.
 */
@OptIn(ExperimentalMaterial3Api::class)
val AppRippleConfiguration = RippleConfiguration(
	rippleAlpha = RippleAlpha(
		draggedAlpha = 0.16f,
		focusedAlpha = 0.10f,
		hoveredAlpha = 0.08f,
		pressedAlpha = 0.10f
	)
)
