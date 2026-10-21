import SwiftUI
import UIKit

/// The last 50 history syncs, then the last 2,000 lines the engine and the driver logged, ready to
/// copy into an email or the hardware log. The auth key is never logged, so it is safe to paste.
///
/// Pushed from the Strap screen (card C6 moved it there), so it uses that screen's navigation stack
/// rather than opening one of its own.
struct DiagnosticsView: View {
    @ObservedObject var controller: StrapController

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Sync log")
                    .font(.headline)
                if controller.syncLogRows.isEmpty {
                    Text("No syncs yet.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    // Newest first: the row H2 looks for is the one nearest the top.
                    ForEach(controller.syncLogRows) { row in
                        Text(row.text)
                            .font(.system(.footnote, design: .monospaced))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .textSelection(.enabled)
                            .accessibilityIdentifier("syncLogRow")
                    }
                }
                Divider()
                Text("Log")
                    .font(.headline)
                Text(controller.logText.isEmpty ? "Nothing logged yet." : controller.logText)
                    .font(.system(.footnote, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .textSelection(.enabled)
                    .accessibilityIdentifier("logText")
            }
            .padding()
        }
        .glowClearsNavBar()
        .navigationTitle("Diagnostics")
        .onAppear { controller.refreshSyncLog() }
        .refreshable { controller.refreshSyncLog() }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Copy") {
                    UIPasteboard.general.string = controller.diagnosticsText
                }
                .accessibilityIdentifier("copyLogButton")
            }
        }
    }
}
