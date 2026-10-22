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
                    ForEach(Array(model.groups.enumerated()), id: \.offset) { _, group in
                        DotPanel {
                            DotHeader(group.title, trailing: group.trailing)
                            ForEach(Array(group.workouts.enumerated()), id: \.offset) { index, analysis in
                                if index > 0 {
                                    DotDivider()
                                }
                                WorkoutLine(analysis: analysis, showDate: false) {
                                    openStart = analysis.workout.start
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .background(ThemeColors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(item: $openStart) { start in
            WorkoutScreen(health: health, start: start)
        }
    }
}
