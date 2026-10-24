import OpenStrapShared
import SwiftUI

/*
 * The last seven nights on one clock, ported from `SleepWindows` in
 * `app/src/main/kotlin/org/openstrap/app/ui/dashboard/SleepPanels.kt:137-210` (card U7).
 *
 * Evening at the top, morning at the bottom, one bar a night from falling asleep to waking, the time
 * asleep over each bar, and dashed lines at your average times. Where every mark goes is
 * `sleepWindows` in shared Kotlin, because that is arithmetic about a clock; this file only draws.
 *
 * One deviation from Compose: its axis is a bare `TextStyle(fontSize = 11.sp)`, which falls back to
 * the platform font. The design system here has no such fallback, so the axis is Funnel Sans 11 and
 * the values Funnel Display 11, the family Compose names for them.
 */

/// `axis` (:141): eleven-point labels down the left and under the columns.
private let windowAxisStyle = AppTextStyle(
    name: "sleep.windows.axis", family: .funnelSans, size: 11, weight: 400, tracking: 0, lineHeight: nil)

/// `value` (:143): the time asleep over each bar, in the display family.
private let windowValueStyle = AppTextStyle(
    name: "sleep.windows.value", family: .funnelDisplay, size: 11, weight: 400, tracking: 0, lineHeight: nil)

/// A chart of the week's nights. (`SleepWindows`, SleepPanels.kt:137)
struct GlowSleepWindows: View {
    var model: SleepWindowsModel

    /// 200.dp (:158), with the two label bands above and below the plot.
    private let height: CGFloat = 200
    private let labelHeight: CGFloat = 18
    private let valueHeight: CGFloat = 18
    /// The clock labels' gutter (:159).
    private let left: CGFloat = 40

    var body: some View {
        Canvas { context, size in
            draw(context, size)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .accessibilityHidden(true)
    }

    private func draw(_ context: GraphicsContext, _ size: CGSize) {
        let chartTop = valueHeight
        let chartBottom = size.height - labelHeight
        let count = max(model.nights.count, 1)
        let slot = (size.width - left) / CGFloat(count)
        let top = CGFloat(model.topMinute)
        let span = CGFloat(max(model.bottomMinute - model.topMinute, 1))
        func yOf(_ minute: Int32) -> CGFloat {
            chartTop + (chartBottom - chartTop) * (CGFloat(minute) - top) / span
        }

        // Hour marks: top, bottom and every three hours between (:169-176).
        for hour in model.hours {
            let y = yOf(hour.minute)
            var line = Path()
            line.move(to: CGPoint(x: left, y: y))
            line.addLine(to: CGPoint(x: size.width, y: y))
            context.stroke(
                line,
                with: .color(ThemeColors.onSurfaceVariant.opacity(0.15)),
                style: StrokeStyle(lineWidth: 1)
            )
            context.draw(
                Text(hour.label)
                    .font(Font(windowAxisStyle.uiFont))
                    .foregroundStyle(ThemeColors.onSurfaceVariant),
                at: CGPoint(x: 0, y: y),
                anchor: .leading
            )
        }

        // Your average bedtime and wake time, dashed (:177-183).
        if model.hasAverage {
            for minute in [model.averageBedtimeMinute, model.averageWakeMinute] {
                let y = yOf(minute)
                var path = Path()
                path.move(to: CGPoint(x: left, y: y))
                path.addLine(to: CGPoint(x: size.width, y: y))
                context.stroke(
                    path,
                    with: .color(ThemeColors.onSurface.opacity(0.5)),
                    style: StrokeStyle(lineWidth: 1, dash: [3, 4])
                )
            }
        }

        let width = slot * 0.42
        for (index, night) in model.nights.enumerated() {
            let centre = left + slot * CGFloat(index) + slot / 2
            // The weekday under the column, the last night brighter than the rest (:187-189).
            context.draw(
                Text(night.label)
                    .font(Font(windowAxisStyle.uiFont))
                    .foregroundStyle(night.isToday ? ThemeColors.onSurface : ThemeColors.onSurfaceVariant),
                at: CGPoint(x: centre, y: chartBottom + 4),
                anchor: .top
            )
            guard night.hasSleep else {
                // A night the strap never recorded is a dot, not a bar (:191-193).
                context.fill(
                    Path(ellipseIn: CGRect(x: centre - 2.5, y: (chartTop + chartBottom) / 2 - 2.5, width: 5, height: 5)),
                    with: .color(ThemeColors.onSurfaceVariant.opacity(0.35))
                )
                continue
            }
            let y0 = yOf(night.startMinute)
            let y1 = yOf(night.endMinute)
            let bar = MetricColors.sleepDeep
            let rect = CGRect(x: centre - width / 2, y: y0, width: width, height: max(y1 - y0, width))
            context.fill(
                Path(roundedRect: rect, cornerRadius: width / 2),
                with: .color(night.isToday ? bar : bar.opacity(0.7))
            )
            // The time asleep above the bar, in the green that says the goal was met (:197-207).
            context.draw(
                Text(night.asleepLabel)
                    .font(Font(windowValueStyle.uiFont))
                    .foregroundStyle(night.metGoal ? MetricColors.sleepRem : ThemeColors.onSurface),
                at: CGPoint(x: centre, y: y0 - 2),
                anchor: .bottom
            )
        }
    }
}
