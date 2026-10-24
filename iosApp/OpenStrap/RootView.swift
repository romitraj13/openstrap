import SwiftUI
import OpenStrapShared

/// The five destinations, in `MainActivity.kt`'s `Tab` order (`MainActivity.kt:125`).
enum AppTab: Int, CaseIterable, Identifiable {
    case today, recovery, sleep, activity, you

    var id: Int { rawValue }

    /// The tab's label, copied verbatim from the `Tab` enum.
    var label: String {
        switch self {
        case .today: return "Today"
        case .recovery: return "Recovery"
        case .sleep: return "Sleep"
        case .activity: return "Activity"
        case .you: return "You"
        }
    }

    var icon: PhosphorIcon {
        switch self {
        case .today: return .house
        case .recovery: return .plant
        case .sleep: return .moon
        case .activity: return .footprints
        case .you: return .user
        }
    }

    var selectedIcon: PhosphorIcon {
        switch self {
        case .today: return .houseFill
        case .sleep: return .moonFill
        case .recovery, .activity, .you: return icon
        }
    }

    var navItem: NavItem { NavItem(label: label, icon: icon, selectedIcon: selectedIcon) }

    /// The four tabs that show one shared day (`DayViewModel` is shared on Android).
    static let dated: [AppTab] = [.today, .recovery, .sleep, .activity]
}

/// The screens stacked under the You tab.
enum YouRoute: Hashable {
    case profile
    case strap
    case diagnostics
    case licences
}

/// The app: five tabs over one selected day, with the floating bar (card U2) instead of a
/// `TabView`. A fresh install starts on Pair instead, because there is no strap to read yet and
/// nothing to show.
struct RootView: View {
    @ObservedObject var controller: StrapController
    @ObservedObject var health: HealthModel

    @State private var tab: AppTab = .today
    /// The day Today, Recovery, Sleep and Activity all show, as Android's shared `DayViewModel` does.
    /// Card F7: the fixed demo day when `-demoData` is on, so the header and the seeded rows agree.
    @State private var selectedDate = HealthModel.today
    /// The You tab's own stack, held here so the header's watch chip can open You › Your strap.
    @State private var youPath: [YouRoute] = []

    private var items: [NavItem] { AppTab.allCases.map(\.navItem) }

    var body: some View {
        if controller.hasStoredKey || DemoData.isEnabled {
            shell
        } else {
            PairView(controller: controller)
        }
    }

    private var shell: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch tab {
                case .today:
                    DayTab(tab: .today, controller: controller, health: health, selectedDate: $selectedDate, onOpenStrap: openStrap) {
                        TodayScreen(controller: controller, health: health, onOpenTab: openTab)
                    }
                case .recovery:
                    DayTab(tab: .recovery, controller: controller, health: health, selectedDate: $selectedDate, onOpenStrap: openStrap) {
                        RecoveryScreen(health: health, controller: controller)
                    }
                case .sleep:
                    DayTab(tab: .sleep, controller: controller, health: health, selectedDate: $selectedDate, onOpenStrap: openStrap) {
                        SleepScreen(health: health, controller: controller)
                    }
                case .activity:
                    DayTab(tab: .activity, controller: controller, health: health, selectedDate: $selectedDate, onOpenStrap: openStrap) {
                        ActivityScreen(health: health, controller: controller)
                    }
                case .you:
                    YouTab(controller: controller, health: health, path: $youPath)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .id(tab)

            FloatingNavBar(items: items, selected: tab.rawValue) { index in
                guard let next = AppTab(rawValue: index) else { return }
                // Android clears every tab's detail stack when the bar is used.
                youPath = []
                tab = next
            }
            .frame(maxHeight: .infinity, alignment: .bottom)
        }
        .background(ThemeColors.background.ignoresSafeArea())
        // Card U8: `-glowStill` holds every glow core still, so a UI test's first query does not
        // wait for a card that never settles (card F5's `-designLabStill`, for the tab screens).
        .environment(\.glowPaused, DemoData.glowStill)
        .onAppear {
            health.prepareDemoData()
            health.reload()
            health.reloadDaysWithData()
            health.reloadSettings()
        }
        .onChange(of: selectedDate) { _, date in
            health.select(date)
        }
    }

    /// The header's watch chip: You › Your strap, whatever tab it was tapped on
    /// (`openStrap`, MainActivity.kt:262).
    private func openStrap() {
        youPath = [.strap]
        tab = .you
    }

    /// A Today card that opens another tab (`onOpenRecovery` / `onOpenActivity`,
    /// GlowTodayScreen.kt:73-83). Android also clears that tab's detail stack when the bar is used.
    private func openTab(_ next: AppTab) {
        youPath = []
        tab = next
    }
}

/// One of the four dated tabs: its own `NavigationStack`, the shared header, pull to sync, and the
/// tab's screen underneath. A detail opens inside this tab and never switches tabs.
struct DayTab<Content: View>: View {
    var tab: AppTab
    @ObservedObject var controller: StrapController
    @ObservedObject var health: HealthModel
    @Binding var selectedDate: Date
    var onOpenStrap: () -> Void
    @ViewBuilder var content: () -> Content

    @State private var path = NavigationPath()

    /// Card F9: the phone's calendar, or the pinned demo day's while `-demoData` is on, so a day
    /// step lands on the same midnight the header and the shared queries measure.
    private var calendar: Calendar { ScreenNow.calendar }
    /// The last day the header may step forward to: the phone's today, or the pinned demo day.
    private var today: Date { ScreenNow.startOfToday }
    private var battery: Int? {
        // The chip only carries a battery while the strap is connected, as Compose's
        // `(connection as? Connected)?.batteryPercent` does.
        controller.state == "ready" && controller.battery >= 0 ? controller.battery : nil
    }

    var body: some View {
        NavigationStack(path: $path) {
            PullSyncScroll(syncing: controller.syncRunning) {
                controller.syncNow()
            } content: {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 12) {
                        DateHeader(
                            date: selectedDate,
                            battery: battery,
                            charging: battery != nil && controller.charging,
                            picker: DayPicker(available: health.daysWithData, onPick: { selectedDate = $0 }),
                            onPrev: { step(-1) },
                            onNext: { step(1) },
                            onOpenStrap: onOpenStrap
                        )
                        content()
                    }
                    .padding(.horizontal, 16)
                    // Card F13: the bar's clearance is the list's own last 136 pt, not a bottom
                    // safe-area inset. Today's pan keeps the range of `contentSize - bounds` and drops
                    // the inset on most runs (runs 40040762359, 40036175056, 40044419882: 30 and 45 drags
                    // both rested at 1378.67, or 127 pt short of the 1505.67 its inset reports), so the
                    // last card came to rest under the bar; a list whose height carries the clearance
                    // cannot lose it. This is the Compose rule: `ScreenList` gives the bar's room to the
                    // list's own `contentPadding` (`ScreenParts.kt:80-84`), and a LazyColumn has no
                    // scroll inset to drop.
                    Color.clear
                        .frame(height: GlowLayout.barClearance + 24)
                        .accessibilityHidden(true)
                }
            }
            .background(ThemeColors.background.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .onAppear { health.reload() }
            .onChange(of: controller.lastSyncEpochSeconds) { _, _ in
                health.reload()
                health.reloadDaysWithData()
            }
        }
    }

    /// One day back or forward, never past today (`DayViewModel.previousDay`/`nextDay`).
    private func step(_ days: Int) {
        guard let next = calendar.date(byAdding: .day, value: days, to: selectedDate) else { return }
        if days > 0 && next > today { return }
        selectedDate = calendar.startOfDay(for: next)
    }
}

/// The You tab: its own stack, holding the list and everything it opens.
struct YouTab: View {
    @ObservedObject var controller: StrapController
    @ObservedObject var health: HealthModel
    @Binding var path: [YouRoute]

    var body: some View {
        NavigationStack(path: $path) {
            YouView(controller: controller, health: health) { route in
                path.append(route)
            }
            .toolbar(.hidden, for: .navigationBar)
            .navigationDestination(for: YouRoute.self) { route in
                switch route {
                case .profile:
                    ProfileScreen(health: health)
                case .strap:
                    StrapView(controller: controller, onDiagnostics: { path.append(.diagnostics) })
                case .diagnostics:
                    DiagnosticsView(controller: controller)
                case .licences:
                    LicencesView()
                }
            }
        }
    }
}
