import SwiftUI

/// What the strap is doing: state, battery, firmware, and live heart rate.
struct StrapView: View {
    @ObservedObject var controller: StrapController

    var body: some View {
        NavigationStack {
            Form {
                Section("Connection") {
                    LabeledContent("State", value: controller.state)
                        .accessibilityIdentifier("strapState")
                    if let detail = controller.stateDetail, !detail.isEmpty {
                        Text(detail)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                Section("Device") {
                    LabeledContent("Firmware", value: controller.firmware ?? "-")
                        .accessibilityIdentifier("firmware")
                    LabeledContent("Battery", value: controller.battery >= 0 ? "\(controller.battery)%" : "-")
                        .accessibilityIdentifier("battery")
                    LabeledContent("Charging", value: controller.charging ? "Yes" : "No")
                }
                Section("Live heart rate") {
                    Toggle("Live heart rate", isOn: Binding(
                        get: { controller.liveHeartRateEnabled },
                        set: { controller.setLiveHeartRate($0) }
                    ))
                    .accessibilityIdentifier("liveHeartRateToggle")
                    LabeledContent("Beats per minute", value: controller.heartRate.map { "\($0)" } ?? "-")
                        .accessibilityIdentifier("heartRate")
                }
                if !controller.hasStoredKey {
                    Section {
                        Text("No key yet. Paste it on the Pair screen.")
                            .font(.footnote)
                    }
                }
            }
            .navigationTitle("Strap")
        }
    }
}
