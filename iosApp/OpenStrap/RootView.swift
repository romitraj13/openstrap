import SwiftUI

/// The three screens C3 ships: Pair, Strap and Diagnostics.
struct RootView: View {
    @StateObject private var controller = StrapController()

    var body: some View {
        TabView {
            PairView(controller: controller)
                .tabItem { Label("Pair", systemImage: "link") }
            StrapView(controller: controller)
                .tabItem { Label("Strap", systemImage: "heart") }
            DiagnosticsView(controller: controller)
                .tabItem { Label("Diagnostics", systemImage: "doc.text") }
        }
    }
}
