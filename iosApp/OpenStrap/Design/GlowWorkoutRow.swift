import SwiftUI
import OpenStrapShared

/*
 * One workout as a glass row, ported from `WorkoutLine` in `GlowActivityScreen.kt:403` (card U5) so
 * Today's workouts card and the Activity tab's list read the same. The second line's words, times
 * and totals come from `TodayGlow.workoutDetail` (shared Kotlin).
 */

/// A sport's name, when it ran and what it did, with the caret that opens it.
struct WorkoutLine: View {
    var analysis: WorkoutAnalysis
    /// The Activity list shows the day; Today's card is already under the day's header.
    var showDate: Bool
    var onClick: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text(analysis.sportName)
                    .appTextStyle(AppTypography.titleSmall)
                    .foregroundStyle(glowInk)
                QuietNote(text: TodayGlow.shared.workoutDetail(analysis: analysis, showDate: showDate))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            PhosphorIcon.caretRight.image
                .resizable()
                .scaledToFit()
                .frame(width: 16, height: 16)
                .foregroundStyle(glowInk.opacity(0.7))
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .glass(glassRowShape)
        .contentShape(glassRowShape)
        .onTapGesture(perform: onClick)
    }
}
