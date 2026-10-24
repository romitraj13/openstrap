import SwiftUI

/// The Phosphor icons OpenStrap uses, ported from
/// `app/src/main/kotlin/org/openstrap/app/ui/theme/PhosphorIcons.kt` (regular weight; `*Fill` for
/// selected states). MIT licensed — see `Design/Licenses/MIT-Phosphor.txt`.
///
/// The raw value is the Kotlin `icon("Name", …)` name, which is also the name of the image set
/// `scripts/gen_icons.py` writes into `Assets.xcassets/Phosphor/`. The sets are template images,
/// so `foregroundStyle` tints them the way Compose's `Icon()` does.
enum PhosphorIcon: String, CaseIterable, Identifiable {
    case house = "House"
    case houseFill = "HouseFill"
    case heart = "Heart"
    case heartFill = "HeartFill"
    case moon = "Moon"
    case moonFill = "MoonFill"
    case watch = "Watch"
    case watchFill = "WatchFill"
    case heartbeat = "Heartbeat"
    case drop = "Drop"
    case wind = "Wind"
    case thermometerSimple = "ThermometerSimple"
    case brain = "Brain"
    case batteryCharging = "BatteryCharging"
    case plant = "Plant"
    case footprints = "Footprints"
    case lightning = "Lightning"
    case chartLine = "ChartLine"
    case chartLineUp = "ChartLineUp"
    case barbell = "Barbell"
    case arrowCircleUp = "ArrowCircleUp"
    case cloudArrowDown = "CloudArrowDown"
    case folderOpen = "FolderOpen"
    case key = "Key"
    case warning = "Warning"
    case warningCircle = "WarningCircle"
    case checkCircle = "CheckCircle"
    case info = "Info"
    case linkBreak = "LinkBreak"
    case bluetooth = "Bluetooth"
    case downloadSimple = "DownloadSimple"
    case terminalWindow = "TerminalWindow"
    case copy = "Copy"
    case x = "X"
    case arrowLeft = "ArrowLeft"
    case caretLeft = "CaretLeft"
    case caretRight = "CaretRight"
    case caretUpFill = "CaretUpFill"
    case caretDownFill = "CaretDownFill"
    case user = "User"
    case genderMale = "GenderMale"
    case genderFemale = "GenderFemale"
    case caretDown = "CaretDown"
    case calendarBlank = "CalendarBlank"
    case slidersHorizontal = "SlidersHorizontal"
    case shieldCheck = "ShieldCheck"

    var id: String { rawValue }

    /// The icon as a template image, so the caller's foreground style tints it.
    var image: Image { Image(rawValue) }

    /// The Kotlin name, for the Design lab and the parity table.
    var name: String { rawValue }
}
