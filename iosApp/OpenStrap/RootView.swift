import SwiftUI

/// The app's four screens: Today, Sleep, Heart and the strap itself. A fresh install starts on Pair
/// instead, because there is no strap to read yet and nothing to show.
struct RootView: View {
    @ObservedObject var controller: StrapController
    @ObservedObject var health: HealthModel

    var body: some View {
        if controller.hasStoredKey || DemoData.isEnabled {
            TabView {
                TodayView(controller: controller, health: health)
                    .tabItem { Label("Today", systemImage: "sun.max") }
                SleepView(health: health)
                    .tabItem { Label("Sleep", systemImage: "bed.double") }
                HeartView(health: health)
                    .tabItem { Label("Heart", systemImage: "heart") }
                StrapView(controller: controller)
                    .tabItem { Label("Strap", systemImage: "bolt.horizontal") }
            }
        } else {
            PairView(controller: controller)
        }
    }
}
