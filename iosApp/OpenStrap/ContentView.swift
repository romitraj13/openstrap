import SwiftUI
import OpenStrapShared

struct ContentView: View {
    var body: some View {
        VStack(spacing: 16) {
            Text("OpenStrap")
                .font(.largeTitle)
            Text(SharedInfo.shared.greeting())
                .font(.body)
                .multilineTextAlignment(.center)
                .accessibilityIdentifier("sharedGreeting")
        }
        .padding()
    }
}
