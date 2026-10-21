import SwiftUI
import UIKit
import OpenStrapShared

/*
 * Age, sex, weight and height, ported from
 * `app/src/main/kotlin/org/openstrap/app/ui/device/ProfileScreen.kt`.
 *
 * The arithmetic — the ranges each field accepts, the cm/lb conversions, the birth year and the
 * two timestamps a save stamps — is `ProfileRules` in shared Kotlin (`org.openstrap.app.ui.device`),
 * copied from this screen by card U4 and tested on the JVM. This view only draws the fields and
 * hands their text over on Save.
 *
 * Age sets the maximum heart rate (and so every effort calculation); sex picks the training-load
 * curve. Kept on this phone only. Back leaves without saving, asking first if anything changed.
 */
struct ProfileScreen: View {
    @ObservedObject var health: HealthModel

    @Environment(\.dismiss) private var dismiss

    @State private var age = ""
    @State private var sex = 2
    @State private var imperial = false
    @State private var weight = ""
    @State private var heightCm = ""
    @State private var feet = ""
    @State private var inches = ""
    @State private var activity = -1
    @State private var betaBlocker = false
    @State private var pregnant = false
    @State private var maxHr = ""
    @State private var sleepNeed = ""

    @State private var hydrated = false
    @State private var initialSnapshot = ""
    @State private var confirmDiscard = false

    /// What the fields hold, so back knows whether there is anything to lose (ProfileScreen.kt:116-117).
    private var snapshot: String {
        [age, String(sex), imperial ? "1" : "0", weight, heightCm, feet, inches,
         String(activity), betaBlocker ? "1" : "0", pregnant ? "1" : "0", maxHr, sleepNeed]
            .joined(separator: "\u{0}")
    }

    private var dirty: Bool { hydrated && snapshot != initialSnapshot }

    /// `valid` (ProfileScreen.kt:101) and the per-field errors, asked of the shared Kotlin rules.
    private var fields: ProfileFields {
        ProfileRules.shared.fields(
            age: age,
            imperial: imperial,
            weight: weight,
            heightCm: heightCm,
            feet: feet,
            inches: inches,
            maxHr: maxHr,
            sleepNeed: sleepNeed
        )
    }

    private var valid: Bool { fields.valid }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                panel
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 40)
        }
        .background(ThemeColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .onAppear(perform: hydrate)
        .confirmationDialog("Discard changes?", isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button("Discard", role: .destructive) { dismiss() }
            Button("Keep editing", role: .cancel) {}
        } message: {
            Text("Your profile changes haven't been saved.")
        }
    }

    private var header: some View {
        HStack(spacing: 0) {
            Button(action: back) {
                PhosphorIcon.arrowLeft.image
                    .resizable()
                    .scaledToFit()
                    .frame(width: 24, height: 24)
                    .foregroundStyle(glowInk)
                    .padding(12)
            }
            .accessibilityLabel("Back")
            .accessibilityIdentifier("profile.back")

            Text("Your profile")
                .appTextStyle(AppTypography.headlineMedium)
                .foregroundStyle(glowInk)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: save) {
                Text("Save")
                    .appTextStyle(AppTypography.labelLarge)
                    .foregroundStyle(valid ? ThemeColors.primary : ThemeColors.onSurfaceVariant)
            }
            .disabled(!valid)
            .accessibilityIdentifier("profile.save")
        }
        .padding(.top, 4)
    }

    private var panel: some View {
        DotPanel {
            Text(
                "Age sets your maximum heart rate, which every effort and recovery number depends on. " +
                    "All fields are optional and stay on this phone."
            )
            .appTextStyle(AppTypography.bodyMedium)
            .foregroundStyle(glowInk)

            HStack(spacing: 10) {
                NumberField(
                    label: "Age",
                    unit: "years",
                    text: $age,
                    error: fields.ageError
                )
                SexToggle(icon: .genderFemale, label: "Female", accent: ThemeColors.female, selected: sex == 0) {
                    sex = sex == 0 ? 2 : 0
                }
                SexToggle(icon: .genderMale, label: "Male", accent: ThemeColors.male, selected: sex == 1) {
                    sex = sex == 1 ? 2 : 1
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                SectionLabel("Units")
                SegmentedTabs(["Metric", "Imperial"], selected: imperial ? 1 : 0) { index in
                    switchUnits(toImperial: index == 1)
                }
            }

            NumberField(
                label: "Weight",
                unit: imperial ? "lb" : "kg",
                text: $weight,
                decimal: true,
                error: fields.weightError
            )

            if imperial {
                HStack(spacing: 10) {
                    NumberField(label: "Feet", unit: "ft", text: $feet, error: fields.heightError, limit: 1)
                    NumberField(label: "Inches", unit: "in", text: $inches, error: fields.heightError, limit: 2)
                }
            } else {
                NumberField(label: "Height", unit: "cm", text: $heightCm, error: fields.heightError)
            }

            VStack(alignment: .leading, spacing: 6) {
                SectionLabel("Usual activity (for the fitness estimate)")
                HStack(spacing: 6) {
                    ForEach(0..<5, id: \.self) { index in
                        ActivityChip(number: index + 1, selected: activity == index) {
                            activity = activity == index ? -1 : index
                        }
                    }
                }
                Text(activityText)
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(glowInk)
            }

            CheckRow("Taking beta-blockers", checked: betaBlocker) { betaBlocker = $0 }
            CheckRow("Pregnant", checked: pregnant) { pregnant = $0 }

            NumberField(
                label: "Max heart rate from a test (optional)",
                unit: "bpm",
                text: $maxHr,
                error: fields.maxHrError
            )
            NumberField(
                label: "Sleep need (7–9, blank for your age's default)",
                unit: "h",
                text: $sleepNeed,
                decimal: true,
                error: fields.sleepNeedError,
                identifier: "profile.sleepNeed"
            )
        }
    }

    // MARK: - State

    private func hydrate() {
        guard !hydrated else { return }
        let profile = health.profile
        age = profile.birthYear.map { String(Calendar.current.component(.year, from: Date()) - Int($0.int32Value)) } ?? ""
        sex = Int(ProfileRules.shared.sexIndex(profile: profile))
        imperial = profile.imperial?.boolValue ?? false
        weight = profile.weightKg.map { ProfileRules.shared.weightText(kg: $0.floatValue, imperial: imperial) } ?? ""
        heightCm = profile.heightCm.map { ProfileRules.shared.heightText(cm: $0.int32Value) } ?? ""
        feet = profile.heightCm.map { ProfileRules.shared.feetText(cm: $0.int32Value) } ?? ""
        inches = profile.heightCm.map { ProfileRules.shared.inchesText(cm: $0.int32Value) } ?? ""
        activity = profile.activityLevel.map { Int($0.int32Value) } ?? -1
        betaBlocker = profile.betaBlocker
        pregnant = profile.pregnant
        maxHr = profile.maxHeartRate.map { String($0.int32Value) } ?? ""
        sleepNeed = profile.sleepNeedMinutes.map { ProfileRules.shared.sleepNeedText(minutes: $0.int32Value) } ?? ""
        initialSnapshot = snapshot
        hydrated = true
    }

    private func back() {
        if dirty { confirmDiscard = true } else { dismiss() }
    }

    private func switchUnits(toImperial: Bool) {
        guard toImperial != imperial else { return }
        // Carry valid entries across so switching never loses what was typed (ProfileScreen.kt:104-113).
        if let kg = fields.weightKg {
            weight = ProfileRules.shared.weightText(kg: kg.floatValue, imperial: toImperial)
        }
        if let cm = fields.heightCm {
            heightCm = ProfileRules.shared.heightText(cm: cm.int32Value)
            feet = ProfileRules.shared.feetText(cm: cm.int32Value)
            inches = ProfileRules.shared.inchesText(cm: cm.int32Value)
        }
        imperial = toImperial
    }

    private func save() {
        let profile = ProfileRules.shared.toProfile(
            profile: health.profile,
            age: age,
            sex: Int32(sex),
            imperial: imperial,
            weight: weight,
            heightCm: heightCm,
            feet: feet,
            inches: inches,
            activity: Int32(activity),
            betaBlocker: betaBlocker,
            pregnant: pregnant,
            maxHr: maxHr,
            sleepNeed: sleepNeed,
            todayIso: Self.isoToday,
            nowEpochSeconds: Int64(Date().timeIntervalSince1970)
        )
        health.saveProfile(profile) { dismiss() }
    }

    // MARK: - The field states the error borders need

    private var activityText: String {
        if activity >= 0 && activity < Vo2max.shared.ACTIVITY_LEVELS.count {
            return Vo2max.shared.ACTIVITY_LEVELS[activity]
        }
        return "Pick the level that best matches a usual week."
    }

    private static var isoToday: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}

// MARK: - Fields

/// One numeric field: a 14 pt rounded outline, the label, the value and its unit.
/// (`NumberField`, ProfileScreen.kt:219)
struct NumberField: View {
    var label: String
    var unit: String
    @Binding var text: String
    var decimal = false
    var error = false
    /// Compose's `take(n)` on the typed text; 5 unless the field caps it lower.
    var limit = 5
    /// The text field's own identifier, for the UI test that checks the end of the panel (card F4).
    var identifier: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !label.isEmpty {
                Text(label)
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(ThemeColors.onSurfaceVariant)
                    .lineLimit(2)
            }
            HStack(spacing: 6) {
                TextField("", text: $text)
                    .keyboardType(decimal ? .decimalPad : .numberPad)
                    .appTextStyle(AppTypography.bodyLarge)
                    .foregroundStyle(glowInk)
                    .modifier(FieldIdentifier(identifier))
                    .onChange(of: text) { _, value in
                        let filtered = Self.filter(value, decimal: decimal, limit: limit)
                        if filtered != value { text = filtered }
                    }
                Text(unit)
                    .appTextStyle(AppTypography.bodySmall)
                    .foregroundStyle(ThemeColors.onSurfaceVariant)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(ThemeColors.surfaceContainerLow, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(error ? ThemeColors.error : ThemeColors.outline, lineWidth: error ? 1.5 : 1)
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Digits, and a decimal point or comma when the field takes one, capped at `limit`
    /// (`v.filter { ... }.take(5)`, ProfileScreen.kt:230).
    static func filter(_ value: String, decimal: Bool, limit: Int) -> String {
        String(value.filter { $0.isNumber || (decimal && ($0 == "." || $0 == ",")) }.prefix(limit))
    }
}

/// `.accessibilityIdentifier` only when the caller named the field, so no field ever carries an
/// empty identifier (card F4 needs one name on the last field of the panel).
private struct FieldIdentifier: ViewModifier {
    private let identifier: String?

    init(_ identifier: String?) {
        self.identifier = identifier
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        if let identifier {
            content.accessibilityIdentifier(identifier)
        } else {
            content
        }
    }
}

/// A round icon button for one sex, in its own colour: muted until selected, then filled.
/// (`SexToggle`, ProfileScreen.kt:257)
struct SexToggle: View {
    var icon: PhosphorIcon
    var label: String
    var accent: Color
    var selected: Bool
    var onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            icon.image
                .resizable()
                .scaledToFit()
                .frame(width: 26, height: 26)
                .foregroundStyle(selected ? accent : accent.opacity(0.55))
                .frame(width: 52, height: 52)
                .background(selected ? accent.opacity(0.2) : ThemeColors.surfaceContainerHigh, in: Circle())
                .overlay(
                    Circle().stroke(selected ? accent : ThemeColors.outline, lineWidth: selected ? 1.5 : 1)
                )
        }
        .buttonStyle(.plain)
        .padding(.top, 8)
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// Two or more labels in a pill; the selected one sits in a lighter, outlined pill.
/// (`SegmentedTabs`, Cards.kt:116)
struct SegmentedTabs: View {
    var options: [String]
    var selected: Int
    var onSelect: (Int) -> Void

    init(_ options: [String], selected: Int, onSelect: @escaping (Int) -> Void) {
        self.options = options
        self.selected = selected
        self.onSelect = onSelect
    }

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(options.enumerated()), id: \.offset) { index, label in
                let on = index == selected
                Text(label)
                    .appTextStyle(AppTypography.labelLarge)
                    .foregroundStyle(on ? ThemeColors.onSurface : ThemeColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(on ? ThemeColors.surfaceContainerHighest : Color.clear, in: Capsule())
                    .overlay(Capsule().stroke(on ? ThemeColors.outline : Color.clear, lineWidth: 1))
                    .contentShape(Capsule())
                    .onTapGesture { onSelect(index) }
                    .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
            }
        }
        .padding(4)
        .background(ThemeColors.surfaceContainerHigh, in: Capsule())
    }
}

/// A row with a box and a label. (`CheckRow`, ProfileScreen.kt:242)
struct CheckRow: View {
    var label: String
    var checked: Bool
    var onChange: (Bool) -> Void

    init(_ label: String, checked: Bool, onChange: @escaping (Bool) -> Void) {
        self.label = label
        self.checked = checked
        self.onChange = onChange
    }

    var body: some View {
        HStack(spacing: 10) {
            // Compose draws a square Checkbox; iOS has none, so the box is the nearest Phosphor
            // glyph, in the same two states.
            PhosphorIcon.checkCircle.image
                .resizable()
                .scaledToFit()
                .frame(width: 20, height: 20)
                .foregroundStyle(checked ? ThemeColors.primary : ThemeColors.onSurfaceVariant)
            Text(label)
                .appTextStyle(AppTypography.bodyMedium)
                .foregroundStyle(glowInk)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture { onChange(!checked) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(checked ? [.isButton, .isSelected] : .isButton)
    }
}

/// One of the five usual-activity levels. (Material 3 `FilterChip`, ProfileScreen.kt:196)
struct ActivityChip: View {
    var number: Int
    var selected: Bool
    var onClick: () -> Void

    var body: some View {
        Text("\(number)")
            .appTextStyle(AppTypography.labelLarge)
            .foregroundStyle(selected ? ThemeColors.onSurface : ThemeColors.onSurfaceVariant)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(selected ? ThemeColors.surfaceContainerHighest : Color.clear, in: Capsule())
            .overlay(Capsule().stroke(selected ? ThemeColors.outline : ThemeColors.outlineVariant, lineWidth: 1))
            .contentShape(Capsule())
            .onTapGesture(perform: onClick)
            .accessibilityLabel("\(number)")
            .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}
