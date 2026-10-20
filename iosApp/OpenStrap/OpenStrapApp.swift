import SwiftUI

@main
struct OpenStrapApp: App {
    /// Made here, as a stored property, rather than from a view or a `@StateObject`: iOS relaunches a
    /// bluetooth-central app in the background when the strap connects, and no UI scene has to come
    /// up for that. This struct is built during launch, so the controller — and with it the
    /// CoreBluetooth central manager that receives the restored connection — exists before any view
    /// is on screen. A controller owned by `RootView` would be created too late, or not at all, for
    /// an overnight sync to happen without the user opening the app.
    private let controller: StrapController

    /// The screens' reads, over the same database file the engine writes (card C6).
    private let health: HealthModel

    init() {
        let controller = StrapController()
        self.controller = controller
        health = HealthModel(databasePath: controller.databasePath)
    }

    var body: some Scene {
        WindowGroup {
            ContentView(controller: controller, health: health)
        }
    }
}
