import SwiftUI

/// The app's root, kept as a name so the SwiftUI lifecycle in `OpenStrapApp` stays unchanged. The
/// controller and the health model are created once in `OpenStrapApp`, so a background relaunch
/// gets the same objects.
struct ContentView: View {
    @ObservedObject var controller: StrapController
    @ObservedObject var health: HealthModel

    var body: some View {
        RootView(controller: controller, health: health)
    }
}
