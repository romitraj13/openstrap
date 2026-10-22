import OpenStrapShared
import SwiftUI

/*
 * One metric's detail, ported from `GlowMetricDetail.kt` (card U9), with `Routes.kt`'s `Metric` and
 * `MetricInfo.kt`'s colours, related list and About copy.
 *
 * The same screen serves every metric: the range switch (Day, Week, Month, Year), the latest card
 * with its chart, Your normal, About and Related, in the order `GlowMetricDetailScreen` (:131-145)
 * builds them. A day shows the day's own series; a week, month or year shows one value a day (a week
 * over a year) against your normal, with the range's lowest and highest underneath.
 *
 * Every number, date and sentence comes from `MetricDetailGlow` in shared Kotlin, so both builds read
 * the same values; this file draws them. The one thing it decides is which chart type to draw, and
 * the model says that too.
 */

/// The colours `MetricInfo.kt`'s `colorOf` names, from `Theme.swift`'s tokens (standing instruction 6).
private func metricColor(_ color: MetricColor) -> Color {
    switch color.name {
    case "Heart": return MetricColors.heart
    case "Hrv": return MetricColors.hrv
    case "Spo2": return MetricColors.spo2
    case "Stress": return MetricColors.stress
    case "Temperature": return MetricColors.temperature
    default: return MetricColors.steps
    }
}

/// A metric's detail: its range switch, its card, and the three dot panels under it.
struct MetricDetailScreen: View {
    @ObservedObject var health: HealthModel
    var metric: Metric
    /// The strap, for the Live switch on today's heart rate (`canUseLive`, GlowMetricDetail.kt:135).
    /// Nil where the screen was pushed from a tab that holds no controller: no strap, no switch.
    var controller: StrapController? = nil

    @Environment(\.dismiss) private var dismiss

    /// `var rangeIndex by rememberSaveable(metric) { mutableIntStateOf(0) }` (:121).
    @State private var rangeIndex = 0
    /// The range's daily values, `null` until the read lands — Compose's `collectAsState(null)` (:125).
    @State private var stats: [DayStat]?
    /// A related metric's detail, pushed on top of this one as Android pushes onto the tab's stack (:145).
    @State private var openMetricName: String?

    var body: some View {
        Group {
            if let day = health.dayState {
                content(day)
            } else {
                // The day's first read is in flight. Nothing is invented for the wait.
                Color.clear.frame(height: 1)
            }
        }
        .background(ThemeColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(item: $openMetricName) { name in
            if let next = TodayGlow.shared.metric(name: name) {
                MetricDetailScreen(health: health, metric: next, controller: controller)
            }
        }
        .onAppear {
            if health.dayState == nil { health.reloadToday() }
            loadHistory()
        }
        .onChange(of: rangeIndex) { _, _ in loadHistory() }
    }

    /// The back arrow, the range switch, the card and the panels, in Compose's order (:131-145).
    private func content(_ day: DayUiState) -> some View {
        let model = MetricDetailGlow.shared.model(
            metric: metric,
            state: day,
            stats: stats,
            rangeIndex: Int32(rangeIndex),
            liveEnabled: controller?.liveHeartRateEnabled ?? false,
            live: controller?.heartRate.map { KotlinInt(int: Int32($0)) },
            canUseLive: controller?.state == "ready"
        )
        return ScrollView {
            VStack(alignment: .leading, spacing: GlowLayout.columnGap) {
                GlowDetailHeader(title: model.title, subtitle: model.subtitle) {
                    dismiss()
                }
                GlowSegments(options: model.ranges, selected: Int(model.rangeIndex)) { index in
                    rangeIndex = index
                }
                MetricCardView(
                    model: model.card,
                    accent: metricColor(model.accent),
                    metric: metric,
                    onLiveToggle: { on in controller?.setLiveHeartRate(on) }
                )
                if !model.lowHigh.isEmpty {
                    LowHighPanel(rows: model.lowHigh)
                }
                NormalPanel(model: model.normal, accent: metricColor(model.accent))
                AboutPanel(model: model.about)
                if !model.related.isEmpty {
                    RelatedPanel(rows: model.related) { next in
                        openMetricName = next.name
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
    }

    /// Asks for the series this range needs, and drops an answer for a range already left.
    private func loadHistory() {
        let requested = rangeIndex
        // Compose keys the read on `(metric, end, from)`, so a new range starts from "Loading…" (:125).
        stats = nil
        health.loadHistory(
            metric: metric,
            range: MetricDetailGlow.shared.historyRange(rangeIndex: Int32(requested)),
            date: health.date
        ) { list in
            guard rangeIndex == requested else { return }
            stats = list
        }
    }
}

// MARK: - The card (DayCard and HistoryCard, GlowMetricDetail.kt:177-308)

/// The metric's leading card: the header with its Live switch, the number and its facts, and the
/// chart for the range. One glow card per screen, in the metric's colour (:197, :264).
private struct MetricCardView: View {
    var model: MetricCardModel
    var accent: Color
    /// The metric itself, which the chart's average label formats with (`metric.format`, :250).
    var metric: Metric
    var onLiveToggle: (Bool) -> Void

    private var style: GlowStyle {
        var style = GlowStyle.chart(accent)
        style.motion = model.motion == "Heartbeat"
            ? .heartbeat(bpm: Int(model.heartbeat))
            : .breath(perMinute: CGFloat(model.breathPerMinute))
        return style
    }

    var body: some View {
        GlowCard(style, sizesToContent: true) {
            VStack(alignment: .leading, spacing: 0) {
                GlowHeader(model.header, trailing: model.trailing) {
                    if model.live {
                        // `Row { Text("Live", …); Spacer(8.dp); GlowSwitch(on, onLiveToggle, accent = color) }` (:207-215).
                        HStack(spacing: 0) {
                            Text("Live")
                                .appTextStyle(AppTypography.bodySmall)
                                .foregroundStyle(glowInk.opacity(0.85))
                            Color.clear.frame(width: 8, height: 0)
                            GlowSwitch(
                                checked: Binding(get: { model.liveOn }, set: { onLiveToggle($0) }),
                                accent: accent
                            )
                        }
                    }
                }
                if let value = model.value {
                    GlowHeroNumber(value, unit: model.unit, facts: model.facts)
                        .accessibilityIdentifier("metric.detail.hero")
                    if let chart = model.chart {
                        GlowChartGap()
                        MetricChartView(chart: chart, accent: accent, metric: metric)
                        if let legend = chart.legend {
                            // `Spacer(10.dp); Row { 10×6 box at 25 %; 8.dp; Text(legend, 70 %) }` (:298-305).
                            Color.clear.frame(height: 10)
                            HStack(spacing: 0) {
                                RoundedRectangle(cornerRadius: 2, style: .continuous)
                                    .fill(Color.white.opacity(0.25))
                                    .frame(width: 10, height: 6)
                                Color.clear.frame(width: 8, height: 0)
                                Text(legend)
                                    .appTextStyle(AppTypography.bodySmall)
                                    .foregroundStyle(glowInk.opacity(0.7))
                            }
                        }
                    }
                } else {
                    Color.clear.frame(height: GlowLayout.titleGap)
                    Text(model.emptyText ?? "")
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(glowInk.opacity(0.85))
                        .accessibilityIdentifier("metric.detail.empty")
                }
            }
        }
    }
}

/// The chart the model names: the day's hour bars, the resting trend, or a line with your normal as
/// a band. (`GlowDotBars` :236, `GlowTrend` :242, `GlowLineChart` :250, :289)
private struct MetricChartView: View {
    var chart: MetricChartModel
    var accent: Color
    var metric: Metric

    var body: some View {
        switch chart.kind.name {
        case "Bars":
            GlowDotBars(
                // Kotlin's `List<Float>` arrives boxed, like every chart series (WorkoutScreen.swift:92).
                values: chart.bars.map { ($0 as? NSNumber)?.floatValue ?? 0 },
                height: GlowLayout.chartHeight,
                labels: chart.labels,
                accent: accent,
                describe: { index, _ in readout(index) }
            )
        case "Trend":
            GlowTrend(
                days: chart.days.map { GlowDay(label: $0.label, value: ($0.value as? NSNumber)?.floatValue) },
                height: GlowLayout.chartHeight
            )
        default:
            GlowLineChart(
                values: chart.values.map { ($0 as? NSNumber)?.floatValue },
                height: GlowLayout.chartHeight,
                labels: chart.labels,
                showAverage: chart.showAverage,
                color: accent,
                band: band,
                format: { value in MetricDetailGlow.shared.format(metric: metric, value: value) },
                describe: { index, _ in readout(index) }
            )
        }
    }

    /// Your normal as the faint band between two dotted lines, when the model has one (:291).
    private var band: ClosedRange<Float>? {
        guard let low = (chart.bandLow as? NSNumber)?.floatValue,
              let high = (chart.bandHigh as? NSNumber)?.floatValue else { return nil }
        return low...high
    }

    /// The touch text the model worded; the axis only asks at a point that has a value.
    private func readout(_ index: Int) -> String {
        guard index >= 0 && index < chart.readouts.count else { return "" }
        return chart.readouts[index]
    }
}

// MARK: - Lowest and highest (LowHighPanel, GlowMetricDetail.kt:332)

/// The range's lowest and highest, each with its day.
private struct LowHighPanel: View {
    var rows: [MetricFactRow]

    var body: some View {
        DotPanel {
            HStack(alignment: .top, spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    VStack(alignment: .leading, spacing: 0) {
                        Fact(label: row.label, value: row.value)
                        QuietNote(text: row.note)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }
}

// MARK: - Your normal (NormalPanel, GlowMetricDetail.kt:348)

/// Your normal over the last 30 days, or how many of the seven days it needs there are.
private struct NormalPanel: View {
    var model: MetricNormalModel
    var accent: Color

    var body: some View {
        DotPanel {
            DotHeader("Your normal", trailing: model.trailing)
            if model.hasNormal {
                HStack(alignment: .top, spacing: 0) {
                    Fact(label: "Usual", value: model.usual)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Fact(label: "Range", value: model.range)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                QuietNote(text: model.note)
            } else {
                QuietNote(text: model.note)
                GlowBar(
                    fraction: (model.progress as? NSNumber)?.floatValue ?? 0,
                    color: accent
                )
            }
        }
    }
}

// MARK: - About (AboutPanel, GlowMetricDetail.kt:364)

/// What the metric is, and what moves it.
private struct AboutPanel: View {
    var model: MetricAboutModel

    var body: some View {
        DotPanel {
            DotHeader(model.title)
            Text(model.what)
                .appTextStyle(AppTypography.bodyMedium)
                .foregroundStyle(glowInk.opacity(0.9))
            QuietNote(text: model.moves)
        }
    }
}

// MARK: - Related (RelatedPanel, GlowMetricDetail.kt:374)

/// The metrics the reader is likely to want next, each a glass row with its colour's dot.
private struct RelatedPanel: View {
    var rows: [RelatedMetricRow]
    var onOpen: (Metric) -> Void

    var body: some View {
        DotPanel {
            DotHeader("Related")
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 0) {
                    Circle()
                        .fill(metricColor(row.color))
                        .frame(width: 7, height: 7)
                        .padding(.trailing, 10)
                    Text(row.metric.title)
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
                .glass(glassRowShape)
                .contentShape(glassRowShape)
                .onTapGesture { onOpen(row.metric) }
                .accessibilityIdentifier("metric.detail.related.\(row.metric.name)")
            }
        }
    }
}
