import SwiftUI
import OpenStrapShared

/*
 * Steps, sleep and active hours to aim for each day, ported from
 * `app/src/main/kotlin/org/openstrap/app/ui/device/GoalsDialog.kt`. Every goal ring and "goal
 * reached" uses these.
 *
 * Compose shows this in its glass alert dialog; on iOS it is a sheet in the same glass, with the
 * same ranges (`GoalsRules` in shared Kotlin) and the same strings.
 *
 * Card F4: the sheet has its own 520 pt detent and covers the shell, so it never scrolls under
 * the floating bar and takes no bottom clearance.
 */
struct GoalsSheet: View {
    @ObservedObject var health: HealthModel

    @Environment(\.dismiss) private var dismiss

    @State private var steps = ""
    @State private var sleepHours = ""
    @State private var sleepMinutes = ""
    @State private var activeHours = ""
    @State private var hydrated = false

    private var valid: Bool {
        GoalsRules.shared.valid(
            steps: steps,
            sleepHours: sleepHours,
            sleepMinutes: sleepMinutes,
            activeHours: activeHours
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                PhosphorIcon.chartLineUp.image
                    .resizable()
                    .scaledToFit()
                    .frame(width: 22, height: 22)
                    .foregroundStyle(glowInk)
                Text("Your goals")
                    .appTextStyle(AppTypography.titleLarge)
                    .foregroundStyle(glowInk)
            }

            Text("What you aim for each day. Change them when they stop fitting.")
                .appTextStyle(AppTypography.bodyMedium)
                .foregroundStyle(glowInk)

            NumberField(label: "Steps", unit: "steps", text: $steps)

            HStack(spacing: 10) {
                NumberField(label: "Sleep", unit: "h", text: $sleepHours, limit: 2)
                NumberField(label: "", unit: "min", text: $sleepMinutes, limit: 2)
            }

            NumberField(label: "Active hours", unit: "of 24", text: $activeHours, limit: 2)

            Text("An active hour is one with at least 250 steps.")
                .appTextStyle(AppTypography.bodySmall)
                .foregroundStyle(ThemeColors.onSurfaceVariant)

            HStack(spacing: 8) {
                Spacer(minLength: 0)
                Button { dismiss() } label: {
                    Text("Cancel")
                        .appTextStyle(AppTypography.labelLarge)
                        .foregroundStyle(ThemeColors.onSurfaceVariant)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("goals.cancel")
                Button(action: save) {
                    Text("Save")
                        .appTextStyle(AppTypography.labelLarge)
                        .foregroundStyle(valid ? ThemeColors.primary : ThemeColors.onSurfaceVariant)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
                .disabled(!valid)
                .accessibilityIdentifier("goals.save")
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            ZStack {
                GlowGlassBackground()
                Color(hex: 0xFF0B0B0D).opacity(0.72)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .strokeBorder(Color.white.opacity(0.12), lineWidth: 1)
        )
        .padding(12)
        .onAppear(perform: hydrate)
    }

    private func hydrate() {
        guard !hydrated else { return }
        steps = GoalsRules.shared.stepsText(goals: health.goals)
        sleepHours = GoalsRules.shared.sleepHoursText(goals: health.goals)
        sleepMinutes = GoalsRules.shared.sleepMinutesText(goals: health.goals)
        activeHours = GoalsRules.shared.activeHoursText(goals: health.goals)
        hydrated = true
    }

    private func save() {
        let goals = GoalsRules.shared.toGoals(
            steps: steps,
            sleepHours: sleepHours,
            sleepMinutes: sleepMinutes,
            activeHours: activeHours,
            current: health.goals
        )
        health.saveGoals(goals) { dismiss() }
    }
}
