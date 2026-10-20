import SwiftUI

/// The app's root, kept as a name so the SwiftUI lifecycle in `OpenStrapApp` stays unchanged. The
/// controller is created once in `OpenStrapApp`, so a background relaunch gets the same object.
struct ContentView: View {
    @ObservedObject var controller: StrapController

    var body: some View {
        RootView(controller: controller)
    }
}
