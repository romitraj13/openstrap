import SwiftUI
import WebKit

/// "Sign in with Zepp" as the fallback pairing path: the user signs in on Zepp's own page inside a
/// non-persistent web view; the app reads the app token and user ID from that page's cookies, asks
/// Zepp once for the account's devices, and offers the Helio Strap's auth key. The token and the
/// cookies are dropped when the sheet closes.
///
/// Pasting the key stays the first option on the Pair screen; this sheet is only the alternative.
struct ZeppSignInView: View {
    @ObservedObject var controller: StrapController
    @StateObject private var model = ZeppSignInModel()
    @Environment(\.dismiss) private var dismiss
    @State private var saveError: String?

    var body: some View {
        NavigationStack {
            Group {
                switch model.step {
                case .signingIn:
                    signIn
                case .fetching:
                    fetching
                case .found(let strap):
                    found(strap)
                case .failed(let failure):
                    failed(failure)
                }
            }
            .navigationTitle("Sign in with Zepp")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Close") { close() }
                        .accessibilityIdentifier("zeppCloseButton")
                }
            }
        }
        .onAppear {
            // The only line the sign-in writes to Diagnostics; never the token, cookies or key.
            model.onLog = { [weak controller] line in controller?.note(line) }
        }
        .onDisappear { model.close() }
    }

    private var signIn: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Use the email and password of the Zepp account your strap is linked to. Google, Apple and other sign-ins don't work here.")
                .font(.footnote)
            Text("This is Zepp's own page, so OpenHealth never sees your password. It reads only the app token and user ID, then asks Zepp once for your devices.")
                .font(.caption)
                .foregroundStyle(.secondary)
            if let webView = model.webView {
                ZeppWebView(webView: webView)
                    .id(model.attempt)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .padding(.horizontal)
    }

    private var fetching: some View {
        VStack(spacing: 12) {
            ProgressView()
            Text("Signed in. Looking up your devices…")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func found(_ strap: ZeppFoundStrap) -> some View {
        Form {
            Section("Found your Helio Strap") {
                if let serial = strap.serial {
                    LabeledContent("Serial", value: serial)
                        .accessibilityIdentifier("zeppSerial")
                }
                if let firmware = strap.firmware {
                    LabeledContent("Firmware", value: firmware)
                        .accessibilityIdentifier("zeppFirmware")
                }
                if let key = strap.authKey {
                    LabeledContent("Auth key", value: ZeppSignInModel.maskedKey(key))
                        .accessibilityIdentifier("zeppMaskedKey")
                }
            }
            Section {
                Text("Keep the strap on its charger near the phone. Don't unlink it in the Zepp app: that wipes this key from the strap.")
                    .font(.footnote)
            }
            if let saveError {
                Section {
                    Text(saveError)
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("zeppSaveError")
                }
            }
            Section {
                Button("Use this strap") { use(strap) }
                    .accessibilityIdentifier("zeppUseStrapButton")
                Button("Other account") { model.restart() }
                    .accessibilityIdentifier("zeppOtherAccountButton")
            }
            Section {
                Text("The app token and user ID stay in memory for this one request only.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func failed(_ failure: ZeppSignInFailure) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(failure.title)
                .font(.headline)
                .accessibilityIdentifier("zeppFailureTitle")
            Text(failure.message)
                .accessibilityIdentifier("zeppFailureMessage")
            Spacer()
            Button("Sign in again") { model.restart() }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("zeppSignInAgainButton")
            Button("Paste the key instead") { close() }
                .accessibilityIdentifier("zeppPasteInsteadButton")
        }
        .padding()
    }

    /// Saves the key through the same Keychain store C3 uses and starts the connection.
    private func use(_ strap: ZeppFoundStrap) {
        guard let key = strap.authKey else { return }
        if let error = controller.connect(pastedKey: key) {
            saveError = error
            return
        }
        close()
    }

    private func close() {
        model.close()
        dismiss()
    }
}

/// Hosts the model's one web view. The model configures it and acts as its navigation delegate.
private struct ZeppWebView: UIViewRepresentable {
    let webView: WKWebView

    func makeUIView(context: Context) -> WKWebView {
        webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
