import SwiftUI
import OpenStrapShared

/*
 * The month calendar in a bottom sheet, ported from
 * `app/src/main/kotlin/org/openstrap/app/ui/dashboard/DayCalendar.kt`.
 *
 * The grid arithmetic (which weekday the 1st falls on, how many week rows, each cell's day number)
 * comes from `GlowGeometry` in shared Kotlin, as standing instruction 5 asks; the locale's month and
 * weekday names come from Foundation, which is what Compose's `Locale.getDefault()` does there.
 */

/// The red full stop from the wordmark marks today. (`TodayDot`, DayCalendar.kt:57)
let todayDot = Color(hex: 0xFFFF4D5E)

/// What the date header's calendar needs: the days that have data, and where a picked day goes.
/// (`DayPicker`, DayCalendar.kt:54)
struct DayPicker {
    var available: Set<Date> = []
    var onPick: (Date) -> Void = { _ in }
}

/// Month calendar in a bottom sheet. Days with data are lit and tappable; the rest are dimmed and
/// can't be picked. Today is always pickable and carries the wordmark's red dot.
/// (`DayCalendarSheet`, DayCalendar.kt:64)
struct DayCalendarSheet: View {
    var selected: Date
    var available: Set<Date>
    var onPick: (Date) -> Void
    var onDismiss: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var month: Date
    @State private var direction: Int = 0

    private let calendar = Calendar.current

    init(selected: Date, available: Set<Date>, onPick: @escaping (Date) -> Void, onDismiss: @escaping () -> Void) {
        self.selected = selected
        self.available = available
        self.onPick = onPick
        self.onDismiss = onDismiss
        // The sheet opens on the month of the day it shows.
        let start = Calendar.current.dateInterval(of: .month, for: selected)?.start ?? selected
        _month = State(initialValue: start)
    }

    private var today: Date { calendar.startOfDay(for: Date()) }
    private var firstMonth: Date {
        let earliest = available.min().map { min($0, today) } ?? today
        return calendar.dateInterval(of: .month, for: earliest)?.start ?? today
    }
    private var lastMonth: Date { calendar.dateInterval(of: .month, for: today)?.start ?? today }

    var body: some View {
        // Glass over the blurred page, in the app's overlay (`GlassSheet`, Glass.kt:186).
        VStack(spacing: 0) {
            Color.clear.frame(width: 36, height: 4)
                .background(Color.white.opacity(0.3), in: RoundedRectangle(cornerRadius: 2, style: .continuous))
                .frame(maxWidth: .infinity)
                .padding(.top, 12)
                .padding(.bottom, 8)
            content
        }
        .frame(maxWidth: .infinity)
        .background {
            ZStack {
                GlowGlassBackground()
                Color(hex: 0xFF0B0B0D).opacity(0.72)
            }
        }
        .clipShape(
            UnevenRoundedRectangle(
                topLeadingRadius: 28, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 28,
                style: .continuous
            )
        )
        .overlay(
            UnevenRoundedRectangle(
                topLeadingRadius: 28, bottomLeadingRadius: 0, bottomTrailingRadius: 0, topTrailingRadius: 28,
                style: .continuous
            )
            .strokeBorder(
                LinearGradient(
                    stops: [
                        .init(color: .white.opacity(0.2), location: 0),
                        .init(color: .white.opacity(0.05), location: 0.5),
                        .init(color: .black.opacity(0.4), location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                ),
                lineWidth: 1
            )
        )
    }

    private var content: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Text(monthTitle)
                    .appTextStyle(AppTypography.titleLarge)
                    .foregroundStyle(glowInk)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button { step(-1) } label: {
                    PhosphorIcon.caretLeft.image
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                        .foregroundStyle(glowInk)
                        .padding(12)
                }
                .disabled(month <= firstMonth)
                .opacity(month <= firstMonth ? 0.38 : 1)
                .accessibilityLabel("Previous month")
                Button { step(1) } label: {
                    PhosphorIcon.caretRight.image
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                        .foregroundStyle(glowInk)
                        .padding(12)
                }
                .disabled(month >= lastMonth)
                .opacity(month >= lastMonth ? 0.38 : 1)
                .accessibilityLabel("Next month")
            }
            Color.clear.frame(height: 12)

            HStack(spacing: 0) {
                ForEach(Array(weekdayInitials.enumerated()), id: \.offset) { _, initial in
                    Text(initial.uppercased())
                        .appTextStyle(AppTypography.labelMedium)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                        .frame(maxWidth: .infinity)
                }
            }
            Color.clear.frame(height: 8)

            monthGrid
                .transition(
                    .asymmetric(
                        insertion: .move(edge: direction >= 0 ? .trailing : .leading).combined(with: .opacity),
                        removal: .move(edge: direction >= 0 ? .leading : .trailing).combined(with: .opacity)
                    )
                )
                .id(month)

            HStack(spacing: 0) {
                Circle()
                    .fill(ThemeColors.surfaceContainerHighest)
                    .overlay(Circle().stroke(ThemeColors.outline, lineWidth: 1))
                    .frame(width: 12, height: 12)
                Color.clear.frame(width: 6)
                Text("Has data")
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(ThemeColors.onSurfaceVariant)
                Color.clear.frame(width: 14)
                Circle()
                    .fill(todayDot)
                    .frame(width: 6, height: 6)
                Color.clear.frame(width: 6)
                Text("Today")
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(ThemeColors.onSurfaceVariant)
                Spacer(minLength: 0)
                if !calendar.isDate(selected, inSameDayAs: today) {
                    Button {
                        onPick(today)
                        onDismiss()
                    } label: {
                        Text("Go to today")
                            .appTextStyle(AppTypography.labelLarge)
                            .foregroundStyle(ThemeColors.primary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 8)
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 12)
    }

    // MARK: Month grid

    private var monthGrid: some View {
        let range = calendar.range(of: .day, in: .month, for: month)
        let daysInMonth = range?.count ?? 30
        let firstDay = calendar.dateInterval(of: .month, for: month)?.start ?? month
        let firstWeekday = kotlinWeekday(of: firstDay)
        let lead = Int(GlowGeometry.shared.calendarLead(
            firstDayOfMonthWeekday: Int32(firstWeekday),
            firstDayOfWeek: Int32(calendar.firstWeekday == 1 ? 7 : calendar.firstWeekday - 1)
        ))
        let weeks = Int(GlowGeometry.shared.calendarWeekCount(lead: Int32(lead), daysInMonth: Int32(daysInMonth)))
        // Today's day number when this month is today's, so future days stay unpickable; every day of
        // an earlier month is in the past, and a past month is the only other month on offer.
        let todayDay = calendar.isDate(month, equalTo: today, toGranularity: .month)
            ? calendar.component(.day, from: today) : 31
        let availableDays = Set(available.filter {
            calendar.isDate($0, equalTo: month, toGranularity: .month)
        }.map { calendar.component(.day, from: $0) })

        return VStack(spacing: 4) {
            ForEach(Array(0..<weeks), id: \.self) { week in
                HStack(spacing: 0) {
                    ForEach(Array(0..<7), id: \.self) { column in
                        let dayNumber = Int(GlowGeometry.shared.calendarDayNumber(
                            week: Int32(week), column: Int32(column), lead: Int32(lead)
                        ))
                        ZStack {
                            if dayNumber >= 1 && dayNumber <= daysInMonth {
                                let date = calendar.date(byAdding: .day, value: dayNumber - 1, to: firstDay) ?? firstDay
                                // `date <= today && (date in available || date == today)` (DayCalendar.kt:148).
                                let enabled = dayNumber <= todayDay
                                    && (availableDays.contains(dayNumber) || dayNumber == todayDay)
                                DayCell(
                                    day: dayNumber,
                                    selected: calendar.isDate(date, inSameDayAs: selected),
                                    today: calendar.isDate(date, inSameDayAs: today),
                                    enabled: enabled
                                ) {
                                    onPick(date)
                                    onDismiss()
                                }
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .aspectRatio(1, contentMode: .fit)
                    }
                }
            }
        }
    }

    /// `DayOfWeek.value`: Monday 1 … Sunday 7, where Foundation counts Sunday 1 … Saturday 7.
    private func kotlinWeekday(of date: Date) -> Int {
        let weekday = calendar.component(.weekday, from: date)
        return weekday == 1 ? 7 : weekday - 1
    }

    /// The locale's two-letter weekday names, starting from the locale's first day of the week
    /// (`DayCalendar.kt:88-95`).
    private var weekdayInitials: [String] {
        let symbols = calendar.shortWeekdaySymbols
        let first = calendar.firstWeekday
        return (0..<7).map { offset in
            let index = (first - 1 + offset) % 7
            return String(symbols[index].prefix(2))
        }
    }

    private var monthTitle: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM yyyy"
        return formatter.string(from: month)
    }

    private func step(_ months: Int) {
        guard let next = calendar.date(byAdding: .month, value: months, to: month) else { return }
        direction = months
        if reduceMotion {
            month = next
        } else {
            withAnimation(.easeInOut(duration: 0.22)) { month = next }
        }
    }
}

/// One day's cell: a circle that fills for the selected day, a hairline outline for a day with data,
/// and the wordmark's red dot under today. (`DayCell`, DayCalendar.kt:158)
private struct DayCell: View {
    var day: Int
    var selected: Bool
    var today: Bool
    var enabled: Bool
    var onPick: () -> Void

    private var textColor: Color {
        if selected { return ThemeColors.surface }
        return enabled ? ThemeColors.onSurface : ThemeColors.onSurfaceVariant.opacity(0.35)
    }

    var body: some View {
        ZStack {
            Circle()
                .fill(selected ? ThemeColors.onSurface : ThemeColors.surfaceContainerHighest)
                .overlay(
                    Circle().stroke(
                        enabled && !selected ? ThemeColors.outline : Color.clear,
                        lineWidth: 1
                    )
                )
                .frame(width: 42, height: 42)
                .opacity(selected || enabled ? 1 : 0)
            Text("\(day)")
                .appTextStyle(AppTypography.titleMedium)
                .foregroundStyle(textColor)
                .multilineTextAlignment(.center)
            if today {
                Circle()
                    .fill(todayDot)
                    .frame(width: 5, height: 5)
                    .padding(.bottom, 5)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Circle())
        .onTapGesture { if enabled { onPick() } }
        .accessibilityLabel("\(day)")
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
