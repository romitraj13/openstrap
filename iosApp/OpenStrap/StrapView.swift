import SwiftUI

/// What the strap is doing: state, the last sync, battery, firmware, and live heart rate.
///
/// Ported from `app/src/main/kotlin/org/openstrap/app/ui/device/DeviceScreen.kt` (`StatusHero`,
/// its action row and its rows) and restyled in the U2 quiet panels, as card U4 asks. The
/// behaviour is the screen's own: the same `StrapController` calls the Strap tab had, no new
/// device plumbing.
struct StrapView: View {
    @ObservedObject var controller: StrapController
    var onDiagnostics: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var confirmForget = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                if controller.hasStoredKey {
                    statusHero
                    actions
                    liveHeartRate
                    if controller.inBackground {
                        backgroundNote
                    }
                } else {
                    PairPanel(controller: controller)
                }
                morePanel
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 40)
        }
        .background(ThemeColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { controller.refreshSyncLog() }
        .confirmationDialog("Forget this strap?", isPresented: $confirmForget, titleVisibility: .visible) {
            Button("Forget", role: .destructive) { controller.forgetStrap() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(
                "OpenStrap will disconnect and delete the strap's address and auth key from this phone. " +
                    "Your synced health data stays. To pair again you'll need the auth key.\n\n" +
                    "This doesn't unbind the strap from your Zepp account."
            )
        }
    }

    private var header: some View {
        HStack(spacing: 0) {
            Button { dismiss() } label: {
                PhosphorIcon.arrowLeft.image
                    .resizable()
                    .scaledToFit()
                    .frame(width: 24, height: 24)
                    .foregroundStyle(glowInk)
                    .padding(12)
            }
            .accessibilityLabel("Back")
            .accessibilityIdentifier("strap.back")

            Text("Your strap")
                .appTextStyle(AppTypography.headlineMedium)
                .foregroundStyle(glowInk)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.top, 4)
    }

    /// Connection label, the last sync, the battery ring's numbers and the firmware.
    /// (`StatusHero`, DeviceScreen.kt:197)
    private var statusHero: some View {
        DotPanel {
            HStack(alignment: .top, spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    SectionLabel(statusLabel, color: statusColor)
                    Text(controller.state)
                        .appTextStyle(AppTypography.titleLarge)
                        .foregroundStyle(glowInk)
                        .accessibilityIdentifier("strapState")
                    if let detail = controller.stateDetail, !detail.isEmpty {
                        Text(detail)
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(ThemeColors.onSurfaceVariant)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                VStack(alignment: .trailing, spacing: 4) {
                    SectionLabel("Last sync")
                    Text(controller.syncRunning ? "Syncing…" : controller.lastSyncText)
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(glowInk)
                        .accessibilityIdentifier("lastSync")
                }
            }

            HStack(spacing: 10) {
                Text(controller.battery >= 0 ? "\(controller.battery)%" : "—")
                    .appTextStyle(AppTypography.displaySmall)
                    .foregroundStyle(controller.battery >= 0 ? MetricColors.battery(controller.battery) : ThemeColors.onSurfaceVariant)
                    .accessibilityIdentifier("battery")
                VStack(alignment: .leading, spacing: 2) {
                    SectionLabel(controller.charging ? "Charging" : "Battery",
                                 color: controller.charging ? MetricColors.battery(controller.battery) : ThemeColors.onSurfaceVariant)
                    Text(controller.firmware.map { "Firmware \($0)" } ?? "Firmware —")
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                        .accessibilityIdentifier("firmware")
                }
            }
        }
    }

    /// Sync now, and Forget this strap. (`DeviceScreen.kt:148-174, 186`)
    private var actions: some View {
        HStack(spacing: 10) {
            Button {
                controller.syncNow()
            } label: {
                Text("Sync now")
                    .appTextStyle(AppTypography.labelLarge)
                    .foregroundStyle(ThemeColors.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(controller.state == "ready" ? ThemeColors.primary : ThemeColors.surfaceContainerHighest, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(controller.state != "ready")
            .accessibilityIdentifier("syncNowButton")

            Button {
                confirmForget = true
            } label: {
                Text("Forget this strap")
                    .appTextStyle(AppTypography.labelLarge)
                    .foregroundStyle(ThemeColors.error)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .overlay(Capsule().stroke(ThemeColors.error.opacity(0.5), lineWidth: 1))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("forgetThisStrap")
        }
    }

    /// The live heart rate switch and the current rate. (The Strap tab's own section, kept.)
    private var liveHeartRate: some View {
        DotPanel {
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Live heart rate")
                        .appTextStyle(AppTypography.titleMedium)
                        .foregroundStyle(glowInk)
                    Text("Beats per minute")
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text(controller.heartRate.map { "\($0)" } ?? "-")
                    .appTextStyle(AppTypography.titleLarge)
                    .foregroundStyle(glowInk)
                    .accessibilityIdentifier("heartRate")
                Toggle("", isOn: Binding(
                    get: { controller.liveHeartRateEnabled },
                    set: { controller.setLiveHeartRate($0) }
                ))
                .labelsHidden()
                .tint(MetricColors.good)
                .accessibilityLabel("Live heart rate")
                .accessibilityIdentifier("liveHeartRateToggle")
            }
        }
    }

    private var backgroundNote: some View {
        DotPanel {
            Text(
                "In the background. A sync that wakes the app runs on the time iOS lends it, and this " +
                    "screen says how it went when it is back."
            )
            .appTextStyle(AppTypography.bodySmall)
            .foregroundStyle(ThemeColors.onSurfaceVariant)
        }
    }

    /// Diagnostics, and the note a strap that isn't paired needs.
    private var morePanel: some View {
        DotPanel(onClick: onDiagnostics) {
            HStack(spacing: 14) {
                YouIcon(.terminalWindow)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Diagnostics")
                        .appTextStyle(AppTypography.titleMedium)
                        .foregroundStyle(glowInk)
                    Text("Sync log and app log")
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                RowCaret()
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("diagnosticsLink")
            .accessibilityAddTraits(.isButton)
        }
    }

    /// `Connected to` / `Connecting to` / `Authenticating` / `Connection failed` / `Not connected`
    /// (DeviceScreen.kt:201-207).
    private var statusLabel: String {
        switch controller.state {
        case "ready": return "Connected to"
        case "connecting": return "Connecting to"
        case "authenticating": return "Authenticating"
        case "failed": return "Connection failed"
        default: return "Not connected"
        }
    }

    private var statusColor: Color {
        switch controller.state {
        case "ready": return MetricColors.good
        case "failed": return ThemeColors.error
        default: return ThemeColors.onSurfaceVariant
        }
    }
}
