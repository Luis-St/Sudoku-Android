package net.luis.sudoku.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's own palette, replacing the project-template purples. Two families: **indigo** carries
 * primary actions and the app's identity, **teal** is the accent for progress, success and currency.
 *
 * Every value exists in a light and a dark variant so [SudokuAndroidTheme] can switch without any
 * screen knowing which mode it is in. Board colors are deliberately NOT here - they live in
 * [BoardPalette], because those are swappable per purchased board theme (see [BoardThemeCatalog]).
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
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)

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

// Online presence. A fixed green rather than a scheme role: "connected" has to read the same in light and
// dark, and no Material role means availability - the nearest, `secondary`, changes with the board theme.
val OnlineGreen = Color(0xFF2E9E5B)
