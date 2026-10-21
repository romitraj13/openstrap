import SwiftUI

/*
 * The list rows the You tab and its screens are built from, ported from
 * `app/src/main/kotlin/org/openstrap/app/ui/components/Cards.kt` (`SectionLabel`,
 * `SettingRow`) and `app/src/main/kotlin/org/openstrap/app/ui/device/YouScreen.kt`
 * (`Group`, `LinkRow`, `YouIcon`).
 *
 * The panels themselves are the U2 `DotPanel`; these are only the insides of them.
 */

/// Small uppercase tracked label: "TIME ASLEEP". (`SectionLabel`, Cards.kt:59)
struct SectionLabel: View {
    var text: String
    var color: Color

    init(_ text: String, color: Color = ThemeColors.onSurfaceVariant) {
        self.text = text
        self.color = color
    }

    var body: some View {
        Text(text.uppercased())
            .appTextStyle(AppTypography.labelMedium)
            .foregroundStyle(color)
    }
}

/// A group heading on the You list. (`Group`, YouScreen.kt:135)
struct GroupLabel: View {
    var title: String

    init(_ title: String) {
        self.title = title
    }

    var body: some View {
        SectionLabel(title)
            .padding(.leading, 4)
            .padding(.top, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The round badge every row on You leads with: the same size, backdrop and white icon
/// throughout. (`YouIcon`, YouScreen.kt:196)
struct YouIcon: View {
    var icon: PhosphorIcon

    init(_ icon: PhosphorIcon) {
        self.icon = icon
    }

    var body: some View {
        icon.image
            .resizable()
            .scaledToFit()
            .frame(width: 26, height: 26)
            .foregroundStyle(ThemeColors.primary)
            .frame(width: 52, height: 52)
            .background(ThemeColors.surfaceContainerHighest, in: Circle())
            .accessibilityHidden(true)
    }
}

/// A title, an optional subtitle under it, and whatever the row ends with. (`SettingRow`,
/// Cards.kt:148)
struct SettingRow<Trailing: View>: View {
    var title: String
    var subtitle: String?
    var titleStyle: AppTextStyle
    var onClick: (() -> Void)?
    private let trailing: () -> Trailing

    init(
        _ title: String,
        subtitle: String? = nil,
        titleStyle: AppTextStyle = AppTypography.bodyLarge,
        onClick: (() -> Void)? = nil,
        @ViewBuilder trailing: @escaping () -> Trailing
    ) {
        self.title = title
        self.subtitle = subtitle
        self.titleStyle = titleStyle
        self.onClick = onClick
        self.trailing = trailing
    }

    var body: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .appTextStyle(titleStyle)
                    .foregroundStyle(glowInk)
                    .multilineTextAlignment(.leading)
                if let subtitle {
                    Text(subtitle)
                        .appTextStyle(AppTypography.bodySmall)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                        .multilineTextAlignment(.leading)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing()
        }
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity)
        .contentShape(Rectangle())
        .modifier(OptionalTap(action: onClick, shape: nil))
    }
}

extension SettingRow where Trailing == EmptyView {
    init(
        _ title: String,
        subtitle: String? = nil,
        titleStyle: AppTextStyle = AppTypography.bodyLarge,
        onClick: (() -> Void)? = nil
    ) {
        self.init(title, subtitle: subtitle, titleStyle: titleStyle, onClick: onClick) { EmptyView() }
    }
}

/// The caret every row that opens something ends with.
struct RowCaret: View {
    var body: some View {
        PhosphorIcon.caretRight.image
            .resizable()
            .scaledToFit()
            .frame(width: 16, height: 16)
            .foregroundStyle(ThemeColors.onSurfaceVariant)
            .accessibilityHidden(true)
    }
}

/// A badge, a title, what is inside, and a caret: the shape of every link on You.
/// (`LinkRow`, YouScreen.kt:182)
struct LinkRow<Trailing: View>: View {
    var icon: PhosphorIcon
    var title: String
    var subtitle: String?
    var onClick: (() -> Void)?
    private let trailing: () -> Trailing

    init(
        _ icon: PhosphorIcon,
        _ title: String,
        _ subtitle: String?,
        onClick: (() -> Void)? = nil,
        @ViewBuilder trailing: @escaping () -> Trailing
    ) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.onClick = onClick
        self.trailing = trailing
    }

    var body: some View {
        HStack(spacing: 14) {
            YouIcon(icon)
            SettingRow(title, subtitle: subtitle, titleStyle: AppTypography.titleMedium, onClick: onClick) {
                trailing()
            }
        }
        .frame(maxWidth: .infinity)
    }
}

extension LinkRow where Trailing == EmptyView {
    /// A row that opens something: its own caret is the trailing mark.
    init(_ icon: PhosphorIcon, _ title: String, _ subtitle: String?, onClick: (() -> Void)? = nil) {
        self.init(icon, title, subtitle, onClick: onClick) { RowCaret() }
    }
}

/// The plain horizontal rule between rows. (`HorizontalDivider`, Material 3, drawn with the
/// outline variant colour YouScreen.kt:106 passes.)
struct RowDivider: View {
    var body: some View {
        Rectangle()
            .fill(ThemeColors.outlineVariant)
            .frame(height: 1)
            .accessibilityHidden(true)
    }
}
