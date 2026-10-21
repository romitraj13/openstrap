import SwiftUI

/// Paste the strap's auth key. The key is normalised by the shared Kotlin code and kept in the
/// Keychain.
///
/// The card restyles this screen in the quiet panels of the glow language (`DotPanel`, U2); its
/// logic is unchanged: the same `StrapController` calls, the same Keychain, the same Zepp sheet.
///
/// Card F4: the fresh-install screen is shown outside `RootView`'s shell, so no bar sits over it,
/// but it takes the same bottom clearance as every other scrolling screen.
struct PairView: View {
    @ObservedObject var controller: StrapController

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("OpenStrap")
                        .appTextStyle(AppTypography.headlineLarge)
                        .foregroundStyle(glowInk)
                        .padding(.top, 12)
                        .accessibilityIdentifier("openStrapTitle")
                    PairPanel(controller: controller)
                }
                .padding(.horizontal, 16)
            }
            .glowClearsNavBar()
            .background(ThemeColors.background.ignoresSafeArea())
            .toolbar(.hidden, for: .navigationBar)
        }
    }
}

/// The pairing rows, shared by the fresh-install screen and by You › Your strap when no key is
/// stored. (`PairPanel`, DeviceScreen.kt:263, with iOS's own key entry in place of the scan.)
struct PairPanel: View {
    @ObservedObject var controller: StrapController

    @State private var pasted = ""
    @State private var error: String?
    @State private var showZeppSignIn = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            DotPanel {
                Text("Pair your Helio Strap")
                    .appTextStyle(GlowType.title)
                    .foregroundStyle(glowInk)
                Text(
                    "OpenStrap needs the strap's auth key, which Zepp creates when you link the strap. " +
                        "You only do this once."
                )
                .appTextStyle(AppTypography.bodyMedium)
                .foregroundStyle(glowInk)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Strap auth key")
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                    SecureField("32 hex characters", text: $pasted)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.password)
                        .appTextStyle(AppTypography.bodyLarge)
                        .foregroundStyle(glowInk)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 12)
                        .background(ThemeColors.surfaceContainerLow, in: glassRowShape)
                        .overlay(glassRowShape.stroke(ThemeColors.outline, lineWidth: 1))
                        .accessibilityIdentifier("authKeyField")
                }

                Button {
                    error = controller.connect(pastedKey: pasted)
                    if error == nil { pasted = "" }
                } label: {
                    Text("Connect")
                        .appTextStyle(AppTypography.labelLarge)
                        .foregroundStyle(ThemeColors.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(ThemeColors.primary, in: Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("connectButton")

                if let error {
                    Text(error)
                        .appTextStyle(AppTypography.bodyMedium)
                        .foregroundStyle(ThemeColors.error)
                        .accessibilityIdentifier("pairError")
                }
            }

            DotPanel {
                Button {
                    showZeppSignIn = true
                } label: {
                    Text("Sign in with Zepp")
                        .appTextStyle(AppTypography.labelLarge)
                        .foregroundStyle(glowInk)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .contentShape(Capsule())
                        .glass(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("zeppSignInButton")

                Text(
                    "Fetches the strap's auth key from your own Zepp account, for when you'd rather " +
                        "not paste it. The key goes straight into this phone's Keychain."
                )
                .appTextStyle(AppTypography.bodySmall)
                .foregroundStyle(ThemeColors.onSurfaceVariant)
            }

            DotPanel {
                SectionLabel("Paired strap")
                Text(controller.hasStoredKey ? "A key is saved on this phone." : "No key saved yet.")
                    .appTextStyle(AppTypography.bodyMedium)
                    .foregroundStyle(glowInk)
                    .accessibilityIdentifier("keyState")
                Button(role: .destructive) {
                    controller.forgetStrap()
                } label: {
                    Text("Forget strap")
                        .appTextStyle(AppTypography.labelLarge)
                        .foregroundStyle(ThemeColors.error)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .contentShape(Capsule())
                        .overlay(Capsule().stroke(ThemeColors.error.opacity(0.5), lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("forgetButton")
            }
        }
        .sheet(isPresented: $showZeppSignIn) {
            ZeppSignInView(controller: controller)
        }
    }
}
