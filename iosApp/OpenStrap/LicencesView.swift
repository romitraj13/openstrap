import SwiftUI

/// The licence texts OpenHealth bundles: upstream's own notices (the MIT and Apache licences the
/// project is used under), the Phosphor icons (MIT), and the two fonts, Funnel and MuseoModerno
/// (SIL OFL 1.1). The files are copies of the repository's root `LICENSE-MIT`/`LICENSE-APACHE` and
/// of `licenses/`, kept in `Design/Licenses/`, which XcodeGen copies to the bundle root.
///
/// Card R1: the MIT and Apache licences require keeping upstream's notice, so the first row is
/// OpenStrap itself, and it opens both texts. The iPhone app's visible name differs from upstream's;
/// the notice does not.
struct LicencesView: View {
    @Environment(\.dismiss) private var dismiss

    /// The fonts and icons, in the order the screen has shown them since U4.
    private static let names = ["MIT-Phosphor", "OFL-Funnel", "OFL-MuseoModerno"]

    /// Upstream OpenStrap's own two notices (card R1).
    static let upstreamMIT = "OpenStrap-LICENSE-MIT"
    static let upstreamApache = "OpenStrap-LICENSE-APACHE"

    @State private var upstreamOpen = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header

                // Card R1: upstream's notice, above the font and icon licences.
                DotPanel(onClick: { upstreamOpen = true }) {
                    HStack(spacing: 0) {
                        Text("OpenStrap")
                            .appTextStyle(AppTypography.titleMedium)
                            .foregroundStyle(glowInk)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        RowCaret()
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityIdentifier("licence.OpenStrap")
                    .accessibilityAddTraits(.isButton)

                    Text(
                        "OpenHealth is based on OpenStrap by sayed0am, used under the MIT licence. "
                            + "github.com/sayed0am/openstrap"
                    )
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(ThemeColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                ForEach(Self.names, id: \.self) { name in
                    DotPanel {
                        SectionLabel(name)
                        Text(Self.text(named: name))
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(ThemeColors.onSurfaceVariant)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            // Card F4: on the text, not the panel. An identifier on the panel lands on
                            // every descendant, and the UI test then finds three of them for one name.
                            .accessibilityIdentifier("licence.\(name)")
                    }
                }
            }
            .padding(.horizontal, 16)
        }
        .glowClearsNavBar()
        .background(ThemeColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(isPresented: $upstreamOpen) { UpstreamLicencesView() }
    }

    private var header: some View {
        HStack(spacing: 0) {
            Button { dismiss() } label: {
                PhosphorIcon.arrowLeft.image
                    .resizable()
                    .scaledToFit()
                    .frame(width: 24, height: 24)
                    .foregroundStyle(glowInk)
                    .padding(12)
            }
            .accessibilityLabel("Back")
            .accessibilityIdentifier("licences.back")

            Text("Licences")
                .appTextStyle(AppTypography.headlineMedium)
                .foregroundStyle(glowInk)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.top, 4)
    }

    static func text(named name: String) -> String {
        guard
            let url = Bundle.main.url(forResource: name, withExtension: "txt"),
            let body = try? String(contentsOf: url, encoding: .utf8)
        else {
            return ""
        }
        return body
    }
}

/// Upstream OpenStrap's own notices: the MIT text, with the Apache text below it (card R1).
struct UpstreamLicencesView: View {
    @Environment(\.dismiss) private var dismiss

    private static let texts = ["MIT", "Apache-2.0"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 0) {
                    Button { dismiss() } label: {
                        PhosphorIcon.arrowLeft.image
                            .resizable()
                            .scaledToFit()
                            .frame(width: 24, height: 24)
                            .foregroundStyle(glowInk)
                            .padding(12)
                    }
                    .accessibilityLabel("Back")
                    .accessibilityIdentifier("upstream.back")

                    Text("OpenStrap")
                        .appTextStyle(AppTypography.headlineMedium)
                        .foregroundStyle(glowInk)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.top, 4)

                ForEach(Self.texts, id: \.self) { name in
                    DotPanel {
                        SectionLabel(name)
                        Text(LicencesView.text(named: self.file(named: name)))
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(ThemeColors.onSurfaceVariant)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .accessibilityIdentifier("licence.OpenStrap-\(name)")
                    }
                }
            }
            .padding(.horizontal, 16)
        }
        .glowClearsNavBar()
        .background(ThemeColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }

    private func file(named name: String) -> String {
        name == "MIT" ? LicencesView.upstreamMIT : LicencesView.upstreamApache
    }
}
