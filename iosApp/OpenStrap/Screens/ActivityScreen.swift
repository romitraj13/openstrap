import OpenStrapShared
import SwiftUI

/*
 * The Activity tab, ported from `ui/glow/GlowActivityScreen.kt` (card U8): "how much did I move and
 * train". Steps glow; PAI, training load, fitness and the day's workouts sit in quiet dot panels
 * underneath.
 *
 * Order, exactly as `GlowActivityScreen.kt:87-97` builds it. Compose splits that list into a left
 * and a right pane at tablet widths; at the size this app runs at (portrait only) `GlowScreenList`
 * stacks the panes, so the order below is the one a phone shows.
 *
 * Every number, sentence and word comes from `ActivityGlow` in shared Kotlin, so both builds read
 * the same values; this file draws them.
 */

// MARK: - Colour names

/// The colours `ActivityGlow`'s accents name, from `Theme.swift`'s tokens and nothing else
/// (standing instruction 6). Compose passes `Color` values into these panels
/// (GlowActivityScreen.kt:174, 316, `WorkoutStyle.kt:6`); shared Kotlin has no colour type, so it
/// passes the name.
enum ActivityColors {
    static func of(_ accent: ActivityAccent) -> Color {
        switch accent.name {
        case "Steps": return MetricColors.steps
        case "Heart": return MetricColors.heart
        case "Hrv": return MetricColors.hrv
        case "Good": return MetricColors.good
        case "Warn": return MetricColors.warn
        case "SleepLight": return MetricColors.sleepLight
        default: return MetricColors.steps
        }
    }
}

// MARK: - Routes

/// What the Activity tab pushes: a metric, a workout, the whole list, or the profile page the
/// energy and load rows open (`onEditProfile`, GlowActivityScreen.kt:80).
enum ActivityRoute: Hashable {
    case metric(String)
    case workout(Int64)
    case allWorkouts
    case profile
}

// MARK: - The screen

struct ActivityScreen: View {
    @ObservedObject var health: HealthModel
    /// The strap, which today's heart-rate detail asks for its Live switch (card U9).
    var controller: StrapController? = nil

    @State private var route: ActivityRoute?

    var body: some View {
        Group {
            if let day = health.dayState {
                content(day)
            } else {
                // The day's first read is in flight. Nothing is invented for the wait.
                Color.clear.frame(height: 1)
            }
        }
        // Activity is a `GlowScreenList` on Android, which at the sizes this app runs at (portrait
        // only) pads its cards with the tight sides.
        .environment(\.glowCardPadding, GlowLayout.wideCardPadding)
        .navigationDestination(item: $route) { route in
            switch route {
            case .metric(let name):
                if let metric = TodayGlow.shared.metric(name: name) {
                    // Card U9: one screen for every metric (GlowMetricDetail.kt).
                    MetricDetailScreen(health: health, metric: metric, controller: controller)
                }
            case .workout(let start):
                WorkoutScreen(health: health, start: start)
            case .allWorkouts:
                WorkoutsScreen(health: health)
            case .profile:
                ProfileScreen(health: health)
            }
        }
        .onAppear { health.reloadActivity() }
    }

    @ViewBuilder
    private func content(_ day: DayUiState) -> some View {
        // `val r = recovery?.takeIf { it.date == state.date }` (GlowActivityScreen.kt:85).
        let recovery = RecoveryGlow.shared.forDay(state: day, recovery: health.recoveryResult)
        // `effects.lastOrNull { it.weeklyLoad != null }` (:94).
        let weeklyEffect = health.sportEffects.last { $0.weeklyLoad != nil }
        let steps = ActivityGlow.shared.steps(state: day, activity: recovery?.activity, goals: health.goals)
        let load = ActivityGlow.shared.load(r: recovery, effect: weeklyEffect)
        let fitness = ActivityGlow.shared.fitness(
            r: recovery,
            workouts: health.workoutList,
            effects: health.sportEffects,
            highestToday: day.dailyMaxHeartRate
        )
        let workouts = ActivityGlow.shared.workoutsPanel(state: day, workouts: health.workoutList)
        VStack(alignment: .leading, spacing: GlowLayout.columnGap) {
            StepsCard(day: day, model: steps) {
                route = .metric(Metric.steps.name)
            }
            if let activity = recovery?.activity {
                ActiveMinutesPanel(model: ActivityGlow.shared.activeMinutes(a: activity))
            }
            EnergyPanel(model: ActivityGlow.shared.energy(e: recovery?.energy)) {
                route = .profile
            }
            PaiPanel(model: ActivityGlow.shared.pai(state: day, week: health.paiWeek))
            LoadPanel(model: load) { route = .profile }
            FitnessPanel(model: fitness)
            WorkoutsPanel(model: workouts) { start in
                route = .workout(start)
            } onAll: {
                route = .allWorkouts
            }
        }
    }
}

// MARK: - Steps (StepsCard, GlowActivityScreen.kt:105)

/// Steps glow and pick up pace with the day's progress; distance, calories and active hours sit
/// under the chart.
private struct StepsCard: View {
    var day: DayUiState
    var model: StepsModel
    var onClick: () -> Void

    /// The hour marks under the dot bars (:101).
    private static let hours = ["00", "06", "12", "18", "24"]

    private var style: GlowStyle {
        var style = GlowStyle.chart(MetricColors.steps)
        // `GlowMotion.Stride(energy = progress)`: cadence is Compose's default, 110 (:108).
        style.motion = .stride(cadence: 110, energy: CGFloat(model.progress))
        return style
    }

    var body: some View {
        GlowCard(style, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                GlowHeader("Steps", chevron: true)
                GlowHeroNumber("\(day.steps)", unit: "steps", facts: [model.goal])
                GlowChartGap()
                GlowDotBars(
                    values: day.hourlySteps.map { $0.floatValue },
                    height: GlowLayout.chartHeight,
                    labels: Self.hours,
                    accent: MetricColors.steps,
                    describe: { hour, value in
                        TodayGlow.shared.hourStepReadout(hour: Int32(hour), value: value)
                    }
                )
                Color.clear.frame(height: 18)
                HStack(alignment: .top, spacing: 0) {
                    Fact(label: "Active hours", value: model.activeHours)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Fact(label: "Distance", value: model.distance)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Fact(label: "Active kcal", value: model.activeKcal)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if let note = model.divergeNote {
                    Color.clear.frame(height: 8)
                    QuietNote(text: note)
                }
                if let note = model.stepGoalNote {
                    QuietNote(text: note)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: onClick)
    }
}

// MARK: - Active minutes (MvpaPanel, GlowActivityScreen.kt:165)

/// Moderate-to-vigorous minutes today and over the week, against the WHO's 150 a week.
private struct ActiveMinutesPanel: View {
    var model: ActiveMinutesModel

    var body: some View {
        DotPanel {
            DotHeader("Active minutes", trailing: model.trailing)
            HStack(alignment: .top, spacing: 0) {
                Fact(label: "This week", value: model.week)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Fact(label: "Moderate", value: model.moderate)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Fact(label: "Vigorous", value: model.vigorous)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            GlowBar(fraction: model.fraction, color: MetricColors.steps)
            QuietNote(text: model.note)
        }
    }
}

// MARK: - Energy (EnergyPanel, GlowActivityScreen.kt:136)

/// Energy expenditure: our per-minute estimate from BMR, steps and (narrowly) heart rate - or the
/// row that asks for the profile it needs.
private struct EnergyPanel: View {
    var model: EnergyModel
    var onEditProfile: () -> Void

    var body: some View {
        DotPanel {
            DotHeader("Energy", trailing: model.partial)
            if !model.hasEstimate {
                // "Add age, weight and height to estimate the energy you use." (:141)
                Button(action: onEditProfile) {
                    HStack(spacing: 0) {
                        Text("Add age, weight and height to estimate the energy you use.")
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(MetricColors.warn)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        PhosphorIcon.caretRight.image
                            .resizable()
                            .scaledToFit()
                            .frame(width: 16, height: 16)
                            .foregroundStyle(glowInk.opacity(0.7))
                            .padding(.leading, 8)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .contentShape(glassRowShape)
                }
                .buttonStyle(.plain)
                .glass(glassRowShape)
            } else {
                HStack(alignment: .top, spacing: 0) {
                    Fact(label: "Total", value: model.total)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Fact(label: "At rest", value: model.atRest)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Fact(label: "Activity level", value: model.activityLevel)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                QuietNote(text: model.note)
            }
        }
    }
}

// MARK: - PAI (PaiPanel, GlowActivityScreen.kt:188)

/// PAI: the strap's rolling 7-day score from heart-rate effort, aiming for 100.
private struct PaiPanel: View {
    var model: PaiModel

    var body: some View {
        DotPanel {
            DotHeader("PAI", trailing: "Last 7 days")
            if !model.hasPai {
                QuietNote(text: model.note)
            } else {
                HStack(alignment: .bottom, spacing: 0) {
                    Text(model.total)
                        .appTextStyle(AppTypography.displaySmall)
                        .foregroundStyle(glowInk)
                    Text(model.target)
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(glowInk.opacity(0.6))
                        .padding(.bottom, 6)
                    Spacer(minLength: 0)
                    if let today = model.today {
                        Fact(label: "Today", value: today)
                    }
                }
                PaiStack { width in
                    ActivityGlow.shared.paiSegments(daily: model.daily, width: width)
                }
                // Each day's points under the bar, oldest first; today in red like its segment (:208-218).
                HStack(spacing: 0) {
                    ForEach(Array(model.days.enumerated()), id: \.offset) { _, day in
                        VStack(spacing: 0) {
                            Text(day.name)
                                .appTextStyle(AppTypography.labelSmall)
                                .foregroundStyle(glowInk.opacity(0.55))
                            Text(day.points)
                                .appTextStyle(AppTypography.bodySmall)
                                .foregroundStyle(day.empty ? glowInk.opacity(0.35) : (day.today ? MetricColors.heart : glowInk))
                                .padding(.top, 2)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                QuietNote(text: model.note)
            }
        }
    }
}

/// The rolling total as one bar to 100, built from the days that fill it (:242-266). The segments'
/// arithmetic is shared Kotlin's (`ActivityGlow.paiSegments`); this draws them.
private struct PaiStack: View {
    /// The bar's segments for a given width, in points. The caller closes over the model's seven
    /// days, so this file never has to name the type Kotlin boxes a `List<Float>` into.
    var segments: (Float) -> [PaiSegment]

    var body: some View {
        GeometryReader { proxy in
            let segments = self.segments(Float(proxy.size.width))
            Canvas { context, size in
                let bar = Path(roundedRect: CGRect(origin: .zero, size: size), cornerRadius: size.height / 2)
                context.fill(bar, with: .color(.white.opacity(0.07)))
                // The bar is a capsule: a segment that runs past the end is cut by its shape (:246-248).
                var clipped = context
                clipped.clip(to: bar)
                for segment in segments {
                    // Today's segment is heart red, the rest white at the day's own opacity (:256-260).
                    let color = segment.today ? MetricColors.heart : Color.white.opacity(Double(segment.white))
                    clipped.fill(
                        Path(CGRect(x: CGFloat(segment.from), y: 0, width: CGFloat(segment.width), height: size.height)),
                        with: .color(color)
                    )
                }
            }
        }
        .frame(height: 14)
        .accessibilityHidden(true)
    }
}

// MARK: - Training load (LoadPanel, GlowActivityScreen.kt:270)

/// Today's load from heart rate (ours) next to the strap's weekly load and its productive range.
private struct LoadPanel: View {
    var model: LoadModel
    var onEditProfile: () -> Void

    var body: some View {
        DotPanel {
            DotHeader("Training load")
            if model.hasRecovery {
                HStack(alignment: .top, spacing: 0) {
                    Fact(label: "Strain", value: model.strain)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Fact(label: "Load today", value: model.loadToday)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Fact(label: "vs your usual", value: model.vsUsual)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if model.hasTrend {
                    HStack(alignment: .top, spacing: 0) {
                        Fact(label: "Fitness", value: model.fitness)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Fact(label: "Fatigue", value: model.fatigue)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Fact(label: "Form", value: model.form)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    QuietNote(text: model.trendNote)
                }
                QuietNote(text: model.effortNote)
            }
            if model.hasAcwr {
                DotDivider()
                HStack(alignment: .bottom, spacing: 0) {
                    Fact(label: "Acute:chronic", value: model.acwr)
                    Spacer(minLength: 0)
                    StatusPill(model.acwrWord, color: .white.opacity(0.7))
                }
                QuietNote(text: model.acwrNote)
            }
            if model.hasWeek {
                DotDivider()
                HStack(alignment: .bottom, spacing: 0) {
                    Fact(label: "This week (strap)", value: model.weekLoad)
                    Spacer(minLength: 0)
                    Text(model.weekWord)
                        .appTextStyle(AppTypography.titleSmall)
                        .foregroundStyle(ActivityColors.of(model.weekAccent))
                }
                GlowBar(fraction: model.weekFraction, color: ActivityColors.of(model.weekAccent))
                QuietNote(text: model.weekNote)
            }
            if let note = model.maxHrNote {
                Button(action: onEditProfile) {
                    HStack(spacing: 0) {
                        Text(note)
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(MetricColors.warn)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        PhosphorIcon.caretRight.image
                            .resizable()
                            .scaledToFit()
                            .frame(width: 16, height: 16)
                            .foregroundStyle(glowInk.opacity(0.7))
                            .padding(.leading, 8)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .contentShape(glassRowShape)
                }
                .buttonStyle(.plain)
                .glass(glassRowShape)
            }
        }
    }
}

// MARK: - Fitness (FitnessPanel, GlowActivityScreen.kt:333)

/// VO2 max and its trend across workouts, recovery time left from the last one, and max heart rate.
private struct FitnessPanel: View {
    var model: FitnessModel

    var body: some View {
        DotPanel {
            DotHeader("Fitness")
            HStack(alignment: .top, spacing: 0) {
                Fact(label: "VO2 max", value: model.vo2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Fact(label: "Recovery", value: model.recovery)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Fact(label: "Max HR", value: model.maxHr)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            if !model.spark.isEmpty {
                // `List<Float?>` crosses as boxed numbers: each gap is the `NSNumber` Kotlin boxes a Float into.
                Sparkline(values: model.spark.map { ($0 as? NSNumber)?.floatValue }, color: MetricColors.hrv)
                    .frame(height: 24)
            }
            if model.hasEstimate {
                DotDivider()
                Fact(label: "Estimated fitness (VO2 max)", value: model.estimate)
                QuietNote(text: model.estimateNote)
            } else if let note = model.withheldNote {
                DotDivider()
                QuietNote(text: note)
            }
            QuietNote(text: model.note)
        }
    }
}

// MARK: - Workouts (WorkoutsPanel, GlowActivityScreen.kt:375)

/// The day's workouts, then the most recent others, then a way to the full list.
private struct WorkoutsPanel: View {
    var model: WorkoutsPanelModel
    var onOpen: (Int64) -> Void
    var onAll: () -> Void

    var body: some View {
        DotPanel {
            DotHeader("Workouts", trailing: model.trailing)
            if let note = model.emptyNote {
                QuietNote(text: note)
            } else {
                if let note = model.noTodayNote {
                    QuietNote(text: note)
                }
                ForEach(model.today, id: \.workout.start) { analysis in
                    WorkoutLine(analysis: analysis, showDate: false) {
                        onOpen(analysis.workout.start)
                    }
                }
                if !model.recent.isEmpty {
                    DotDivider()
                    Text("RECENT")
                        .appTextStyle(AppTypography.labelSmall)
                        .foregroundStyle(glowInk.opacity(0.55))
                    ForEach(model.recent, id: \.workout.start) { analysis in
                        WorkoutLine(analysis: analysis, showDate: true) {
                            onOpen(analysis.workout.start)
                        }
                    }
                }
                if model.showAll {
                    Button(action: onAll) {
                        HStack(spacing: 0) {
                            Text("All workouts")
                                .appTextStyle(AppTypography.bodyMedium)
                                .foregroundStyle(glowInk)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            PhosphorIcon.caretRight.image
                                .resizable()
                                .scaledToFit()
                                .frame(width: 16, height: 16)
                                .foregroundStyle(glowInk.opacity(0.7))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .contentShape(glassRowShape)
                    }
                    .buttonStyle(.plain)
                    .glass(glassRowShape)
                }
            }
        }
    }
}
