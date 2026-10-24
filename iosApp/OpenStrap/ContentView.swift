import SwiftUI

/// The app's root, kept as a name so the SwiftUI lifecycle in `OpenStrapApp` stays unchanged. The
/// controller and the health model are created once in `OpenStrapApp`, so a background relaunch
/// gets the same objects.
struct ContentView: View {
    @ObservedObject var controller: StrapController
    @ObservedObject var health: HealthModel

    var body: some View {
        screen
            // Always dark: the glow design is built for OLED black and does not follow the
            // system light mode.
            .preferredColorScheme(.dark)
    }

    @ViewBuilder
    private var screen: some View {
        #if DEBUG
        // Card U1: the design lab, debug builds only, opened with `-designLab`.
        if DesignLabView.isEnabled {
            DesignLabView()
        } else {
            RootView(controller: controller, health: health)
        }
        #else
        RootView(controller: controller, health: health)
        #endif
    }
}
