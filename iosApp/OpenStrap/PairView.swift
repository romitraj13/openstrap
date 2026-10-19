import SwiftUI

/// Paste the strap's auth key. The key is normalised by the shared Kotlin code and kept in the Keychain.
struct PairView: View {
    @ObservedObject var controller: StrapController
    @State private var pasted = ""
    @State private var error: String?
    @State private var showZeppSignIn = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("OpenStrap")
                        .font(.largeTitle)
                        .accessibilityIdentifier("openStrapTitle")
                }
                Section("Strap auth key") {
                    SecureField("32 hex characters", text: $pasted)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.password)
                        .accessibilityIdentifier("authKeyField")
                    Button("Connect") {
                        error = controller.connect(pastedKey: pasted)
                        if error == nil { pasted = "" }
                    }
                    .accessibilityIdentifier("connectButton")
                }
                if let error {
                    Section {
                        Text(error)
                            .foregroundStyle(.red)
                            .accessibilityIdentifier("pairError")
                    }
                }
                Section("Or sign in with Zepp") {
                    Button("Sign in with Zepp") { showZeppSignIn = true }
                        .accessibilityIdentifier("zeppSignInButton")
                    Text("Fetches the strap's auth key from your own Zepp account, for when you'd rather not paste it. The key goes straight into this phone's Keychain.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Section("Paired strap") {
                    Text(controller.hasStoredKey ? "A key is saved on this phone." : "No key saved yet.")
                        .accessibilityIdentifier("keyState")
                    Button("Forget strap", role: .destructive) {
                        controller.forgetStrap()
                    }
                    .accessibilityIdentifier("forgetButton")
                }
            }
            .navigationTitle("Pair")
            .sheet(isPresented: $showZeppSignIn) {
                ZeppSignInView(controller: controller)
            }
        }
    }
}
