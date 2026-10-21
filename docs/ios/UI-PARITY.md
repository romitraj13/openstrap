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
| `size(pitch * columns - gap, pitch * ROWS - gap)` (77) | `size` (DotMatrixText.swift:56) | ported |
| `contentDescription = text` (78) | `.accessibilityLabel(text)` (DotMatrixText.swift:82) | ported |
| `drawCircle(color, d / 2, …)` (86) | `context.fill(Path(ellipseIn:), with: .color(color))` (DotMatrixText.swift:69) | ported |
| glyph advance `(rows[0].length + 1) * step` (89) | `x0 += CGFloat(rows[0].count + 1) * pitch` (DotMatrixText.swift:79) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowParts.kt` — the rest of the file

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `ArcMark`, `ArcTimeline` (105-137) | — | not ported: card U2 ports the chart parts |
| `GlowSwitch` (145-164) | — | not ported: card U2 ports the quiet parts |

## Not in this card

The debug Design lab (`DesignLabView.swift`, `-designLab`) has no Compose counterpart: Android has
no design lab screen, and U1 needs one to put every token, card and motion in front of a screenshot.
Its labels are debug-only.

The three font files and the three licence files are copies, not ports; they are logged in
`docs/ios/UPSTREAM.md`.
