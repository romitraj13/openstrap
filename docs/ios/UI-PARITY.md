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
| `GlowCard(style, modifier, onClick, contentPadding, content)` (92) | `GlowCard` (GlowCard.swift:194), `init(_:contentPadding:content:)` (211) | ported: `onClick` is not in U1 — no card is tappable yet (U2/U3 bring the screens' taps) |
| `shape = RoundedCornerShape(style.radius)` (99) | `GlowCard.shape` (GlowCard.swift:232) | ported |
| `palette = remember(style) { GlowPalette(style) }` (100) | `GlowPalette(style)` inside the renderer (GlowCard.swift:482) | ported |
| `slab = lerp(palette.mid, Black, 0.55f)` (101) | `GlowPalette.slab` (GlowCard.swift:393) | ported (card F6): Compose's `lerp` mixes in Oklab, so this does too |
| per-card `grainSeed` (103) | `GlowStyle.grainSeed` (GlowStyle.swift:73) | ported, from the style rather than a per-instance random so the rendered layers stay cacheable by style and size |
| `CoreDrift(Random)` (104) | `CoreDrift(seed:)` + `GlowRandom` (GlowCard.swift:22, 119) | ported |
| `animate` from `ANIMATOR_DURATION_SCALE` (108) | `@Environment(\.accessibilityReduceMotion)` (GlowCard.swift:301) | ported: iOS's equivalent switch |
| time only advances while unpaused (114-127) | `TimelineView(.animation(paused:))` + `elapsed(at:)` (GlowCard.swift:350, 365) | ported |
| cache key `(style.copy(motion = Still), size, density)` (131) | `GlowLayerCache.Key(style:width:height:scale:)` (GlowCard.swift:416) | ported (card F6): the key also carries the look's resolved sRGB colour, so two looks at one size are two entries |
| `produceState` / render off the main thread (132-137) | `load()` + `Task.detached` (GlowCard.swift:374) | ported (card F6: the layers' view takes the key as its identity, so a look that changes loads its own layers instead of drawing the previous look's) |
| slab `drawBehind` for `depth` (144-147) | `GlowCard.slab` (GlowCard.swift:268), as the card's background | ported (card F6): a copy of the card offset down by `depth` (`6.dp` on Today's overview, GlowTodayScreen.kt:240), behind the card and outside its clip. Compose's `drawBehind` gives it no say in the layout, so this adds no height either: the 6 pt band shows in the gap under the card |
| `clip(shape)` (149) | `.clipShape(shape)` (GlowCard.swift:257) | ported |
| `drawBitmap(b.base)` (156-159) | `Image(uiImage: layers.base)` (GlowCard.swift:320) | ported |
| `CoreLayer` (163, 173-191) | `core(_:)` (GlowCard.swift:349) | ported: translation, scale, transform origin, alpha |
| `Box(Modifier.padding(contentPadding), content)` (165) | `content().padding(contentPadding)` (GlowCard.swift:240) | ported |
| overlay: grain + hairlines (167, 196-210) | `overlay(...)` (GlowCard.swift:679) | ported |
| `GlowBitmaps` (213) | `GlowLayers` (GlowCard.swift:401) | ported |
| `GlowCache` LRU, 10 entries (224-241) | `GlowLayerCache`, 10 entries (GlowCard.swift:409) | ported |
| `render()` at half resolution (244-297) | `GlowRenderer.render` (GlowCard.swift:482), core at `scale * 0.5` (633) | ported |
| `halfResBitmap(scale 0.5, margin)` (300-313) | `GlowRenderer.format(scale:)` + `core` margin (GlowCard.swift:498, 620) | ported |
| `fillShader`: rim melt, 12 eased steps, reach 0.4, `LightFrom.All` flat (320-332) | `GlowRenderer.fill` (GlowCard.swift:548) | ported |
| `insetShadow(size, radius, dy, blur, spread, color)` (338-351) | `GlowRenderer.insetShadow` (GlowCard.swift:585) | ported: same even-odd path, `blur / 2` as the sigma |
| `blurFilter(sigma)` (354-355) | `GlowRenderer.gaussianBlur` (GlowCard.swift:773) | ported with Core Image; the effective sigma is `blur - 0.5` as in the mask filter |
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
| `ScreenList`'s `contentPadding.bottom + 24.dp` under the floating bar (`ScreenParts.kt:80-84`), which every pushed Compose screen gets from `Scaffold(bottomBar = …)`'s `padding` (`MainActivity.kt:304-311`) | `GlowLayout.barClearance` = `navBarHeight` 88 + `navBarGap` 24 (GlowLayout.swift:279-291), applied by `View.glowClearsNavBar()` (GlowLayout.swift:293-309) on the tab roots, ProfileScreen, StrapView, PairView, LicencesView and DiagnosticsView (card F4) | ported; `GoalsSheet` is a sheet over the shell and does not scroll under the bar, so it takes none |
| gutter `max((w - 1040)/2, tight ? 16 : 20)` and tight = wide or < 400 (104-106) | the same (GlowLayout.swift:91-97) | ported |
| runs of left/right blocks in two columns, full blocks spanning; a lopsided run stacks (117-127) | `sections` + `sectionView` (GlowLayout.swift:135-168) | ported |
| `scrollTo` animates to the block's key (110-113) | `ScrollViewReader` + `onAppear` / `onChange` (GlowLayout.swift:113-124) | ported |
| `GlowHeader(title, trailing, chevron, trailingContent)` (162-177) | `GlowHeader` (GlowLayout.swift:171) | ported |
| `GlowHeroNumber(value, unit, facts, side)` (184-193) | `GlowHeroNumber` (GlowLayout.swift:225) | ported: `Row` measures its unweighted number with the whole remaining width and leaves the rest to the `weight(1f)` unit text, so the Swift `HStack` gives the same priority to the `DotMatrixText` (`.layoutPriority(1)`, card F8: without it the number took a share of the row and `.clipped()` cut its last digit) |
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
| `"Today"` (161) | `label(for:today:calendar:)` (DateHeader.swift:280) | ported: the day is compared with the day the app shows as today (`ScreenNow`, card F9) in the day's own zone — not with the system clock, which called the pinned demo day "Yesterday" |
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
| `"${lines.size} lines"` (453) | `"1 line"` / `"N lines"`, `StrapController.lineCountText` (StrapController.swift:264), on the You row (YouView.swift:136) and the Diagnostics log's heading (DiagnosticsView.swift:32-43) | ported with the **third** allowed exception to standing instruction 4 (product decision, 2026-10-22): iOS pluralises the count — "1 line" for one, "N lines" otherwise, wherever it is shown. Android's `${lines.size} lines` is a plural bug, not wording, so there is no string to copy |
| `"Tap to hide"` / `"Hide"` (453, 456) | — | not ported: the row opens a screen instead of expanding in place |
| `"Copy log"` (461) | `Copy` in DiagnosticsView's toolbar (DiagnosticsView.swift:47) | ported (pre-existing screen) |
| `"No events yet."` (475) | `"Nothing logged yet."` (DiagnosticsView.swift:44) | not ported: the existing iOS diagnostics screen keeps its own wording |
| sample-data button (118-120) | — | not ported: `-demoData` is the iOS debug seed (card C6) |
| `"OpenStrap ${BuildConfig.VERSION_NAME} · local-first, no cloud"` (124) | `"OpenStrap \(appVersion) · local-first, no cloud"` (YouView.swift:161) | ported: the version comes from `CFBundleShortVersionString` |
| `"Licences"` row | `you.licences` (YouView.swift:145-158) | ported: the card's own exception; opens `LicencesView` (LicencesView.swift:6) with the three texts U1 put in `Design/Licenses/` |

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

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayScreen.kt` (card U5)

Today's list is `TodayList` (:136). Every block is drawn in the same order; the layout hints
(`left`/`right`) only matter at 600 pt and wider, which the app's portrait-only window never
reaches, so iOS stacks them (`TodayScreen.swift:110`) exactly as Compose's phone path does.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `GlowTodayScreen(state, liveHeartRate, battery, …, checkIn, onCheckIn)` (64-103) | `TodayScreen(controller:health:onOpenTab:)` (TodayScreen.swift:58), its data from `HealthModel.reloadToday` (HealthModel.swift:119) | ported |
| `LocalGlowPaused provides { list.isScrollInProgress }` (100) | not applied: the scroll view belongs to `DayTab` (card U4), and `glowScrollPhasePause()` needs the `ScrollView` itself | not ported: the glow keeps moving while Today scrolls; every card still stops under Reduce Motion (`GlowCard.swift:215`) |
| `full { DateHeader(…) }` (138) | `DateHeader` in `DayTab` (RootView.swift:157) | ported in U4 |
| `OverviewCard` (143, 205-269) | `OverviewCard` (TodayScreen.swift:257) | ported |
| `Score("Charge", …, "since waking")` (215) | `TodayGlow.overview` labels (TodayGlow.kt:151), drawn by `ScoreBar` (TodayScreen.swift:371) | ported |
| `Score("Recovery", …, "vs yesterday")` (218-223) | the same | ported |
| `Score("Sleep", …, "vs night before")` (224) | the same | ported |
| `chargeLeads(state, rec)` (143) | `TodayGlow.chargeLeads` (TodayGlow.kt:290) | ported |
| `readinessState(r, estimate, short)` (287-301) | `TodayGlow.readinessState` (TodayGlow.kt:217) | ported |
| `"Learning N/14 nights"` (289), `"No sleep yet"` (290), `"Learning N/7 nights"` (291), `"Needs HRV data"` (292) | `TodayGlow.readinessState` (TodayGlow.kt:217-231) | ported |
| `"Strap's estimate"`, `"Strap's estimate, N/7 nights"`, `"Strap's estimate, no HRV yet"` (296-299) | the same | ported |
| `Change(score, withNote)` — arrow, level dots, amount (304-317) | `Change` (TodayScreen.swift:333) | ported |
| `ScoreBar` — label, caret, value, change, `GlowBar`, note (323-350) | `ScoreBar` (TodayScreen.swift:371) | ported |
| `overviewColor(score, strain)` (357-363) | `MetricColors.overview(_:)` (Theme.swift:170); the thresholds are `TodayGlow.overviewTone` (TodayGlow.kt:236) | ported (card F6): the switch is on the Kotlin enum, not on `tone.name` — `GlowCardTests.testEveryOverviewToneGetsTheAndroidColour` holds all five cases to Android's literals |
| `GlowStyle.Bloom.copy(… coreShape Radial, breath 5/min, depth 6.dp)` (236-241) | `OverviewCard.style` (TodayScreen.swift:264) | ported |
| `"Heart rate"` (374) | `HeartCard` (TodayScreen.swift:657) | ported |
| `"Latest"` (375) | the same (`trailing:`) | ported |
| `"Live"` + `GlowSwitch` (377-384) | `HeartCard` + `GlowSwitch` (TodayScreen.swift:661-672), from `StrapController.setLiveHeartRate` | ported |
| `"bpm"` (387) | `GlowHeroNumber(unit: "bpm")` (TodayScreen.swift:675) | ported |
| `"Resting N"` (387) | `TodayGlow.heartFacts` (TodayGlow.kt:411) | ported |
| `"No heart-rate data yet. Sync your strap."` (390) | `HeartCard` (TodayScreen.swift:679) | ported |
| `bucket(points, range, 5 * 60)` (189-198) | `TodayGlow.bucket` (TodayGlow.kt:134), via `heartBuckets` (:399) | ported |
| `GlowLineChart(values, ChartHeight, HOURS)` + readout `"N bpm · HH:mm"` (392-393) | `GlowLineChart` (TodayScreen.swift:683) + `heartReadout` (TodayGlow.kt:403) | ported |
| `HOURS = 00/06/12/18/24` (184) | `HeartCard.hours` (TodayScreen.swift:638), `ActivityCard.hours` (:713) | ported |
| `WhatChanged` (413-448) | `WhatChangedPanel` (TodayScreen.swift:542) | ported |
| `"What changed"` (434) | the same (:554) | ported |
| `"vs. your last 7 days"` (437) | the same (:560) | ported |
| `advice` = `Narrative.readinessBody` (160, 439) | `advice()` (TodayScreen.swift:216) with `Narrative.shared` | ported |
| `EditPill` — sliders icon, `"Edit"` (450-463) | `EditPill` (TodayScreen.swift:604) | ported |
| `TileGrid` of `DotStatTile` (441-443) | `TileGrid` + `DotStatTile` (TodayScreen.swift:571) | ported |
| `Signal("Resting HR", …, "bpm", 0, 1.5f, false)` (416) | `TodayGlow.signalTiles` (TodayGlow.kt:324) | ported |
| `Signal("HRV", …, "ms", 0, 3f, true)` (417) | the same | ported |
| `Signal("Blood oxygen", …, "%", 0, 1f, true)` (418) | the same | ported |
| `Signal("Skin temp", …, "°C", 1, 0.3f, null)` (419-422) | the same | ported |
| `Signal("Stress", …, "", 0, 4f, false)` (423) | the same | ported |
| `Signal("Breathing", …, "/min", 0, 1f, null)` (424) | the same | ported |
| `"Your normal builds over the first week of wear."` (444-446) | `WhatChangedPanel` (TodayScreen.swift:584) | ported |
| `avg()` over the charts' points (ScreenParts.kt:191) | `TodayGlow.signalTiles`' private `avg` (TodayGlow.kt:341) | ported |
| `ActivityGlow` (466-485) | `ActivityCard` (TodayScreen.swift:708) | ported |
| `"Activity"` + chevron (472) | the same (:728) | ported |
| `"Goal reached"` / `"%d%% of %,d"` (474) | `TodayGlow.activityFacts` (TodayGlow.kt:389) | ported |
| `"steps"` (476) | `GlowHeroNumber(unit: "steps")` (TodayScreen.swift:731) | ported |
| `"Active N of M h"` (477) | `TodayGlow.activityFacts` (TodayGlow.kt:389) | ported |
| `"PAI N"` (477) | the same | ported |
| `GlowDotBars(hourlySteps, …)` + `"HH:00–HH:00 · N steps"` (480-482) | `GlowDotBars` (TodayScreen.swift:735) + `hourStepReadout` (TodayGlow.kt:395) | ported |
| `SleepPanel` (488-516) | `SleepCard` (TodayScreen.swift:755) | ported |
| `"Sleep"` + `"HH:mm – HH:mm"` + chevron (492) | `SleepCard` (:764) + `sleepRange` (TodayGlow.kt:435) | ported |
| `"%d:%02d"` asleep + `"asleep"` (494-495) | `asleepClock` (TodayGlow.kt:417) + `GlowHeroNumber` (TodayScreen.swift:770) | ported |
| `"Score N"` + `Narrative.sleepHeadline` (496) | `TodayGlow.sleepFacts` (TodayGlow.kt:420) | ported |
| `GlowSleepStages(state.sleepStages, ChartHeight)` (499-502) | `GlowSleepStages` (GlowSleepStages.swift:39) | ported |
| naps line `"N naps · duration · HH:mm, …"` (503-513) | `TodayGlow.napsLine` (TodayGlow.kt:424) | ported |
| `TodayWorkouts` — `"Workouts"` + `WorkoutLine` (174-180) | `TodayWorkouts` (TodayScreen.swift:799) + `WorkoutLine` (GlowWorkoutRow.swift:14) | ported |
| `state.alerts.isNotEmpty()` → `AlertsPanel` (150) | `TodayScreen.content` (TodayScreen.swift:126) | ported |
| `history != null && isRecapDay(state)` → `WeeklyRecapPanel` (153) | `recap(_:)` (TodayScreen.swift:223) + `isRecapDay` (TodayGlow.kt:603) | ported |
| `morning = date == LocalDate.now() && hour < 12` (146) | `TodayGlow.isMorning` (TodayGlow.kt:269); the clock is `TodayFlags.now` (TodayScreen.swift:33) so the UI test can pin both orders | ported |
| `hasSleep = asleepSeconds != null \|\| naps.isNotEmpty()` (156) | `TodayScreen.content` (TodayScreen.swift:103) | ported |
| `chargeNow.minus(atWake)`, `readinessNow.minus(before)` (215-224, 271) | `TodayGlow.overview` (TodayGlow.kt:151); `readinessBefore` (:296) | ported |
| `onOpenRecovery(RecoverySection)` from the card and the bar (242, 264) | `onOpenTab(.recovery)` (TodayScreen.swift:112-115) | ported as the tab tap; the section it lands on is not: card U6 found the tab's scroll view belongs to `DayTab`/`PullSyncScroll`, shared by the four dated tabs, so `GlowRecoveryScreen.kt:70`'s `focus` has no hook yet (U6's own section, below) |
| `onOpenSleep` from the Sleep bar and card (265, 490) | `onOpenTab(.sleep)` (TodayScreen.swift:115, 177) | ported |
| `onOpenActivity` from the Activity card (466) | `onOpenTab(.activity)` (TodayScreen.swift:160) | ported |
| `onOpenMetric(metric)` from the heart card, tiles and alerts (74, 163, 367) | `TodayRoute.metric(name)` → `MetricDetailPlaceholder` (TodayScreen.swift:928) | ported as the route: card U9 replaces the screen the route pushes |
| `onOpenWorkout(start)` from the workouts rows (79, 178) | `TodayRoute.workout(start)` → `MetricDetailPlaceholder` (TodayScreen.swift:246) | ported as the route: card U9 replaces the screen |
| `left`/`right` layout hints, two columns at 600 pt (141-170) | stacked in the same order (TodayScreen.swift:110) | not ported: the two-column branch; the app declares portrait only, so it never reaches 600 pt |
| `contentPadding` from the shell | `DayTab`'s `.glowClearsNavBar()` (RootView.swift:176, card F4) | ported in U4/F4 |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowTodayExtras.kt` (card U5)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `alertText` codes 1-5 and the fallback (40-47) | `TodayGlow.alertLines` (TodayGlow.kt:502) | ported |
| `"High heart rate: N bpm"`, `"Low heart rate: N bpm"`, `"High stress: N"`, `"Low blood oxygen: N%"`, `"Irregular heart rhythm"`, `"Alert from the strap"` (41-46) | the same (:506) | ported |
| `AlertsPanel` — `"Strap alerts"`, `"1 today"`/`"N today"`, warning dot (51-63) | `AlertsPanel` (TodayScreen.swift:483) | ported |
| alert rows sorted newest first, opening `Metric` 3 → Stress, 4 → Spo2, else HeartRate (54-57) | `TodayGlow.alertLines` (TodayGlow.kt:506-515), `onTapGesture` (TodayScreen.swift:503) | ported |
| `"If you feel unwell, checking in with a healthcare professional may be helpful. " + DISCLAIMER` (61) | `TodayGlow.alertsDisclaimer` (TodayGlow.kt:521) | ported |
| `clockOf` for an alert's time (65) | `TodayGlow.clock` (TodayGlow.kt:624) | ported |
| `WeeklyRecapPanel` (74-123) | `WeeklyRecapPanel` (TodayScreen.swift:514) | ported |
| `"Your week"` + `"d MMM – d MMM"` (97) | `DotHeader` (TodayScreen.swift:519) + `TodayGlow.shortDate` (TodayGlow.kt:616) | ported |
| `Fact("Steps a day", …)` (100) | `TodayGlow.weeklyRecap` (TodayGlow.kt:531) + `Fact` (GlowRecoveryParts.swift:76) | ported |
| `Fact("Recovery", …)` (101) | the same | ported |
| `Fact("Asleep a night", …)` (104) | the same | ported |
| `Fact("Workouts", n)` (105) | the same | ported |
| `"Steps held steady"`, `"Steps up N%"`, `"Steps down N%"` (111) | `weeklyRecap` (TodayGlow.kt:531-596) | ported |
| `"sleep about the same"`, `"N more sleep a night"`, `"N less sleep a night"` (115) | the same | ported |
| `"Comparisons with the week before start once there are two weeks of data."` (119) | the same | ported |
| `"… than the week before."` (120) | the same | ported |
| `isRecapDay(state)` — Monday (126) | `TodayGlow.isRecapDay` (TodayGlow.kt:600) | ported |
| `TODAY_TILES` order (131) | `TodayGlow.TODAY_TILES` (TodayGlow.kt:121), `allTodayTiles()` (:303) | ported |
| `TilesDialog` — `"What changed shows"` (135-158) | `TilesSheet` (TodayScreen.swift:819) | ported as a sheet, the way U4 ports `GoalsDialog`; the same strings and the same "at least one stays on" rule |
| `Checkbox` per tile with `Metric.title` (142-152) | `CheckBox` (TodayScreen.swift:901) + `Text(metric.title)` (:837) | ported: the tick is drawn with shapes, since the icon set has no checkbox |
| `"Save"` (155) | `Button("Save")` (TodayScreen.swift:861) | ported |
| `"Cancel"` (157) | `Button("Cancel")` (TodayScreen.swift:850) | ported |
| stored tile names, unknown ones dropped, empty means all (MainActivity.kt:176-182) | `TodayGlow.todayTiles` (TodayGlow.kt:313), kept in the app's defaults by `HealthModel.saveTodayTiles` (HealthModel.swift:163) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowCheckIn.kt` (card U5)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `CheckInPanel(feeling, onCheckIn)` (24-35) | `CheckInPanel` (TodayScreen.swift:432) | ported |
| `"Morning check-in"` + `CheckIn.label` (26) | the same (:438) | ported |
| `"How do you feel this morning?"` (27) | the same (:439) | ported |
| `(CheckIn.MIN..MAX)` pills, tapping the chosen one clears it (29-31) | `ForEach(1...5)` (TodayScreen.swift:443) + `HealthModel.saveCheckIn` (HealthModel.swift:147) | ported |
| `ChoicePill` — white when selected, black text (39-49) | `ChoicePill` (TodayScreen.swift:457) | ported |
| `"1 is poor, 5 is great."` (33) | `TodayGlow.checkInNote` (TodayGlow.kt:614) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/dashboard/NapsPanel.kt` (card U5)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `NapsPanel(naps, onClick)` (21-27) | — | not ported: this composable is not called by any live screen (GlowSleepScreen.kt:300 has its own naps panel for the Sleep tab); the naps Today shows are its sleep card's line (`GlowTodayScreen.kt:503-513`), ported as `TodayGlow.napsLine` (TodayGlow.kt:424) |
| `PanelHeader("Naps", Moon, sleepLight, chevron)` (23) | — | not ported: the same reason as the panel above — Today has no separate naps panel, as its Compose source has none |
| `"HH:mm – HH:mm · duration"` per nap (25) | `TodayGlow.napsLine` (TodayGlow.kt:424) carries the same figures into Today's sleep card | ported where Today shows naps |

## `app/src/main/kotlin/org/openstrap/app/ui/dashboard/ScreenParts.kt` (card U5's part)

`DateHeader`, `ScreenList` and `avg` were ported by U4; card U5 adds the rule that decides which
score leads and the average behind What changed's statistics.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `chargeLeads(state, rec)` — today, with a value, three hours after waking or from noon (183-189) | `TodayGlow.chargeLeads` (TodayGlow.kt:250, ISO and state forms at :276 and :290) | ported |
| `READINESS_HOURS_AFTER_WAKING = 3` (176) | `TodayGlow`'s private constant (TodayGlow.kt:125) | ported |
| `List<ChartPoint>.avg()` (191) | `TodayGlow.signalTiles`' private `avg` (TodayGlow.kt:341) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/components/Insights.kt` (card U5's reading of it)

The card names this file as Today's "insight bar". The file is `FloatingNavBar`/`FloatingNavRail`/
`NavItem` — already ported by U2 (`Design/FloatingNavBar.swift`), and its rows are in the U2 section
above. There is no insight component in the Compose source: the only insight text on Today is the
advice line inside What changed (`Narrative.readinessBody`, `GlowTodayScreen.kt:160`), which card U5
ports there.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `FloatingNavBar`, `FloatingNavRail`, `NavItem`, `NavCell`, glass constants (52-173) | `FloatingNavBar.swift` | ported in U2 |
| no insight component exists in this file (or in Today's other sources) | the insight is `Narrative.readinessBody` (TodayScreen.swift:216) | not ported: there is no such element to port |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowSleepScreen.kt` — what Today shares (card U5)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `SleepGlow = GlowStyle.Midnight.copy(color = sleepDeep, core 0.5/0.6 1.1×0.9, Breath(5))` (108-113) | `GlowStyle.sleep` (GlowSleepStages.swift:17) | ported |
| `GlowSleepStages(spans, height)` (175-192) | `GlowSleepStages` (GlowSleepStages.swift:39) | ported |
| `DayViewModel.SLEEP_STAGE_ROWS` — Awake 0, REM 1, Light 2, Deep 3 (178) | `TodayGlow.sleepStageChart` (TodayGlow.kt:475) | ported |
| `SLEEP_NAMES` — `"Awake"`, `"REM"`, `"Light"`, `"Deep"` (ScreenParts.kt:60-65) | `TodayGlow.sleepStageChart` names (TodayGlow.kt:481) | ported |
| stage fractions of the night, `start`/`end` clocks (179-186) | the same (:482-492) | ported |
| readout `"<stage> · HH:mm–HH:mm · N min"` (187-190) | the same (:486) | ported |
| `GlowHypnogram` drawing | `GlowHypnogram` (GlowCharts.swift:677, U2) | ported in U2 |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt` — what Today shares (card U5)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `GlowBar(fraction, color)` — track, fill, knob (272-290) | `GlowBar` (GlowRecoveryParts.swift:14) | ported |
| `Note(text)` (295) | `QuietNote` (GlowRecoveryParts.swift:74) | ported |
| `Fact(label, value)` (457-462) | `Fact` (GlowRecoveryParts.swift:76) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowActivityScreen.kt` — what Today shares (card U5)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `WorkoutLine(a, showDate, onClick)` — glass row, sport name, caret (403-422) | `WorkoutLine` (GlowWorkoutRow.swift:14) | ported |
| `"HH:mm"` / `"EEE d MMM · HH:mm"` + duration + `"avg N bpm"` + `"N kcal"` (410-418) | `TodayGlow.workoutDetail` (TodayGlow.kt:445) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowCard.kt` — the card's own inset (card U5)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `contentPadding: PaddingValues = LocalCardPadding.current` (96) | `GlowCard`'s `contentPadding ?? cardPadding` (GlowCard.swift:244); the inset comes from `glowCardPadding`, which `GlowScreenList` sets and Today sets to `wideCardPadding` (TodayScreen.swift:79) | ported |
| the card measures from its content (`Column(Modifier.padding(contentPadding))` inside a `Box` that wraps the content, 108-160) | `GlowCard(sizesToContent: true)` (GlowCard.swift:245-252) draws the glow behind the content, so the content sets the height; Today's four cards use it (TodayScreen.swift:287, 656, 726, 762). The older `GeometryReader` form is kept for the Design lab, which imposes `frame(height: 160)` | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/theme/Theme.kt` — the colours Today names (card U5)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `overviewColor`'s four literals `0xFF6E7280`, `0xFFF2A33A`, `MetricColors.hrv`, `0xFFF0605A` (GlowTodayScreen.kt:357-363) | `MetricColors.overview(_:)` (Theme.swift:170) | ported, and pinned case by case in `GlowCardTests.testEveryOverviewToneGetsTheAndroidColour` (card F6) |
| What changed's accents — heart, hrv, spo2, temperature, stress, sleepLight (GlowTodayScreen.kt:416-424) | `MetricColors.signal(_:)` (Theme.swift:177) | ported |
| the three score accents — chargeDeep, readinessDeep, SleepGlow's sleepDeep (GlowTodayScreen.kt:215-224) | `MetricColors.today(_:)` (Theme.swift:191) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowRecoveryScreen.kt` (card U6)

The Recovery tab, section by section in Compose's order (the list, `:76-95`): `DateHeader`,
`BodySignalsPanel`, `RecoveryCard`, `ContributorsPanel`, `ChargeCard`, `ChargeFlowPanel`,
`SignalsPanel`, `HowItWorksPanel`. On a phone every block stacks in that order, and the two-column
`left`/`right` panes are not reached (portrait only), as on Today.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `RecoverySection { Recovery, Charge }` (54) | `TodayRoute`'s metric push is the only cross-tab hook iOS has; the section a Today tap targets is not carried (see the `focus` row) | not ported: the tab's scroll view belongs to `DayTab`/`PullSyncScroll`, shared by the four dated tabs; Today's taps still open the tab (RootView.swift:85-88) |
| `GlowScreenList(contentPadding, list, scrollTo = focus, onScrolled = onFocused)` (76) | the tab's list is `DayTab`'s `PullSyncScroll` + a `VStack` spaced `GlowLayout.columnGap` (RecoveryScreen.swift:95), with `glowCardPadding` set to `wideCardPadding` (RecoveryScreen.swift:90) | ported, except `scrollTo`/`focus` (row above) |
| `full { DateHeader(...) }` (77) | `DateHeader` in `DayTab` (RootView.swift:164-172), ported by card U4 | ported |
| `recovery?.takeIf { it.date == state.date }` (78) | `RecoveryGlow.forDay` (RecoveryGlow.kt:190), called at RecoveryScreen.swift:94 | ported |
| `DotPanel { Text("Calculating…", bodyMedium, white 75%) }` (79-81) | `DotPanel` + `Text("Calculating…")` (RecoveryScreen.swift:119-124) | ported |
| Body-signal alert leads when `state != Normal` (87-88) | `if body.state.name != "Normal"` → `BodySignalsAlertPanel` (RecoveryScreen.swift:98-100) | ported |
| `BodySignalsCopy.TITLE` "Body signals off baseline" (178) | `BodySignalsModel.title` (RecoveryGlow.kt:152), drawn at RecoveryScreen.swift:209 | ported |
| `trailing = if (alert) null else "Watching"` (178) | `RecoveryGlow.bodySignals(...).trailing` (RecoveryGlow.kt:522), drawn at RecoveryScreen.swift:209 | ported |
| `warning = alert` (178) | `warning: model.alert` (RecoveryScreen.swift:209) | ported |
| the signals sentence: `<Signals, capitalised>. ` + alert's `CAUSES ADVICE`, else "One signal is outside your usual range. OpenStrap is keeping an eye on it." (179-183) | `RecoveryGlow.bodySignals` (RecoveryGlow.kt:520-528) | ported |
| `Note("Likely cause: ${causes.joinToString(", ")}.")` (184) | `BodySignalsModel.cause` (RecoveryGlow.kt:529) | ported |
| `Note(BodySignalsCopy.DISCLAIMER)` "This is a wellness estimate, not a medical assessment." (185) | `BodySignalsModel.disclaimer` (RecoveryGlow.kt:530) | ported |
| `RecoveryCard` glow: `GlowStyle.Bloom` + radial core 0.5/0.8, 0.95×0.75, blur 30, tone 16, shift −10, glow 0.6, bevel 0.6, `Breath(5f)`, `depth = 6.dp` (109-114) | `RecoveryScoreCard.style` (RecoveryScreen.swift:138-156); the slab itself is card F6's in `GlowCard.swift` | ported |
| `GlowHeader("Recovery", trailing = …)` (117) | `GlowHeader("Recovery", …)` (RecoveryScreen.swift:160) | ported |
| trailing: `"Learning ${n}/${SCORED_NIGHTS} nights"` / `"Learning ${n}/${SETTLED_NIGHTS} nights"` / `"Calibrated · " + vsYesterday` / `"Calibrated"` / `vsYesterday` / null (119-127) | `RecoveryGlow.trailing` (RecoveryGlow.kt:244-254), in `RecoveryCardModel.trailing` | ported |
| `"Steady vs yesterday"`, `"+4 vs yesterday"`, `−3 vs yesterday` (218-219) | `RecoveryGlow.vsYesterday` (RecoveryGlow.kt:267) | ported |
| "Recovery is scored each morning from the night's sleep. Wear the strap to bed." (134) | `noScoreBody`, first branch (RecoveryGlow.kt:258) | ported |
| "OpenStrap scores Recovery once it has 7 good nights of your own HRV and resting heart rate." (135-136) | `noScoreBody`, withheld branch (RecoveryGlow.kt:259-261) | ported |
| "Last night's HRV and resting heart rate didn't have enough clean readings, so there's no Recovery score today. Sleep alone isn't readiness." (137) | `noScoreBody`, else branch (RecoveryGlow.kt:262-265) | ported |
| `Note("Sleep ${sleep.score} · ${sleep.detail}")` (141) | `RecoveryCardModel.sleepNote` (RecoveryGlow.kt:210) | ported |
| `Fact("Strap's estimate", "$strapScore")` while `showStrapEstimate` (145) | `RecoveryCardModel.strapValue` (RecoveryGlow.kt:211), drawn with the label at RecoveryScreen.swift:188 | ported |
| strap parts: `"HRV 70 · resting HR 66 · temperature 60 · breathing 55"` + ". Shown for reference; it never enters OpenStrap's score." (146-150) | `RecoveryCardModel.strapNote` (RecoveryGlow.kt:212-216) | ported |
| `Note("HRV ${it.toInt()} ms (strap fallback)")`, `Note("Resting HR ${it.toInt()} bpm (strap fallback)")` (152-153) | `RecoveryCardModel.notes` (RecoveryGlow.kt:217-222) | ported |
| `GlowHeroNumber("$score", "/ 100", listOf(Narrative.readinessHeadline(score, strainAlert)))` (156) | `GlowHeroNumber(...)` (RecoveryScreen.swift:163); the headline is `RecoveryCardModel.heroFacts` (RecoveryGlow.kt:229) | ported |
| `Spacer(12)` + `Text(Narrative.readinessBody(ready), white 85%)` (157-158) | `Color.clear.frame(height: 12)` + the body text (RecoveryScreen.swift:164-170); the sentence is `heroBody` (RecoveryGlow.kt:230) | ported |
| `GlowChartGap()` + `GlowTrend(days, height = ChartHeight)` when any day has a score (159-165) | `GlowChartGap` + `GlowTrend` (RecoveryScreen.swift:171-178); `hasTrend`/`trend` (RecoveryGlow.kt:231-232, 271) | ported |
| `GlowDay(dayOfWeek SHORT take(2), v?.toFloat())` (162) | `RecoveryTrendDay.label` (RecoveryGlow.kt:271-274), the `WEEKDAYS` table cut to two letters | ported |
| `ChargeCard(c, dayStart..dayEnd)` (91) | `RecoveryChargeCard` (RecoveryScreen.swift:271), model from `RecoveryGlow.chargeCard` (RecoveryGlow.kt:282) | ported |
| `HOURS = 00, 06, 12, 18, 24` (101) | `CHARGE_HOURS` (RecoveryGlow.kt:178), `ChargeCardModel.labels` | ported |
| Charge glow: `GlowStyle.Ember` + `chargeAt(now)` + `Breath(4f)` (193) | `RecoveryChargeCard.style` (RecoveryScreen.swift:275-280) | ported |
| `GlowHeader("Charge", trailing = "$atWake at waking")` (196) | `ChargeCardModel.trailing` (RecoveryGlow.kt:291), drawn at RecoveryScreen.swift:291 | ported |
| "Charge starts after your first night wearing the strap. Sleep is what fills it, so it needs one to measure from." (198) / "Charge appears once the strap has a few hours of heart rate." (199) (200-205) | `ChargeCardModel.hint` (RecoveryGlow.kt:285-290), drawn at RecoveryScreen.swift:293-296 | ported |
| `GlowHeroNumber("${now.toInt()}", "/ 100", listOf(chargeHeadline, "${signed(recharged,+1)} · ${signed(drained,-1)}"))` (207-208) | `ChargeCardModel.value/unit/facts` (RecoveryGlow.kt:292-301), drawn at RecoveryScreen.swift:298 | ported |
| `"−0" reads oddly, so zero prints "0"` (251-254) | `RecoveryGlow.signed` (RecoveryGlow.kt:334) | ported |
| `GlowLineChart(bucket(points, day, 900), height = ChartHeight, labels = HOURS, showAverage = false, yRange = 0..100, readout "${v.toInt()} · ${clock(day.first + i*15*60)}")` (210-214) | `ChargeCardModel.values` (RecoveryGlow.kt:308) + `GlowLineChart` (RecoveryScreen.swift:302-313) + `chargeReadout` (RecoveryGlow.kt:312) | ported |
| `bucket(points, range, seconds)` — 15-minute averages, empty buckets as gaps (222-231) | `RecoveryGlow.bucket` (RecoveryGlow.kt:322) | ported |
| `ChargeFlowPanel` shown when `charge.latest != null` (92) | `if rec.charge.latest != nil` → `ChargeFlowPanel` (RecoveryScreen.swift:108-110) | ported |
| `DotHeader("Where your charge went")` (239) | `DotHeader("Where your charge went")` (RecoveryScreen.swift:325) | ported |
| rows: "Sleep and calm rest", "Movement and exercise", "Physiological stress (estimate)", "Being awake", each `signed` and a fraction of `max(recharged, drain, 1)` (241-244) | `RecoveryGlow.flow` (RecoveryGlow.kt:341-357), drawn at RecoveryScreen.swift:326-338 | ported |
| `GlowBar(fraction, color)` per row (263) | `GlowBar` (GlowRecoveryParts.swift:14), colours from `RecoveryColors.flow` (RecoveryScreen.swift:37) | ported |
| `Note("Highest ${highest.toInt()} · lowest ${lowest.toInt()} this day")` (245) | `ChargeFlowModel.highLow` (RecoveryGlow.kt:361-365) | ported |
| `Note(Narrative.stressThreshold(threshold))` (246) | `ChargeFlowModel.stressNote` (RecoveryGlow.kt:366) | ported |
| `ContributorsPanel` shown when `contributors.isNotEmpty()` (90) | `if !ready.contributors.isEmpty` (RecoveryScreen.swift:102-104) | ported |
| `DotPanel(onClick = if (folds) toggle else null)` (306) | `DotPanel(onClick: unfold)` (RecoveryScreen.swift:237) | ported |
| `DotHeader("What drove it", trailing = "Top 3 of N", expanded = open)` (307) | `DotHeader("What drove it", …)` (RecoveryScreen.swift:238); `trailing`/`foldable` (RecoveryGlow.kt:404-405) | ported |
| groups `Body` / `Sleep` / `Load`, uppercase, `DotDivider()` between them (302-313) | `ContributorGroup.name/afterDivider` (RecoveryGlow.kt:396-402), drawn at RecoveryScreen.swift:239-246 | ported |
| rows: factor label, `Narrative.ratingFor(score).label` in its colour, `Note(detail)`, `GlowBar(score/100f, rating.color)` (316-325) | `ContributorRow` (RecoveryGlow.kt:114) + `SignalTileView`-style row (RecoveryScreen.swift:247-262); colours from `RecoveryColors.rating` (RecoveryScreen.swift:48) | ported |
| fold: `size > CONTRIBUTORS_FOLDED`, `sortedByDescending { weight }.take(3).toSet()` (297-300, 339) | `RecoveryGlow.contributors` (RecoveryGlow.kt:367-372), `CONTRIBUTORS_FOLDED` (RecoveryGlow.kt:181) | ported |
| `Note("Capped: one body signal is far from your normal, so good sleep can't fully make up for it.")` (327) | `ContributorsModel.notes` (RecoveryGlow.kt:406-410) | ported |
| `Note("7-night HRV is $word your normal range" + " (thin: N of 7 nights).")` (328-331) | `RecoveryGlow.contributors` (RecoveryGlow.kt:411-415) | ported |
| fallback notes with ": our own reading wasn't clean enough last night." (332-333) | `RecoveryGlow.contributors` (RecoveryGlow.kt:416-421) | ported |
| `Note("Check: $it")` per quality flag (334) | `RecoveryGlow.contributors` (RecoveryGlow.kt:422) | ported |
| `SignalsPanel` → `DotHeader("Body signals", trailing = "vs. your normal")` (390) | `DotHeader("Body signals", trailing: "vs. your normal")` (RecoveryScreen.swift:356) | ported |
| `TileGrid(signals)` two columns, `GlowTile(label, accent, onClick, …)` (391, 411) | `TileGrid` + `GlowTile` (RecoveryScreen.swift:357-359, 391) | ported |
| tiles: "Resting HR", "HRV", "Skin temp", "Breathing", "Blood oxygen", "Stress" (374-382) | `RecoveryGlow.signals` (RecoveryGlow.kt:430-470) | ported |
| value + unit on the number's baseline, unit at 60% (413-421) | `SignalTileView` (RecoveryScreen.swift:392-404) | ported |
| `StatusPill(word ?: "Normal", TrendGood/TrendBad, alpha 0 when no word)` (423-426) | `SignalTileView` (RecoveryScreen.swift:405-408) | ported |
| word: `1 -> up`, `-1 -> down`, `0 -> "Normal"`, else null; Faster/Slower for resting HR (405-410, 374) | `RecoveryGlow.build` (RecoveryGlow.kt:474-505) | ported |
| skin temperature as `±deviation` from the normal, `m.format(abs(d))` (399-404) | `RecoveryGlow.build` (RecoveryGlow.kt:490-497) | ported |
| `Sparkline(s.week, accent, height 22, top 10)` (428) | `Sparkline` (GlowRecoveryParts.swift:99), drawn at RecoveryScreen.swift:411-412 | ported |
| `Sparkline` itself: dots joined by a faint line, gaps, last day at full colour (434-452) | `Sparkline` (GlowRecoveryParts.swift:99-140) | ported |
| `Note("Your normal builds over the first week of wear; until then each signal shows its week.")` (392) | `SignalsModel.note` (RecoveryGlow.kt:466-471) | ported |
| a tile opens its metric (`onOpenMetric`) (391) | `route = .metric(metric.name)` → `MetricDetailPlaceholder` (RecoveryScreen.swift:113-116, 85-92) | ported as a placeholder: card U9 builds the real detail |
| `HowItWorksPanel` (466-496) | `HowItWorksPanel` (RecoveryScreen.swift:425) | ported |
| `DotPanel(onClick = { open = !open })` + `DotHeader("How it works", expanded = open)` (468-470) | `DotPanel(onClick:)` + `DotHeader("How it works", expanded: open)` (RecoveryScreen.swift:431-433) | ported |
| "Each morning OpenStrap compares your night … one signal far from normal caps it." (471-476) | `HowItWorksModel.explainer` (RecoveryGlow.kt:542-546) | ported |
| "Charge is a running battery: sleep and calm rest fill it, movement, stress and simply being awake drain it." (477-480) | `HowItWorksModel.chargeExplainer` (RecoveryGlow.kt:547-548) | ported |
| `Fact("The strap's own score", "$strapScore")` / `Fact("Ours", "$ours")` (484-488) | `HowItWorksModel.strapLabel/strapValue/oursLabel/oursValue` (RecoveryGlow.kt:549-552), drawn at RecoveryScreen.swift:437-446 | ported |
| "The strap's score doesn't count sleep length … once there are a couple of weeks to compare." (489-492) | `HowItWorksModel.strapNote` (RecoveryGlow.kt:553-557) | ported |
| `Note(BodySignalsCopy.DISCLAIMER)` (494) | `HowItWorksModel.disclaimer` (RecoveryGlow.kt:558) | ported |
| "Training load" (the card body's section) | — | not ported: no such section exists in this file — the panel title lives only in `GlowActivityScreen.kt:272` (card U8); the Load factor shows in "What drove it" (`Recovery.Factor.PreviousDayLoad`) and in the How it works paragraph, both ported above |
| `GlowBar` (272-290) | `GlowBar` (GlowRecoveryParts.swift:14) | ported by card U5 |
| `Note(text)` (292) | `QuietNote` (GlowRecoveryParts.swift:74) | ported by card U5 |
| `Fact(label, value)` (457-462) | `Fact` (GlowRecoveryParts.swift:76) | ported by card U5 |

## `app/src/main/kotlin/org/openstrap/app/ui/dashboard/ChargeCard.kt` (card U6's reading of it)

The Recovery tab draws the glow `ChargeCard` inside `GlowRecoveryScreen.kt:191`, not this dashboard
`Panel`, and words the at-waking note "at waking" where this file says "Woke at". The parts of this
file that do reach the tab are ported from the glow card; the rest belongs to the non-glow dashboard
iOS does not have.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `PanelHeader("Charge", Lightning, chargeAt(latest), trailing = "Woke at N")` (75) | `GlowHeader("Charge", trailing: "N at waking")` (RecoveryScreen.swift:291, from `RecoveryGlow.chargeCard`) | ported in the glow card's wording, per GlowRecoveryScreen.kt:196 |
| `EmptyHint("Charge starts after your first night wearing the strap. …")` (77-80) | `ChargeCardModel.hint` (RecoveryGlow.kt:286-288) | ported |
| `EmptyHint("Charge appears once the strap has a few hours of heart rate.")` (81-84) | `ChargeCardModel.hint` (RecoveryGlow.kt:289) | ported |
| `BigValue("${now.toInt()}", "/ 100", caption = chargeHeadline)` + Recharged / Drained (85-90) | `ChargeCardModel.value/unit/facts` (RecoveryGlow.kt:292-301) | ported: one hero with "+41 · −33", as the glow card draws it (GlowRecoveryScreen.kt:208) |
| `LineChart(points, chargeAt(now), dayRange, height = 130.dp, band = sleepBand, segments = chargeSegments(points), valueGradient = chargeScale)` (91-95) | `GlowLineChart` over `ChargeCardModel.values`, height `GlowLayout.chartHeight` (RecoveryScreen.swift:302-313) | not ported: this file's chart carries the sleep band, the state strip and the value gradient; the Recovery tab's chart is the glow one (`GlowRecoveryScreen.kt:211`: no band, no segments, `yRange = 0..100`), which is what Swift draws |
| `chargeSegments(points)` — 15-minute most-common state, joined (49-61) | — | not ported: only the dashboard chart draws the strip (`:93`); the glow chart does not take segments |
| `STATE_COLORS` per state (35-42) and `LegendDot("Sleep"/"Calm"/"Active"/"Physiological stress")` (96-101) | — | not ported: the legend belongs to the dashboard Panel, which no live screen calls; the Recovery tab's Charge card has no legend (GlowRecoveryScreen.kt:191-216) |
| `HowItWorks()` and `ExplainerSection` (105-160) | — | not ported: private to this file and not called from it; the Recovery tab's "How it works" is GlowRecoveryScreen.kt:466-496, ported above |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowQuiet.kt` — the tiles Recovery draws with (card U6)

`DotPanel`, `DotHeader`, `GlowTile`, `TileGrid`, `StatusPill`, `DotDivider`, `glass` and `DotStatTile`
were ported by card U2 (`GlowQuiet.swift`). Card U6 only consumes them; no rows change. The one
element the Recovery screen brings into Swift from elsewhere in that file's language is `Sparkline`,
which lives in `GlowRecoveryScreen.kt:434` and is filed under that section above.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `GlowTile(label, accent, onClick, modifier, content)` (170-191) | `GlowTile` (GlowQuiet.swift:234) | ported by U2, used for the six tiles (RecoveryScreen.swift:391) |
| `TileGrid(items, tile)` (204-213) | `TileGrid` (GlowQuiet.swift:288) | ported by U2, used at RecoveryScreen.swift:357 |
| `StatusPill(text, color)` (195-200) | `StatusPill` (GlowQuiet.swift:322) | ported by U2, used at RecoveryScreen.swift:405 |
| `DotDivider()` (126-134) | `DotDivider` (GlowQuiet.swift:158) | ported by U2, used between contributor groups (RecoveryScreen.swift:241) |
| `DotHeader(title, trailing, chevron, warning, expanded)` (88-109) | `DotHeader` (GlowQuiet.swift:88) | ported by U2; Recovery uses `trailing`, `warning` and `expanded` |
| `glowInk`-based text colours: title white, trailing 70%, group label 55% (94-95, 314) | `glowInk`, `.opacity(0.7)`, `.opacity(0.55)` (RecoveryScreen.swift:244, 248-250) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowSleepScreen.kt` (card U7)

The Sleep tab. Every string and every section of the Compose file, in the order `:81-101` builds
them. The numbers, sentences, shares and words come from `SleepGlow` and `SleepPanels` in shared
Kotlin (both with JVM tests in `SleepGlowTest`); `SleepScreen.swift` draws them.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `GlowScreenList` with `LocalGlowPaused` (81) | DayTab's `PullSyncScroll` (RootView.swift:160) | ported by U4; pause comes from `GlowScrollPhasePause` in GlowCard.swift |
| `full { DateHeader(...) }` (82) | `DateHeader` (RootView.swift:164) | ported by U4 |
| `left { NightCard(state) }` (85) | `NightCard` (SleepScreen.swift:125), first section (SleepScreen.swift:85) | ported |
| `state.sleepBreakdown?.let { left { StagesPanel(b) } }` (87) | `StagesPanel` (SleepScreen.swift:179), drawn when `day.sleepBreakdown != nil` (SleepScreen.swift:87-89) | ported |
| `session.score != null && contributors.isNotEmpty() → ContributorsPanel` (92) | `SleepContributorsPanel` (SleepScreen.swift:214), drawn when `SleepGlow.contributors` is non-nil (SleepScreen.swift:90-92) | ported; not reachable from `-demoData` (see below) |
| `sleepVitals(state).isNotEmpty() → left { VitalsPanel }` (93-94) | `SleepVitalsPanel` (SleepScreen.swift:243), drawn when the list is non-empty (SleepScreen.swift:93-97) | ported |
| `right { NightChartsPanel(state, session) }` (95) | `NightChartsPanel` (SleepScreen.swift:295), drawn when `SleepGlow.nightCharts` is non-nil (SleepScreen.swift:98-100) | ported |
| `if (rec?.regularity != null) right { SleepHealthPanel }` (97) | `SleepHealthPanel` (SleepScreen.swift:326) behind `rec.regularity` (SleepScreen.swift:101-103) | ported |
| `rec?.breathingDisturbances?.let { right { BreathingCard } }` (98) | `SleepBreathingPanel` (SleepScreen.swift:361) (SleepScreen.swift:104-106) | ported |
| `if (state.naps.isNotEmpty()) right { NapsPanel }` (99) | `SleepNapsPanel` (SleepScreen.swift:381) behind `!day.naps.isEmpty` (SleepScreen.swift:107-111) | ported; not reachable from `-demoData` |
| `full { WeekPanel(state) }` (100) | `SleepWeekPanel` (SleepScreen.swift:474) (SleepScreen.swift:112) | ported |
| `SleepGlow = GlowStyle.Midnight.copy(...)`, breathing at 5/min (108-112) | `GlowStyle.sleep` (GlowSleepStages.swift:15) | ported by U5 (Today's sleep card shares it) |
| `GlowCard(SleepGlow, fillMaxWidth)` + `GlowHeader("Sleep", trailing = "${clock(start)} – ${clock(end)}")` (125-127) | `GlowCard(GlowStyle.sleep, sizesToContent: true)` + `GlowHeader("Sleep", trailing:)` (SleepScreen.swift:130-133) | ported |
| `"No sleep recorded for this night. Wear the strap to bed and sync in the morning."` (131) | `SleepGlow.hero.emptyText` (SleepGlow.kt:150), drawn at SleepScreen.swift:136 | ported |
| `"No night sleep recorded, only naps."` (132) | `SleepGlow.hero.emptyText` (SleepGlow.kt:152) | ported |
| `Spacer(Modifier.height(GlowLayout.TitleGap))` before the empty sentence (129) | `Color.clear.frame(height: GlowLayout.titleGap)` (SleepScreen.swift:135) | ported |
| `Box(fillMaxWidth, padding top 6, BottomCenter) { GlowHorizon(score/100 ?: asleep/goal, White, 100.dp); DotMatrixText(score ?: "H:MM", bottom 6, dot 5.5, gap 2) }` (140-146) | `ZStack(alignment: .bottom)` + `GlowHorizon(progress:color:height: 100)` + `DotMatrixText(_, dot: 5.5, gap: 2)` (SleepScreen.swift:142-149) | ported |
| `"Sleep score"` / `"asleep · goal ${formatDuration(goal)}"` (147-150) | `SleepGlow.hero.label` (SleepGlow.kt:157), drawn at SleepScreen.swift:150 | ported |
| `Spacer(Modifier.height(14.dp))` (151) | `Color.clear.frame(height: 14)` (SleepScreen.swift:157) | ported |
| `Narrative.sleepHeadline(score)` / `"${formatDuration(asleep)} asleep"` (152-155) | `SleepGlow.hero.headline` (SleepGlow.kt:158), drawn at SleepScreen.swift:158 | ported |
| `"${asleep} asleep · ${in bed} in bed · woke N×"` (156-163) | `SleepGlow.hero.facts` (SleepGlow.kt:161-165), drawn at SleepScreen.swift:162 | ported |
| `GlowChartGap()` + `GlowSleepStages(state.sleepStages, GlowLayout.ChartHeight)` (165-168) | `GlowChartGap()` + `GlowSleepStages(spans:height:)` (SleepScreen.swift:167-168) | ported by U5 |
| `StagesPanel` → `DotPanel` + `DotHeader("Stages")` (192-198) | `StagesPanel` → `DotPanel` + `DotHeader("Stages")` (SleepScreen.swift:179-184) | ported |
| `SLEEP_NAMES.forEach` in Awake, REM, Light, Deep order, `SLEEP_COLORS[stage]` dot (203-208) | `SleepGlow.stageRows` order and accents (SleepGlow.kt:227-241), dot at SleepScreen.swift:188 | ported |
| `formatDuration(secs)` and `"${secs * 100 / total}%"` (210-214) | `SleepStageRow.value` / `.percent` (SleepGlow.kt:238-239), drawn at SleepScreen.swift:196-203 | ported |
| `GlowBar(secs / total, color)` (216) | `GlowBar(fraction:color:)` (SleepScreen.swift:205) | ported |
| `ContributorsPanel`: `DotHeader("What shaped the score")` (222-224) | `SleepContributorsPanel` (SleepScreen.swift:214-219) | ported |
| `Text(c.label)` + `Text(c.rating.label)` in the rating's colour (226-230) | SleepScreen.swift:222-229, colour from `MetricColors.rating` (Theme.swift:178) | ported |
| `Note(c.detail)` + `GlowBar(c.fraction, rating colour)` (231-232) | `QuietNote(text: row.detail)` + `GlowBar` (SleepScreen.swift:231-232) | ported |
| `Note("Ratings use general sleep guidance (7+ hours, …)")` (235) | `SleepContributorsModel.note` (SleepGlow.kt:265-266), drawn at SleepScreen.swift:235 | ported |
| `VitalsPanel`: `DotHeader("Sleep vitals", trailing = "During sleep")` + `TileGrid` (240-246) | `SleepVitalsPanel` (SleepScreen.swift:243-257) | ported |
| `VitalTile`: value `titleLarge` padding top 8 (249-251) | `Text(vital.value)` (SleepScreen.swift:276-278) | ported |
| `if (v.isOff) StatusPill(note.removeSuffix(" than usual"), TrendBad)` else `Text(note, 55%)` (252-255) | `vital.pill` → `StatusPill(_, color: trendBad)` / `vital.note` (SleepScreen.swift:279-285) | ported |
| `NightChartsPanel`: `DotHeader("Heart rate", trailing = "Lowest X · avg Y bpm")` (269) | `SleepGlow.nightCharts` trailing (SleepGlow.kt:296), drawn at SleepScreen.swift:304 | ported |
| `GlowLineChart(values, height = 110.dp, labels, MetricColors.heart)` (271) | `GlowLineChart(... height: 110, color: heart)` (SleepScreen.swift:307-318) | ported |
| `if (hr.isNotEmpty() && breath.isNotEmpty()) DotDivider()` (275) | `if index > 0 { DotDivider() }` (SleepScreen.swift:299-301) | ported |
| `DotHeader("Breathing", trailing = "avg %.1f br/min")` (277) | `SleepGlow.nightCharts` trailing (SleepGlow.kt:305), drawn at SleepScreen.swift:304 | ported |
| `GlowLineChart(values, height = 90.dp, MetricColors.sleepLight)` (279) | `GlowLineChart(... height: 90, color: sleepLight)` (SleepScreen.swift:307-318) | ported |
| readouts `"${v} bpm · ${clock}"` / `"${v} br/min · ${clock}"` (272, 280) | `SleepGlow.readout(from:index:value:unit:)` (SleepGlow.kt:337), called at SleepScreen.swift:315 | ported |
| `evenly(points, range, 5 * 60)` (286-296) | `SleepGlow.evenly` (SleepGlow.kt:392-406) | ported |
| `NapsPanel` here (not the dashboard's): `DotHeader("Naps", trailing = total)` (300-303) | `SleepNapsPanel` + `DotHeader("Naps", trailing: model?.total)` (SleepScreen.swift:381-392) | ported |
| `if (i > 0) DotDivider()` (305) | `if index > 0 { DotDivider() }` (SleepScreen.swift:394-396) | ported |
| glass nap row, `"${clock(start)} – ${clock(end)} · ${formatDuration}"` (307-314) | `Button` + `.glass(glassRowShape)` + `nap.line` (SleepScreen.swift:397-420) | ported |
| caret 16.dp, rotated 180° when open, label "Hide"/"Details" (315-316) | `PhosphorIcon.caretDown`, `.rotationEffect` + accessibility label (SleepScreen.swift:411-415) | ported; the turn is instant, standing instruction 7 |
| `Note("The strap didn't record stages for this nap.")` (320) | `nap.stagesNote` (SleepGlow.kt:351), drawn at SleepScreen.swift:446 | ported |
| `GlowSleepStages(stages, 110.dp)` (322) | `GlowSleepStages(spans: nap.stages, height: 110)` (SleepScreen.swift:423) | ported |
| `state.napBreakdowns[start]?.let { StageRows(it) }` (323) | `nap.stageRows` (SleepGlow.kt:350), drawn at SleepScreen.swift:425-444 | ported |
| `Note("Heart rate · lowest N bpm")` (329) | `nap.hrNote` (SleepGlow.kt:355), drawn at SleepScreen.swift:450 | ported |
| `GlowLineChart(values, height = 80.dp, color = heart)` (330) | `GlowLineChart(... height: 80, color: heart)` (SleepScreen.swift:452-462) | ported |
| `WeekPanel`: `DotHeader("This week", trailing = "Last 7 nights")` (337-344) | `SleepWeekPanel` + `DotHeader` (SleepScreen.swift:474-477) | ported |
| `Note("No sleep recorded in the last 7 nights.")` (346) | `SleepWeekModel.emptyNote` (SleepGlow.kt:373), drawn at SleepScreen.swift:479 | ported |
| `SleepWindows(w.nights, w.regularity, zone)` (349) | `GlowSleepWindows(model:)` (SleepScreen.swift:480) | ported (GlowSleepWindows.swift) |
| `Fact("Avg. asleep")`, `Fact("Avg. score")`, `Fact("Reached ${formatDuration(goal)}")` (350-354) | `SleepWeekModel.facts` (SleepGlow.kt:380-384), drawn at SleepScreen.swift:482-487 | ported |
| `if (r == null) Note(w.learningNote)` (356) | `SleepWeekModel.learningNote` (SleepGlow.kt:404), drawn at SleepScreen.swift:508-509 | ported |
| `Fact("Regularity", "${r.score}%")` + band in `bandColor` (359-363) | `SleepRegularityModel.score/.band/.accent` (SleepGlow.kt:386-397), drawn at SleepScreen.swift:490-505 | ported |
| `Note("Fall asleep ${minutesClock(r.bedtime)}")`, `Note("Wake up ${minutesClock(r.wake)}")` (365-366) | `SleepRegularityModel.fallAsleep/.wakeUp` (SleepGlow.kt:392-394), drawn at SleepScreen.swift:502-504 | ported |
| `GlowBar(r.score / 100f, bandColor(r.band))` (369) | `GlowBar(fraction: regularity.fraction, color:)` (SleepScreen.swift:506) | ported |
| `Note(w.regularityNote.orEmpty())` (370) | `SleepRegularityModel.note` (SleepGlow.kt:396), drawn at SleepScreen.swift:507 | ported |
| `BreathingCard`: `DotHeader("Breathing", trailing = "vs. your nights")` (379-382) | `SleepBreathingPanel` (SleepScreen.swift:361-366) | ported |
| `Fact("Disturbances", "%.1f /h")`, `Fact("Your usual", … ?: "—")` (383-386) | `SleepBreathingModel.disturbances/.usual` (SleepGlow.kt:413-415), drawn at SleepScreen.swift:368-370 | ported |
| `"Your usual builds after ${Recovery.SCORED_NIGHTS} nights with a breathing summary."` (388) | `SleepGlow.breathing` (SleepGlow.kt:417) | ported |
| `"Dips in blood oxygen during sleep, … " + BodySignalsCopy.DISCLAIMER` (389-390) | `SleepGlow.breathing` (SleepGlow.kt:419-420), drawn at SleepScreen.swift:372 | ported |
| `SleepHealthPanel`: `DotHeader("Sleep health", trailing = "N of 3 good")` (399-402) | `SleepHealthPanel` + `DotHeader` (SleepScreen.swift:326-331) | ported |
| `Fact("Regularity", sri ?: ±SD ?: "—")`, `Fact("Band", …)` (403-406) | `SleepHealthModel.regularityValue/.bandValue` (SleepGlow.kt:428-431), drawn at SleepScreen.swift:333-335 | ported |
| the three `note` branches (407-413) | `SleepHealthModel.note` (SleepGlow.kt:432-439), drawn at SleepScreen.swift:337 | ported |
| `DotDivider()` + `HealthRow("Duration"/"Continuity"/"Regularity", word(v))` (415-419) | `SleepHealthModel.rows` (SleepGlow.kt:441-449), drawn at SleepScreen.swift:341-350 | ported |
| `Note("Midpoint of your sleep … , outside 01:30–04:30. …")` (420-423) | `SleepHealthModel.midpointNote` (SleepGlow.kt:451-459), drawn at SleepScreen.swift:352 | ported |
| `HealthRow(label, value)` (428-434) | the inline `HStack` at SleepScreen.swift:341-350 | ported |
| `LocalGoals.current.sleepSeconds` (124, 341) | `health.goals.sleepSeconds` (SleepScreen.swift:80, 112) | ported; both read the same stored goal |
| `onOpenMetric(v.metric)` (94, 244) | `route = .metric(metric.name)` (SleepScreen.swift:95-97) | ported to Today's detail route, as card U6's tiles do (card U9 fills it) |

Two sections cannot be reached from `-demoData` and so have no screenshot: the scored hero with its
"What shaped the score" panel, and "Naps". `SampleData.kt:73` inserts `SleepSession(start, end)` with
no `score`, no `nap` and no stage totals, and the seed writes no naps at all, so every seeded night
is the no-score state the card asks for and the demo day has no nap. The screenshots say exactly
that; no rows were invented for them.

## `app/src/main/kotlin/org/openstrap/app/ui/dashboard/SleepPanels.kt` (card U7)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `class SleepVital(metric, label, value, note, accent)` (31-37) | `SleepVital` (SleepPanels.kt:23-36), with `off` and `pill` folded in (:33-36) | ported |
| `sleepVitals(state)`: lowest and average HR, then HRV, blood oxygen, breathing, skin temperature (44-84) | `sleepVitals` (SleepPanels.kt:44-101) | ported |
| `"Lowest heart rate"`, `"${bpm} bpm"`, `"at ${clockOf(x)}"` (64) | SleepPanels.kt:71-76 | ported |
| `"Average heart rate"`, `"while asleep"` (65) | SleepPanels.kt:75 | ported |
| `"HRV"`, `"${v} ms"`; `"Blood oxygen"`, `"${v} %"`; `"Breathing"`, `"%.1f br/min"`; `"Skin temperature"`, `"%+.1f °C"` (66-81) | SleepPanels.kt:76-100 | ported |
| `"Higher than usual"` / `"Lower than usual"` / `"Usual"` / `"Learning n/7 nights"` (50-55) | SleepPanels.kt:52-58 | ported |
| skin temperature as a deviation once there is a normal, `-` printed `−` (77) | SleepPanels.kt:97-98 | ported; `%+` was missing from shared `String.format` and was added with `FormatTest` |
| `SleepVital.isOff` (87) | `SleepVital.off` / `.pill` (SleepPanels.kt:33-36) | ported |
| `class SleepWeek` + `learningNote` + `regularityNote` (93-107) | `SleepWeek` (SleepPanels.kt:120-139) | ported |
| `sleepWeek(state, goalSeconds, zone)` (110-124) | `sleepWeek` (SleepPanels.kt:142-164) | ported |
| `bandColor(band)` (126-130) | `bandAccent` (SleepPanels.kt:167-171) | ported |
| `SleepWindows` canvas: 200.dp, 40.dp gutter, 18.dp label bands, hour marks every 3 h, dashed averages, 0.42-slot bars, value over each bar (137-210) | `GlowSleepWindows` (GlowSleepWindows.swift:34-160) | ported; the axis is Funnel Sans 11 where Compose's bare `TextStyle` falls back to the platform font |
| `"No sleep recorded in the last 7 nights."` is GlowSleepScreen's (346) | — | see the screen's table |
| `minutesClock(minutes)` (212) | `minutesClock` (SleepPanels.kt:105) | ported |
| `clockOf(epochSeconds)` (214-215) | `clockOf(epoch, zone)` (SleepPanels.kt:257-260) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/dashboard/NapsPanel.kt` (card U7)

This is the dashboard's panel, with a moon icon and a chevron; the Sleep tab draws
`GlowSleepScreen.kt`'s own `NapsPanel` (:298-335), which is the one `sleep.png` shows, so that is
what Swift ports. The file is listed here because the card names it.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `NapsPanel(naps, onClick)` → `Panel` + `PanelHeader("Naps", Moon, sleepLight, chevron)` (21-23) | `SleepNapsPanel` (SleepScreen.swift:381) draws GlowSleepScreen's `DotHeader("Naps", trailing: total)` instead | not ported: the dashboard's icon-and-chevron header is a different panel from the Sleep tab's, which the reference screenshot shows |
| `Text("${clock(start)} – ${clock(end)} · ${formatDuration(end - start)}")` (25) | `SleepNap.line` (SleepGlow.kt:345-347) | ported (in the Sleep tab's panel) |
| `clock(epochSeconds)` (16-17) | `clockOf` (SleepPanels.kt:257) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowActivityScreen.kt` (card U8)

The Activity tab. On a wide screen Compose puts the steps, active minutes, energy and PAI in a left
pane and load, fitness and workouts in a right one (`:89-96`); this app is portrait only, so
`GlowScreenList` stacks them in the order written here, which is what `ActivityScreen` draws.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `PAI_TARGET = 100` (53) | `PAI_TARGET` (ActivityGlow.kt:40) | ported |
| `RECENT_WORKOUTS = 3` (56) | `RECENT_WORKOUTS` (ActivityGlow.kt:43) | ported |
| `GlowActivityScreen(state, recovery, workouts, effects, paiHistory, battery, charging, onPrev, onNext, picker, contentPadding, onOpenMetric, onOpenWorkout, onAllWorkouts, onEditProfile)` (66-82) | `ActivityScreen` (ActivityScreen.swift:50) | ported; `DateHeader`, `onPrev`, `onNext` and `picker` are `RootView.DayTab`'s (card U4), and `onEditProfile` pushes `ProfileScreen` over the tab as Android opens its profile page |
| The list's order (87-97) | `ActivityScreen.content` (ActivityScreen.swift:88-118) | ported |
| `HOURS = ["00", "06", "12", "18", "24"]` (101) | `StepsCard.hours` (ActivityScreen.swift:130) | ported |
| `GlowStyle.chart(steps)` with `GlowMotion.Stride(energy = progress)` (108) | `StepsCard.style` (ActivityScreen.swift:133-138) | ported |
| `GlowHeader("Steps", chevron = true)` (111) | ActivityScreen.swift:142 | ported |
| `"Goal reached"` / `"%d%% of %,d"` (112) | `ActivityGlow.steps` (ActivityGlow.kt:229-250) | ported |
| `GlowHeroNumber("${state.steps}", "steps", listOf(goal))` (113) | ActivityScreen.swift:143 | ported |
| `GlowChartGap()` (114) | ActivityScreen.swift:144 | ported |
| `GlowDotBars(state.hourlySteps, GlowLayout.ChartHeight, HOURS, steps)` (115) | ActivityScreen.swift:145-154 | ported |
| `"%02d:00–%02d:00 · %,d steps"` (116) | `TodayGlow.hourStepReadout` (TodayGlow.kt:396-397) | ported by card U5 |
| `Spacer(Modifier.height(18.dp))` (118) | `Color.clear.frame(height: 18)` (ActivityScreen.swift:155) | ported |
| `Fact("Active hours", "${state.activeHours} of ${goals.activeHours}")` (120) | ActivityScreen.swift:156 / `ActivityGlow.steps` | ported |
| `Fact("Distance", …)` — `"≈ %.1f km"`, else `"—"` (122) | ActivityScreen.swift:158 / `ActivityGlow.steps` (234-236) | ported |
| `Fact("Active kcal", state.activeCalories ?: "—")` (123) | ActivityScreen.swift:160 / `ActivityGlow.steps` | ported |
| `"The strap's step counter (%,d) and its minute records (%,d) differ by more than 5 %."` (127) | `StepsModel.divergeNote` (ActivityGlow.kt:242-245) | ported |
| `"Most of the benefit is reached by about %,d steps a day (Paluch et al. 2022)."` (129) | `StepsModel.stepGoalNote` (ActivityGlow.kt:239-241) | ported |
| `DotPanel { DotHeader("Energy", trailing = "Partial day") }` (137-138) | `EnergyPanel` (ActivityScreen.swift:204-210) | ported |
| `"Add age, weight and height to estimate the energy you use."` with the caret row (140-143) | ActivityScreen.swift:212-231 | ported |
| `Fact("Total", "≈ ${kcal} kcal")`, `Fact("At rest", "${bmr} /day")`, `Fact("Activity level", …)` (147-149) | ActivityScreen.swift:234-238 / `ActivityGlow.energy` | ported |
| Energy's note: Mifflin-St Jeor sentence, the partial total, the implausible-total warning, HR-assisted minutes, food digestion (151-159) | `EnergyModel.note` (ActivityGlow.kt:282-288) | ported |
| `DotHeader("Active minutes", trailing = "Last 7 days")` (168) | ActivityScreen.swift:185 | ported |
| `Fact("This week", "${a.weekEquivalent} / $target")`, `"Moderate"`, `"Vigorous"` (170-172) | ActivityScreen.swift:187-191 / `ActivityGlow.activeMinutes` | ported |
| `GlowBar(a.weekEquivalent / target, MetricColors.steps)` (174) | ActivityScreen.swift:194 | ported |
| `"Brisk walking (100+ steps a minute) is moderate, …"` with its HR-assisted clause (175-179) | `ActiveMinutesModel.note` (ActivityGlow.kt:257-260) | ported |
| `DotHeader("PAI", trailing = "Last 7 days")` (191) | ActivityScreen.swift:255 | ported |
| `"PAI shows after the strap's next sync. It counts minutes your heart rate is raised, harder minutes counting more."` (193) | `ActivityGlow.pai` (ActivityGlow.kt:311-323) | ported |
| `Text("${total}")` + `" / $PAI_TARGET"` (196-198) | ActivityScreen.swift:259-267 | ported |
| `Fact("Today", "+${today}")` (200-201) | ActivityScreen.swift:269 | ported |
| Seven day columns: `getDisplayName(SHORT).take(2)`, the points, `labelSmall` over `bodySmall` (203-218) | ActivityScreen.swift:271-286 / `PaiDayRow` | ported |
| `paiPoints`: `"+12"`, `"+<1"`, `"–"` (232) | `ActivityGlow.paiPoints` (ActivityGlow.kt:383-387) | ported |
| `PaiStack`: 14.dp bar, 0.07 track, 1.5.dp gaps, 3.dp sliver for any day, heart red today, fading whites (242-266) | `PaiStack` (ActivityScreen.swift:298-326) with `ActivityGlow.paiSegments` (ActivityGlow.kt:358-377) | ported; where a segment starts is Kotlin's, the draw is Swift's |
| `"Tomorrow ${day}'s +N drops off."` / `"You're at or above 100. …"` / `"N to go. …"` (219-227) | `ActivityGlow.pai` (ActivityGlow.kt:337-342) | ported |
| `DotHeader("Training load")` (272) | ActivityScreen.swift:330 | ported |
| `Fact("Strain", "%.1f")`, `Fact("Load today", …)`, `Fact("vs your usual", "${pct}th pct" / "—")` (275-277) | ActivityScreen.swift:334-338 / `ActivityGlow.load` | ported |
| `Fact("Fitness")`, `Fact("Fatigue")`, `Fact("Form", sign + Int)` (279-285) | ActivityScreen.swift:343-350 / `ActivityGlow.load` | ported |
| `"Fitness and fatigue are 42- and 7-day averages of your daily load; form is fitness minus fatigue."` (285) | `LoadModel.trendNote` (ActivityGlow.kt:415) | ported |
| `"Each minute counts by how hard your heart works between resting (…) and maximum (…). Strain runs 0–21 …"` (287-290) | `LoadModel.effortNote` (ActivityGlow.kt:416-419) | ported |
| Acute:chronic row, `StatusPill("Contested", white 0.7)`, its note (292-300) | ActivityScreen.swift:356-361 / `ActivityGlow.load` | ported |
| `Fact("This week (strap)", …)`, `"Productive"` / `"Below productive"` / `"Above productive"`, its bar and note (301-318) | ActivityScreen.swift:364-373 / `ActivityGlow.load` (399-443) | ported |
| `"Max HR is estimated. Add your age for accurate effort numbers."` row (319-327) | ActivityScreen.swift:375-395 | ported |
| `DotHeader("Fitness")` (339) | ActivityScreen.swift:408 | ported |
| `Fact("VO2 max")`, `Fact("Recovery", "Done" / "… left")`, `Fact("Max HR")` (340-348) | ActivityScreen.swift:410-414 / `ActivityGlow.fitness` | ported |
| `Sparkline(vo2.takeLast(7), MetricColors.hrv, 24.dp)` (349) | ActivityScreen.swift:417-421 | ported |
| `Fact("Estimated fitness (VO2 max)", "≈ ${low}–${high}")` and the Jurca/FRIEND note with the fitness age (352-360) | ActivityScreen.swift:423-425 / `FitnessModel.estimateNote` (ActivityGlow.kt:465-473) | ported |
| `"Estimated fitness: ${it.reason}"` when withheld (361) | ActivityScreen.swift:427-429 / `FitnessModel.withheldNote` | ported |
| `"VO2 max appears after a workout …"`, `"Recovery time is the strap's estimate …"`, `"Highest heart rate today N bpm."`, and the `ifEmpty` line (363-369) | `FitnessModel.note` (ActivityGlow.kt:484-488) | ported |
| `DotHeader("Workouts", trailing = "${n} recorded")` (381) | ActivityScreen.swift:443 | ported |
| `"Workouts you start on the strap show up here after a sync, with your heart rate every second and how fast it recovered."` (383) | ActivityScreen.swift:446-448 / `WorkoutsPanelModel.emptyNote` | ported |
| `"No workouts this day."` (386) | ActivityScreen.swift:451-453 / `WorkoutsPanelModel.noTodayNote` | ported |
| The day's `WorkoutLine`s (387) | ActivityScreen.swift:454-458 | ported |
| `DotDivider()` + `"RECENT"` (389-390) | ActivityScreen.swift:462-465 | ported |
| The recent three, `showDate = true` (391) | ActivityScreen.swift:464-468 | ported |
| `"All workouts"` row with its caret (393-398) | ActivityScreen.swift:474-497 | ported |
| `WorkoutLine(a, showDate, onClick)` (403-422) | `WorkoutLine` (GlowWorkoutRow.swift:11) | ported by card U5; its second line is `TodayGlow.workoutDetail` |
| `shortDate(epoch)` (424) | `ActivityGlow.shortDate` (ActivityGlow.kt:517-520) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowWorkoutScreen.kt` (card U8)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `clock(epoch)` / `dateLine(epoch)` (38-39) | `WorkoutGlow.clock` / `WorkoutGlow.dateLine` (WorkoutGlow.kt:358-378) | ported |
| `GlowWorkoutScreen`: `BackHandler`, the list's order (46-62) | `WorkoutScreen` (WorkoutScreen.swift:17-63) | ported; the back arrow pops the stack, as BackHandler does |
| `GlowDetailHeader(a?.sportName ?: "Workout", dateLine(start), onBack)` (51) | WorkoutScreen.swift:30-36 | ported |
| `DotPanel { Note("Loading…") }` while the workout is null (52-55) | WorkoutScreen.swift:50-53 | ported |
| `GlowStyle.chart(heart)` with `GlowMotion.Heartbeat(avg ?: 100)` (68) | `HeartCard.style` (WorkoutScreen.swift:71-77) | ported |
| `GlowHeader("Heart rate", trailing = "Every second")` (71) | WorkoutScreen.swift:82 | ported |
| `GlowHeroNumber(avg ?: "-", "bpm avg", [Peak N, duration · kcal])` (72-75) | WorkoutScreen.swift:83 / `WorkoutGlow.heart` (WorkoutGlow.kt:174-230) | ported |
| `"The strap hasn't sent this workout's heart rate yet."` (78) | WorkoutScreen.swift:86 / `WorkoutHeartModel.emptyNote` | ported |
| Ten-second buckets across the workout, gaps null (83-94) | `WorkoutGlow.heart` (WorkoutGlow.kt:210-230) | ported |
| `GlowLineChart(values, ChartHeight, [clock(first), clock(last)], showAverage = false)` (96-99) | WorkoutScreen.swift:91-104 | ported |
| `"${v.toInt()} bpm · ${clock(first + i * step)}"` (98-99) | `WorkoutGlow.heartReadout` (WorkoutGlow.kt:234-240) | ported |
| `"The strap kept recording for ${formatDuration(…)} after you stopped, for recovery heart rate."` (100-103) | `WorkoutHeartModel.tailNote` (WorkoutGlow.kt:226-228) | ported |
| `Fact("Active")`, `Fact("Paused")`, `Fact("Lowest HR")` and `"%,d steps."` (113-119) | `SummaryPanel` (WorkoutScreen.swift:121-131) / `WorkoutGlow.summary` (WorkoutGlow.kt:244-256) | ported |
| `DotHeader("Recovery heart rate", trailing = label)` with Excellent/Good/Fair/Low (128-135) | `RecoveryPanel` (WorkoutScreen.swift:146) / `WorkoutGlow.recovery` (WorkoutGlow.kt:258-292) | ported; an unrated drop has no label, as Compose's `null` |
| `Fact("After 1 min", "${signedDrop} bpm")`, `Fact("After 2 min", …)` (136-139) | WorkoutScreen.swift:148-152 | ported |
| `GlowBar(drop / 40f, color)`, rated only (140) | WorkoutScreen.swift:155-157 | ported |
| Recovery's two notes: the rated one and the "close to resting" one (141-149) | `WorkoutRecoveryModel.note` (WorkoutGlow.kt:279-291) | ported |
| `DotHeader("Heart-rate zones", trailing = "Max N bpm")` (157) | `ZonesPanel` (WorkoutScreen.swift:171) | ported |
| Zones 5 to 1: 7.dp dot, name, `"$lo$hi"`, `formatDuration`, 56.dp column, `GlowBar(share)` (158-170) | WorkoutScreen.swift:172-191 / `WorkoutGlow.zones` (WorkoutGlow.kt:294-313) | ported |
| `DotHeader("Training effect", trailing = "From the strap")` (180) | `EffectPanel` (WorkoutScreen.swift:205) | ported |
| `Fact("Aerobic")`, `Fact("Anaerobic")`, `Fact("Load")` (181-185) | WorkoutScreen.swift:207-213 | ported |
| `Fact("VO₂ max")` at weight 1 and `Fact("Recovery time")` at weight 2 (187-190) | WorkoutScreen.swift:214-219 | ported; the second fact takes an equal half where Compose gives it twice the width |
| `"Training effect runs 0–5 for each system. VO₂ max shows up once the strap has enough data to estimate it."` (191) | `WorkoutEffectModel.note` (WorkoutGlow.kt:331) | ported |
| `GlowDetailHeader("Workouts", "${workouts.size} recorded", onBack)` (201) | `WorkoutsScreen` (WorkoutsScreen.swift:24) | ported |
| `"Workouts you start on the strap show up here after a sync."` (203) | WorkoutsScreen.swift:28-30 / `AllWorkoutsModel.emptyNote` | ported |
| One `DotPanel` per day: `dayName(date)`, the year when it is not this year's, `DotDivider` between rows (206-217) | WorkoutsScreen.swift:32-47 / `WorkoutGlow.all` (WorkoutGlow.kt:336-355) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/workout/WorkoutStyle.kt` (card U8)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `ZONE_COLORS` — five zone colours, cool to hot (6) | `ZONE_ACCENTS` (WorkoutGlow.kt:30-33) through `ActivityColors.of` (ActivityScreen.swift:23-34) | ported |
| `ZONE_NAMES` — "Warm-up", "Fat burn", "Aerobic", "Anaerobic", "Maximum" (7) | `ZONE_NAMES` (WorkoutGlow.kt:29) | ported |
| `signedDrop` — "−24", "+3", "0" (10) | `WorkoutGlow.signedDrop` (WorkoutGlow.kt:35-39) | ported |

## `app/src/main/kotlin/org/openstrap/app/ui/glow/GlowMetricDetail.kt` (card U9)

One screen for every metric. The numbers, dates and sentences are computed by `MetricDetailGlow`
(shared Kotlin, standing instruction 5); `MetricDetailScreen.swift` draws them.

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `GlowDetailHeader(title, subtitle, onBack)` — 48.dp back arrow, title at `GlowType.title` × 1.25, subtitle in `bodySmall` at 60 % (61-69) | `GlowDetailHeader` (GlowDetailHeader.swift:10-60), used at MetricDetailScreen.swift:83 | ported by card U8; the metric detail uses the same row |
| `dayName(date)` — "Today", "Yesterday", else "EEE d MMM" (71-75) | `MetricDetailGlow.dayName` (MetricDetailGlow.kt:218-224) → the subtitle (MetricDetailScreen.swift:83) | ported |
| `GlowSegments(options, selected, onSelect)`: white 6 % pill, chosen segment white 16 %, 9.dp vertical, label in `labelLarge` (79-96) | `GlowSegments` (GlowDetailHeader.swift:66-95); `GlowSegmentStyle` presses to 0.97 (GlowDetailHeader.swift:97-105) | ported; the press is a scale, and still under Reduce Motion |
| `HOURS = ["00", "06", "12", "18", "24"]` (100) | `MetricDetailGlow.HOURS` (MetricDetailGlow.kt:141) | ported |
| `GlowMetricDetailScreen`: `BackHandler`, `rangeIndex` remembered per metric (108-127) | `MetricDetailScreen` (MetricDetailScreen.swift:31-125); `@State rangeIndex` (:41), the back arrow pops the stack | ported |
| `Range.entries.map { it.label }` as the switch's options (132) | `MetricDetailGlow.RANGES` (MetricDetailGlow.kt:144) | ported |
| The history read's window: `maxOf(range.days, NORMAL_DAYS)` days ending the day (124-125) | `MetricDetailGlow.historyRange` (MetricDetailGlow.kt:151-152) → `HealthModel.loadHistory` (HealthModel.swift:224-230) | ported; the screen fetches a `Month` read for Day/Week/Month and a `Year` read for Year |
| `stats` starts null and each new range restarts it — "Loading…" until the read lands (125) | `MetricDetailScreen.stats` (MetricDetailScreen.swift:43), set to nil per range (:115) | ported |
| The list's order: header, switch, the card, Lowest/Highest (not Day), Your normal, About, Related (131-145) | MetricDetailScreen.swift:82-104 | ported |
| `DayCard`: the day's series and window per metric; breathing over `sleep.start..sleep.end` (181-189) | `MetricDetailGlow.dayCard` (MetricDetailGlow.kt:248-268) | ported |
| The day's number: live HR else `latestHeartRate`; `restingHeartRate`; `steps`; else the series' average (190-195) | `dayCard` (MetricDetailGlow.kt:270-275) | ported |
| `GlowStyle.chart(color)` with `Heartbeat(value ?: 62)` for heart rate, else `Breath(5f)` (196) | `MetricCardView.style` (MetricDetailScreen.swift:138-144) from `MetricCardModel.motion` / `.heartbeat` | ported |
| `"Latest"` / `"Now"` (201) | `dayCard` header (MetricDetailGlow.kt:276-283); the `"Now"` rule is tested in `MetricDetailGlowTest` | ported |
| `"Resting"` (202) | same (:279) | ported |
| `"Total"` (203) | same (:280) | ported |
| `"Average during sleep"` (204) | same (:281) | ported |
| `"Day average"` (205) | same (:282) | ported |
| `"Live"` + `GlowSwitch(on, onLiveToggle, accent = color)` (207-215) | MetricDetailScreen.swift:146-163 | ported; shown only for today's heart rate on a connected strap (`canUseLive`, :135) |
| `facts` for heart rate: `"Low N · high N"`, `"Average N"` (219-221) | `dayCard` (MetricDetailGlow.kt:285-295) | ported |
| `normalFacts`: `"Usually N"`, `"Above your normal"`, `"Below your normal"`, `"Within your normal"` (165-173) | `MetricDetailGlow.normalFacts` (MetricDetailGlow.kt:362-372) | ported |
| `"No ${title.lowercase()} for this day yet."` (227) | `dayCard` (MetricDetailGlow.kt:297-303) | ported |
| `"Breathing rate is measured during sleep."` (227) | same (:299-301) | ported |
| `GlowHeroNumber(format(value).replace(",", ""), unit.ifEmpty { "/ 100" }, facts)` (232) | MetricDetailScreen.swift:166 / `dayCard` (MetricDetailGlow.kt:345-347) | ported |
| `GlowDotBars(hourlySteps, ChartHeight, HOURS, accent = White)` (236-239) | `MetricChartView` (MetricDetailScreen.swift:194-200) | ported |
| `"%02d:00–%02d:00 · %,d steps"` (237) | `dayCard` readouts (MetricDetailGlow.kt:322-324) | ported |
| `GlowTrend(restingTrend, ChartHeight)` when the trend has a value (240-243) | MetricDetailScreen.swift:202-205 / `dayCard` (MetricDetailGlow.kt:327-336) | ported |
| Five-minute buckets for heart rate, fifteen for the rest (246) | `dayCard` (MetricDetailGlow.kt:340) | ported |
| `evenly(points, range, seconds)` — averages into buckets, gaps null (151-160) | `MetricDetailGlow.evenly` (MetricDetailGlow.kt:225-238) | ported |
| `clock(epoch)` — "HH:mm" (162) | `MetricDetailGlow.clock` (MetricDetailGlow.kt:240-241) | ported |
| `labels = if (window == day) HOURS else [clock(first), clock(last)]` (248) | `dayCard` (MetricDetailGlow.kt:341-343) | ported |
| `GlowLineChart(values, ChartHeight, labels, format = metric::format)` (250) | `MetricChartView` (MetricDetailScreen.swift:207-213) | ported |
| `"${format(v)} ${unit} · ${clock(from + i * bucket)}".replace("  ", " ")` (251) | `dayCard` readouts (MetricDetailGlow.kt:344-346) | ported |
| `HistoryCard`: `GlowStyle.chart(color)` with `Breath(6f)` (264) | `MetricCardView.style` (MetricDetailScreen.swift:138-144) from `MetricCardModel.breathPerMinute` = 6 | ported |
| `"Steps a day"` / `"Daily average"` (267) | `MetricDetailGlow.historyCard` (MetricDetailGlow.kt:384) | ported |
| `"Last 7 days"` / `"Last 30 days"` / `"Last 12 months"` (268) | same (:385-389) | ported |
| `"Loading…"` / `"No ${title.lowercase()} in this range yet."` (271-276) | same (:391-401) | ported |
| `"%,d in total"`, `"${days} days with data"`, and the status word (279-285) | same (:403-408) | ported; a year carries no status word (:282) |
| `GlowLineChart(values, ChartHeight, labels, showAverage = false, band = normal.low..high)` (287-292) | `MetricChartView` (MetricDetailScreen.swift:207-213), band from `MetricChartModel.bandLow`/`bandHigh` (:218-223) | ported |
| `"Week of d MMM"` over a year, `"EEE d MMM"` otherwise, and its readout (293-296) | `historyCard` (MetricDetailGlow.kt:420-430) | ported |
| `series(range, stats, start, end)` — a day, or a Monday-started week over a year (311-329) | `MetricDetailGlow.series` (MetricDetailGlow.kt:446-472) | ported |
| Week labels: `getDisplayName(SHORT).take(2)` (324) | same (:461) | ported |
| Month/year labels: four dates at 0, ⅓, ⅔, 1 (325-326) | same (:462-471) / `FOUR_POINTS` (:473) | ported |
| The "Your normal" legend row: 10×6 box at white 25 %, text at 70 % (298-305) | `MetricCardView` (MetricDetailScreen.swift:169-186) | ported |
| `"Your normal"` (301) | `historyCard` (MetricDetailGlow.kt:433-435) | ported |
| `"Your normal shows after ${MIN_NORMAL_DAYS} days of data"` (302) | same (:434-436) | ported |
| `LowHighPanel`: a `DotPanel` of two equal columns (332-344) | `LowHighPanel` (MetricDetailScreen.swift:237-252) | ported |
| `"Lowest"` / `"Highest"`, or `"Lowest day"` / `"Highest day"` over a year (338) | `MetricDetailGlow.lowHigh` (MetricDetailGlow.kt:477-485) | ported |
| `"${format(value)} ${unit}".trim()` and `"EEE d MMM"` (338-339) | same (:480-483) | ported |
| `NormalPanel`: `DotHeader("Your normal", trailing = "Last 30 days")` (350) | `NormalPanel` (MetricDetailScreen.swift:258-281) / `MetricDetailGlow.normalPanel` (MetricDetailGlow.kt:486-489) | ported |
| `"Your normal shows after ${MIN_NORMAL_DAYS} days of data ($days so far). It's the range your daily values usually fall in."` (352) | same (:493-497) | ported |
| `GlowBar(days / MIN_NORMAL_DAYS, colorOf(metric))` (353) | MetricDetailScreen.swift:275-278 | ported |
| `Fact("Usual", …)` and `Fact("Range", "low–high")` (357-358) | same (:266-270) / `normalPanel` (:503-505) | ported |
| `"From ${days} days. Most of your days fall inside this range; outside it is worth a look, not a worry.")` (360) | same (:506-509) | ported |
| `AboutPanel`: `DotHeader("About ${title.lowercase()}")`, the paragraph at 90 %, the note (364-372) | `AboutPanel` (MetricDetailScreen.swift:287-298) / `MetricAboutModel` (MetricDetailGlow.kt:106-107) | ported |
| `RelatedPanel`: `DotHeader("Related")`, one glass row per metric (374-389) | `RelatedPanel` (MetricDetailScreen.swift:304-334) | ported |
| The row's 7.dp colour dot, title in `bodyMedium`, 70 % caret, 14/12 padding (383-386) | same (:309-329) | ported; the dot's colour is `RelatedMetricRow.color` (MetricDetailGlow.kt:95) |

## `app/src/main/kotlin/org/openstrap/app/ui/dashboard/Routes.kt` (card U9)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `Metric` enum: eight entries, titles and units (9-21) | `Metric` (Metric.kt:15-27, copied by U4) | ported; the enum crossed into shared at U4 |
| `Metric.format`: `%,d` for steps, else the entry's decimals (20) | `Metric.format` (Metric.kt:26) → `MetricDetailGlow.format` (MetricDetailGlow.kt:244) | ported |
| `Detail.OfMetric` / `Detail.Workout` / `Detail.AllWorkouts` (27-31) | `TodayRoute` (TodayScreen.swift:23-26) and `ActivityRoute` (ActivityScreen.swift:40-46) | ported; each tab keeps its own stack, as Compose's `Saver` does |
| `Detail.StackSaver` — the stack survives process death (35-54) | not ported: iOS restores a tab's stack through SwiftUI's own `NavigationStack` state, which this app does not persist across a relaunch | not ported: nothing in the app writes the stack to disk, so there is no string to restore |

## `app/src/main/kotlin/org/openstrap/app/ui/dashboard/MetricInfo.kt` (card U9)

| Compose element or string (file:line) | Swift (file:line) | Status |
|---|---|---|
| `Range`: Day, Week, Month, Year, with 1/7/30/365 days (13) | `Range` (Metric.kt:47) | ported at U4 |
| `DailyHistory` — a `Flow` per metric and window (16) | `ScreenQueries.history` (ScreenQueries.kt:85-88), a plain callback, never a `Flow` (standing instruction 12) | ported as the Swift-safe shape |
| `colorOf(metric)` (18-25) | `Metric.color` (Metric.kt:36-44) → `MetricDetailModel.accent`, mapped to `Theme.swift` in `metricColor` (MetricDetailScreen.swift:19-28) | ported |
| `RELATED` — the three metrics each detail offers (27-36) | `RELATED` (Metric.kt:50-59) → `MetricDetailModel.related` (MetricDetailGlow.kt:209) | ported |
| `ABOUT` — the eight "what it is / what moves it" pairs (38-71) | `ABOUT` (Metric.kt:62-94) → `MetricAboutModel` (MetricDetailGlow.kt:203-207) | ported, word for word |


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
## Rename to OpenHealth (card R1)

Not a port: card R1 renames what a person reads inside the iPhone app to **OpenHealth** and changes
nothing that identifies the app or its data to iOS. The Android app keeps OpenStrap, because it is
upstream's, so every row below is a deliberate difference from the Compose source rather than a
translation of it. The Compose lines keep their old text; the Swift and shared Kotlin lines carry
the new one.

| Where | Old text | New text | Status |
|---|---|---|---|
| `project.yml:40` `CFBundleDisplayName` (the home screen's label) | absent — iOS fell back to `CFBundleName`, which is `$(PRODUCT_NAME)` = OpenStrap | `OpenHealth` | renamed (Romit, 2026-10-22) |
| `project.yml:52` `NSBluetoothAlwaysUsageDescription` | OpenStrap connects to your Amazfit Helio Strap over Bluetooth to read your heart rate and sync your history. | OpenHealth connects to your Amazfit Helio Strap over Bluetooth to read your heart rate and sync your history. | renamed (Romit, 2026-10-22) |
| `project.yml:53` `NSHealthUpdateUsageDescription` | OpenStrap writes your heart rate, sleep and activity to Apple Health. | OpenHealth writes your heart rate, sleep and activity to Apple Health. | renamed (Romit, 2026-10-22) |
| Wordmark (`DateHeader.swift:233`, `ScreenParts.kt:113-124`) | OpenStrap. | OpenHealth. | renamed (Romit, 2026-10-22): the same font, size, weight and red full stop; only the text differs |
| `YouView.swift:162` | OpenStrap \(appVersion) · local-first, no cloud | OpenHealth \(appVersion) · local-first, no cloud | renamed (Romit, 2026-10-22) |
| `StrapView.swift:44` forget-strap dialog | OpenStrap will disconnect and delete the strap's address and auth key from this phone. | OpenHealth will disconnect and delete the strap's address and auth key from this phone. | renamed (Romit, 2026-10-22) |
| `PairView.swift:18` pairing title | OpenStrap | OpenHealth | renamed (Romit, 2026-10-22) |
| `PairView.swift:50` auth-key sentence | OpenStrap needs the strap's auth key, which Zepp creates when you link the strap. | OpenHealth needs the strap's auth key, which Zepp creates when you link the strap. | renamed (Romit, 2026-10-22) |
| `ZeppSignIn/ZeppSignInView.swift:50` | This is Zepp's own page, so OpenStrap never sees your password. … | This is Zepp's own page, so OpenHealth never sees your password. … | renamed (Romit, 2026-10-22) |
| `BleDriverImpl.swift:214` Diagnostics log line | Bluetooth permission is denied for OpenStrap | Bluetooth permission is denied for OpenHealth | renamed (Romit, 2026-10-22) |
| `RecoveryCopy.kt:17` `LEARNING` (`GlowRecoveryScreen.kt`) | OpenStrap scores Recovery once it has ${Recovery.SCORED_NIGHTS} good nights of your own HRV and resting heart rate. | OpenHealth scores Recovery once it has … | renamed (Romit, 2026-10-22) |
| `RecoveryCopy.kt:33` `EXPLANATION` (`HowItWorksPanel`) | Each morning OpenStrap compares your night with what's normal for you, … | Each morning OpenHealth compares your night with what's normal for you, … | renamed (Romit, 2026-10-22) |
| `RecoveryGlow.kt:229` strap estimate note | … Shown for reference; it never enters OpenStrap's score. | … Shown for reference; it never enters OpenHealth's score. | renamed (Romit, 2026-10-22) |
| `RecoveryGlow.kt:267` `noScoreBody` withheld | OpenStrap scores Recovery once it has … | OpenHealth scores Recovery once it has … | renamed (Romit, 2026-10-22) |
| `RecoveryGlow.kt:532` `bodySignals` watch | One signal is outside your usual range. OpenStrap is keeping an eye on it. | One signal is outside your usual range. OpenHealth is keeping an eye on it. | renamed (Romit, 2026-10-22) |
| `RecoveryGlow.kt:550` `howItWorks` explainer | Each morning OpenStrap compares your night with what's normal for you, … | Each morning OpenHealth compares your night with what's normal for you, … | renamed (Romit, 2026-10-22) |
| `RecoveryGlow.kt:562` `howItWorks` strap note | OpenStrap keeps both each day to tune its own score … | OpenHealth keeps both each day to tune its own score … | renamed (Romit, 2026-10-22) |
| `LicencesView.swift:29-48` new first row (`YouScreen.kt`'s Licences list has no upstream row) | — | Title `OpenStrap`; subtitle `OpenHealth is based on OpenStrap by sayed0am, used under the MIT licence. github.com/sayed0am/openstrap`; opens the MIT text with the Apache text below it | added (Romit, 2026-10-22): the card's one new wording, required by the MIT and Apache notices |

What deliberately did **not** change, and is pinned by `RenameTests`:
`PRODUCT_BUNDLE_IDENTIFIER` (`Base.xcconfig`, `Local.xcconfig`), `PRODUCT_NAME`, the three target
names and the one scheme name, the Swift module `OpenStrap` and the framework `OpenStrapShared`, the
`org.openstrap.*` Kotlin packages, and the seven string constants `org.openstrap.ios`,
`strap-auth-key`, `org.openstrap.central`, `org.openstrap.ble`, `openstrap.db`,
`openstrap.strapPeripheralIdentifier` and `openstrap.writeToAppleHealth`.

## The dated tabs' end of scroll (cards F4, F10, F11)

The four dated tabs scroll their content under the floating bar, so each one keeps the bar's clearance
(`GlowLayout.barClearance` = 112 pt) after its last card, and that card must come clear of the bar once
the list has been dragged to its end. F4 measures that for the You screens and F9's workout screens;
`testEveryDatedTabScrollsClearOfTheBar` (card F10) does it for Today, Recovery, Sleep and Activity. Its
walk follows the header's wordmark and stops when two drags in a row leave it in the same place; then it
measures the lowest text on the screen against `navBar.top` (754.0 pt in the 402×874 simulator window).

Card F11 found two defects, both in the measurement path rather than in the list.

The first is the walk's stop condition. The wordmark's frame stops updating once the wordmark is far off
screen: XCUITest reports it at its last on-screen position rather than its scrolled one. A temporary
DEBUG probe in the scroll content (`UIScrollView.contentSize`, the tallest laid-out subview, the insets,
`bounds`, `contentOffset`), printed after every drag, caught it:

    F11 Today drag 2: wordmark=-1299.67  contentH=2172.67  laidOutH=2172.67  boundsH=874.0
                      insetTop=62.0  insetBottom=146.0  offsetY=1307.67  rangeEndY=1444.67
    F11 Today drag 3: wordmark=-1299.67  ...  offsetY=1307.67
    F11 Today drag 4: wordmark=-1299.67  ...  offsetY=1307.67

The list was complete, the bar's clearance was in the adjusted inset, and the scroll view still had
137 pt to give; the walk read the frozen frame as the end and measured the last card wherever the list
happened to be. The walk now drags a fixed budget and never reads an element's frame for the end.
Whether the list grew under it is `measureTabEnd`'s settle loop, which re-reads the lowest *on-screen*
text after 2.5 s and walks again if it moved.

The second defect was the budget itself. A fling on the CI simulator is worth 75-114 pt on Today, 124 on
Recovery, 198 on Sleep and 154 on Activity, while Today needs about 1506 pt of travel to reach its own
range — so twelve drags (900-1370 pt) never got there, and eighteen (1350-2050) still fell short on the
runs whose flings were weakest. That is what read as a clamp on run after run. Thirty drags is past the
longest walk at the shortest fling: Today then reaches its range and its last card comes to rest at
691.99 against the bar's 754.0, as it did on the earlier runs whose walk happened not to freeze.

No product code had to change for any of this: Today's list, its bottom inset and its content height
were right in every probe. An earlier attempt in this card gated Today's list on the reads behind it (day
and workouts) so the workouts card could not arrive after the list's first layout; it was dropped once
the probe showed the list's height was never the problem, and because it could leave Today showing
nothing at all when a read did not answer (run 40006225116's `testActivity` waited 120 s for a card that
never came). `TodayScreen` asks for its reads in its own `onAppear`, as every other screen does.

The one product fix from this card is the recap flag: `-todayMonday` forces the recap card on a day that
is not a Monday, and `HealthModel.reloadRecapSteps` now honours the same flag, so a forced recap reads
its steps (the row read "Steps a day —" without them, in `U5-todayrecap-1`). A real Monday is
unaffected, and a release build reads the flag as false.

