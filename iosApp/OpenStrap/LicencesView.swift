import SwiftUI

/// The licence texts of the three assets OpenStrap bundles: the Phosphor icons (MIT), and the two
/// fonts, Funnel and MuseoModerno (SIL OFL 1.1). The files are U1's copies of the repository's
/// `licenses/` in `Design/Licenses/`, which XcodeGen copies to the bundle root.
struct LicencesView: View {
    @Environment(\.dismiss) private var dismiss

    private static let names = ["MIT-Phosphor", "OFL-Funnel", "OFL-MuseoModerno"]

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
                    .accessibilityIdentifier("licences.back")

                    Text("Licences")
                        .appTextStyle(AppTypography.headlineMedium)
                        .foregroundStyle(glowInk)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.top, 4)

                ForEach(Self.names, id: \.self) { name in
                    DotPanel {
                        SectionLabel(name)
                        Text(Self.text(named: name))
                            .appTextStyle(AppTypography.bodySmall)
                            .foregroundStyle(ThemeColors.onSurfaceVariant)
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .accessibilityIdentifier("licence.\(name)")
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 40)
        }
        .background(ThemeColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .accessibilityIdentifier("screen.licences")
    }

    private static func text(named name: String) -> String {
        guard
            let url = Bundle.main.url(forResource: name, withExtension: "txt"),
            let body = try? String(contentsOf: url, encoding: .utf8)
        else {
            return ""
        }
        return body
    }
}
