# iOS UI parity

One section per Compose file, in the order the cards port them. `ported` means the element
exists in Swift with the same values, sizes and states; anything else says why not.

Card U1 ports the design foundation. The files carry **no user-visible strings** — everything in
them is colour, type, geometry and motion — so the string rule of standing instruction 4 has
nothing to copy here. The Design lab's labels ("Design lab", "Type", "Bloom", …) are debug-only
and never appear in a release build.

## `app/src/main/kotlin/org/openstrap/app/ui/theme/Theme.kt`

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `MetricColors` object (17) | `MetricColors` (Theme.swift:106) | ported |
| `MetricColors.heart` (18) | `MetricColors.heart` (Theme.swift:107) | ported |
| `MetricColors.steps` (19) | `MetricColors.steps` (Theme.swift:108) | ported |
| `MetricColors.sleepDeep` (20) | `MetricColors.sleepDeep` (Theme.swift:109) | ported |
| `MetricColors.sleepLight` (21) | `MetricColors.sleepLight` (Theme.swift:110) | ported |
| `MetricColors.sleepRem` (22) | `MetricColors.sleepRem` (Theme.swift:111) | ported |
| `MetricColors.sleepAwake` (23) | `MetricColors.sleepAwake` (Theme.swift:112) | ported |
| `MetricColors.spo2` (24) | `MetricColors.spo2` (Theme.swift:113) | ported |
| `MetricColors.stress` (25) | `MetricColors.stress` (Theme.swift:114) | ported |
| `MetricColors.hrv` (26) | `MetricColors.hrv` (Theme.swift:115) | ported |
| `MetricColors.temperature` (27) | `MetricColors.temperature` (Theme.swift:116) | ported |
| `MetricColors.readiness` (28) | `MetricColors.readiness` (Theme.swift:117) | ported |
| `MetricColors.charge` (29) | `MetricColors.charge` (Theme.swift:118) | ported |
| `MetricColors.readinessDeep` (31) | `MetricColors.readinessDeep` (Theme.swift:120) | ported |
| `MetricColors.chargeDeep` (32) | `MetricColors.chargeDeep` (Theme.swift:121) | ported |
| `MetricColors.good` (33) | `MetricColors.good` (Theme.swift:122) | ported |
| `MetricColors.warn` (34) | `MetricColors.warn` (Theme.swift:123) | ported |
| `MetricColors.bad` (35) | `MetricColors.bad` (Theme.swift:124) | ported |
| `chargeScale` (38) | `MetricColors.chargeScale` (Theme.swift:135), stops in `chargeScaleRGB` (127) | ported |
| `chargeAt(level)` (41-47) | `MetricColors.chargeAt(_:)` (Theme.swift:139) | ported: the same last-stop-below search and `lerp`, which is Oklab in Compose — `SRGBColor.mixedOklab` (Theme.swift:58) |
| `battery(level)` (50-55) | `MetricColors.battery(_:)` (Theme.swift:154) | ported |
| `Dark` scheme `primary` (62) | `ThemeColors.primary` (Theme.swift:197) | ported |
| `onPrimary` (63) | `ThemeColors.onPrimary` (198) | ported |
| `primaryContainer` (64) | `ThemeColors.primaryContainer` (199) | ported |
| `onPrimaryContainer` (65) | `ThemeColors.onPrimaryContainer` (200) | ported |
| `secondary` (66) | `ThemeColors.secondary` (201) | ported |
| `onSecondary` (67) | `ThemeColors.onSecondary` (202) | ported |
| `secondaryContainer` (68) | `ThemeColors.secondaryContainer` (203) | ported |
| `onSecondaryContainer` (69) | `ThemeColors.onSecondaryContainer` (204) | ported |
| `tertiary` (70) | `ThemeColors.tertiary` (205) | ported |
| `onTertiary` (71) | `ThemeColors.onTertiary` (206) | ported |
| `tertiaryContainer` (72) | `ThemeColors.tertiaryContainer` (207) | ported |
| `onTertiaryContainer` (73) | `ThemeColors.onTertiaryContainer` (208) | ported |
| `surfaceTint = Color.Transparent` (75) | `ThemeColors.surfaceTint` (209) | ported |
| `background` (77) | `ThemeColors.background` (210) | ported |
| `onBackground` (78) | `ThemeColors.onBackground` (211) | ported |
| `surface` (79) | `ThemeColors.surface` (212) | ported |
| `onSurface` (80) | `ThemeColors.onSurface` (213) | ported |
| `surfaceContainerLowest` (81) | `ThemeColors.surfaceContainerLowest` (214) | ported |
| `surfaceContainerLow` (82) | `ThemeColors.surfaceContainerLow` (215) | ported |
| `surfaceContainer` (83) | `ThemeColors.surfaceContainer` (216) | ported |
| `surfaceContainerHigh` (84) | `ThemeColors.surfaceContainerHigh` (217) | ported |
| `surfaceContainerHighest` (85) | `ThemeColors.surfaceContainerHighest` (218) | ported |
| `onSurfaceVariant` (86) | `ThemeColors.onSurfaceVariant` (219) | ported |
| `outline` (87) | `ThemeColors.outline` (220) | ported |
| `outlineVariant` (88) | `ThemeColors.outlineVariant` (221) | ported |
| `error` (89) | `ThemeColors.error` (222) | ported |
| `variable(res, weight)` (92) | `DesignFontFamily.uiFont(size:weight:)` (Typography.swift:40) | ported through the `wght` variation axis |
| `FunnelDisplay` (99) | `DesignFontFamily.funnelDisplay`, weights 300/400/500/600/700 (Typography.swift:15) | ported |
| `FunnelSans` (106) | `DesignFontFamily.funnelSans`, weights 400/500/600/700 (Typography.swift:16) | ported |
| `MuseoModerno` (114) | `DesignFontFamily.museoModerno`, weight 600 (Typography.swift:17) | ported |
| `Base = Typography()` (116) | Material 3 defaults, written into each style below | ported |
| `display(style, weight, size, tracking)` (119) | `AppTextStyle` + `DesignFontFamily.font` (Typography.swift:52, 83) | ported: `letterSpacing` in sp becomes `.tracking` in points, `tnum` becomes `.monospacedDigit()` |
| `text(style)` (122) | `AppTextStyle` with `family: .funnelSans` (Typography.swift:83) | ported |
| `displayLarge` (125) | `AppTypography.displayLarge` (Typography.swift:113) | ported: Funnel Display SemiBold 64, tracking -1.5, line height 64 |
| `displayMedium` (126) | `AppTypography.displayMedium` (115) | ported: 48, -1, 52 |
| `displaySmall` (127) | `AppTypography.displaySmall` (117) | ported: 34, -0.5, 44 |
| `headlineLarge` (128) | `AppTypography.headlineLarge` (119) | ported: 30, -0.4, 40 |
| `headlineMedium` (129) | `AppTypography.headlineMedium` (121) | ported: 26, -0.3, 36 |
| `headlineSmall` (130) | `AppTypography.headlineSmall` (123) | ported: 22, -0.2, 32 |
| `titleLarge` (131) | `AppTypography.titleLarge` (125) | ported: 20, 0, 28 |
| `titleMedium` (132) | `AppTypography.titleMedium` (127) | ported: Funnel Sans SemiBold 16, tnum, line height 24 |
| `titleSmall` (133) | `AppTypography.titleSmall` (129) | ported: 14 SemiBold, 20 |
| `bodyLarge` (134) | `AppTypography.bodyLarge` (131) | ported: 16, 24 |
| `bodyMedium` (135) | `AppTypography.bodyMedium` (133) | ported: 14, 20 |
| `bodySmall` (136) | `AppTypography.bodySmall` (135) | ported: 12, 16 |
| `labelLarge` (137) | `AppTypography.labelLarge` (137) | ported: 14 SemiBold, 20 |
| `labelMedium` (139) | `AppTypography.labelMedium` (140) | ported: 11 SemiBold, tracking 1.4, 16 |
| `labelSmall` (140) | `AppTypography.labelSmall` (142) | ported: 11 Medium, 16 |
| `OpenStrapTheme { }` (145) | `.preferredColorScheme(.dark)` on the root (ContentView.swift:14) | ported: always dark; no Material theme wrapper exists on iOS, so the scheme is applied at the root instead |
| `MaterialTheme(colorScheme, typography)` (146) | not ported: iOS has no `MaterialTheme`; the tokens are used directly from `ThemeColors` / `AppTypography` | not ported: no Material design system to configure |

## `app/src/main/kotlin/org/openstrap/app/ui/theme/PhosphorIcons.kt`

The image sets are generated from this file by `iosApp/scripts/gen_icons.py` (46 `icon("…")`
calls, 46 `.imageset`s). `--check` fails if the two ever disagree.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `icon("House", …)` (14) | `PhosphorIcon.house` (PhosphorIcon.swift:11) | ported |
| `icon("HouseFill", …)` (15) | `PhosphorIcon.houseFill` (PhosphorIcon.swift:12) | ported |
| `icon("Heart", …)` (16) | `PhosphorIcon.heart` (PhosphorIcon.swift:13) | ported |
| `icon("HeartFill", …)` (17) | `PhosphorIcon.heartFill` (PhosphorIcon.swift:14) | ported |
| `icon("Moon", …)` (18) | `PhosphorIcon.moon` (PhosphorIcon.swift:15) | ported |
| `icon("MoonFill", …)` (19) | `PhosphorIcon.moonFill` (PhosphorIcon.swift:16) | ported |
| `icon("Watch", …)` (20) | `PhosphorIcon.watch` (PhosphorIcon.swift:17) | ported |
| `icon("WatchFill", …)` (21) | `PhosphorIcon.watchFill` (PhosphorIcon.swift:18) | ported |
| `icon("Heartbeat", …)` (22) | `PhosphorIcon.heartbeat` (PhosphorIcon.swift:19) | ported |
| `icon("Drop", …)` (23) | `PhosphorIcon.drop` (PhosphorIcon.swift:20) | ported |
| `icon("Wind", …)` (24) | `PhosphorIcon.wind` (PhosphorIcon.swift:21) | ported |
| `icon("ThermometerSimple", …)` (25) | `PhosphorIcon.thermometerSimple` (PhosphorIcon.swift:22) | ported |
| `icon("Brain", …)` (26) | `PhosphorIcon.brain` (PhosphorIcon.swift:23) | ported |
| `icon("BatteryCharging", …)` (27) | `PhosphorIcon.batteryCharging` (PhosphorIcon.swift:24) | ported |
| `icon("Plant", …)` (28) | `PhosphorIcon.plant` (PhosphorIcon.swift:25) | ported |
| `icon("Footprints", …)` (29) | `PhosphorIcon.footprints` (PhosphorIcon.swift:26) | ported |
| `icon("Lightning", …)` (30) | `PhosphorIcon.lightning` (PhosphorIcon.swift:27) | ported |
| `icon("ChartLine", …)` (31) | `PhosphorIcon.chartLine` (PhosphorIcon.swift:28) | ported |
| `icon("ChartLineUp", …)` (32) | `PhosphorIcon.chartLineUp` (PhosphorIcon.swift:29) | ported |
| `icon("Barbell", …)` (33) | `PhosphorIcon.barbell` (PhosphorIcon.swift:30) | ported |
| `icon("ArrowCircleUp", …)` (34) | `PhosphorIcon.arrowCircleUp` (PhosphorIcon.swift:31) | ported |
| `icon("CloudArrowDown", …)` (35) | `PhosphorIcon.cloudArrowDown` (PhosphorIcon.swift:32) | ported |
| `icon("FolderOpen", …)` (36) | `PhosphorIcon.folderOpen` (PhosphorIcon.swift:33) | ported |
| `icon("Key", …)` (37) | `PhosphorIcon.key` (PhosphorIcon.swift:34) | ported |
| `icon("Warning", …)` (38) | `PhosphorIcon.warning` (PhosphorIcon.swift:35) | ported |
| `icon("WarningCircle", …)` (39) | `PhosphorIcon.warningCircle` (PhosphorIcon.swift:36) | ported |
| `icon("CheckCircle", …)` (40) | `PhosphorIcon.checkCircle` (PhosphorIcon.swift:37) | ported |
| `icon("Info", …)` (41) | `PhosphorIcon.info` (PhosphorIcon.swift:38) | ported |
| `icon("LinkBreak", …)` (42) | `PhosphorIcon.linkBreak` (PhosphorIcon.swift:39) | ported |
| `icon("Bluetooth", …)` (43) | `PhosphorIcon.bluetooth` (PhosphorIcon.swift:40) | ported |
| `icon("DownloadSimple", …)` (44) | `PhosphorIcon.downloadSimple` (PhosphorIcon.swift:41) | ported |
| `icon("TerminalWindow", …)` (45) | `PhosphorIcon.terminalWindow` (PhosphorIcon.swift:42) | ported |
| `icon("Copy", …)` (46) | `PhosphorIcon.copy` (PhosphorIcon.swift:43) | ported |
| `icon("X", …)` (47) | `PhosphorIcon.x` (PhosphorIcon.swift:44) | ported |
| `icon("ArrowLeft", …)` (48) | `PhosphorIcon.arrowLeft` (PhosphorIcon.swift:45) | ported |
| `icon("CaretLeft", …)` (49) | `PhosphorIcon.caretLeft` (PhosphorIcon.swift:46) | ported |
| `icon("CaretRight", …)` (50) | `PhosphorIcon.caretRight` (PhosphorIcon.swift:47) | ported |
| `icon("CaretUpFill", …)` (51) | `PhosphorIcon.caretUpFill` (PhosphorIcon.swift:48) | ported |
| `icon("CaretDownFill", …)` (52) | `PhosphorIcon.caretDownFill` (PhosphorIcon.swift:49) | ported |
| `icon("User", …)` (53) | `PhosphorIcon.user` (PhosphorIcon.swift:50) | ported |
| `icon("GenderMale", …)` (54) | `PhosphorIcon.genderMale` (PhosphorIcon.swift:51) | ported |
| `icon("GenderFemale", …)` (55) | `PhosphorIcon.genderFemale` (PhosphorIcon.swift:52) | ported |
| `icon("CaretDown", …)` (56) | `PhosphorIcon.caretDown` (PhosphorIcon.swift:53) | ported |
| `icon("CalendarBlank", …)` (57) | `PhosphorIcon.calendarBlank` (PhosphorIcon.swift:54) | ported |
| `icon("SlidersHorizontal", …)` (58) | `PhosphorIcon.slidersHorizontal` (PhosphorIcon.swift:55) | ported |
| `icon("ShieldCheck", …)` (59) | `PhosphorIcon.shieldCheck` (PhosphorIcon.swift:56) | ported |
| `autoMirror` (65, for ArrowLeft/CaretLeft/CaretRight) | `PhosphorIcon` cases carry the same icons; SwiftUI's `Image` mirrors in RTL only through `.environment(\.layoutDirection, …)`, and the design is left-to-right only | ported: the flag has no effect because no screen uses an RTL layout |
| `ImageVector.Builder(24.dp, 24.dp, 256f viewport)` (66) | `viewBox="0 0 256 256"` in each SVG (Assets.xcassets/Phosphor) | ported |
| `PathParser` path data (67) | `<path d="…">` in each SVG, one path per icon | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowStyle.kt`

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `CoreShape { Rounded, Radial }` (12) | `CoreShape { .rounded, .radial }` (GlowStyle.swift:5) | ported |
| `LightFrom { Top, Bottom, All }` (15) | `LightFrom { .top, .bottom, .all }` (GlowStyle.swift:11) | ported |
| `GlowMotion` sealed interface (21) | `GlowMotion` enum (GlowStyle.swift:19) | ported |
| `GlowMotion.Drift` (23) | `.drift` (GlowStyle.swift:22) | ported |
| `GlowMotion.Heartbeat(bpm = 62)` (26) | `.heartbeat(bpm:)` (GlowStyle.swift:25) + `heartbeatDefault` (33) | ported |
| `GlowMotion.Breath(perMinute = 6f)` (29) | `.breath(perMinute:)` (GlowStyle.swift:27) + `breathDefault` (34) | ported |
| `GlowMotion.Stride(cadence = 110, energy = 1f)` (35) | `.stride(cadence:energy:)` (GlowStyle.swift:29) + `strideDefault` (35) | ported |
| `GlowMotion.Still` (38) | `.still` (GlowStyle.swift:31) | ported |
| `GlowStyle.color` (50) | `GlowStyle.color` (GlowStyle.swift:45) | ported |
| `radius = 48.dp` (51) | `radius: 48` (GlowStyle.swift:46) | ported |
| `coreShape = Rounded` (52) | `coreShape: .rounded` (47) | ported |
| `coreX = 0.5f` (53) | `coreX: 0.5` (48) | ported |
| `coreY = 0.64f` (54) | `coreY: 0.64` (49) | ported |
| `coreWidth = 0.74f` (55) | `coreWidth: 0.74` (50) | ported |
| `coreHeight = 0.6f` (56) | `coreHeight: 0.6` (51) | ported |
| `coreBlur = 22.dp` (57) | `coreBlur: 22` (52) | ported |
| `coreOpacity = 1f` (58) | `coreOpacity: 1` (53) | ported |
| `coreLightness = 0.09f` (60) | `coreLightness: 0.09` (55) | ported |
| `toneShift = -40f` (62) | `toneShift: -40` (57) | ported |
| `toneWidth = 24.dp` (63) | `toneWidth: 24` (58) | ported |
| `lightFrom = Top` (64) | `lightFrom: .top` (59) | ported |
| `glowSize = 30.dp` (65) | `glowSize: 30` (60) | ported |
| `glowStrength = 0.8f` (66) | `glowStrength: 0.8` (61) | ported |
| `bevel = 0f` (68) | `bevel: 0` (63) | ported |
| `depth = 0.dp` (70) | `depth: 0` (65) | ported |
| `grain = 0.06f` (72) | `grain: 0.06` (67) | ported |
| `motion = Drift` (73) | `motion: .drift` (68) | ported |
| `tinted(color)` (76) | `tinted(_:)` (GlowStyle.swift:78) | ported |
| `Bloom` (80) | `GlowStyle.bloom` (GlowStyle.swift:85) | ported: D65AA0, bevel 1, every other default |
| `Midnight` (83-90) | `GlowStyle.midnight` (88) | ported |
| `Ember` (93-100) | `GlowStyle.ember` (98) | ported |
| `presets` (102) | `GlowStyle.presets` (108), `GlowPreset` (132) | ported: same three names in the same order |
| `chart(color)` (107-113) | `GlowStyle.chart(_:)` (GlowStyle.swift:115) | ported |
| `GlowPalette.light` (118) | `GlowPalette.light` (GlowStyle.swift:151) | ported |
| `GlowPalette.mid` (119) | `GlowPalette.mid` (152) | ported |
| `GlowPalette.tone` (120) | `GlowPalette.tone` (153) | ported |
| `GlowPalette.core` (121) | `GlowPalette.core` (154) | ported |
| `ColorUtils.colorToHSL` (125) | `GlowPalette.hsl(from:)` (GlowStyle.swift:193) | ported |
| `s = s0.coerceIn(0.35f, 0.9f)` unless grey (128) | same rule (GlowStyle.swift:162) | ported |
| `light = hsl(h, s * 0.9f, (l + 0.03f).coerceIn(0.45f, 0.6f))` (130) | GlowStyle.swift:165 | ported |
| `mid = capped(h - 4f, s * 0.85f, 0.32f, MID_MAX_LUMINANCE)` (134) | GlowStyle.swift:170, `capped` (182) | ported |
| `tone = capped(h + toneShift, s * 0.8f, 0.28f, TONE_MAX_LUMINANCE)` (135) | GlowStyle.swift:171 | ported |
| `core = hsl(h, 0.7f, coreLightness)` (136) | GlowStyle.swift:173 | ported |
| `MID_MAX_LUMINANCE = 0.09f` (141) | `GlowPalette.midMaxLuminance` (GlowStyle.swift:177) | ported |
| `TONE_MAX_LUMINANCE = 0.07f` (143) | `GlowPalette.toneMaxLuminance` (179) | ported |
| `capped()` step-down loop (147-150) | `GlowPalette.capped(h:s:l:maxLuminance:)` (182) | ported: same 0.01 steps, same `0.08` floor |
| `hsl()` wrap/clamp (152-153) | `GlowPalette.rgb(h:s:l:)` (218) | ported: Compose's `Color.hsl` |
| `luminance()` in `capped` (150) | `SRGBColor.luminance` (Theme.swift:49) | ported: Compose's `Color.luminance()` |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowCard.kt`

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `LocalGlowPaused` (82) | `EnvironmentValues.glowPaused` (GlowCard.swift:149), provided by `glowScrollPhasePause()` (175) | ported |
| `LocalGlowPagePaused` (89) | not ported: iOS has no tab pager keeping adjacent pages alive (the tab bar draws one screen at a time) | not ported: nothing to pause |
| `GlowCard(style, modifier, onClick, contentPadding, content)` (92) | `GlowCard` (GlowCard.swift:190), `init(_:contentPadding:content:)` (205) | ported: `onClick` is not in U1 — no card is tappable yet (U2/U3 bring the screens' taps) |
| `shape = RoundedCornerShape(style.radius)` (99) | `RoundedRectangle(cornerRadius: style.radius, style: .continuous)` (236) | ported |
| `palette = remember(style) { GlowPalette(style) }` (100) | `GlowPalette(style)` inside the renderer (GlowCard.swift:365) | ported |
| `slab = lerp(palette.mid, Black, 0.55f)` (101) | not ported: no preset has `depth > 0`, so no slab is ever drawn — the field exists (GlowStyle.swift:65) and is unused | not ported: `depth` is 0 in every style the app uses |
| per-card `grainSeed` (103) | `GlowStyle.grainSeed` (GlowStyle.swift:73) | ported, from the style rather than a per-instance random so the rendered layers stay cacheable by style and size |
| `CoreDrift(Random)` (104) | `CoreDrift(seed:)` + `GlowRandom` (GlowCard.swift:22, 119) | ported |
| `animate` from `ANIMATOR_DURATION_SCALE` (108) | `@Environment(\.accessibilityReduceMotion)` (GlowCard.swift:195) | ported: iOS's equivalent switch |
| time only advances while unpaused (114-127) | `TimelineView(.animation(paused:))` + `elapsed(at:)` (GlowCard.swift:250, 270) | ported |
| cache key `(style.copy(motion = Still), size, density)` (131) | `GlowLayerCache.Key(style:width:height:scale:)` (GlowCard.swift:305) | ported |
| `produceState` / render off the main thread (132-137) | `load(_:)` + `Task.detached` (GlowCard.swift:279) | ported |
| slab `drawBehind` for `depth` (144-147) | not ported with the slab above | not ported: `depth` is 0 |
| `clip(shape)` (149) | `.clipShape` (GlowCard.swift:236) | ported |
| `drawBitmap(b.base)` (156-159) | `Image(uiImage: layers.base)` (GlowCard.swift:219) | ported |
| `CoreLayer` (163, 173-191) | `core(_:size:)` (GlowCard.swift:250) | ported: translation, scale, transform origin, alpha |
| `Box(Modifier.padding(contentPadding), content)` (165) | `content().padding(contentPadding)` (GlowCard.swift:223) | ported |
| overlay: grain + hairlines (167, 196-210) | `overlay(...)` (GlowCard.swift:541) | ported |
| `GlowBitmaps` (213) | `GlowLayers` (GlowCard.swift:295) | ported |
| `GlowCache` LRU, 10 entries (224-241) | `GlowLayerCache`, 10 entries (GlowCard.swift:304) | ported |
| `render()` at half resolution (244-297) | `GlowRenderer.render` (GlowCard.swift:362), core at `scale * 0.5` (514) | ported |
| `halfResBitmap(scale 0.5, margin)` (300-313) | `GlowRenderer.format(scale:)` + `core` margin (GlowCard.swift:379, 501) | ported |
| `fillShader`: rim melt, 12 eased steps, reach 0.4, `LightFrom.All` flat (320-332) | `GlowRenderer.fill` (GlowCard.swift:429) | ported |
| `insetShadow(size, radius, dy, blur, spread, color)` (338-351) | `GlowRenderer.insetShadow` (GlowCard.swift:466) | ported: same even-odd path, `blur / 2` as the sigma |
| `blurFilter(sigma)` (354-355) | `GlowRenderer.gaussianBlur` (GlowCard.swift:635) | ported with Core Image; the effective sigma is `blur - 0.5` as in the mask filter |
| `Drift(dx, dy, scale, fade)` (359) | `GlowDrift` (GlowCard.swift:10) | ported |
| `CoreDrift` (365-411) | `CoreDrift` (GlowCard.swift:22) | ported: same 10-24 s waves, same 0.035/0.03 amounts, same per-card offset |
| `wander(t, amount)` (401-406) | `CoreDrift.wander` (GlowCard.swift:79) | ported |
| `Wave(period, phase)` (408-410) | `CoreDrift.Wave` (GlowCard.swift:23) | ported |
| `ecg(phase)` (417-423) | `CoreDrift.ecg` (GlowCard.swift:90) | ported |
| `inhale(phase)` (426-430) | `CoreDrift.inhale` (GlowCard.swift:102) | ported |
| `GRAIN_TILE = 512` (432) | `GlowRenderer.grainTileSize` (GlowCard.swift:605) | ported |
| `grainShader(seed)` (438-443) | `GlowRenderer.drawGrain` (GlowCard.swift:586) | ported |
| `grainBitmap`: 55% clear, 35% dark specks, `Random(7)` (445-457) | `GlowRenderer.grainImage` (GlowCard.swift:607) | ported with SplitMix64 instead of Kotlin's XorWowRandom: the tile rules and thresholds are identical, the exact speck positions are not |
| Motion: `GlowMotion.Drift` (376) | `GlowCard.swift:54` | ported |
| Motion: `Heartbeat` (378-384) | `GlowCard.swift:58-64` | ported |
| Motion: `Breath` (385-390) | `GlowCard.swift:65-70` | ported |
| Motion: `Stride` (391-397) | `GlowCard.swift:71-77` | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowType.kt`

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `GlowType` object (13) | `GlowType` (Typography.swift:158) | ported |
| `headline` (15) | `GlowType.headline` (Typography.swift:160) | ported: Funnel Display Light 26, tracking -0.02 em = -0.52 pt, line height 30, tabular figures |
| `title` (18) | `GlowType.title` (165) | ported: Light 20, -0.4 pt |
| `number` (21) | `GlowType.number` (170) | ported: Light 20, -0.4 pt |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowParts.kt` — `DotMatrixText` (line 64)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `GLYPHS` map, `0`…`9`, `.`, `,`, `:`, `-`, `k`, `%`, space (38-56) | `DotMatrixText.glyphs` (DotMatrixText.swift:16) | ported: the same strings, row for row |
| `ROWS = 7` (57) | `DotMatrixText.rows` (DotMatrixText.swift:36) | ported |
| `DotMatrixText(text, modifier, dot, gap, color)` (64-70) | `DotMatrixText.init(_:dot:gap:color:)` (DotMatrixText.swift:38) | ported: `dot` 5, `gap` 2, white |
| one empty column between glyphs (73) | `columns` (DotMatrixText.swift:50) | ported |
| `size(pitch * columns - gap, pitch * ROWS - gap)` (77) | `size` (DotMatrixText.swift:56) | ported, and the size is a ceiling: `DotMatrixSize` (DotMatrixText.swift:102) gives that size to a parent that asks for it and the width on offer when that is narrower, which is what Compose's `size()` does with `enforceIncoming` (card F2: `.frame(width:)` refused to shrink, so one 411 pt row made the Design lab column 411 pt on a 402 pt screen and both edges were cut) |
| `contentDescription = text` (78) | `.accessibilityLabel(text)` (DotMatrixText.swift:95) | ported |
| `drawCircle(color, d / 2, …)` (86) | `context.fill(Path(ellipseIn:), with: .color(color))` (DotMatrixText.swift:83) | ported |
| glyph advance `(rows[0].length + 1) * step` (89) | `x0 += CGFloat(rows[0].count + 1) * pitch` (DotMatrixText.swift:86) | ported |
| nothing clips the Canvas, so a coerced size still draws past its own bounds until a parent clips | `.clipped()` (DotMatrixText.swift:91) | ported as the stricter version: the dots stop at the width the layout settled on (card F2) |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowParts.kt` — the rest of the file (card U2)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `ArcMark(at, color)` (105) | `ArcMark` (GlowParts.swift:13) | ported |
| `ArcTimeline(marks, arcs, modifier, color)` (112-137) | `ArcTimeline` (GlowParts.swift:26) | ported |
| dotted baseline: white 65%, 2 wide, 9 gap, 6 above the bottom (120-124) | `glowDottedLine` (GlowParts.swift:34) | ported |
| an arc bridging each pair, last one solid, others 65% (125-134) | `Path.addArc` 180°→360° (GlowParts.swift:44-58) | ported |
| marks as 4pt dots on the line (135) | `Path(ellipseIn:)` 8pt (GlowParts.swift:59-65) | ported |
| `GlowSwitch(checked, onCheckedChange, modifier, accent)` (145) | `GlowSwitch` (GlowParts.swift:75) | ported |
| track: accent 55% on, white 8% off (146) | `Capsule().fill` (GlowParts.swift:95-96) | ported (the colour changes without an animation: instruction 7 allows only offset, scale and opacity to move) |
| `size(60.dp, 34.dp)`, clipped to a circle, 3dp padding (148-155) | `frame(width: 60, height: 34)` + 3pt knob padding (GlowParts.swift:103, 99) | ported |
| knob 28, offset 26 when on, spring(damping 0.6, stiffness 500) (147, 159, 162) | `frame(28)` / `offset(x:)` / `.spring(response: 0.25, dampingFraction: 0.6)` (GlowParts.swift:99-101) | ported |
| knob fill: radial white → `#DCDCEA`, 6dp shadow (162) | `RadialGradient` + `.shadow` (GlowParts.swift:89-97) | ported |
| `Role.Switch` (154) | `.accessibilityAddTraits(.isButton)` + on/off label (GlowParts.swift:107-109) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowCharts.kt` (card U2)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `Ink` / `Faint` 16% / `Muted` 55% (50-52) | `glowInk` / `glowFaint` / `glowMuted` (GlowCharts.swift:15-17) | ported |
| `axisText` FunnelSans 10 (54) | `axisTextStyle` (GlowCharts.swift:20) | ported |
| `valueText` FunnelSans 11 (55) | `valueTextStyle` (GlowCharts.swift:23) | ported |
| `readoutText` FunnelSans 12 (56) | `readoutTextStyle` (GlowCharts.swift:25) | ported |
| `dottedLine`: 1.6 wide, round cap, dash (0, gap 6) (59-63) | `glowDottedLine` (GlowCharts.swift:42) | ported: one 1.6pt dot per 6pt instead of a zero-length dash, same pixels |
| `text(m, s, style, x, y, align)` with the canvas clamp (65-68) | `glowDrawText` (GlowCharts.swift:83) | ported |
| `readout`, the glass pill: 18/10 padding, 12 gap, black 72%, white 18% hairline (71-80) | `glowReadout` (GlowCharts.swift:121) | ported |
| `guide`, 1.6 wide white 60% dash (0, 4) (83-86) | `glowGuide` (GlowCharts.swift:151) | ported |
| `axis`, labels spread 0…1 (89-95) | `glowAxis` (GlowCharts.swift:103) | ported |
| `GlowLineChart` (103-190) | `GlowLineChart` (GlowCharts.swift:185) | ported |
| `height = 120.dp` (107) | `height: CGFloat = 120` (GlowCharts.swift:188) | ported |
| `axisH` 18 / `top` 10 / `inset` 7 (126-127, 132) | the same three constants (GlowCharts.swift:238-239, 254) | ported |
| domain: yRange, else band and data, flat gains 1 (129-130) | `GlowGeometry.lineChartDomain` (GlowGeometry.kt:51) | ported |
| `xOf` / `yOf` (133-134) | `GlowGeometry.lineChartX` / `lineChartY` (GlowGeometry.kt:72, 76) | ported |
| guides at 0, 0.5, 1 (136) | `for fraction in [0, 0.5, 1]` (GlowCharts.swift:266) | ported |
| band fill 10%, two dotted edges 45% gap 4 (137-143) | the same (GlowCharts.swift:271-282) | ported |
| runs of consecutive readings; a null starts a new run (146-149) | the same (GlowCharts.swift:285-295) | ported |
| a lone reading is a 2.6 dot (151) | 5.2pt circle (GlowCharts.swift:298-305) | ported |
| smooth line: `smoothPath`, fill gradient 14%, halo 4 at 8%, line 1.8 (153-158) | `glowSmoothPath` / `glowGeometry.smoothCubics` / the same three strokes (GlowCharts.swift:306-331) | ported |
| average: dotted line 50% gap 4 and `"avg ${format(avg)}"` right-aligned 16 above (161-166) | `"avg \(format(average))"` (GlowCharts.swift:334-343) | ported: the only string is `avg `, copied verbatim |
| latest reading: 6.5 halo at 20%, 3.5 dot (168-171) | the same (GlowCharts.swift:345-355) | ported |
| axis labels at `h + 4` (173) | `glowAxis` (GlowCharts.swift:357) | ported |
| scrub readout: nearest non-null reading within count/48 + 1 (176-188) | `nearestIndex` + the guide, black 60% dot and 4pt dot (GlowCharts.swift:360-373, 377-391) | ported |
| `GlowDotBars` (198-239) | `GlowDotBars` (GlowCharts.swift:400) | ported |
| `height = 96.dp`, `rows = 10` (201-202) | the same defaults (GlowCharts.swift:402-403) | ported |
| `highlight = values.indexOfLast { it > 0f }` (205) | the same default (GlowCharts.swift:425) | ported |
| pitchX / pitchY / radius 32% / max ≥ 1 / lit rows (216-222) | `GlowGeometry.dotBarPitchX` … `dotBarLit` (GlowGeometry.kt:113-140) | ported |
| other columns dim to 35% while scrubbing (224) | `dim` (GlowCharts.swift:454) | ported |
| unlit 10%, highlight accent, lit `0.55 + 0.45 * (row+1)/rows` (228-232) | the same (GlowCharts.swift:458-465) | ported |
| column centre and row y from the bottom (225, 233) | `GlowGeometry.dotBarCenterX` / `dotBarDotY` (GlowGeometry.kt:134, 137) | ported |
| touch readout at the column, anchored to the top (237) | `glowReadout` (GlowCharts.swift:476-481) | ported |
| `GlowDay(label, value)` (244) | `GlowDay` (GlowCharts.swift:488) | ported |
| `GlowTrend` (250-294) | `GlowTrend` (GlowCharts.swift:497) | ported |
| `height = 130.dp` (254) | `height: CGFloat = 130` (GlowCharts.swift:499) | ported |
| axisH 18, top 22, bottom inset 6 (260-261, 267) | the same (GlowCharts.swift:519-521, 527) | ported |
| domain: 85% of the lowest, 1 of room at the top (265-266) | `GlowGeometry.trendDomain` (GlowGeometry.kt:153) | ported |
| `yOf` with the label room (267) | `GlowGeometry.trendY` (GlowGeometry.kt:163) | ported |
| average line white 45% gap 4 (269) | the same (GlowCharts.swift:534-538) | ported |
| day label, today in white, others muted (273) | the same (GlowCharts.swift:544-547) | ported |
| null day: a 2.5 faint dot 3 above the axis (276) | the same (GlowCharts.swift:548-554) | ported |
| stem: 2 wide, dash (0, 5), 90% today / 45% (280-284) | `glowDottedLine` width 2 gap 5 (GlowCharts.swift:556-559) | ported |
| today: 11 halo at 18% + 6 dot; others a 4 dot (286-289) | the same (GlowCharts.swift:560-574) | ported |
| value printed 20 above, rounded (291) | `String(Int(value.rounded()))` 20 above (GlowCharts.swift:575-579) | ported |
| `GlowArcGauge` (303-340) | `GlowArcGauge` (GlowCharts.swift:591) | ported |
| `aspectRatio(1.9f)`, `widthIn(max = 440.dp)`, content at the bottom middle (312-315) | `aspectRatio(1.9)` + `frame(maxWidth: gaugeMaxWidth)` + `ZStack(alignment: .bottom)` (GlowCharts.swift:602-613) | ported |
| `r = width/2 - 10`, centre 3 above the bottom edge (317-318) | `GlowGeometry.arcGaugeRadius` / `arcGaugeCentre` (GlowGeometry.kt:177, 180) | ported |
| sweep `180 * progress.coerceIn(0, 1)` (321) | `GlowGeometry.arcGaugeSweep` (GlowGeometry.kt:183) | ported |
| remaining track: dotted 2, gap 7, 22% (322-325) | `glowDottedStroke` (GlowCharts.swift:623-626) | ported |
| fill: 8 wide white 10% under a 3 wide line (327-332) | the same two strokes (GlowCharts.swift:629-643) | ported |
| sweep gradient 55% at 0.5 → white at the end, starting at 3 o'clock (329-332) | `conicGradient` with stops 0.5 and (180+sweep)/360 (GlowCharts.swift:635-643) | ported |
| end dot: 9 at 30% behind 5 at 100% (333-336) | the same (GlowCharts.swift:645-655) | ported |
| `GlowStage(from, to, row)` (345) | `GlowStage` (GlowCharts.swift:667) | ported |
| `GlowHypnogram` (351-409) | `GlowHypnogram` (GlowCharts.swift:677) | ported |
| `height = 130.dp` (356) | `height: CGFloat = 130` (GlowCharts.swift:681) | ported |
| axisH 18, name column 46 wide (365-366) | the same (GlowCharts.swift:714-715) | ported |
| `rowH = h / rows`, `pillH = rowH * 0.46`, lane centres (369-371) | `GlowGeometry.hypnogramRowH` / `hypnogramPillH` / `hypnogramRowY` (GlowGeometry.kt:203-209) | ported |
| `xOf(f) = nameW + f * w` (372) | `GlowGeometry.hypnogramX` (GlowGeometry.kt:212) | ported |
| a dotted lane per stage name, the name 7 above it (374-377) | the same (GlowCharts.swift:728-734) | ported |
| connectors where consecutive stages change lane, 30% (378-383) | the same (GlowCharts.swift:736-749) | ported |
| pill alpha `0.55 + 0.45 * row / (rows - 1)` (385) | `GlowGeometry.hypnogramPillAlpha` (GlowGeometry.kt:218) | ported |
| pill: rounded by its half height, never thinner than its height (386-391) | `GlowGeometry.hypnogramPill` (GlowGeometry.kt:222) | ported |
| axis labels under the plot (393-398) | the same (GlowCharts.swift:766-772) | ported |
| scrub: the stage under the finger, else the nearest, with the guide and readout (399-407) | the same (GlowCharts.swift:774-789) | ported |
| `GlowHorizon(progress, color, modifier, height = 72.dp)` (419) | `GlowHorizon` (GlowCharts.swift:799) | ported |
| sweep 84°, `start = 270 - 42`, chord `w - 28`, `r`, `sag`, top 14 (427-431) | `GlowGeometry.horizonRadius` / `horizonSag` / `horizonPoint` (GlowGeometry.kt:246-262) | ported |
| `at(f)` on the arc (436-439) | `GlowGeometry.horizonPoint` (GlowGeometry.kt:260) | ported |
| dotted track 1.6, gap 6 (440-443) | `glowDottedStroke` (GlowCharts.swift:836-839) | ported |
| five ticks at `r + 9` (444) | the same (GlowCharts.swift:841-849) | ported |
| fill: 9 wide at 10% under 2.4 wide white (448-449) | the same (GlowCharts.swift:851-861) | ported |
| knob: 11 at 30%, 6.5 in colour, 2.6 white (452-454) | the same (GlowCharts.swift:862-876) | ported |
| `"0"` and `"100"` under the arc's ends (457-458) | the same strings (GlowCharts.swift:884-887) | ported |
| the fill sweeps in over 700 ms (422-425) | the finished arc fades in over 700 ms, and is simply there under Reduce Motion (GlowCharts.swift:813-825) | ported with a recorded deviation: standing instruction 7 allows only offset, scale and opacity to move, so the arc is not redrawn per frame |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowLayout.kt` (card U2)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `Gutter` 20 (35) | `GlowLayout.gutter` (GlowLayout.swift:9) | ported |
| `CardPadding` 30/30/32/26 (37) | `GlowLayout.cardPadding` (GlowLayout.swift:12) | ported |
| `TitleGap` 14 (39) | `GlowLayout.titleGap` (GlowLayout.swift:14) | ported |
| `ChartGap` 20 (41) | `GlowLayout.chartGap` (GlowLayout.swift:16) | ported |
| `ChartHeight` 116 (43) | `GlowLayout.chartHeight` (GlowLayout.swift:18) | ported |
| `HeroDot` 4.5, `HeroGap` 1.8 (45-46) | `GlowLayout.heroDot` / `heroGap` (GlowLayout.swift:20-21) | ported |
| `TwoColumnWidth` 600 (48) | `GlowLayout.twoColumnWidth` (GlowLayout.swift:23) | ported |
| `MaxContentWidth` 1040 (50) | `GlowLayout.maxContentWidth` (GlowLayout.swift:25) | ported |
| `ColumnGap` 12 (52) | `GlowLayout.columnGap` (GlowLayout.swift:27) | ported |
| `GaugeMaxWidth` 440 (54) | `GlowLayout.gaugeMaxWidth` (GlowLayout.swift:29) | ported |
| `WideGutter` 16 (60) | `GlowLayout.wideGutter` (GlowLayout.swift:31) | ported |
| `NarrowWidth` 400 (62) | `GlowLayout.narrowWidth` (GlowLayout.swift:33) | ported |
| `WideCardPadding` 20/20/32/26 (63) | `GlowLayout.wideCardPadding` (GlowLayout.swift:34) | ported |
| `LocalCardPadding` (67) | `EnvironmentValues.glowCardPadding` (GlowLayout.swift:42) | ported: dot panels read it the same way cards do |
| `Pane { Full, Left, Right }` (70) | `Pane` (GlowLayout.swift:54) | ported |
| `GlowBlocks.full/left/right(key)` (76-86) | `GlowBlock.full/left/right(key:)` (GlowLayout.swift:62) | ported |
| `GlowScreenList(contentPadding, state, scrollTo, onScrolled)` (96-132) | `GlowScreenList` (GlowLayout.swift:85) | ported |
| gutter `max((w - 1040)/2, tight ? 16 : 20)` and tight = wide or < 400 (104-106) | the same (GlowLayout.swift:91-97) | ported |
| runs of left/right blocks in two columns, full blocks spanning; a lopsided run stacks (117-127) | `sections` + `sectionView` (GlowLayout.swift:135-168) | ported |
| `scrollTo` animates to the block's key (110-113) | `ScrollViewReader` + `onAppear` / `onChange` (GlowLayout.swift:113-124) | ported |
| `GlowHeader(title, trailing, chevron, trailingContent)` (162-177) | `GlowHeader` (GlowLayout.swift:171) | ported |
| `GlowHeroNumber(value, unit, facts, side)` (184-193) | `GlowHeroNumber` (GlowLayout.swift:225) | ported |
| `GlowChartGap()` (197) | `GlowChartGap` (GlowLayout.swift:271) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowQuiet.kt` (card U2)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `PanelShape` 28 (48) | `paneShape` (GlowQuiet.swift:11) | ported |
| `DotPanel(modifier, onClick, content)`, spacing 14, card padding (52-79) | `DotPanel` (GlowQuiet.swift:34) | ported |
| the dotted outline: inset 1, 1.6 wide, round cap, dash (0, 7), white 30% (61-74) | `strokeBorder` with dash (0.1, 7) (GlowQuiet.swift:48-54) | ported: a 0.1pt dash under a round cap draws the same dot as a zero-length one |
| `DotHeader(title, trailing, chevron, warning, expanded)` (88-109) | `DotHeader` (GlowQuiet.swift:88) | ported |
| warning icon 20 in `MetricColors.warn`, 10 before the title (90-93) | the same (GlowQuiet.swift:105-110) | ported |
| trailing text 70%, 4 + a 16 caret at 70% (95-99) | the same (GlowQuiet.swift:116-125) | ported |
| expanded caret: 32 glass circle, 16 icon, turned 180° (100-107) | the same, turned without animating (GlowQuiet.swift:126-142) | ported: standing instruction 7 leaves rotation out of the moving properties, so the turn is instant |
| `"Hide"` / `"Show"` (105) | `.accessibilityLabel(expanded ? "Hide" : "Show")` (GlowQuiet.swift:141) | ported |
| `Modifier.glass(shape, onClick, scale)` (116-119) | `View.glass(_:)` + `GlassSurface` (GlowQuiet.swift:65-79) | ported |
| `GlassRowShape` 16 (122) | `glassRowShape` (GlowQuiet.swift:82) | ported |
| `DotDivider`, 1.5 wide, gap 6, white 16% (126-134) | `DotDivider` (GlowQuiet.swift:158) | ported |
| `DotArrow(up, color)`: five 1.3 dots in an 11×9 box (138-149) | `DotArrow` (GlowQuiet.swift:177) | ported |
| `DotLevel(color)`: three 1.3 dots, same footprint (153-161) | `DotLevel` (GlowQuiet.swift:211) | ported |
| `TileShape` 18 (163) | the tile's `RoundedRectangle(cornerRadius: 18)` (GlowQuiet.swift:240) | ported |
| `GlowTile(label, accent, onClick, modifier, content)`: white 5% fill, 8% hairline, 14/12 padding, 6pt dot (170-191) | `GlowTile` (GlowQuiet.swift:234) | ported |
| `StatusPill(text, color)`: labelSmall, 14% fill, capsule, 8/3 (195-200) | `StatusPill` (GlowQuiet.swift:322) | ported |
| `TileGrid(items, tile)`: two a row, 10 apart, equal heights (204-213) | `TileGrid` (GlowQuiet.swift:288) | ported with `Grid`, which gives a row equal cell heights |
| `DotStatTile(...)` (219-247) | `DotStatTile` (GlowQuiet.swift:343) | ported |
| `"—"` for a missing value (234) | `Text(value ?? "—")` (GlowQuiet.swift:362) | ported |
| `"usually $baseline"` (245) | `Text("usually \(baseline)")` (GlowQuiet.swift:379) | ported |
| trend arrow tints: `TrendGood` / `TrendBad` / white 60% (240) | `trendGood` / `trendBad` / white 60% (GlowQuiet.swift:350-353, 388-389) | ported |
| `TrendGood` `#3DDC97`, `TrendBad` `#FFB547` (249-250) | `MetricColors.good` / `MetricColors.warn` (Theme.swift:122-123) | ported: the same two values |

## `app/src/main/kotlin/org/openstrap/app/ui/components/Insights.kt` (card U2)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `NavItem(label, icon, selectedIcon = icon)` (52) | `NavItem` (FloatingNavBar.swift:13) | ported |
| `FloatingNavBar(items, selected, onSelect, modifier, glass)` (59-97) | `FloatingNavBar` (FloatingNavBar.swift:59) | ported |
| outer padding 20 horizontal, 10 vertical, capped at 440 (60-66) | the same (FloatingNavBar.swift:100-104) | ported |
| `hazeBlur` black glass: 24 radius, black, `#0B0B0D` at 70% (163-169) | `GlowGlassBackground`: `.ultraThinMaterial` over `#0B0B0D` at 70% (FloatingNavBar.swift:45-52) | ported: the haze library is Android-only; the material blur is the same effect |
| `GlassEdge` border, white 20% → 5% → black 40% (Glass.kt:39) | `glassEdge` (FloatingNavBar.swift:29) | ported |
| inner padding 6, `IntrinsicSize.Min` (73) | `padding(6)` and the cells' own height (FloatingNavBar.swift:82) | ported |
| one pill sliding to the selected tab: `(cell + gap) * selected`, `tween(240)` (77-85) | a capsule offset by the same amount (FloatingNavBar.swift:85-95) | ported |
| gap 2 between cells, each cell an equal share (77, 90) | the same (FloatingNavBar.swift:63, 66-72) | ported |
| `NavCell`: 24 icon, 1.08 scale when selected, labelSmall, white / 55% (147-160) | `NavCell` (FloatingNavBar.swift:171) | ported |
| `FloatingNavRail`: the same bar standing up, 40 corner, 92 wide with margins, 440 tall (104-143) | `FloatingNavRail` (FloatingNavBar.swift:111) | ported |
| `NavRailWidth` 92 (143) | `NavRailWidth` (FloatingNavBar.swift:168) | ported |

The card body also names "the insight bar" in this file; `Insights.kt` holds no such composable —
`FloatingNavBar`, `FloatingNavRail` and the private `NavCell` are the whole file, so there was
nothing else to port.

## `app/src/main/kotlin/org/openstrap/app/ui/dashboard/DayCalendar.kt` (card U2)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `DayPicker(available, onPick)` (54) | `DayPicker` (DayCalendarSheet.swift:18) | ported |
| `TodayDot` `#FF4D5E` (57) | `todayDot` (DayCalendarSheet.swift:14) | ported |
| `DayCalendarSheet(selected, available, onPick, onDismiss)` (64-127) | `DayCalendarSheet` (DayCalendarSheet.swift:26) | ported |
| the glass sheet: 28 top corners, `GlassTint` 72%, `GlassEdge`, 36×4 grab handle (71, 186-196 in Glass.kt) | the same, inline rather than in an overlay (DayCalendarSheet.swift:66-119) | ported |
| `"MMMM yyyy"` month title (77) | `monthTitle` (DayCalendarSheet.swift:263) | ported: a `DateFormatter` with the same pattern, so the locale names match |
| `"Previous month"` / `"Next month"` with the month bounds (79-84) | the same content descriptions, disabled at the first and last month (DayCalendarSheet.swift:149-167) | ported |
| weekday initials from the locale's first day of the week (88-95) | `weekdayInitials` (DayCalendarSheet.swift:246) | ported |
| `AnimatedContent` month change: slide a quarter width + fade, 220 ms (99-109) | `.move` + `.opacity` transition, 220 ms, nothing under Reduce Motion (DayCalendarSheet.swift:170-181, 271-278) | ported |
| `"Has data"` legend swatch, 12pt (111-117) | the same (DayCalendarSheet.swift:186-192) | ported |
| `"Today"` legend dot, 6pt (118-121) | the same (DayCalendarSheet.swift:193-198) | ported |
| `"Go to today"` when another day is selected (123) | the same (DayCalendarSheet.swift:200-213) | ported |
| `MonthGrid`: `lead = (dow - first + 7) % 7`, `(cells + 6) / 7` weeks, 4 apart (130-155) | `GlowGeometry.calendarLead` / `calendarWeekCount` / `calendarDayNumber` (GlowGeometry.kt:325-338) | ported |
| `DayCell`: 42 circle, selected fills white with black text, a day with data gets the container fill and a hairline, the rest are dimmed to 35% (158-192) | `DayCell` (DayCalendarSheet.swift:275) | ported |
| today's 5pt red dot 5 above the cell's bottom (185-190) | the same (DayCalendarSheet.swift:303-308) | ported |
| pickable when `date <= today && (date in available || date == today)` (148) | the same expression (DayCalendarSheet.swift:225-226) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/components/Charts.kt` — the helpers the glow charts call (card U2)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `Modifier.scrub(key, onScrub)`: horizontal drag or press-and-hold (66-81) | `GlowScrub`, a simultaneous drag so the list still scrolls (GlowCharts.swift:161) | ported: long-press is dropped, the sideways drag does the same job in SwiftUI |
| `smoothPath(pts)`, monotone cubic (Fritsch–Carlson) (288-319) | `GlowGeometry.smoothCubics` + `glowSmoothPath` / `glowAddCubics` (GlowGeometry.kt:278, GlowCharts.swift:895) | ported |
| `niceStep(raw)` (321-324) | — | not ported: only `LineChart` calls it, and the glow charts draw three fixed guides |
| `ChartPoint`, `ChartSegment`, `LineChart` (44-286) | `ChartPoint` (ChartPoints.kt:11) | `ChartPoint` ported as data (card U3); `ChartSegment` and `LineChart` not ported: the older screens' chart, and card U2 is the glow language's own charts |
| `withUnit(value, unit)` (84-88) | — | not ported: the glow charts print their own readouts and no glow card calls it |
| `hourLabel(epoch)` (47) | — | not ported: the glow charts take their axis labels as strings from the screen |
| `TrendPoint`, `StageSpan` (327-330) | `TrendPoint`, `StageSpan` (ChartPoints.kt:14,17) | ported for the data: card U3 copies both (with `ChartPoint`) into `shared/` because `DayUiState` holds them. The glow `GlowDay`/`GlowStage` still replace them as *drawing* types. |
| `ProgressRing(progress, color, size, stroke)` (334-345) | — | not ported: the glow screens draw `GlowArcGauge` instead; nothing in the listed files calls it |

## Card U3 — the state the screens read (Kotlin only)

U3 ports no Swift: it copies the state the Android screens are built from into `shared/`, so the
iPhone screens read the same objects instead of re-deriving them. The middle column therefore names
the shared Kotlin, with the card that will draw it in brackets; the Status column says whether the
thing exists yet or why not.

### `app/src/main/kotlin/org/openstrap/app/ui/dashboard/DayViewModel.kt`

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `SLEEP_NIGHTS = 14` (43) | `SLEEP_NIGHTS` (DayState.kt:27) | ported |
| `Baselines` — restingHr, hrv, spo2, stress, skinTemperature, breathRate (46-53) | `Baselines` (DayState.kt:30) | ported |
| `DayUiState` — date, steps, minuteSteps, hourlySteps, activeHours (55-61) | `DayUiState` (DayState.kt:39) | ported |
| `DayUiState` — heartRate, latestHeartRate, restingHeartRate, restingEstimated, restingTrend, hrvTrend (62-68) | `DayUiState` (DayState.kt:39) | ported |
| `DayUiState` — sleep, previousSleepScore, naps, napStages, napBreakdowns, sleepStages, sleepBreakdown (70-78) | `DayUiState` (DayState.kt:39); naps as lists (ScreenAccessors.kt:34,37) | ported |
| `DayUiState` — spo2, stress, hrv, skinTemperature, breathRate (79-83) | `DayUiState` (DayState.kt:39) | ported |
| `DayUiState` — pai, baselines (84-85) | `DayUiState` (DayState.kt:39) | ported |
| `DayUiState` — sleepSpo2, hrvSdnn, osaResult, osaEvents (86-93) | `DayUiState` (DayState.kt:39) | ported |
| `DayUiState` — dailyMaxHeartRate, distanceMeters, activeCalories, alerts (94-101) | `DayUiState` (DayState.kt:39) | ported |
| `DayUiState` — nights, sleepHeartRate (102-105) | `DayUiState` (DayState.kt:39); `nightList()` (ScreenAccessors.kt:44) | ported |
| `DayUiState` — dayStart, dayEnd, `asleepSeconds` (106-109) | `DayUiState` (DayState.kt:39) | ported |
| `SleepNight` (343-352) | `SleepNight` (DayState.kt:97) | ported |
| `stateFor(date)`: the activity, heart-rate and vitals combines (156-157, 259-305) | `DayStateLoader.load` (DayStateLoader.kt:47) | ported |
| `stateFor`: main sleep, naps and their stages (158-170) | `DayStateLoader.load` (DayStateLoader.kt:56-70) | ported |
| `stateFor`: skin temperature, breathing, PAI (171-173) | `DayStateLoader.load` (DayStateLoader.kt:72-74) | ported |
| `stateFor`: the week's resting-HR and HRV trends (174-191) | `DayStateLoader.load` (DayStateLoader.kt:76-95) | ported |
| `stateFor`: baselines over the seven days before (192-206) | `DayStateLoader.load` (DayStateLoader.kt:97-108) | ported |
| `stateFor`: sleep SpO2, SDNN, apnea, max HR, extended activity, alerts (208-225) | `DayStateLoader.load` (DayStateLoader.kt:110-124) | ported |
| `stateFor`: the fourteen nights and the night's heart rate (227-251) | `DayStateLoader.load` (DayStateLoader.kt:126-149) | ported |
| `stateFor`: the strap's live step total (254-257) | `load(date, todaySteps)` (DayStateLoader.kt:47) | ported: the flow became the argument Swift passes from `StrapEngine` |
| `daysWithData` (125-126) | `DayStateLoader.daysWithData` (DayStateLoader.kt:225) | ported |
| `recovery` (133-148), including `Log.w("OpenStrap", "recovery failed", e)` | `DayStateLoader.recovery` (DayStateLoader.kt:198) | ported: the log became a constructor callback |
| `MIN_MINUTES_FOR_ESTIMATE = 6 * 60` (310) | `MIN_MINUTES_FOR_ESTIMATE` (DayStateLoader.kt:230) | ported |
| `SLEEP_STAGE_ROWS` (312-317) | — | not ported: it is the hypnogram's lane order, and `GlowSleep` is Swift's (card U7) |
| `previousDay` / `nextDay` / `select` (120-122) | — | not ported: the selected date is Swift's `RootView` state (card U4) |
| `SleepParts`, `MoreParts` private carries (321-340) | `load`'s locals, `MoreParts` (DayStateLoader.kt:250) | ported |

### `app/src/main/kotlin/org/openstrap/app/ui/workout/WorkoutsViewModel.kt`

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `fallbackMaxHr`, the app's max-HR rule read once per profile (32-36) | `WorkoutsLoader.fallbackMaxHr` (WorkoutsLoader.kt:33) | ported |
| `workouts` list, newest first (39-43) | `WorkoutsLoader.workouts` (WorkoutsLoader.kt:43) | ported |
| `effects`, the sport-effect records (46-47) | `WorkoutsLoader.effects` (WorkoutsLoader.kt:62) | ported |
| `open`, the analysed workout with its stream (51-56) | `WorkoutsLoader.workout(start)` (WorkoutsLoader.kt:53) | ported |
| `open(start)` selection (49, 58) | — | not ported: which workout is open is Swift's navigation state (card U9) |

### `app/src/main/kotlin/org/openstrap/app/ui/dashboard/Routes.kt`

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `Metric.HeartRate("Heart rate", "bpm", 0)` (10) | `Metric.HeartRate` (Metric.kt:15) | ported |
| `Metric.RestingHr("Resting heart rate", "bpm", 0)` (11) | `Metric.RestingHr` (Metric.kt:15) | ported |
| `Metric.Hrv("Heart-rate variability", "ms", 0)` (12) | `Metric.Hrv` (Metric.kt:15) | ported |
| `Metric.Spo2("Blood oxygen", "%", 0)` (13) | `Metric.Spo2` (Metric.kt:15) | ported |
| `Metric.Stress("Stress", "", 0)` (14) | `Metric.Stress` (Metric.kt:15) | ported |
| `Metric.SkinTemperature("Skin temperature", "°C", 1)` (15) | `Metric.SkinTemperature` (Metric.kt:15) | ported |
| `Metric.Breathing("Breathing rate", "br/min", 0)` (16) | `Metric.Breathing` (Metric.kt:15) | ported |
| `Metric.Steps("Steps", "steps", 0)` (17) | `Metric.Steps` (Metric.kt:15) | ported |
| `Metric.format` — `%,d` for steps, `%.<decimals>f` otherwise (20) | `Metric.format` (Metric.kt:26) | ported |
| `LocalGoals` (24) | — | not ported: a `CompositionLocal` has no iOS equivalent; `ScreenQueries.goals()` reads the same values (ScreenQueries.kt:110) |
| `Detail.OfMetric` / `Detail.Workout` / `Detail.AllWorkouts` (28-31) | — | not ported: the details are SwiftUI navigation destinations (cards U4, U9) |
| `Detail.StackSaver`, `key`, `restore` — the "m:Hrv\|w:1792227300" string (35-54) | — | not ported: it exists to survive Android process death, which the Swift navigation stack does not use |

### `app/src/main/kotlin/org/openstrap/app/ui/dashboard/MetricInfo.kt`

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `Range` — Day, Week, Month, Year with 1, 7, 30, 365 days (13) | `Range` (Metric.kt:47) | ported |
| `DailyHistory` typealias (16) | — | not ported: a function type over a `Flow`; `ScreenQueries.history` replaces it (ScreenQueries.kt:82) |
| `colorOf` (18-25) | `Metric.color` → `MetricColor` (Metric.kt:33,36); Swift picks from `Theme.swift` | ported |
| `RELATED[Metric.HeartRate]` — RestingHr, Hrv, Stress (28) | `RELATED` (Metric.kt:50); `relatedMetrics()` (ScreenAccessors.kt:50) | ported |
| `RELATED[Metric.RestingHr]` — Hrv, HeartRate, SkinTemperature (29) | `RELATED` (Metric.kt:50) | ported |
| `RELATED[Metric.Hrv]` — RestingHr, Stress, Breathing (30) | `RELATED` (Metric.kt:50) | ported |
| `RELATED[Metric.Spo2]` — Breathing, RestingHr (31) | `RELATED` (Metric.kt:50) | ported |
| `RELATED[Metric.Stress]` — Hrv, HeartRate (32) | `RELATED` (Metric.kt:50) | ported |
| `RELATED[Metric.SkinTemperature]` — RestingHr, Breathing (33) | `RELATED` (Metric.kt:50) | ported |
| `RELATED[Metric.Breathing]` — Spo2, SkinTemperature, Hrv (34) | `RELATED` (Metric.kt:50) | ported |
| `RELATED[Metric.Steps]` — HeartRate (35) | `RELATED` (Metric.kt:50) | ported |
| `ABOUT[Metric.HeartRate]` — "How fast your heart beats, measured all day by the strap." / "Movement, stress, caffeine and heat raise it; rest and sleep bring it down." (40-41) | `ABOUT` (Metric.kt:62); `aboutMetric()` (ScreenAccessors.kt:53) | ported |
| `ABOUT[Metric.RestingHr]` — "Your heart rate at its calmest, …" / "It rises after hard training, late meals, alcohol, poor sleep or when your body is fighting something off, and falls as fitness builds." (44-45) | `ABOUT` (Metric.kt:62) | ported |
| `ABOUT[Metric.Hrv]` — "Heart-rate variability: how much the time between beats varies. …" / "Good sleep and rest raise it. Stress, hard training and alcohol lower it. Compare it with your own normal, not other people's." (48-49) | `ABOUT` (Metric.kt:62) | ported |
| `ABOUT[Metric.Spo2]` — "Blood oxygen: how much of your blood's capacity is carrying oxygen. Most people sit at 95–100%." / "Altitude and breathing pauses in sleep can lower it. A loose strap can also give low readings." (52-53) | `ABOUT` (Metric.kt:62) | ported |
| `ABOUT[Metric.Stress]` — "The strap's 0–100 stress level, …" / "Pressure, caffeine and too little sleep raise it; calm breathing, rest and time outdoors bring it down." (56-57) | `ABOUT` (Metric.kt:62) | ported |
| `ABOUT[Metric.SkinTemperature]` — "The temperature of your skin under the strap. …" / "It moves with alcohol, a warm room, hard training, the menstrual cycle, or when your body is fighting something off." (60-61) | `ABOUT` (Metric.kt:62) | ported |
| `ABOUT[Metric.Breathing]` — "How many breaths you take a minute while asleep. …" / "It can rise above your normal after a hard training day, in a warm, stuffy room, or when your body is fighting something off." (64-65) | `ABOUT` (Metric.kt:62) | ported |
| `ABOUT[Metric.Steps]` — "Steps counted by the strap through the day." / "Short walks spread across the day add up; most people aim for 7,000–10,000." (68-69) | `ABOUT` (Metric.kt:62) | ported |

### `app/src/main/kotlin/org/openstrap/app/MainActivity.kt` lines 140-260 (how the screens are fed)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `DayViewModel(...)`, one shared day (146) | `ScreenQueries.day(date:)` (ScreenQueries.kt:67); the shared date is `RootView`'s (card U4) | ported |
| `workoutsVm.workouts` (160) | `ScreenQueries.workouts` (ScreenQueries.kt:95) | ported |
| `workoutsVm.open`, `workoutsVm.effects` (161-162) | `ScreenQueries.workout(start:)` (ScreenQueries.kt:100); effects via `WorkoutsLoader.effects` | ported |
| `history = { m, from, to -> container.health.daily(m, from, to) }` (169) | `ScreenQueries.history(metric:range:date:)` (ScreenQueries.kt:82) | ported |
| `goals` (173) | `ScreenQueries.goals` (ScreenQueries.kt:110) | ported |
| `recovery` (181) | `ScreenQueries.recovery(date:)` (ScreenQueries.kt:72) | ported |
| `profile` (182) | `ScreenQueries.profile` (ScreenQueries.kt:106) | ported |
| `day` (183) | `ScreenQueries.day` (ScreenQueries.kt:67) | ported |
| `checkIn` (185) | `ScreenQueries.checkIn(date:)` (ScreenQueries.kt:115) | ported |
| `daysWithData` (186) | `ScreenQueries.daysWithData` (ScreenQueries.kt:88) | ported |
| `editGoals` → `settings.goals` (172-173) | `ScreenQueries.saveGoals` (ScreenQueries.kt:112) | ported |
| `saveProfile` (ProfileScreen) | `ScreenQueries.saveProfile` (ScreenQueries.kt:108) | ported |
| `saveCheckIn` (check-in card) | `ScreenQueries.saveCheckIn` (ScreenQueries.kt:120) | ported |
| `repo.changes().debounce(800)` behind `recovery` (131-148) | `ScreenQueries.changes` (ScreenQueries.kt:132) | ported |
| `todayTileNames`, `todayTiles` (174-180) | — | not ported: which metrics Today's "What changed" shows; no card asks for the picker yet |
| `strapReadiness` (184) | — | not ported: the strap's own readiness for calibration; no screen shows it yet |

## `app/src/main/kotlin/org/openstrap/app/MainActivity.kt` — the shell (card U4)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `Tab` enum, five entries (125-131) | `AppTab` (RootView.swift:4) | ported |
| `Tab.Today("Today", House, HouseFill)` (126) | `AppTab.today`, label/icon/selectedIcon (RootView.swift:5-38) | ported |
| `Tab.Recovery("Recovery", Plant, Plant)` (127) | `AppTab.recovery` | ported |
| `Tab.Sleep("Sleep", Moon, MoonFill)` (128) | `AppTab.sleep` | ported |
| `Tab.Activity("Activity", Footprints, Footprints)` (129) | `AppTab.activity` | ported |
| `Tab.You("You", User, User)` (130) | `AppTab.you` | ported |
| `YouPage` enum (134) | `YouRoute` (RootView.swift:45) | ported: Hub is the You list itself, as it is on Android; Alarms and Firmware have no iOS screens |
| one `DayViewModel` for the four dated tabs (146) | `HealthModel.date`, shared by the four `DayTab`s (HealthModel.swift:37) | ported: one selected day, reloaded on change |
| `tab` state (149) | `RootView.tab` (RootView.swift:60) | ported |
| `todayStack/activityStack/recoveryStack/sleepStack` (165-168) | each `DayTab`'s own `NavigationPath` (RootView.swift:141) | ported: a detail opens inside its tab |
| `showStrap`, `showAlarms`, `showFirmware`, `profileTab` (155, 171) | `YouRoute` path (RootView.swift:62) | ported for profile/strap/diagnostics/licences; alarms and firmware not ported (no iOS screen) |
| `openStrap` = You + `showStrap` (262) | `RootView.openStrap()` (RootView.swift:124) | ported |
| `LaunchedEffect(tab)` clears nothing; `selectTab` clears every stack (298-303) | nav bar sets `youPath = []` (RootView.swift:102-107) | ported: picking a tab drops that tab's detail |
| `FloatingNavBar(navItems, tab, selectTab)` (306) | `FloatingNavBar` (RootView.swift:102) | ported |
| `HorizontalPager` swipe between tabs (241-245, 355) | — | not ported: swipe-between-tabs is not part of this card's list, and iOS keeps the tab bar's tap |
| `rail` for windows wider than tall (296, 496) | — | not ported: iPhone only |
| `ListDetail`, `SideBySideWidth` (367, 405, 426, 449, 483) | `NavigationStack` per tab | ported: iPhone shows the detail full width; the side-by-side panes are a tablet layout |
| `BackHandler` (258, 261) | `NavigationStack` back, and the explicit `ArrowLeft` rows (ProfileScreen.swift:88, StrapView.swift:55) | ported: iOS's own back gesture and a visible back button |
| `recoveryFocus` (151) | — | not ported: Today's "open Recovery section" is a U5/U6 link |
| `pullToSync` wrapper over each tab (362-364) | `PullSyncScroll` (DateHeader.swift:46) | ported |
| `OledSurfaces` around the You tab (483) | — | not ported: Android's OLED scroll surface; iOS draws the same black directly |
| `GlassOverlay`/`GlassHost` (266, 500) | `.sheet` with a glass background | ported: the calendar and goals open over a glass surface |

## `app/src/main/kotlin/org/openstrap/app/ui/components/PullToSync.kt` (card U4)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `PullSyncState(fraction, syncing)` (55) | `PullSyncState` (DateHeader.swift:19) | ported |
| `LocalPullSync` (58) | `EnvironmentValues.pullSync` (DateHeader.swift:30) | ported |
| `PullToSync` box, `isRefreshing = pulled \|\| running` (104-123) | `PullSyncScroll` (DateHeader.swift:46) | ported with `.refreshable`; see the note below |
| `onRefresh` calls the strap's sync (107-115) | `.refreshable { onSync() }` (DateHeader.swift:76) → `controller.syncNow()` (RootView.swift:144) | ported |
| `"The strap isn't connected"` (110) | — | not ported: `StrapController.syncNow()` logs the reason instead of showing a toast |
| `REVEAL = 48.dp` (164) | `reveal: CGFloat = 48` (DateHeader.swift:91) | ported |
| `TICKS = 4` ratchet haptics (165, 93-102) | — | not ported: haptics are not in this card's list |
| `START_GRACE_MS = 3000` (166, 88) | — | not ported: `.refreshable` ends the gap when the sync's own state says done |
| `PullSyncReveal` (127) | `PullSyncReveal` (DateHeader.swift:87) | ported |
| height = `fraction * 48`, alpha = `fraction` (137, 143) | `.frame(height: fraction * reveal)`, `.opacity(alpha)` (DateHeader.swift:110-112) | ported |
| watch icon 18 pt, turns with the pull, spins while syncing (146-150) | 18 pt, `.rotationEffect(fraction * 180)`, an opacity pulse while syncing (DateHeader.swift:96-104) | ported with the motion rule: standing instruction 7 allows only offset, scale and opacity to animate, so the continuous spin is a pulse |
| `"Syncing strap…"` (153) | `label(ready:)` (DateHeader.swift:123) | ported |
| `"Release to sync strap"` (154) | same | ported |
| `"Pull to sync strap"` (155) | same | ported |
| tint: primary when ready or syncing, else onSurfaceVariant (149) | same (DateHeader.swift:103) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowLayout.kt` — `GlowHeader` (card U4 uses it)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `GlowHeader` title row (162-177) | `GlowHeader` (GlowLayout.swift:171, card U2) | ported: no new instance is added in U4; the You screens keep the U2 panes' own `DotHeader` |

## `app/src/main/kotlin/org/openstrap/app/ui/dashboard/ScreenParts.kt` — `DateHeader` (card U4)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `DateHeader` (101-173) | `DateHeader` (DateHeader.swift:133) | ported |
| `WordmarkDot = Color(0xFFFF4D5E)` (94) | `wordmarkDot` (DateHeader.swift:141) | ported |
| `"OpenStrap"` in MuseoModerno SemiBold 22 sp (113-124) | `wordmark` (DateHeader.swift:225) | ported |
| the coloured `"."` (116) | same | ported |
| strap chip: battery text, `"⚡$battery%"` (135) | `strapChip` (DateHeader.swift:232-266) | ported: `charging ? "⚡\(battery)%" : "\(battery)%"` |
| battery tint at ≤15% or charging (137) | same | ported through `MetricColors.battery` |
| watch icon 20 pt + 6 pt battery dot (141-147) | same | ported |
| `"Strap connected"` / `"Strap not connected"` (143) | `strapChip` accessibility label (DateHeader.swift:264) | ported |
| `"Open your strap"` click label (129) | `.accessibilityValue("Open your strap")` (DateHeader.swift:265) | ported |
| `PullSyncReveal()` under the wordmark (151) | `PullSyncReveal()` (DateHeader.swift:162) | ported |
| `"Previous day"` (153) | `date.previous` (DateHeader.swift:168-175) | ported |
| `"Pick a day"` (155) | `date.pick` (DateHeader.swift:177-193) | ported |
| `"Today"` (161) | `label(for:)` (DateHeader.swift:268) | ported |
| `"Yesterday"` (162) | same | ported |
| `date.format("EEE d MMM")` (163) | same, via `Locale.current` | ported |
| caret-down 14 pt beside the date (168) | same (DateHeader.swift:184) | ported |
| `"Next day"`, disabled on today (170) | `date.next`, `.disabled(isToday)` (DateHeader.swift:195-208) | ported |
| `DayCalendarSheet` opened from the date (110) | `.sheet` presenting `DayCalendarSheet` (DateHeader.swift:210-219) | ported (U2's sheet, unchanged) |
| `ScreenList` gutter 16, top 4, spacing 12 (69-88) | `.padding(.horizontal, 16)`, `spacing: 12` (RootView.swift:157-160) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/device/YouScreen.kt` (card U4)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `"You"` headline (63) | `you.title` (YouView.swift:26) | ported |
| profile panel (67-78) | `you.profile` panel (YouView.swift:31-50) | ported |
| `"Your profile"` (72) | same (YouView.swift:37) | ported |
| `profile.summary()` (73) | `ProfileRules.summary` (YouView.swift:41) | ported: the Kotlin function, copied from ProfileScreen.kt |
| goals panel (81-92) | `you.goals` panel (YouView.swift:52-70) | ported |
| `"Your goals"` (86) | same (YouView.swift:58) | ported |
| `goals.summary()` (87) | shared `Goals.summary()` (YouView.swift:62) | ported: already in shared Kotlin |
| `Group("Device")` (94) | `GroupLabel("Device")` (YouView.swift:73) | ported |
| `StrapCard(paired?.name, …)` (95, 139-179) | strap card (YouView.swift:75-102) | ported: iOS has no device name, so the title is Android's own `"Your strap"` when a key is stored |
| `"Pair your Helio Strap"` (146) | same (YouView.swift:80) | ported |
| `"Scan, or sign in with Zepp to fetch the key"` (147) | same (YouView.swift:91) | ported |
| `"Connected"` (152) | `statusText` (YouView.swift:189) | ported |
| `"Connecting…"` (153) | same | ported |
| `"Connection failed"` (154) | same | ported |
| `"Not connected"` (155) | same | ported |
| `"Charging $it%"` / `"$it%"` (163) | `statusLine` (YouView.swift:199) | ported |
| `"Syncing…"` (165) | same, from `controller.syncRunning` | ported |
| `"Synced ${clock(...)}"` (166) | `"Synced \(controller.lastSyncText)"` | ported: Android's `d MMM HH:mm` is iOS's `lastSyncText` |
| `" · "` between the status parts (169) | same | ported |
| `YouIcon` 52 pt circle, 26 pt white icon (196-201) | `YouIcon` (QuietRows.swift:47) | ported |
| `LinkRow` (182-192) | `LinkRow` (QuietRows.swift:138) | ported |
| `Group("Your data")` (97) | `GroupLabel("Your data")` (YouView.swift:105) | ported |
| `"Health data stays on this phone. Nothing leaves it unless you export it."` (101) | same (YouView.swift:108) | ported |
| `"Export CSV"` / `"A zip of every synced record"` (105) | — | not ported: iOS has no CSV path, and the card names only the Apple Health row |
| `"Import CSV"` / its subtitle (107) | — | not ported: same reason |
| `"Health Connect"` / `"Copy your data to Android Health Connect"` (109) | `"Apple Health"` / `"Copy your data to Apple Health"` (YouView.swift:112) | ported as the standing instruction's Apple Health exception; it drives `controller.setAppleHealth` |
| `"Restore from Health Connect"` (111) | — | not ported: no iOS import path |
| `Group("Support")` (115) | `GroupLabel("Support")` (YouView.swift:124) | ported |
| `DiagnosticsPanel(log, asRow = true)` (116, 441-482) | `you.diagnostics` row (YouView.swift:126-142) | ported as a row that opens the existing Diagnostics screen |
| `"Diagnostics"` (453) | same (YouView.swift:132) | ported |
| `"${lines.size} lines"` (453) | `"\(controller.logLines.count) lines"` (YouView.swift:137) | ported |
| `"Tap to hide"` / `"Hide"` (453, 456) | — | not ported: the row opens a screen instead of expanding in place |
| `"Copy log"` (461) | `Copy` in DiagnosticsView's toolbar (DiagnosticsView.swift:47) | ported (pre-existing screen) |
| `"No events yet."` (475) | `"Nothing logged yet."` (DiagnosticsView.swift:34) | not ported: the existing iOS diagnostics screen keeps its own wording |
| sample-data button (118-120) | — | not ported: `-demoData` is the iOS debug seed (card C6) |
| `"OpenStrap ${BuildConfig.VERSION_NAME} · local-first, no cloud"` (124) | `"OpenStrap \(appVersion) · local-first, no cloud"` (YouView.swift:161) | ported: the version comes from `CFBundleShortVersionString` |
| `"Licences"` row | `you.licences` (YouView.swift:145-158) | ported: the card's own exception; opens `LicencesView` (LicencesView.swift:6) with the three bundled texts |

## `app/src/main/kotlin/org/openstrap/app/ui/device/ProfileScreen.kt` (card U4)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `UserProfile.summary()` (56-63) | `ProfileRules.summary` (DeviceRules.kt:23) | ported into shared Kotlin, with JVM tests |
| `ProfileScreen` (72-216) | `ProfileScreen` (ProfileScreen.swift:20) | ported |
| `"Your profile"` (158) | same (ProfileScreen.swift:97) | ported |
| `"Save"` (159) | `profile.save` (ProfileScreen.swift:101-106) | ported |
| back arrow (157) | `profile.back` (ProfileScreen.swift:88-95) | ported |
| `"Age sets your maximum heart rate, …"` (166-168) | same (ProfileScreen.swift:113-116) | ported |
| `"Age"` / `"years"` (171) | `NumberField("Age", "years")` (ProfileScreen.swift:121-124) | ported |
| `SexToggle` female/male (173-174, 257-268) | `SexToggle` (ProfileScreen.swift:324) | ported |
| `"Female"`, `"Male"` (173-174) | same (ProfileScreen.swift:126-131) | ported |
| `"Units"` (177) | same (ProfileScreen.swift:136) | ported |
| `SegmentedTabs("Metric", "Imperial")` (178) | `SegmentedTabs` (ProfileScreen.swift:353) | ported |
| `"Metric"`, `"Imperial"` (178) | same | ported |
| `"Weight"`, `"lb"`/`"kg"` (180-183) | same (ProfileScreen.swift:141-147) | ported |
| `"Feet"`, `"Inches"`, `"Height"`, `"ft"/"in"/"cm"` (184-191) | same (ProfileScreen.swift:149-157) | ported |
| `"Usual activity (for the fitness estimate)"` (193) | same (ProfileScreen.swift:160) | ported |
| the 1-5 chips (194-201) | `ActivityChip` (ProfileScreen.swift:419) | ported |
| `Vo2max.ACTIVITY_LEVELS[i]` (203) | `Vo2max.shared.ACTIVITY_LEVELS` (ProfileScreen.swift:255) | ported: already shared Kotlin |
| `"Pick the level that best matches a usual week."` (203) | same (ProfileScreen.swift:258) | ported |
| `"Taking beta-blockers"` (207) | same (ProfileScreen.swift:173) | ported |
| `"Pregnant"` (208) | same (ProfileScreen.swift:174) | ported |
| Checkbox (244) | `PhosphorIcon.checkCircle` + tap (ProfileScreen.swift:386-412) | ported with a glyph: iOS has no square checkbox |
| `"Max heart rate from a test (optional)"`, `"bpm"` (209) | same (ProfileScreen.swift:175-180) | ported |
| `"Sleep need (7–9, blank for your age's default)"`, `"h"` (210) | same (ProfileScreen.swift:181-187) | ported |
| `NumberField` outline 14 pt, label, suffix, error state (219-239) | `NumberField` (ProfileScreen.swift:274) | ported: the label sits above the field, since SwiftUI's `TextField` has no floating label |
| `formatNumber` (250) | `ProfileRules` `formatNumber` (DeviceRules.kt:186) | ported into shared Kotlin |
| `FemaleColor`/`MaleColor` (252-253) | `ThemeColors.female`/`.male` (Theme.swift:224-225) | ported |
| `"Discard changes?"` (124) | same (ProfileScreen.swift:75) | ported |
| `"Your profile changes haven't been saved."` (125) | same (ProfileScreen.swift:79) | ported |
| `"Discard"`, `"Keep editing"` (126-127) | same (ProfileScreen.swift:76-77) | ported |
| the `valid` expression (101-102) | `ProfileRules.fields`/`valid` (DeviceRules.kt:37-82) | ported into shared Kotlin |
| `copy(birthYear, sex, weight, height, imperial, sleepNeed, maxHr, activity, activityAnsweredAt, bodyMeasuredAt, betaBlocker, pregnant)` (131-147) | `ProfileRules.toProfile` (DeviceRules.kt:115) | ported into shared Kotlin |
| `switchUnits` carrying entries across (104-113) | `switchUnits` (ProfileScreen.swift:216-229) | ported: the conversion itself is Kotlin (`weightText`/`feetText`) |

## `app/src/main/kotlin/org/openstrap/app/ui/device/GoalsDialog.kt` (card U4)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `GoalsDialog` (22-60) | `GoalsSheet` (GoalsSheet.swift:13) | ported as a sheet in the same glass |
| `"Your goals"` (36) | same (GoalsSheet.swift:39) | ported |
| `ChartLineUp` icon (35) | same (GoalsSheet.swift:31) | ported |
| `"What you aim for each day. Change them when they stop fitting."` (39) | same (GoalsSheet.swift:44) | ported |
| `"Steps"`, `"steps"` (40) | same (GoalsSheet.swift:48) | ported |
| `"Sleep"`, `"h"`, `""`, `"min"` (42-43) | same (GoalsSheet.swift:51-52) | ported |
| `"Active hours"`, `"of 24"` (45) | same (GoalsSheet.swift:55) | ported |
| `"An active hour is one with at least 250 steps."` (47) | same (GoalsSheet.swift:57) | ported |
| `"Save"`, `"Cancel"` (56-58) | same (GoalsSheet.swift:62-76) | ported |
| the ranges and `sleepValue` (28-31) | `GoalsRules.valid`/`toGoals` (DeviceRules.kt:199-220) | ported into shared Kotlin, with JVM tests |
| `Goals(steps, sleepMinutes, activeHours)` (55) | `GoalsRules.toGoals` (DeviceRules.kt:206) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/device/DeviceScreen.kt` — the strap screens, restyled (card U4)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `DeviceScreen` (72-193) | `StrapView` (StrapView.swift:9) | ported and restyled in `DotPanel`s; the logic is the iOS controller's own, unchanged |
| `"Your strap"` (137) | same (StrapView.swift:65) | ported |
| back arrow (136) | `strap.back` (StrapView.swift:55-63) | ported |
| `StatusHero` (197-260) | `statusHero` (StrapView.swift:73) | ported as a quiet panel |
| `"Connected to"`, `"Connecting to"`, `"Authenticating"`, `"Connection failed"`, `"Not connected"` (201-207) | `statusLabel` (StrapView.swift:219-228) | ported |
| `"Last sync"` (213) | same (StrapView.swift:93) | ported |
| `"Syncing…"` for a running sync (217) | same (StrapView.swift:94) | ported |
| battery ring / `"$it%"` / `"Battery"` / `"Charging"` (229-239) | battery text + `SectionLabel` (StrapView.swift:101-113) | ported without the ring: the ring is `ProgressRing`, which U4 doesn't port; the number and colour are the same |
| `"Firmware $it"` (246) | same (StrapView.swift:109) | ported |
| `"Sync now"` (155, 171) | same (StrapView.swift:124) | ported |
| `"Disconnect"` and its dialog (89-109, 157-163) | — | not ported: the iOS controller has no disconnect, and this card changes no device logic |
| `"Connect"` (172) | `"Connect"` on the pairing panel (PairView.swift:74) | ported |
| `"Cancel"` while connecting (167) | — | not ported: no iOS mid-connection cancel |
| `"Alarms & alerts"` (178) | — | not ported: no iOS alarms screen |
| `"Firmware update"` (182) | — | not ported: no iOS firmware install |
| `"Forget this strap"` (186) | same (StrapView.swift:138) | ported |
| `"Forget this strap?"` (115) | same (StrapView.swift:39) | ported |
| the forget dialog's body (119-121) | same (StrapView.swift:42-48) | ported |
| `"Forget"`, `"Cancel"` (125-129) | same (StrapView.swift:40-41) | ported |
| `PairPanel` (263-315) | `PairPanel` (PairView.swift:31) | ported with iOS's own key entry |
| `"Pair your Helio Strap"` (281) | same (PairView.swift:43) | ported |
| `"OpenStrap needs the strap's auth key, …"` (282-285) | same (PairView.swift:46-49) | ported |
| `"Sign in with Zepp"` (308) | same (PairView.swift:96) | ported: opens the existing `ZeppSignInView` |
| the three pair steps (286-300) | — | not ported: the Zepp steps describe Android's scan-and-pair flow; iOS pairs with a pasted key or the Zepp sheet |
| `"Enter the key yourself"` / `ManualPairDialog` (310, 344-423) | `PairPanel`'s key field (PairView.swift:59-78) | ported as the screen's only manual entry: no scan, no address field (no iOS scan API in this card) |
| `KeyRejectedPanel` (427-437) | `pairError` (PairView.swift:81-85) | ported as the controller's error line |
| `DiagnosticsPanel(lines, asRow = false)` (441-482) | `morePanel` (StrapView.swift:190-215) | ported as a row that opens the existing `DiagnosticsView` |
| `clock(epochSeconds)` `"d MMM HH:mm"` (484-485) | `StrapController.lastSyncText` | ported (pre-existing) |

## Not in this card

The debug Design lab (`DesignLabView.swift`, `-designLab`) has no Compose counterpart: Android has
no design lab screen, and U1 needs one to put every token, card and motion in front of a screenshot.
Card U2 adds the chart, gauge, hypnogram, parts, quiet, tab bar and calendar sections to it with
fixed sample data. Its section titles and sample values are debug-only and never appear in a release
build; the panel titles and metric names inside those samples (`Heart rate`, `Resting heart rate`,
`Steps`, `Skin temperature`, `Recovery`, `Blood oxygen`, `HRV`, `Resting`, `Normal`, `Higher`,
`Lower`, `Awake`, `REM`, `Light`, `Deep`) are copied from `Routes.kt`, `GlowRecoveryScreen.kt` and
`ScreenParts.kt` rather than written for the lab.

The three font files and the three licence files are copies, not ports; they are logged in
`docs/ios/UPSTREAM.md`.
