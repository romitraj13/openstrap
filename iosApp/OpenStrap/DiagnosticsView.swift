import SwiftUI
import UIKit

/// The last 2,000 lines the engine and the driver logged, ready to copy into an email or the
/// hardware log. The auth key is never logged, so it is safe to paste.
struct DiagnosticsView: View {
    @ObservedObject var controller: StrapController

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(controller.logText.isEmpty ? "Nothing logged yet." : controller.logText)
                    .font(.system(.footnote, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
                    .padding()
                    .accessibilityIdentifier("logText")
            }
            .navigationTitle("Diagnostics")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Copy") {
                        UIPasteboard.general.string = controller.logText
                    }
                    .accessibilityIdentifier("copyLogButton")
                }
            }
        }
    }
}
