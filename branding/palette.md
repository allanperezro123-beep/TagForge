# Tag Forge - brand palette (Material 3)

Design intent: **professional, minimal, calm.** The neon of the original artwork (glow, #1565FF to #00E5FF at full saturation on near-black) is replaced by flat, slightly desaturated colour. Colour is used sparingly: the blue-to-cyan gradient lives ONLY in the logo (launcher icon, in-app mark). Inside the app, buttons, chips and surfaces are flat Material 3 roles.

Method: tonal palettes generated in OKLCH (constant hue per palette, so blues do not drift purple as they darken), with tone = CIELAB L* like Material's HCT. Contrast = WCAG 2.x relative luminance ratio, computed (not eyeballed) for every pair below.

| Palette | OKLCH hue / chroma | Notes |
|---|---|---|
| Blue (primary) | 263 / 0.20 (containers 0.12) | Same hue family as the user's #1565FF, a little less chroma |
| Slate (secondary) | 256 / 0.035 | Near-neutral blue-grey: keeps the UI calm |
| Cyan (tertiary accent) | 212 / 0.115 (containers 0.08) | Same hue family as #00E5FF, much lower chroma |
| Neutral / neutral-variant | 258 / 0.010 / 0.018 | Cool, almost grey surfaces |
| Dark surface | `#0E1622` | Identical to the launcher background (`ic_launcher_background`) so the icon and app feel like one object |

## 1. Logo colours

| Token | Hex | Use |
|---|---|---|
| `brand_gradient_start` (dark) | `#3670EE` | Launcher icon, in-app mark on dark surfaces. Bottom-left of the mark |
| `brand_gradient_end` (dark) | `#48D2E9` | Top-right of the mark |
| `brand_gradient_start_light` | `#275FDC` | In-app mark on LIGHT surfaces (darker so it holds 3:1) |
| `brand_gradient_end_light` | `#0796AB` | idem |
| `brand_navy` | `#0E1622` | Launcher background, dark `surface`/`background` |

Gradient: 2 stops, linear, bottom-left to top-right across the mark bounding box. No glow, no shadow.

| Logo pair (graphic, 3:1) | FG | BG | Ratio | Needs | Result |
|---|---|---|---|---|---|
| Logo blue stop on launcher navy | `#3670EE` | `#0E1622` | **4.07:1** | 3:1 | Pass |
| Logo cyan stop on launcher navy | `#48D2E9` | `#0E1622` | **10.09:1** | 3:1 | Pass |
| Logo blue stop (light variant) on light surface | `#275FDC` | `#F6F9FF` | **5.30:1** | 3:1 | Pass |
| Logo cyan stop (light variant) on light surface | `#0796AB` | `#F6F9FF` | **3.34:1** | 3:1 | Pass |
| Light themed icon: glyph on tile | `#001454` | `#D5E4FE` | **13.32:1** | 3:1 | Pass |
| Dark themed icon: glyph on tile | `#D5E4FE` | `#234387` | **7.35:1** | 3:1 | Pass |

## 2. Role table (hex)

| Role | Light | Dark | Used for |
|---|---|---|---|
| `primary` | `#1D55D0` | `#6F9EFE` | Filled buttons, active controls, links, 'Tag' wordmark |
| `onPrimary` | `#FFFFFF` | `#001454` | Text/icons on primary |
| `primaryContainer` | `#D5E4FE` | `#234387` | Tonal fills: icon chips, selected cards |
| `onPrimaryContainer` | `#001454` | `#D5E4FE` | Content on primaryContainer |
| `secondary` | `#526072` | `#B8C7DD` | Neutral blue-slate: secondary actions, chips |
| `onSecondary` | `#FFFFFF` | `#253142` | Content on secondary |
| `secondaryContainer` | `#D4E4FA` | `#3A4859` | Nav-bar active pill, tonal buttons, read-only chips |
| `onSecondaryContainer` | `#101C2C` | `#D4E4FA` | Content on secondaryContainer |
| `tertiary` | `#027080` | `#58D6EE` | THE cyan accent: 'Forge' wordmark, small highlights, success-of-scan feedback |
| `onTertiary` | `#FFFFFF` | `#00363E` | Content on tertiary |
| `tertiaryContainer` | `#B6F2FE` | `#004E5A` | 'Writable' / NFC-active chips (use sparingly) |
| `onTertiaryContainer` | `#011F25` | `#9FEEFF` | Content on tertiaryContainer |
| `error` | `#B3261E` | `#F2B8B5` | Errors, destructive text |
| `onError` | `#FFFFFF` | `#601410` | Content on error |
| `errorContainer` | `#F9DEDC` | `#8C1D18` | Error banners |
| `onErrorContainer` | `#410E0B` | `#F9DEDC` | Content on errorContainer |
| `background` | `#F6F9FF` | `#0E1622` | Screen background (== surface) |
| `onBackground` | `#181B20` | `#DEE2E9` | Content on background |
| `surface` | `#F6F9FF` | `#0E1622` | App bars, sheets, default screen surface |
| `onSurface` | `#181B20` | `#DEE2E9` | Primary text and icons |
| `surfaceVariant` | `#DCE3EF` | `#3F4854` | Input fill, dividers-as-fill |
| `onSurfaceVariant` | `#414851` | `#BFC7D3` | Secondary text, inactive icons |
| `outline` | `#717882` | `#8A929C` | Field/button borders (3:1 UI contrast) |
| `outlineVariant` | `#BFC7D3` | `#3F4854` | Hairline dividers (decorative) |
| `surfaceContainerLowest` | `#FFFFFF` | `#080E19` | Lowest elevation fills |
| `surfaceContainerLow` | `#EFF4FA` | `#141B26` | Low elevation fills |
| `surfaceContainer` | `#EAEEF5` | `#19202B` | Navigation bar, default container |
| `surfaceContainerHigh` | `#E4E9EF` | `#212934` | Cards, list items |
| `surfaceContainerHighest` | `#DEE2E9` | `#29303C` | Highest elevation fills, text fields |
| `inverseSurface` | `#2D3135` | `#DEE2E9` | Snackbars |
| `inverseOnSurface` | `#EDF1F7` | `#2D3135` | Text on inverseSurface |
| `inversePrimary` | `#AAC8FF` | `#1D55D0` | Action text on snackbars |
| `scrim` | `#000000` | `#000000` | Modal scrim |
| `surfaceTint` | `#1D55D0` | `#6F9EFE` | Elevation tint (can be set to Transparent to keep surfaces flat) |

Wordmark text colours: **"Tag" = `primary`**, **"Forge" = `tertiary`** (so they re-theme automatically; light mode cyan is intentionally darkened to a teal-cyan `#027080` to pass AA, dark mode uses the bright `#58D6EE`).

## 3. WCAG contrast - LIGHT (surface `#F6F9FF`)

| Pair (foreground on background) | FG | BG | Ratio | Needs | Result |
|---|---|---|---|---|---|
| onSurface on surface | `#181B20` | `#F6F9FF` | **16.37:1** | 4.5:1 | Pass AAA |
| onSurfaceVariant on surface | `#414851` | `#F6F9FF` | **8.77:1** | 4.5:1 | Pass AAA |
| onSurface on surfaceContainerHigh (cards) | `#181B20` | `#E4E9EF` | **14.14:1** | 4.5:1 | Pass AAA |
| onSurfaceVariant on surfaceContainerHigh | `#414851` | `#E4E9EF` | **7.58:1** | 4.5:1 | Pass AAA |
| primary on surface (links, text buttons, 'Tag' wordmark) | `#1D55D0` | `#F6F9FF` | **6.10:1** | 4.5:1 | Pass AA |
| primary on surfaceContainerHigh | `#1D55D0` | `#E4E9EF` | **5.27:1** | 4.5:1 | Pass AA |
| onPrimary on primary (filled button) | `#FFFFFF` | `#1D55D0` | **6.44:1** | 4.5:1 | Pass AA |
| onPrimaryContainer on primaryContainer | `#001454` | `#D5E4FE` | **13.32:1** | 4.5:1 | Pass AAA |
| tertiary (cyan accent) on surface ('Forge' wordmark, accents as text) | `#027080` | `#F6F9FF` | **5.48:1** | 4.5:1 | Pass AA |
| tertiary on surfaceContainerHigh | `#027080` | `#E4E9EF` | **4.74:1** | 4.5:1 | Pass AA |
| onTertiary on tertiary | `#FFFFFF` | `#027080` | **5.78:1** | 4.5:1 | Pass AA |
| onTertiaryContainer on tertiaryContainer | `#011F25` | `#B6F2FE` | **13.99:1** | 4.5:1 | Pass AAA |
| onSecondaryContainer on secondaryContainer | `#101C2C` | `#D4E4FA` | **13.29:1** | 4.5:1 | Pass AAA |
| error on surface | `#B3261E` | `#F6F9FF` | **6.20:1** | 4.5:1 | Pass AA |
| onError on error | `#FFFFFF` | `#B3261E` | **6.54:1** | 4.5:1 | Pass AA |
| onErrorContainer on errorContainer | `#410E0B` | `#F9DEDC` | **12.77:1** | 4.5:1 | Pass AAA |
| outline on surface (component borders, UI) | `#717882` | `#F6F9FF` | **4.23:1** | 3:1 | Pass AA-large / UI (3:1) |
| primary on surface (icons, focus ring, UI) | `#1D55D0` | `#F6F9FF` | **6.10:1** | 3:1 | Pass AA |
| tertiary on surface (icons, UI) | `#027080` | `#F6F9FF` | **5.48:1** | 3:1 | Pass AA |

## 4. WCAG contrast - DARK (surface `#0E1622`)

| Pair (foreground on background) | FG | BG | Ratio | Needs | Result |
|---|---|---|---|---|---|
| onSurface on surface | `#DEE2E9` | `#0E1622` | **13.97:1** | 4.5:1 | Pass AAA |
| onSurfaceVariant on surface | `#BFC7D3` | `#0E1622` | **10.66:1** | 4.5:1 | Pass AAA |
| onSurface on surfaceContainerHigh (cards) | `#DEE2E9` | `#212934` | **11.29:1** | 4.5:1 | Pass AAA |
| onSurfaceVariant on surfaceContainerHigh | `#BFC7D3` | `#212934` | **8.61:1** | 4.5:1 | Pass AAA |
| primary on surface (links, text buttons, 'Tag' wordmark) | `#6F9EFE` | `#0E1622` | **6.92:1** | 4.5:1 | Pass AA |
| primary on surfaceContainerHigh | `#6F9EFE` | `#212934` | **5.59:1** | 4.5:1 | Pass AA |
| onPrimary on primary (filled button) | `#001454` | `#6F9EFE` | **6.51:1** | 4.5:1 | Pass AA |
| onPrimaryContainer on primaryContainer | `#D5E4FE` | `#234387` | **7.35:1** | 4.5:1 | Pass AAA |
| tertiary (cyan accent) on surface ('Forge' wordmark, accents as text) | `#58D6EE` | `#0E1622` | **10.61:1** | 4.5:1 | Pass AAA |
| tertiary on surfaceContainerHigh | `#58D6EE` | `#212934` | **8.57:1** | 4.5:1 | Pass AAA |
| onTertiary on tertiary | `#00363E` | `#58D6EE` | **7.68:1** | 4.5:1 | Pass AAA |
| onTertiaryContainer on tertiaryContainer | `#9FEEFF` | `#004E5A` | **7.22:1** | 4.5:1 | Pass AAA |
| onSecondaryContainer on secondaryContainer | `#D4E4FA` | `#3A4859` | **7.23:1** | 4.5:1 | Pass AAA |
| error on surface | `#F2B8B5` | `#0E1622` | **10.63:1** | 4.5:1 | Pass AAA |
| onError on error | `#601410` | `#F2B8B5` | **7.66:1** | 4.5:1 | Pass AAA |
| onErrorContainer on errorContainer | `#F9DEDC` | `#8C1D18` | **7.17:1** | 4.5:1 | Pass AAA |
| outline on surface (component borders, UI) | `#8A929C` | `#0E1622` | **5.77:1** | 3:1 | Pass AA |
| primary on surface (icons, focus ring, UI) | `#6F9EFE` | `#0E1622` | **6.92:1** | 3:1 | Pass AA |
| tertiary on surface (icons, UI) | `#58D6EE` | `#0E1622` | **10.61:1** | 3:1 | Pass AAA |

Every pair above meets or exceeds its threshold (4.5:1 normal text, 3:1 non-text UI). Body text and secondary text are AAA in both themes.

Notes that matter when implementing:
- Raw bright cyan (`#58D6EE` or the logo's `#48D2E9`) FAILS as text on white (about 1.7 to 1.8:1). Never use dark-mode cyan on light surfaces; always go through the `tertiary` role.
- `onSurfaceVariant` on `surfaceContainerHigh` still passes AAA in light and dark, so card subtitles are safe.
- `outlineVariant` is intentionally low contrast: use it only for decorative hairlines, never for the edge of an interactive control (use `outline`).
- `error` roles are the Material 3 baseline red, which already meets AA/AAA against these surfaces.
- Dynamic colour (Material You) will override this palette. If the brand blue/cyan identity matters, keep `dynamicColor = false` (or use it only as an opt-in setting).

## 5. Compose scheme (Material 3 1.2+ for `surfaceContainer*`)

```kotlin
val LightColors = lightColorScheme(
    primary = Color(0xFF1D55D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E4FE),
    onPrimaryContainer = Color(0xFF001454),
    inversePrimary = Color(0xFFAAC8FF),
    secondary = Color(0xFF526072),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E4FA),
    onSecondaryContainer = Color(0xFF101C2C),
    tertiary = Color(0xFF027080),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB6F2FE),
    onTertiaryContainer = Color(0xFF011F25),
    background = Color(0xFFF6F9FF),
    onBackground = Color(0xFF181B20),
    surface = Color(0xFFF6F9FF),
    onSurface = Color(0xFF181B20),
    surfaceVariant = Color(0xFFDCE3EF),
    onSurfaceVariant = Color(0xFF414851),
    surfaceTint = Color(0xFF1D55D0),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEDF1F7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF717882),
    outlineVariant = Color(0xFFBFC7D3),
    scrim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4FA),
    surfaceContainer = Color(0xFFEAEEF5),
    surfaceContainerHigh = Color(0xFFE4E9EF),
    surfaceContainerHighest = Color(0xFFDEE2E9),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF6F9EFE),
    onPrimary = Color(0xFF001454),
    primaryContainer = Color(0xFF234387),
    onPrimaryContainer = Color(0xFFD5E4FE),
    inversePrimary = Color(0xFF1D55D0),
    secondary = Color(0xFFB8C7DD),
    onSecondary = Color(0xFF253142),
    secondaryContainer = Color(0xFF3A4859),
    onSecondaryContainer = Color(0xFFD4E4FA),
    tertiary = Color(0xFF58D6EE),
    onTertiary = Color(0xFF00363E),
    tertiaryContainer = Color(0xFF004E5A),
    onTertiaryContainer = Color(0xFF9FEEFF),
    background = Color(0xFF0E1622),
    onBackground = Color(0xFFDEE2E9),
    surface = Color(0xFF0E1622),
    onSurface = Color(0xFFDEE2E9),
    surfaceVariant = Color(0xFF3F4854),
    onSurfaceVariant = Color(0xFFBFC7D3),
    surfaceTint = Color(0xFF6F9EFE),
    inverseSurface = Color(0xFFDEE2E9),
    inverseOnSurface = Color(0xFF2D3135),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF8A929C),
    outlineVariant = Color(0xFF3F4854),
    scrim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF080E19),
    surfaceContainerLow = Color(0xFF141B26),
    surfaceContainer = Color(0xFF19202B),
    surfaceContainerHigh = Color(0xFF212934),
    surfaceContainerHighest = Color(0xFF29303C),
)

// Logo-only tokens (not part of the M3 scheme)
val BrandGradientDark  = listOf(Color(0xFF3670EE), Color(0xFF48D2E9))
val BrandGradientLight = listOf(Color(0xFF275FDC), Color(0xFF0796AB))
```

Tip: set `surfaceTint = Color.Transparent` in the scheme if you want elevation to stay flat (no blue wash on cards), which fits the minimal direction.

## 6. Usage rules (keep it restrained)

1. Gradient only in the logo. Buttons, FABs, chips, progress: flat roles.
2. One accent at a time: primary blue is the action colour; cyan (`tertiary`) is a highlight (wordmark, "NFC active" chip, scan-success indicator), never a second button colour.
3. No glow, no coloured shadows. Elevation = tonal surface steps (`surfaceContainer*`).
4. Status colours are semantic only (error). Do not reuse brand blue/cyan for success/warning; add a separate green/amber only if the product needs them.
5. Grayscale test: hierarchy survives without hue (onSurface > onSurfaceVariant > outline); colour only reinforces it.

## 7. Where this lives in the repo

- Colours: `app/src/main/kotlin/com/tagforge/app/ui/theme/Color.kt` (`ForgePalette`, `surfaceTint = Transparent`). `BrandPaletteRolesTest` pins every role of section 2 to this file; `BrandPaletteContrastTest` re-measures sections 3, 4 and the logo pairs of section 1.
- Not defined above, so derived in code: `surfaceDim` / `surfaceBright` (one tonal step beyond the container ramp) and the semantic `success` / `warning` text colours, which keep their earlier text-safe values.
- Launcher icon: `app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml`, wired by `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`.
- Splash: `drawable/ic_splash_icon.xml` (light-surface stops) and `drawable-night/ic_splash_icon.xml`; background `splash_background` (`#F6F9FF` / `#0E1622`).
- In-app mark: `drawable/ic_logo_mark_{light,dark}.xml`, chosen by `TagForgeWordmark` from the Compose colour scheme (the in-app theme setting can differ from the system night mode).
- Dynamic colour (Material You) is an opt-in in Settings; the brand palette is the default.
