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
    /// Card F11: which build of the tab's scroll view is on screen. It is rebuilt once, on the frame
    /// after the list first appears, so the scroll view measures a list whose cards have all been laid
    /// out at least once (see the `onAppear` on the scroll view below).
    @State private var listLayout = 0

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
            Group {
                if ready {
                    PullSyncScroll(syncing: controller.syncRunning) {
                        controller.syncNow()
                    } content: {
                        VStack(alignment: .leading, spacing: 12) {
                            header
                            content()
                        }
                        .padding(.horizontal, 16)
                    }
                    .id(listLayout)
                    .glowClearsNavBar()
                    .onAppear {
                        // Card F11: one frame after the list first appears, build it again. The first
                        // layout can commit the list's height before the last card has been laid out
                        // (measured: a content frame of 2002 pt with a 2172.67 pt list drawn into it, so
                        // the drags stop 170 pt short of the end and the last card stays under the bar).
                        // On the second pass the cards have been laid out once and the scroll view
                        // measures the list it actually draws.
                        guard tab == .today, listLayout == 0 else { return }
                        DispatchQueue.main.async { listLayout = 1 }
                    }
                } else {
                    // Card F11: Today's scroll view is not built until the reads that feed its list have
                    // all answered, so the list it lays out first is the list it keeps. A scroll view that
                    // exists while its content is still arriving clamps its gestures to a height short of
                    // the cards it ends up drawing (measured: a range ending at 1444.67, drags stopping
                    // dead at 1309.0), which left Today's last card under the floating bar. The other
                    // three tabs are built when the bar switches to them, with their data already read;
                    // this is the same rule for the one tab that is on screen from launch.
                    //
                    // The header is drawn while the first read is in, as it was before the gate: it does
                    // not depend on the list, and a launch should show the wordmark and the day rather
                    // than a black window.
                    VStack(alignment: .leading, spacing: 12) {
                        header
                    }
                    .padding(.horizontal, 16)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                }
            }
            .background(ThemeColors.background.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
            .onAppear {
                // Today's own reads, asked for here rather than by the screen, because the screen is not
                // built until they have answered.
                if tab == .today && health.dayState == nil { health.reloadToday() }
                health.reload()
            }
            .onChange(of: controller.lastSyncEpochSeconds) { _, _ in
                health.reload()
                health.reloadDaysWithData()
            }
        }
    }

    /// The shared header: wordmark and chip, then the day row. Part of the scrolled content, and on
    /// its own while Today's first read is in.
    @ViewBuilder private var header: some View {
        DateHeader(
            date: selectedDate,
            battery: battery,
            charging: battery != nil && controller.charging,
            picker: DayPicker(available: health.daysWithData, onPick: { selectedDate = $0 }),
            onPrev: { step(-1) },
            onNext: { step(1) },
            onOpenStrap: onOpenStrap
        )
    }

    /// Card F11: a dated tab may draw its list once every read that puts a card on it has answered.
    /// Only Today can be waiting: the other three are built when the bar switches to them, after the
    /// same reads have already landed. `todayWorkoutsRead` stays true after its first answer, so this
    /// only gates the list's first build and a later reload keeps the scroll view the reader is in.
    private var ready: Bool {
        tab != .today || (health.dayState != nil && health.todayWorkoutsRead)
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
