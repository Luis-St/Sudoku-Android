package net.luis.sudoku.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's own palette, replacing the project-template purples. Two families: **indigo** carries
 * primary actions and the app's identity, **teal** is the accent for progress, success and currency.
 *
 * Every value exists in a light and a dark variant so [SudokuAndroidTheme] can switch without any
 * screen knowing which mode it is in. Board colors are deliberately NOT here - they live in
 * [BoardPalette], because those are swappable per purchased theme (see [AppThemeCatalog]).
 */

// Light
val IndigoPrimaryLight = Color(0xFF4C4ED9)
val IndigoOnPrimaryLight = Color(0xFFFFFFFF)
val IndigoContainerLight = Color(0xFFE3E2FF)
val IndigoOnContainerLight = Color(0xFF0C0865)

val TealSecondaryLight = Color(0xFF00696E)
val TealOnSecondaryLight = Color(0xFFFFFFFF)
val TealContainerLight = Color(0xFFB0ECEF)
val TealOnContainerLight = Color(0xFF002022)

val AmberTertiaryLight = Color(0xFF8A5100)
val AmberOnTertiaryLight = Color(0xFFFFFFFF)
val AmberContainerLight = Color(0xFFFFDDB8)
val AmberOnContainerLight = Color(0xFF2C1600)

// Design item 1: light mode is clean white with a light blue outline. The surfaces are plain white rather
// than the old off-white lavender, and every hairline border is the same light blue, so the chrome reads as
// one deliberate accent instead of neutral grey.
val BackgroundLight = Color(0xFFFFFFFF)
val OnBackgroundLight = Color(0xFF1B1B21)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF1B1B21)
val SurfaceVariantLight = Color(0xFFE6F0FA)
val OnSurfaceVariantLight = Color(0xFF4A5560)
val OutlineLight = Color(0xFF6FAFE0)
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// The quiet half of the outline pair. [OutlineLight] is the light blue every hairline border is drawn in,
// which is a deliberate accent and far too loud for the things `outlineVariant` is actually for - a sheet
// handle, a decorative rule, the edge of something that is not a control. This is that blue at the weight
// those want.
val OutlineVariantLight = Color(0xFFC6DEF2)

// The inverse pair: what a snackbar is drawn on, and the only surface in the app that is dark while the rest
// of it is light. Unassigned, these were Material's lavender - which is exactly the surface a player sees the
// most of the few times anything goes wrong.
val InverseSurfaceLight = Color(0xFF303036)
val InverseOnSurfaceLight = Color(0xFFF3EFF7)

/**
 * The two stops of the app background wash in light mode - see [AppGradients]. Both white: "clean white"
 * means the wash must not tint the page, it only exists so the same brush works in dark mode.
 */
val BackgroundGradientTopLight = Color(0xFFFFFFFF)
val BackgroundGradientBottomLight = Color(0xFFFFFFFF)

// Dark
val IndigoPrimaryDark = Color(0xFFC1C1FF)
val IndigoOnPrimaryDark = Color(0xFF1B1B9E)
val IndigoContainerDark = Color(0xFF3435C0)
val IndigoOnContainerDark = Color(0xFFE3E2FF)

val TealSecondaryDark = Color(0xFF4DD9E0)
val TealOnSecondaryDark = Color(0xFF00363A)
val TealContainerDark = Color(0xFF004F53)
val TealOnContainerDark = Color(0xFFB0ECEF)

val AmberTertiaryDark = Color(0xFFFFB865)
val AmberOnTertiaryDark = Color(0xFF4A2800)
val AmberContainerDark = Color(0xFF693C00)
val AmberOnContainerDark = Color(0xFFFFDDB8)

val BackgroundDark = Color(0xFF131318)
val OnBackgroundDark = Color(0xFFE4E1E9)
val SurfaceDark = Color(0xFF131318)
val OnSurfaceDark = Color(0xFFE4E1E9)
val SurfaceVariantDark = Color(0xFF46464F)
val OnSurfaceVariantDark = Color(0xFFC7C5D0)
val OutlineDark = Color(0xFF918F9A)
val OutlineVariantDark = Color(0xFF46464F)
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

/** The inverse pair, dark mode: a light surface on a dark app, which is the mirror of the light one. */
val InverseSurfaceDark = Color(0xFFE4E1E9)
val InverseOnSurfaceDark = Color(0xFF303036)

/**
 * What a popup is drawn on in dark mode - dialogs, menus, sheets (Material's `surfaceContainer*` roles).
 * <p>
 * Light mode pins all of them to plain white, because there the point is that no popup is a different
 * shade from any other. Dark mode cannot do the same: a dialog painted [SurfaceDark] on a [SurfaceDark]
 * page has no edge at all, so it gets one tone up, the same for every container role.
 */
val SurfaceContainerDark = Color(0xFF1F1F27)

val BackgroundGradientTopDark = Color(0xFF1D1D2E)
val BackgroundGradientBottomDark = Color(0xFF131318)

// Per-action gradient stops (design item 2, home item 1). Every action button carries a gradient now, not
// just the one emphasised action, so they need distinguishable bases rather than one shared accent - the
// gradient is what tells two adjacent buttons apart at a glance. Each pair is a hue and a neighbouring hue,
// never a hue and its complement, so a button reads as one colour with depth rather than two colours fighting.
val GradientIndigoStart = Color(0xFF4C4ED9)
val GradientIndigoEnd = Color(0xFF00696E)

val GradientAmberStart = Color(0xFFE8901B)
val GradientAmberEnd = Color(0xFFD1552B)

val GradientRoseStart = Color(0xFFD93E5C)
val GradientRoseEnd = Color(0xFF9B2B6B)

val GradientTealStart = Color(0xFF0E8C8C)
val GradientTealEnd = Color(0xFF2E7D5B)

val GradientVioletStart = Color(0xFF7E3FD1)
val GradientVioletEnd = Color(0xFF4530B8)

val GradientSkyStart = Color(0xFF2B8FE0)
val GradientSkyEnd = Color(0xFF1F5FC4)

// The learn area (learn item 1). Yellow-green into green is the one band of the wheel none of the six above
// occupy: teal is already blue-green and amber is already orange, so this is the only pair left that cannot
// be mistaken for a neighbour on the home screen.
val GradientLimeStart = Color(0xFF6BA32B)
val GradientLimeEnd = Color(0xFF2F8F52)

/**
 * Classic's fixed accent tones - see [FixedTones]. Nothing here is a new pigment: each is one of the four
 * tones the theme already owns, read in the order Material asks for them.
 */
val ClassicPrimaryFixed = FixedTones(IndigoContainerLight, IndigoPrimaryDark, IndigoOnContainerLight, IndigoContainerDark)
val ClassicSecondaryFixed = FixedTones(TealContainerLight, TealSecondaryDark, TealOnContainerLight, TealContainerDark)
val ClassicTertiaryFixed = FixedTones(AmberContainerLight, AmberTertiaryDark, AmberOnContainerLight, AmberContainerDark)

// Online presence. A fixed green rather than a scheme role: "connected" has to read the same in light and
// dark, and no Material role means availability - the nearest, `secondary`, changes with the board theme.
val OnlineGreen = Color(0xFF2E9E5B)

// ---------------------------------------------------------------------------------------------------
// Ember - the second theme (see [AppThemeCatalog.EMBER]).
//
// One warm accent instead of Classic's three families: primary carries the whole identity, and secondary,
// tertiary and the neutral-variant are the same accent read 22 degrees, 60 degrees and near-grey off it.
// Only primary carries full chroma; everything structural stays warm-neutral, which is what makes the app
// read as calm rather than as a second set of brand colours.
//
// Every value below is a measured tone from that derivation, not a hand-picked hex: the light scheme is the
// palettes read at tone 40 with their containers at 90, the dark one is the same palettes read at 80 and 30.
// The two are a separate pass rather than an inversion, which is why nothing here is the light value with
// its channels flipped.
// ---------------------------------------------------------------------------------------------------

// Light
val EmberPrimaryLight = Color(0xFFAD1F00)
val EmberOnPrimaryLight = Color(0xFFFFFFFF)
val EmberPrimaryContainerLight = Color(0xFFFFD8D0)
val EmberOnPrimaryContainerLight = Color(0xFF3D0000)

val EmberSecondaryLight = Color(0xFF805232)
val EmberOnSecondaryLight = Color(0xFFFFFFFF)
val EmberSecondaryContainerLight = Color(0xFFFFDAC2)
val EmberOnSecondaryContainerLight = Color(0xFF311100)

val EmberTertiaryLight = Color(0xFF725C00)
val EmberOnTertiaryLight = Color(0xFFFFFFFF)
val EmberTertiaryContainerLight = Color(0xFFF8E29A)
val EmberOnTertiaryContainerLight = Color(0xFF241A00)

val EmberBackgroundLight = Color(0xFFFFF7F6)
val EmberOnBackgroundLight = Color(0xFF211A18)
val EmberSurfaceLight = Color(0xFFFFF7F6)
val EmberOnSurfaceLight = Color(0xFF211A18)
val EmberSurfaceVariantLight = Color(0xFFF0DFD4)
val EmberOnSurfaceVariantLight = Color(0xFF544338)
val EmberOutlineLight = Color(0xFF867367)
val EmberOutlineVariantLight = Color(0xFFD5C3B7)
val EmberErrorLight = Color(0xFF920006)
val EmberOnErrorLight = Color(0xFFFFFFFF)
val EmberErrorContainerLight = Color(0xFFFFC0B7)
val EmberOnErrorContainerLight = Color(0xFF370000)

/**
 * The light surface ramp, five real steps rather than Classic's one pinned tone.
 *
 * It runs *down* in lightness as elevation goes up, which is the opposite of what dark mode does and is
 * correct in both: on a near-white page, moving away from the page means moving away from white. The card at
 * `container` and the popup at `containerHigh` are the two anybody will actually see.
 */
val EmberSurfacesLight = SurfaceTones(
	dim = Color(0xFFE9E0DE),
	bright = Color(0xFFFFF7F6),
	containerLowest = Color(0xFFFFFFFF),
	containerLow = Color(0xFFFAF1EF),
	container = Color(0xFFF4ECEA),
	containerHigh = Color(0xFFEFE6E4),
	containerHighest = Color(0xFFE9E0DE)
)

val EmberInverseSurfaceLight = Color(0xFF362E2C)
val EmberInverseOnSurfaceLight = Color(0xFFF7EFED)

/** The background wash: the page's own tone into the popup tone, which is one step and no more. */
val EmberBackgroundGradientTopLight = Color(0xFFFFF7F6)
val EmberBackgroundGradientBottomLight = Color(0xFFEFE6E4)

// Dark
val EmberPrimaryDark = Color(0xFFFFB09E)
val EmberOnPrimaryDark = Color(0xFF5E0B00)
val EmberPrimaryContainerDark = Color(0xFF841500)
val EmberOnPrimaryContainerDark = Color(0xFFFFD8D0)

val EmberSecondaryDark = Color(0xFFE9BC9E)
val EmberOnSecondaryDark = Color(0xFF4C2403)
val EmberSecondaryContainerDark = Color(0xFF613D24)
val EmberOnSecondaryContainerDark = Color(0xFFFFDAC2)

val EmberTertiaryDark = Color(0xFFDDC577)
val EmberOnTertiaryDark = Color(0xFF3C2F00)
val EmberTertiaryContainerDark = Color(0xFF564500)
val EmberOnTertiaryContainerDark = Color(0xFFF8E29A)

val EmberBackgroundDark = Color(0xFF181210)
val EmberOnBackgroundDark = Color(0xFFE9E0DE)
val EmberSurfaceDark = Color(0xFF181210)
/** Held below pure white on purpose - this is a board somebody stares at for an hour. */
val EmberOnSurfaceDark = Color(0xFFE9E0DE)
val EmberSurfaceVariantDark = Color(0xFF544338)
val EmberOnSurfaceVariantDark = Color(0xFFD5C3B7)
val EmberOutlineDark = Color(0xFF9F8C80)
val EmberOutlineVariantDark = Color(0xFF544338)
val EmberErrorDark = Color(0xFFFF8A7D)
val EmberOnErrorDark = Color(0xFF530001)
val EmberErrorContainerDark = Color(0xFF720003)
val EmberOnErrorContainerDark = Color(0xFFFFC0B7)

/** The dark ramp, stepping up in lightness with elevation - see [EmberSurfacesLight] for why. */
val EmberSurfacesDark = SurfaceTones(
	dim = Color(0xFF130C0B),
	bright = Color(0xFF3B3331),
	containerLowest = Color(0xFF130C0B),
	containerLow = Color(0xFF211A18),
	container = Color(0xFF251E1C),
	containerHigh = Color(0xFF302826),
	containerHighest = Color(0xFF3B3331)
)

val EmberInverseSurfaceDark = Color(0xFFE9E0DE)
val EmberInverseOnSurfaceDark = Color(0xFF362E2C)

/** Ember's fixed accent tones - see [FixedTones] and [ClassicPrimaryFixed]. */
val EmberPrimaryFixed = FixedTones(EmberPrimaryContainerLight, EmberPrimaryDark, EmberOnPrimaryContainerLight, EmberPrimaryContainerDark)
val EmberSecondaryFixed = FixedTones(EmberSecondaryContainerLight, EmberSecondaryDark, EmberOnSecondaryContainerLight, EmberSecondaryContainerDark)
val EmberTertiaryFixed = FixedTones(EmberTertiaryContainerLight, EmberTertiaryDark, EmberOnTertiaryContainerLight, EmberTertiaryContainerDark)

val EmberBackgroundGradientTopDark = Color(0xFF251E1C)
val EmberBackgroundGradientBottomDark = Color(0xFF181210)

// Ember's action gradients. Each sweep runs between two tones of **one** palette rather than between two
// hues the way Classic's do, because a theme built on a single accent has no second hue to sweep to: a
// gradient from the red to the gold would read as two colours fighting rather than as one with depth.
//
// That leaves depth as the only axis, so the seven slots are the four palettes at tone 40 -> 30 and the
// three chromatic ones again at 30 -> 20. They are further apart than they look on paper (a tone step is
// roughly a doubling of luminance) but they are unavoidably closer together than Classic's seven hues, and
// that is the cost of a one-accent design rather than a mistake in this list.
val EmberGradientPrimaryStart = Color(0xFFAD1F00)
val EmberGradientPrimaryEnd = Color(0xFF841500)

val EmberGradientTertiaryStart = Color(0xFF725C00)
val EmberGradientTertiaryEnd = Color(0xFF564500)

val EmberGradientSecondaryStart = Color(0xFF805232)
val EmberGradientSecondaryEnd = Color(0xFF613D24)

val EmberGradientNeutralStart = Color(0xFF544338)
val EmberGradientNeutralEnd = Color(0xFF3B2F27)

val EmberGradientPrimaryDeepStart = Color(0xFF841500)
val EmberGradientPrimaryDeepEnd = Color(0xFF5E0B00)

val EmberGradientTertiaryDeepStart = Color(0xFF564500)
val EmberGradientTertiaryDeepEnd = Color(0xFF3C2F00)

val EmberGradientSecondaryDeepStart = Color(0xFF613D24)
val EmberGradientSecondaryDeepEnd = Color(0xFF432917)
