import OpenStrapShared
import SwiftUI

/*
 * Every workout, ported from `GlowWorkoutsScreen` in `ui/glow/GlowWorkoutScreen.kt:195-218` (card
 * U8): newest first, one dot panel per day, each day's workouts in the same glass rows the Activity
 * tab and Today use.
 *
 * The days' names and their workouts come from `WorkoutGlow.all` in shared Kotlin, so both builds
 * group and word them the same way; this file draws them.
 */

struct WorkoutsScreen: View {
    @ObservedObject var health: HealthModel

    @Environment(\.dismiss) private var dismiss
    @State private var openStart: Int64?

    var body: some View {
        let model = WorkoutGlow.shared.all(workouts: health.workoutList)
        ScrollView {
            VStack(alignment: .leading, spacing: GlowLayout.columnGap) {
                // `GlowDetailHeader("Workouts", "${workouts.size} recorded", onBack)` (:201).
                GlowDetailHeader(title: "Workouts", subtitle: model.subtitle) {
                    dismiss()
                }
                if let note = model.emptyNote {
                    DotPanel {
                        QuietNote(text: note)
                    }
                } else {
                    ForEach(Array(model.groups.enumerated()), id: \.offset) { groupIndex, group in
                        DotPanel {
                            DotHeader(group.title, trailing: group.trailing)
                            ForEach(Array(group.workouts.enumerated()), id: \.offset) { index, analysis in
                                if index > 0 {
                                    DotDivider()
                                }
                                WorkoutLine(analysis: analysis, showDate: false) {
                                    openStart = analysis.workout.start
                                }
                                // The list's last row, and the row card F9's end-of-scroll walk
                                // measures: the newest group comes first, so the oldest workout in
                                // the last group is the bottom of the screen.
                                .identified(
                                    groupIndex == model.groups.count - 1
                                        && index == group.workouts.count - 1,
                                    as: "workouts.last"
                                )
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        // Card F9: built after F4, so it never took the bar's clearance; its last day's last row is
        // now measured by the end-of-scroll walk.
        .glowClearsNavBar()
        .background(ThemeColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(item: $openStart) { start in
            WorkoutScreen(health: health, start: start)
        }
    }
}

private extension View {
    /// Sets `identifier` on the row it names and leaves every other row unlabelled: an empty
    /// identifier on all but one row would put a nameless element in the tree for each of them.
    @ViewBuilder
    func identified(_ on: Bool, as identifier: String) -> some View {
        if on {
            accessibilityIdentifier(identifier)
        } else {
            self
        }
    }
}
