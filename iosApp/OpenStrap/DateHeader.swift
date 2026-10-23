import SwiftUI
import UIKit

/*
 * The top of every date screen: the OpenHealth wordmark and the strap chip, the gap a pull opens,
 * then ‹ TODAY › with a calendar of the days that have data.
 *
 * Ported from `DateHeader` (`app/src/main/kotlin/org/openstrap/app/ui/dashboard/ScreenParts.kt:101`)
 * and `PullToSync`/`PullSyncReveal` (`app/src/main/kotlin/org/openstrap/app/ui/components/
 * PullToSync.kt`).
 *
 * Compose reads the pull distance out of Material's `PullToRefreshState`; SwiftUI's `.refreshable`
 * keeps that to itself, so `PullSyncScroll` measures the scroll view's own overscroll instead and
 * publishes the same thing: the fraction of the way to the sync point, and whether a sync is
 * running. The card asks for `.refreshable` for the gesture, and this for the header.
 */

/// How far the pull has gone (1 = far enough to sync) and whether a sync is running.
/// (`PullSyncState`, PullToSync.kt:55)
struct PullSyncState: Equatable {
    var fraction: CGFloat = 0
    var syncing: Bool = false
}

private struct PullSyncKey: EnvironmentKey {
    static let defaultValue = PullSyncState()
}

extension EnvironmentValues {
    /// Set by `PullSyncScroll`; `PullSyncReveal` reads it. (`LocalPullSync`, PullToSync.kt:58)
    var pullSync: PullSyncState {
        get { self[PullSyncKey.self] }
        set { self[PullSyncKey.self] = newValue }
    }
}

private struct PullOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

/// A screen's scroll view with pull to sync. Any sync opens the same gap in the header, pulled or
/// not (on connect, in the background), so it is the app's one sync indicator.
struct PullSyncScroll<Content: View>: View {
    /// The distance a full pull travels; at this offset the header reads "Release to sync strap".
    static var pullDistance: CGFloat { 80 }

    var syncing: Bool
    /// Card F11: the identity of the scrolled content. When it changes the scroll view is built again,
    /// which is what makes it take the list's new height into its scroll range: today's workouts card
    /// arrives after the list has laid out, and a scroll view that has already measured its content
    /// keeps the range it had (measured: 2172.67 pt of content, drags stopping at offset 1308.0 with
    /// the range's end at 1444.67). See `HealthModel.todayListKey`. Empty — one identity for the life
    /// of the tab — for a screen whose cards are all there at first layout.
    var contentKey: String = ""
    var onSync: () -> Void
    @ViewBuilder var content: () -> Content

    @State private var pull: CGFloat = 0

    private let space = "pullSync"

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                GeometryReader { proxy in
                    Color.clear.preference(key: PullOffsetKey.self, value: proxy.frame(in: .named(space)).minY)
                }
                .frame(height: 0)
                content()
                #if DEBUG
                // Card F11, temporary: the probe the UI test reads to see this scroll view's own
                // geometry. Removed before the card's final commit.
                ScrollGeometryProbe()
                    .frame(width: 1, height: 1)
                #endif
            }
        }
        .coordinateSpace(name: space)
        .onPreferenceChange(PullOffsetKey.self) { offset in
            // Only a pull down, and only while it is being pulled: the offset returns to 0 on
            // release and the reveal stays open through the sync instead.
            pull = max(0, offset)
        }
        .environment(\.pullSync, PullSyncState(fraction: fraction, syncing: syncing))
        .refreshable { onSync() }
        .id(contentKey)
    }

    /// The fraction the header draws with: the pull, or a full one while a sync runs.
    private var fraction: CGFloat {
        let pulled = min(max(pull / Self.pullDistance, 0), 1.6)
        return max(pulled, syncing ? 1 : 0)
    }
}

/// The gap a pull opens in the header: a strap icon that turns as you pull and pulses while
/// syncing, with the label under it. (`PullSyncReveal`, PullToSync.kt:127)
struct PullSyncReveal: View {
    @Environment(\.pullSync) private var pull
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false

    private let reveal: CGFloat = 48

    var body: some View {
        let fraction = min(max(pull.fraction, 0), 1.6)
        let alpha = min(max(pull.fraction, 0), 1)
        let ready = pull.fraction >= 1
        HStack(spacing: 8) {
            PhosphorIcon.watch.image
                .resizable()
                .scaledToFit()
                .frame(width: 18, height: 18)
                // Compose turns the icon with the pull and spins it while syncing. Standing
                // instruction 7 allows only offset, scale and opacity to animate, so the spin
                // becomes a pulse; the pull's turn follows the finger and is not an animation.
                .rotationEffect(.degrees(reduceMotion ? 0 : Double(fraction * 180)))
                .opacity(pull.syncing && !reduceMotion ? (pulse ? 1 : 0.35) : 1)
                .foregroundStyle(ready || pull.syncing ? ThemeColors.primary : ThemeColors.onSurfaceVariant)
            Text(label(ready: ready))
                .appTextStyle(AppTypography.bodySmall)
                .foregroundStyle(ThemeColors.onSurfaceVariant)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .frame(height: fraction * reveal)
        .opacity(alpha)
        .clipped()
        .accessibilityHidden(true)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: pulse)
        .onChange(of: pull.syncing) { _, syncing in
            pulse = syncing && !reduceMotion
        }
    }

    private func label(ready: Bool) -> String {
        if pull.syncing { return "Syncing strap…" }
        return ready ? "Release to sync strap" : "Pull to sync strap"
    }
}

/// Top bar: OpenHealth wordmark and strap battery chip, then ‹ TODAY › centered on its own row.
/// Tapping the day opens a calendar of the days that have data. (`DateHeader`, ScreenParts.kt:101)
struct DateHeader: View {
    var date: Date
    var battery: Int?
    var charging: Bool
    var picker: DayPicker
    var onPrev: () -> Void
    var onNext: () -> Void
    var onOpenStrap: () -> Void

    @State private var calendarOpen = false

    /// Card F9: the phone's calendar, or the pinned demo day's while `-demoData` is on.
    private var calendar: Calendar { ScreenNow.calendar }
    private let wordmarkDot = Color(hex: 0xFFFF4D5E)

    /// The phone's today, or the fixed demo day while `-demoData` is pinned (card F7/F9).
    private var today: Date { ScreenNow.startOfToday }
    private var isToday: Bool { calendar.isDate(date, inSameDayAs: today) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                wordmark
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityIdentifier("app.wordmark")
                strapChip
            }
            .padding(.top, 8)

            // Pull to sync opens here, under the wordmark, and pushes the day and everything
            // below it down.
            PullSyncReveal()

            HStack(spacing: 0) {
                Spacer(minLength: 0)
                Button(action: onPrev) {
                    PhosphorIcon.caretLeft.image
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                        .foregroundStyle(glowInk)
                        .padding(12)
                }
                .accessibilityLabel("Previous day")
                .accessibilityIdentifier("date.previous")

                Button {
                    calendarOpen = true
                } label: {
                    HStack(spacing: 4) {
                        SectionLabel(Self.label(for: date), color: ThemeColors.onSurface)
                        PhosphorIcon.caretDown.image
                            .resizable()
                            .scaledToFit()
                            .frame(width: 14, height: 14)
                            .foregroundStyle(ThemeColors.onSurfaceVariant)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .contentShape(Capsule())
                }
                .accessibilityLabel("Pick a day")
                .accessibilityIdentifier("date.pick")

                Button(action: onNext) {
                    PhosphorIcon.caretRight.image
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                        .foregroundStyle(glowInk)
                        .padding(12)
                }
                .disabled(isToday)
                .opacity(isToday ? 0.38 : 1)
                .accessibilityLabel("Next day")
                .accessibilityIdentifier("date.next")
                Spacer(minLength: 0)
            }
        }
        .sheet(isPresented: $calendarOpen) {
            DayCalendarSheet(
                selected: date,
                available: picker.available,
                onPick: { picker.onPick($0) },
                onDismiss: { calendarOpen = false }
            )
            .presentationDetents([.height(520)])
            .presentationDragIndicator(.hidden)
            .presentationBackground(.clear)
            .presentationCornerRadius(28)
        }
    }

    /// "OpenHealth." with the red full stop (`buildAnnotatedString`, ScreenParts.kt:113-124).
    ///
    /// Card R1: the text deliberately differs from Android's wordmark, which keeps upstream's name
    /// OpenStrap. The iPhone app's visible name is OpenHealth (Romit's decision, 2026-10-22), while
    /// the Android app, which is upstream's, stays as it is. Only the text differs: the font, size,
    /// weight and the red period are the Compose screen's.
    private var wordmark: Text {
        let font = DesignFontFamily.museoModerno.font(size: 22, weight: 600)
        return Text("OpenHealth").font(font).foregroundColor(ThemeColors.onSurface)
            + Text(".").font(font).foregroundColor(wordmarkDot)
    }

    /// WHOOP's strap chip: always there, and it opens "Your strap". (ScreenParts.kt:126-148)
    private var strapChip: some View {
        Button(action: onOpenStrap) {
            HStack(spacing: 4) {
                if let battery {
                    Text(charging ? "⚡\(battery)%" : "\(battery)%")
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(
                            battery <= 15 || charging
                                ? MetricColors.battery(battery)
                                : ThemeColors.onSurfaceVariant
                        )
                }
                ZStack(alignment: .topTrailing) {
                    PhosphorIcon.watch.image
                        .resizable()
                        .scaledToFit()
                        .frame(width: 20, height: 20)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                    if let battery {
                        Circle()
                            .fill(MetricColors.battery(battery))
                            .frame(width: 6, height: 6)
                    }
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .contentShape(Capsule())
        }
        .accessibilityLabel(battery != nil ? "Strap connected" : "Strap not connected")
        .accessibilityValue("Open your strap")
        .accessibilityIdentifier("header.strap")
    }

    /// "Today", "Yesterday", or the date ("EEE d MMM"), as `DateHeader` formats it before
    /// `SectionLabel` uppercases it.
    ///
    /// Card F9: the two words compare the day against the day the app is showing as today, not
    /// against the system clock. `Calendar.isDateInToday` / `isDateInYesterday` ask the wall clock,
    /// so on a CI run at a later real date they called the pinned demo day "Yesterday" while every
    /// shared date said Today.
    static func label(
        for date: Date,
        today: Date = ScreenNow.startOfToday,
        calendar: Calendar = ScreenNow.calendar
    ) -> String {
        let day = calendar.startOfDay(for: date)
        if day == today { return "Today" }
        if let yesterday = calendar.date(byAdding: .day, value: -1, to: today), day == yesterday {
            return "Yesterday"
        }
        let formatter = DateFormatter()
        formatter.locale = Locale.current
        formatter.calendar = calendar
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = "EEE d MMM"
        return formatter.string(from: date)
    }
}

#if DEBUG
/// Card F11, temporary probe (removed before this card's final commit): the enclosing scroll view's
/// own geometry, read by `testEveryDatedTabScrollsClearOfTheBar`.
///
/// The label is computed when the test takes its accessibility snapshot rather than when the view is
/// laid out, so the offset it reports is the offset at the end of the walk. `rangeEndY` is the largest
/// content offset the scroll view will accept (`contentSize + bottom inset - bounds`): the number the
/// walk's end is supposed to reach.
private final class ScrollGeometryView: UIView {
    override var accessibilityLabel: String? {
        get {
            var ancestor = superview
            while let candidate = ancestor, !(candidate is UIScrollView) {
                ancestor = candidate.superview
            }
            guard let scroll = ancestor as? UIScrollView else { return "no enclosing scroll view" }
            let inset = scroll.adjustedContentInset
            let content = scroll.contentSize.height
            let height = scroll.bounds.height
            return "contentH=\(content) boundsH=\(height) insetTop=\(inset.top) "
                + "insetBottom=\(inset.bottom) offsetY=\(scroll.contentOffset.y) "
                + "rangeEndY=\(content + inset.bottom - height)"
        }
        set { }
    }
}

struct ScrollGeometryProbe: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = ScrollGeometryView(frame: CGRect(x: 0, y: 0, width: 1, height: 1))
        view.isAccessibilityElement = true
        view.accessibilityIdentifier = "debug.scrollGeom"
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ view: UIView, context: Context) { }
}
#endif
