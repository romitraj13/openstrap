import SwiftUI
import OpenStrapShared

/*
 * The "You" tab, ported from `app/src/main/kotlin/org/openstrap/app/ui/device/YouScreen.kt`:
 * who you are, the strap, then settings grouped by what they touch. Strap controls live one level
 * down, on "Your strap".
 *
 * The card narrows Android's list to what iOS has: the profile, the goals, the strap, Apple Health
 * (where Android has the Health Connect, Export CSV and Import CSV rows), Diagnostics, and the
 * licences. The privacy sentence, the row labels and the summary lines are the Compose strings.
 */
struct YouView: View {
    @ObservedObject var controller: StrapController
    @ObservedObject var health: HealthModel
    var onOpen: (YouRoute) -> Void

    @State private var goalsOpen = false

    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("You")
                    .appTextStyle(AppTypography.headlineMedium)
                    .foregroundStyle(glowInk)
                    .padding(.top, 12)
                    .padding(.bottom, 4)
                    .accessibilityIdentifier("you.title")

                // Your profile (YouScreen.kt:66-78).
                DotPanel(onClick: { onOpen(.profile) }) {
                    HStack(spacing: 14) {
                        YouIcon(.user)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Your profile")
                                .appTextStyle(AppTypography.titleMedium)
                                .foregroundStyle(glowInk)
                            Text(ProfileRules.shared.summary(profile: health.profile))
                                .appTextStyle(AppTypography.bodySmall)
                                .foregroundStyle(ThemeColors.onSurfaceVariant)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        RowCaret()
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("you.profile")
                    .accessibilityAddTraits(.isButton)
                }

                // Your goals (YouScreen.kt:80-92).
                DotPanel(onClick: { goalsOpen = true }) {
                    HStack(spacing: 14) {
                        YouIcon(.chartLineUp)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Your goals")
                                .appTextStyle(AppTypography.titleMedium)
                                .foregroundStyle(glowInk)
                            Text(health.goals.summary())
                                .appTextStyle(AppTypography.bodySmall)
                                .foregroundStyle(ThemeColors.onSurfaceVariant)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        RowCaret()
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("you.goals")
                    .accessibilityAddTraits(.isButton)
                }

                GroupLabel("Device")
                // StrapCard (YouScreen.kt:139-179).
                DotPanel(onClick: { onOpen(.strap) }) {
                    HStack(spacing: 14) {
                        YouIcon(.watch)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(controller.hasStoredKey ? "Your strap" : "Pair your Helio Strap")
                                .appTextStyle(AppTypography.titleMedium)
                                .foregroundStyle(glowInk)
                            if controller.hasStoredKey {
                                HStack(spacing: 6) {
                                    Circle()
                                        .fill(statusColor)
                                        .frame(width: 7, height: 7)
                                    Text(statusLine)
                                        .appTextStyle(AppTypography.bodySmall)
                                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                                }
                            } else {
                                Text("Scan, or sign in with Zepp to fetch the key")
                                    .appTextStyle(AppTypography.bodySmall)
                                    .foregroundStyle(ThemeColors.onSurfaceVariant)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        RowCaret()
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("you.strap")
                    .accessibilityAddTraits(.isButton)
                }

                GroupLabel("Your data")
                // The privacy sentence and the Apple Health row (YouScreen.kt:98-113).
                DotPanel {
                    Text("Health data stays on this phone. Nothing leaves it unless you export it.")
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                    RowDivider()
                    LinkRow(.heartbeat, "Apple Health", "Copy your data to Apple Health", onClick: nil) {
                        Toggle("", isOn: Binding(
                            get: { controller.appleHealthEnabled },
                            set: { controller.setAppleHealth($0) }
                        ))
                        .labelsHidden()
                        .tint(MetricColors.good)
                        .accessibilityLabel("Apple Health")
                        .accessibilityIdentifier("appleHealthToggle")
                    }
                }

                GroupLabel("Support")
                // DiagnosticsPanel (DeviceScreen.kt:441), as a row.
                DotPanel(onClick: { onOpen(.diagnostics) }) {
                    HStack(spacing: 14) {
                        YouIcon(.terminalWindow)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Diagnostics")
                                .appTextStyle(AppTypography.titleMedium)
                                .foregroundStyle(glowInk)
                            // Card F9: "1 line" for one, "N lines" otherwise, the third allowed
                            // exception to standing instruction 4 (Android prints "1 lines").
                            Text(controller.logLineCountText)
                                .appTextStyle(AppTypography.bodySmall)
                                .foregroundStyle(ThemeColors.onSurfaceVariant)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        RowCaret()
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("you.diagnostics")
                    .accessibilityAddTraits(.isButton)
                }

                // The licences of the three assets the app bundles. The card allows this row's own
                // wording; the texts are the files in `licenses/`, copied into the bundle.
                DotPanel(onClick: { onOpen(.licences) }) {
                    HStack(spacing: 14) {
                        YouIcon(.shieldCheck)
                        Text("Licences")
                            .appTextStyle(AppTypography.titleMedium)
                            .foregroundStyle(glowInk)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        RowCaret()
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("you.licences")
                    .accessibilityAddTraits(.isButton)
                }

                Text("OpenHealth \(appVersion) · local-first, no cloud")
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(ThemeColors.onSurfaceVariant)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 8)
            }
            .padding(.horizontal, 16)
        }
        .glowClearsNavBar()
        .background(ThemeColors.background.ignoresSafeArea())
        .sheet(isPresented: $goalsOpen) {
            GoalsSheet(health: health)
                .presentationDetents([.height(520)])
                .presentationBackground(.clear)
        }
        .onAppear { health.reloadSettings() }
    }

    /// The state word and colour (YouScreen.kt:151-156).
    private var statusColor: Color {
        switch controller.state {
        case "ready": return MetricColors.good
        case "failed": return ThemeColors.error
        default: return ThemeColors.onSurfaceVariant
        }
    }

    private var statusText: String {
        switch controller.state {
        case "ready": return "Connected"
        case "connecting", "authenticating": return "Connecting…"
        case "failed": return "Connection failed"
        default: return "Not connected"
        }
    }

    /// "Connected · 85% · Synced 09:12", with the pieces the app knows (YouScreen.kt:157-173).
    private var statusLine: String {
        var parts = [statusText]
        if controller.state == "ready", controller.battery >= 0 {
            parts.append(controller.charging ? "Charging \(controller.battery)%" : "\(controller.battery)%")
        }
        if controller.syncRunning {
            parts.append("Syncing…")
        } else if controller.lastSyncEpochSeconds > 0 {
            parts.append("Synced \(controller.lastSyncText)")
        }
        return parts.joined(separator: " · ")
    }
}
