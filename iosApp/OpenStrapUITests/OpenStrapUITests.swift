import UIKit
import XCTest

final class OpenStrapUITests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    /// The Pair screen is where a fresh install starts: no key in the Keychain.
    func testPairScreenAppears() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["openStrapTitle"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.secureTextFields["authKeyField"].exists)
    }

    /// Card U4: five tabs, the shared header, the calendar and every You screen. `-demoData`
    /// seeds the sample month, so each tab has something to draw without a strap.
    ///
    /// One screenshot per state the card touches, named `U4-<screen>-<n>` so `screens.py` picks
    /// them up.
    func testEveryTabAndYouScreen() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded sample data"
        )
        // The header the four date tabs share.
        XCTAssertTrue(app.staticTexts["app.wordmark"].exists, "The header never drew its wordmark")
        attach(app, "U4-today-1")

        // The five destinations, in the order MainActivity's Tab enum declares them.
        for label in ["Today", "Recovery", "Sleep", "Activity", "You"] {
            XCTAssertTrue(app.buttons[label].exists, "There is no \(label) tab")
        }

        app.buttons["Recovery"].tap()
        XCTAssertTrue(
            app.staticTexts["Recovery"].waitForExistence(timeout: 30),
            "The Recovery tab never drew its panel"
        )
        attach(app, "U4-recovery-1")

        app.buttons["Sleep"].tap()
        XCTAssertTrue(app.staticTexts["sleep.title"].waitForExistence(timeout: 60), "Sleep never drew the night's card")
        attach(app, "U4-sleep-1")

        app.buttons["Activity"].tap()
        XCTAssertTrue(
            app.staticTexts["Activity"].waitForExistence(timeout: 30),
            "The Activity tab never drew its panel"
        )
        attach(app, "U4-activity-1")

        // The date navigator's calendar sheet, from the shared header.
        XCTAssertTrue(app.buttons["date.pick"].waitForExistence(timeout: 30), "The header has no day picker")
        app.buttons["date.pick"].tap()
        if !app.staticTexts["Has data"].waitForExistence(timeout: 30) {
            attach(app, "U4-calendar-missing")
            XCTFail("The calendar sheet never opened")
        }
        attach(app, "U4-calendar-1")
        dismissSheet(app)
        XCTAssertTrue(
            app.staticTexts["Has data"].waitForNonExistence(timeout: 30),
            "The calendar sheet never closed, so the tab bar is covered"
        )

        // You, and everything it opens. Each step waits on an element only that screen has: the
        // hub's own rows carry the same words.
        app.buttons["You"].tap()
        XCTAssertTrue(app.staticTexts["you.title"].waitForExistence(timeout: 30), "The You tab never drew")
        // Card F4: every screen that scrolls under the floating bar must let its last row come clear
        // of it. Each screen's numbers are measured as the walk goes, and asserted together at the end
        // so one run names every screen whose last row is still under the bar.
        var navBarFailures: [String] = []
        // Card F4: the hub's last row is the Licences row; scrolled to the end it must sit above
        // the floating bar, and the screenshot is taken there.
        if let problem = clearsNavBar(app, lastRow: "you.licences", screen: "You") { navBarFailures.append(problem) }
        attach(app, "U4-you-1")
        scrollToTop(app, until: app.buttons["you.profile"])

        tap(app, "you.profile")
        XCTAssertTrue(app.buttons["profile.back"].waitForExistence(timeout: 30), "Your profile never opened")
        // The panel's last field is the one to reach at the end; Save is in the header above it.
        if let problem = clearsNavBar(app, lastRow: "profile.sleepNeed", screen: "Your profile") { navBarFailures.append(problem) }
        attach(app, "U4-profile-1")
        scrollToTop(app, until: app.buttons["profile.back"])
        app.buttons["profile.back"].tap()
        XCTAssertTrue(app.buttons["you.profile"].waitForExistence(timeout: 30), "Back from Your profile never returned to You")

        tap(app, "you.goals")
        if !app.buttons["goals.cancel"].waitForExistence(timeout: 30) {
            // A screenshot and the hierarchy, so a failure here says what it saw.
            attach(app, "U4-goalssheet-missing")
            print("U4 after you.goals:\n\(app.debugDescription)")
            XCTFail("The goals sheet never opened")
        }
        attach(app, "U4-goals-1")
        app.buttons["goals.cancel"].tap()
        XCTAssertTrue(app.buttons["you.goals"].waitForExistence(timeout: 30), "Cancel never closed the goals sheet")

        tap(app, "you.strap")
        XCTAssertTrue(app.buttons["strap.back"].waitForExistence(timeout: 30), "Your strap never opened")
        // The Diagnostics row is the last thing on the screen, and the row U4's screenshot showed
        // under the bar.
        if let problem = clearsNavBar(app, lastRow: "diagnosticsLink", screen: "Your strap") { navBarFailures.append(problem) }
        attach(app, "U4-strap-1")
        scrollToTop(app, until: app.buttons["strap.back"])
        app.buttons["strap.back"].tap()
        XCTAssertTrue(app.buttons["you.strap"].waitForExistence(timeout: 30), "Back from Your strap never returned to You")

        tap(app, "you.diagnostics")
        XCTAssertTrue(app.navigationBars["Diagnostics"].waitForExistence(timeout: 30), "Diagnostics never opened")
        // The log is the last thing here, and it can be thousands of points long.
        if let problem = clearsNavBar(app, lastRow: "logText", screen: "Diagnostics") { navBarFailures.append(problem) }
        attach(app, "U4-diagnostics-1")
        app.navigationBars["Diagnostics"].buttons.firstMatch.tap()
        XCTAssertTrue(app.buttons["you.diagnostics"].waitForExistence(timeout: 30), "Back from Diagnostics never returned to You")

        tap(app, "you.licences")
        XCTAssertTrue(app.buttons["licences.back"].waitForExistence(timeout: 30), "The licence texts never opened")
        // The last licence text is the last row of the screen.
        if let problem = clearsNavBar(app, lastRow: "licence.OFL-MuseoModerno", screen: "Licences") { navBarFailures.append(problem) }
        attach(app, "U4-licences-1")
        scrollToTop(app, until: app.buttons["licences.back"])
        app.buttons["licences.back"].tap()

        XCTAssertTrue(app.staticTexts["you.title"].waitForExistence(timeout: 30), "Back from Licences never returned to You")

        XCTAssertTrue(
            navBarFailures.isEmpty,
            "the floating tab bar still covers the last row of:\n" + navBarFailures.joined(separator: "\n")
        )
    }

    /// Card F4: a screen that scrolls under the floating bar must let its last row come fully clear
    /// of it. `navBar.top` is the bar's own top edge (`FloatingNavBar`), so the bound is the bar's
    /// real frame and not a number copied into the test. Each screen's numbers are printed as they
    /// are measured and returned as a sentence when the row is still under the bar, so one run
    /// names every screen that is wrong instead of stopping at the first (the walk asserts them
    /// together at the end).
    private func clearsNavBar(_ app: XCUIApplication, lastRow identifier: String, screen: String) -> String? {
        let bar = app.descendants(matching: .any)["navBar.top"]
        guard bar.waitForExistence(timeout: 30) else {
            print("F4 \(screen): there is no navBar.top. Tree:\n\(app.debugDescription)")
            return "\(screen): the floating bar never reported its top edge"
        }
        let row = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
        guard row.waitForExistence(timeout: 30) else {
            return "\(screen): \(identifier) never appeared"
        }
        scrollToEnd(app, until: row)
        let barTop = bar.frame.minY
        // An identifier set on a container is repeated by SwiftUI on every text inside it
        // (`LicencesView`'s DotPanel does this, so `licence.OFL-MuseoModerno` matches the label and
        // the body). The row's bottom edge is what must clear the bar, so measure the lowest match.
        let lowest = app.descendants(matching: .any).matching(identifier: identifier)
            .allElementsBoundByIndex.max { $0.frame.maxY < $1.frame.maxY }
        let rowBottom = (lowest ?? row).frame.maxY
        print("F4 \(screen): \(identifier) maxY=\(rowBottom) barTop=\(barTop) window=\(app.windows.firstMatch.frame)")
        guard rowBottom <= barTop else {
            return "\(screen): \(identifier) ends at \(rowBottom), under the floating bar whose top is "
                + "\(barTop), so the last row cannot be scrolled clear of it"
        }
        return nil
    }

    /// Drags to the end of a scrolling screen: a held drag moves about half a screen, so the walk
    /// repeats until the row being watched stops moving up (twice, so a single stale frame cannot
    /// end the walk early). A row thousands of points down the list reports a frame that does not
    /// move with the scroll (card F2's note), so only a row the window shows is trusted to say the
    /// list has reached its end.
    private func scrollToEnd(_ app: XCUIApplication, until row: XCUIElement) {
        let window = app.windows.firstMatch.frame
        var previous = row.frame.maxY
        var still = 0
        for _ in 0..<80 {
            drag(app, from: 0.78, to: 0.22)
            Thread.sleep(forTimeInterval: 0.25)
            let frame = row.frame
            let onScreen = frame.minY < window.maxY && frame.maxY > window.minY
            still = onScreen && frame.maxY >= previous - 1 ? still + 1 : 0
            previous = frame.maxY
            if still == 2 { return }
        }
    }

    /// Back to the top of a screen whose header scrolls with it (Your profile, Licences): the back
    /// button is only reachable once the list is back at its start.
    private func scrollToTop(_ app: XCUIApplication, until element: XCUIElement) {
        var drags = 0
        while !element.isHittable && drags < 60 {
            drag(app, from: 0.28, to: 0.78)
            Thread.sleep(forTimeInterval: 0.15)
            drags += 1
        }
        XCTAssertTrue(element.isHittable, "the screen never scrolled back to its top")
    }

    /// One held drag, from `from` to `to` of the window's height. It starts above the floating bar
    /// (its top edge is about 0.85 of the window) so the bar never takes the gesture, and holds at
    /// the end so momentum does not carry the scroll further than the drag.
    private func drag(_ app: XCUIApplication, from: CGFloat, to: CGFloat) {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: from))
            .press(
                forDuration: 0.05,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: to)),
                withVelocity: .fast,
                thenHoldForDuration: 0.05
            )
    }

    /// Taps a row once it can actually take the tap. A popped `NavigationStack` keeps the old
    /// screen's frame over the list for a moment, and a tap sent then lands on the screen leaving
    /// (run 39619975079: no goals sheet, because the tap went to Your profile's fading view). Rows
    /// below the fold are scrolled up first.
    private func tap(_ app: XCUIApplication, _ identifier: String) {
        let row = app.buttons[identifier]
        XCTAssertTrue(row.waitForExistence(timeout: 30), "\(identifier) never appeared")
        let deadline = Date().addingTimeInterval(20)
        var swipes = 0
        while !row.isHittable && Date() < deadline {
            // A row past the bottom of the list needs the list moved, not more waiting.
            if swipes < 4 && row.frame.minY > app.windows.firstMatch.frame.maxY - 120 {
                app.swipeUp()
                swipes += 1
            }
            Thread.sleep(forTimeInterval: 0.3)
        }
        XCTAssertTrue(row.isHittable, "\(identifier) never became tappable")
        row.tap()
    }

    /// Open a metric's detail from a What changed tile.
    ///
    /// The tile is on screen at the top of Today, so its frame is real — which is the whole point.
    /// Card U5's five red runs were one lesson: a SwiftUI stack's off-screen children report frames
    /// clamped into the viewport (card F2's note in this file), so "tap Heart rate" tapped the
    /// overview card's Sleep row every time, whatever the test did about scrolling. The tile is the
    /// same `TodayRoute.metric` push the heart card makes.
    private func openTileDetail(_ app: XCUIApplication, _ label: String, opens title: String) {
        let tile = app.staticTexts[label]
        XCTAssertTrue(tile.waitForExistence(timeout: 30), "the \(label) tile never appeared")
        tile.tap()
        let detail = app.staticTexts["today.detail"]
        if !detail.waitForExistence(timeout: 30) {
            attach(app, "U5-detail-missing")
            print("U5 after tapping \(label):\n\(app.debugDescription)")
            XCTFail("Tapping the \(label) tile pushed no detail")
            return
        }
        XCTAssertEqual(detail.label, title, "the detail that opened is not the tile's metric")
    }

    /// Card U6: the Recovery tab, every section in Compose's order, the folded panels opened, and the
    /// learning state from the first days of the seeded month (too few nights behind them to score).
    /// Screenshots: `U6-recovery-1..n` from the top down, then `U6-recoverylearning-1..n`.
    func testRecovery() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        app.buttons["Recovery"].tap()
        XCTAssertTrue(
            app.staticTexts["recovery.title"].firstMatch.waitForExistence(timeout: 60),
            "The Recovery tab never drew its score card"
        )

        // Every section `GlowRecoveryScreen.kt:76-95` draws, in its order. A section that never
        // rendered at all fails here rather than quietly missing from the screenshots.
        let sections = ["Recovery", "What drove it", "Charge", "Where your charge went", "Body signals", "How it works"]
        let missing = sections.filter { !app.staticTexts[$0].exists }
        XCTAssertTrue(missing.isEmpty, "Recovery never drew: \(missing)")
        walk(app, prefix: "U6-recovery", milestones: sections, screenfuls: 5)

        // "How it works" is folded away at the bottom of the screen: scroll until it can take a tap,
        // then open it for Compose's two paragraphs.
        let howItWorks = app.staticTexts["How it works"]
        for _ in 0..<6 where !howItWorks.isHittable {
            app.swipeUp()
            Thread.sleep(forTimeInterval: 0.4)
        }
        XCTAssertTrue(howItWorks.isHittable, "How it works never came into reach")
        howItWorks.tap()
        let chargeParagraph = "Charge is a running battery: sleep and calm rest fill it, movement, stress "
            + "and simply being awake drain it."
        XCTAssertTrue(
            app.staticTexts[chargeParagraph].firstMatch.waitForExistence(timeout: 30),
            "Tapping How it works opened nothing"
        )
        attach(app, "U6-recovery-6")

        // Back at the top, the contributors unfold from "Top 3 of N" to the whole list.
        scrollToTop(app)
        app.staticTexts["What drove it"].tap()
        Thread.sleep(forTimeInterval: 0.5)
        attach(app, "U6-recovery-7")

        // The learning state: the first days of the demo seed have fewer than the seven nights a
        // score needs, so the card says what it is waiting for (GlowRecoveryScreen.kt:135-137).
        //
        // Three runs (39696188876, 39699291782, 39702319788) died here asking XCUITest to read a
        // list of elements step by step: a query for an element that is not there raises "Failed to
        // get matching snapshot" rather than answering empty, and enumerating `app.staticTexts`
        // while a day's card re-renders can go stale mid-list. So the walk is a fixed count and one
        // wait: the demo seed is three weeks, and `RecoveryQueriesTest` pins that its learning
        // state covers the week at its start, days 14-20 back; 17 taps lands inside that window
        // even if two taps are swallowed by a re-render.
        scrollToTop(app)
        let previous = app.buttons["date.previous"]
        XCTAssertTrue(previous.waitForExistence(timeout: 30), "The header has no previous-day button")
        for _ in 0..<17 {
            previous.tap()
            Thread.sleep(forTimeInterval: 0.5)
        }
        XCTAssertTrue(
            app.staticTexts["recovery.learning"].firstMatch.waitForExistence(timeout: 60),
            "Seventeen days back is not drawing the learning state's sentence"
        )
        walk(app, prefix: "U6-recoverylearning", milestones: ["Recovery", "Charge", "Body signals"], screenfuls: 3)
    }

    /// Card U7: the Sleep tab, every section in Compose's order, and a night the strap never recorded.
    /// Screenshots: `U7-sleep-1..n` from the top down, then `U7-sleepempty-1..n`.
    ///
    /// The demo data writes a scored night (`DemoData.kt` seeds `score = 72 + …`), so the hero here
    /// is the scored state and "What shaped the score" is reachable. It still writes no naps, so
    /// "Naps" is the one section `-demoData` cannot photograph.
    func testSleep() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        app.buttons["Sleep"].tap()
        XCTAssertTrue(
            app.staticTexts["sleep.title"].firstMatch.waitForExistence(timeout: 60),
            "The Sleep tab never drew the night's card"
        )
        // A scored night: the arc carries the score (GlowSleepScreen.kt:141-150).
        XCTAssertTrue(
            app.staticTexts["sleep.score"].exists,
            "The seeded night is not drawing the scored label"
        )

        // Every section `GlowSleepScreen.kt:81-101` draws for this data, in its order. A section that
        // never rendered at all fails here rather than quietly missing from the screenshots.
        let sections = ["Sleep", "Stages", "What shaped the score", "Sleep vitals", "Heart rate", "This week", "Last 7 nights"]
        let missing = sections.filter { !app.staticTexts[$0].exists }
        XCTAssertTrue(missing.isEmpty, "Sleep never drew: \(missing)")
        walk(app, prefix: "U7-sleep", milestones: sections, screenfuls: 6)

        // A night the strap never recorded: far enough back that the seeded month has run out
        // (GlowSleepScreen.kt:128-135).
        scrollToTop(app)
        let previous = app.buttons["date.previous"]
        XCTAssertTrue(previous.waitForExistence(timeout: 30), "The header has no previous-day button")
        for _ in 0..<40 {
            previous.tap()
        }
        XCTAssertTrue(
            app.staticTexts["sleep.empty"].waitForExistence(timeout: 60),
            "A day before the seeded month is not drawing the no-sleep sentence"
        )
        walk(app, prefix: "U7-sleepempty", milestones: [], screenfuls: 2)
    }

    /// Card U8: the Activity tab, every panel in `GlowActivityScreen.kt:87-97`'s order.
    /// Screenshots: `U8-activity-1..n`.
    ///
    /// The demo data writes PAI, workouts and their sport effects (`DemoData.kt`, Android's emulator
    /// seed), so the PAI panel shows its total and the workouts panel lists today's run. It still
    /// writes no profile, so the energy row that asks for the profile and the fitness estimate's
    /// withheld note are the missing-data states shown here. `testWorkout` opens the run and the
    /// all-workouts list.
    func testActivity() {
        let app = XCUIApplication()
        // `-glowStill` holds the cards' cores still: the steps card's stride never settles, and
        // XCUITest waits for a quiescent app before every query (card F5's `-designLabStill`, and
        // run 39737184826's 30 s query timeout on this test).
        app.launchArguments = ["-demoData", "-glowStill"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        app.buttons["Activity"].tap()

        // The steps card glows on the day's stride and never settles, and XCUITest waits for a
        // quiescent app before every query: card F5 lost runs 39587579214 and 39594198894 to a 30 s
        // query timeout on a screen that animated for ever, and this test timed out the same way in
        // run 39737184826 after passing in 39732609901 on an identical tree. A glow card renders its
        // blurred layers on first appearance, one at a time and behind a lock, so the first query
        // after the tab switch is the one that waits. Pause for that render, then ask one query per
        // group of strings instead of one per string.
        Thread.sleep(forTimeInterval: 5)

        XCTAssertTrue(
            app.staticTexts["Steps"].waitForExistence(timeout: 60),
            "The Activity tab never drew its steps card"
        )

        // Every section `GlowActivityScreen.kt:87-97` draws, in its order, in a single query.
        let sections = ["Steps", "Active minutes", "Energy", "PAI", "Training load", "Fitness", "Workouts"]
        let drawn = Set(
            app.staticTexts
                .matching(NSPredicate(format: "label IN %@", sections))
                .allElementsBoundByIndex
                .map(\.label)
        )
        let missing = sections.filter { !drawn.contains($0) }
        XCTAssertTrue(missing.isEmpty, "Activity never drew: \(missing)")

        // The state each panel is in for this data: PAI's note, the profile row the energy panel
        // asks for, the workouts panel's empty note and the load panel's trend line.
        let notes = [
            "Add age, weight and height to estimate the energy you use.",
            "Fitness and fatigue are 42- and 7-day averages of your daily load; form is fitness minus fatigue.",
        ]
        let written = Set(
            app.staticTexts
                .matching(NSPredicate(format: "label IN %@", notes))
                .allElementsBoundByIndex
                .map(\.label)
        )
        for note in notes {
            XCTAssertTrue(written.contains(note), "a panel is not drawing its own note: \(note)")
        }

        // The demo data has PAI and a workout, so those panels show data, not their empty notes.
        XCTAssertTrue(
            app.staticTexts["Outdoor run"].exists,
            "The Workouts panel is not listing the seeded run"
        )

        // The two sentences that carry a figure, matched by their opening words.
        let prefixOne = "Estimated fitness: "
        let prefixTwo = "Each minute counts by how hard your heart works between resting "
        let sentences = app.staticTexts
            .matching(NSPredicate(format: "label BEGINSWITH %@ OR label BEGINSWITH %@", prefixOne, prefixTwo))
            .allElementsBoundByIndex
            .map(\.label)
        for prefix in [prefixOne, prefixTwo] {
            XCTAssertTrue(
                sentences.contains { $0.hasPrefix(prefix) },
                "no sentence starts with \(prefix), so that panel's own line is missing"
            )
        }

        walk(app, prefix: "U8-activity", milestones: [], screenfuls: 6)
    }

    /// Card U8: the workout detail and the all-workouts list, reached from the Activity tab's
    /// Workouts panel. Screenshots: `U8-workout-1..n` (today's run, pushed) and `U8-workouts-1..n`
    /// (the list). The demo data seeds one run today and ten on other evenings (`DemoData.kt`), so
    /// both screens have a workout to draw.
    ///
    /// The recovery-heart-rate panel needs the three-minute tail the strap records after a workout,
    /// which the seed's `WorkoutHeartRate` stream stops short of, so it is not asserted here — the
    /// heart chart, zones and training effect are.
    func testWorkout() {
        let app = XCUIApplication()
        app.launchArguments = ["-demoData", "-glowStill"]
        app.launch()

        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        app.buttons["Activity"].tap()
        XCTAssertTrue(
            app.staticTexts["Steps"].waitForExistence(timeout: 60),
            "The Activity tab never drew its steps card"
        )
        Thread.sleep(forTimeInterval: 5)

        // Scroll the Workouts panel into view and open today's run, the first "Outdoor run" row.
        let run = app.staticTexts["Outdoor run"].firstMatch
        var swipes = 0
        while !run.isHittable && swipes < 12 {
            app.swipeUp()
            swipes += 1
        }
        XCTAssertTrue(run.isHittable, "never scrolled the run into view")
        run.tap()

        // The pushed detail: the heart card, the summary, the zones and the strap's training effect.
        XCTAssertTrue(
            app.staticTexts["Training effect"].waitForExistence(timeout: 60),
            "The workout detail never drew its training-effect panel"
        )
        let detail = ["Heart rate", "Active", "Paused", "Lowest HR", "Heart-rate zones", "Training effect"]
        let drawn = Set(
            app.staticTexts
                .matching(NSPredicate(format: "label IN %@", detail))
                .allElementsBoundByIndex
                .map(\.label)
        )
        let missing = detail.filter { !drawn.contains($0) }
        XCTAssertTrue(missing.isEmpty, "the workout detail never drew: \(missing)")
        walk(app, prefix: "U8-workout", milestones: detail, screenfuls: 3)

        // Back to the Activity tab, then the whole list.
        app.buttons["Back"].tap()
        XCTAssertTrue(
            app.staticTexts["Steps"].waitForExistence(timeout: 60),
            "Back never returned to the Activity tab"
        )
        let all = app.staticTexts["All workouts"].firstMatch
        var listSwipes = 0
        while !all.isHittable && listSwipes < 12 {
            app.swipeUp()
            listSwipes += 1
        }
        XCTAssertTrue(all.isHittable, "never scrolled the All workouts row into view")
        all.tap()
        XCTAssertTrue(
            app.staticTexts["Workouts"].waitForExistence(timeout: 60),
            "The all-workouts list never drew"
        )
        walk(app, prefix: "U8-workouts", milestones: ["Workouts", "Outdoor run"], screenfuls: 4)
    }

    /// A medium sheet has no close button: drag it down, as a finger would.
    private func dismissSheet(_ app: XCUIApplication) {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            .press(
                forDuration: 0.1,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 1.05)),
                withVelocity: .fast,
                thenHoldForDuration: 0.1
            )
    }

    /// Card U5: Today, in the two orders a day has, the tile chooser, the pushed detail, Monday's
    /// recap and a day with no data at all. Screenshots: `U5-today-1..n` (later in the day),
    /// `U5-tilesheet-1`, `U5-detail-1`, `U5-todaymorning-1..n`, `U5-todayempty-1..n`,
    /// `U5-todayrecap-1..n`.
    ///
    /// The app is launched several times, which is safe: the demo seed writes the same rows for the
    /// same day (`SampleDataTest` in shared pins that), so the screenshots all show one month of wear.
    func testToday() {
        // Later in the day: Charge leads the top card, and last night sits at the bottom.
        let app = XCUIApplication()
        app.launchArguments = ["-demoData", "-todayEvening"]
        app.launch()
        XCTAssertTrue(
            app.staticTexts["What changed"].waitForExistence(timeout: 120),
            "Today never drew its What changed panel from the seeded month"
        )
        walk(app, prefix: "U5-today", milestones: ["What changed", "Heart rate", "steps", "asleep"], screenfuls: 5)

        // The tile chooser, from the top of the screen.
        scrollToTop(app)
        app.staticTexts["Edit"].tap()
        if !app.buttons["tiles.cancel"].waitForExistence(timeout: 30) {
            attach(app, "U5-tilesheet-missing")
            print("U5 after Edit:\n\(app.debugDescription)")
            XCTFail("The tile chooser never opened")
        }
        XCTAssertTrue(app.staticTexts["What changed shows"].exists, "The tile chooser has no title")
        attach(app, "U5-tilesheet-1")
        app.buttons["tiles.cancel"].tap()
        XCTAssertTrue(app.staticTexts["What changed"].waitForExistence(timeout: 30), "Cancel never closed the tile chooser")

        // A metric tap pushes the detail route card U9 fills in, from a What changed tile: the tile
        // is on screen, so its frame is real (see `openTileDetail`).
        openTileDetail(app, "Resting HR", opens: "Resting heart rate")
        attach(app, "U5-detail-1")
        // Back to Today. The pushed card wears the stack's own bar; if the tab's hidden toolbar takes
        // it with it, restart instead of guessing at a button that is not there.
        if app.navigationBars.buttons.count > 0 {
            app.navigationBars.buttons.firstMatch.tap()
        } else {
            app.terminate()
            app.launch()
        }
        XCTAssertTrue(app.staticTexts["What changed"].waitForExistence(timeout: 60), "Back never returned to Today")

        // A day with no data at all: far enough back that the seeded month has run out.
        scrollToTop(app)
        let previous = app.buttons["date.previous"]
        XCTAssertTrue(previous.waitForExistence(timeout: 30), "The header has no previous-day button")
        for _ in 0..<40 {
            previous.tap()
        }
        XCTAssertTrue(
            app.staticTexts["No heart-rate data yet. Sync your strap."].waitForExistence(timeout: 60),
            "A day before the seeded month is not drawing the empty state"
        )
        XCTAssertFalse(app.staticTexts["What changed"].exists, "An empty day still drew What changed")
        walk(app, prefix: "U5-todayempty", milestones: ["Heart rate", "steps"], screenfuls: 2)

        app.terminate()

        // The morning order: the check-in under the score, and last night right after it.
        let morning = XCUIApplication()
        morning.launchArguments = ["-demoData", "-todayMorning"]
        morning.launch()
        XCTAssertTrue(
            morning.staticTexts["Morning check-in"].waitForExistence(timeout: 120),
            "Today never drew the morning check-in"
        )
        walk(morning, prefix: "U5-todaymorning", milestones: ["Morning check-in", "What changed", "Heart rate", "steps"], screenfuls: 5)
        morning.terminate()

        // Monday: the week's recap, under the top card.
        let monday = XCUIApplication()
        monday.launchArguments = ["-demoData", "-todayMonday", "-todayEvening"]
        monday.launch()
        XCTAssertTrue(
            monday.staticTexts["Your week"].waitForExistence(timeout: 120),
            "Monday's recap never drew"
        )
        attach(monday, "U5-todayrecap-1")
        monday.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
            .press(
                forDuration: 0.1,
                thenDragTo: monday.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)),
                withVelocity: .slow,
                thenHoldForDuration: 0.4
            )
        Thread.sleep(forTimeInterval: 0.4)
        attach(monday, "U5-todayrecap-2")
    }

    /// Every screenful of a long screen from the top down, attaching `prefix-1`, `prefix-2`, ….
    ///
    /// The stop condition is a count, not a frame: a SwiftUI stack's off-screen children report
    /// frames clamped into the viewport (card F2's note), so "the milestone is inside the window"
    /// is true even at the very top of the list — card U5's first walk stopped after one screenshot
    /// for that reason. `screenfuls` is how many screenfuls the screen is tall, so the screenshots
    /// really do run top to bottom. The milestones are then asserted to have rendered at all.
    @discardableResult
    private func walk(_ app: XCUIApplication, prefix: String, milestones: [String], screenfuls: Int = 4) -> Int {
        let window = app.windows.firstMatch
        XCTAssertTrue(window.waitForExistence(timeout: 60), "the app never drew a window")
        let missing = milestones.filter { !app.staticTexts[$0].exists }
        XCTAssertTrue(
            missing.isEmpty,
            "the walk over \(prefix) found no \(missing) in the tree at all, so the screen is missing a section"
        )

        func capture(_ number: Int) {
            attach(app, "\(prefix)-\(number)")
        }

        func scrollOneScreenful() {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
                .press(
                    forDuration: 0.1,
                    thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.25)),
                    withVelocity: .slow,
                    thenHoldForDuration: 0.4
                )
        }

        capture(1)
        for number in 2...max(screenfuls, 1) {
            scrollOneScreenful()
            Thread.sleep(forTimeInterval: 0.5)
            capture(number)
        }
        return max(screenfuls, 1)
    }

    /// Back to the top of a dated tab, where the header and the day's first card are.
    private func scrollToTop(_ app: XCUIApplication) {
        for _ in 0..<8 {
            app.swipeDown()
        }
        Thread.sleep(forTimeInterval: 0.3)
    }

    /// Cards U2, F2 and F5: the design lab, one section per launch.
    ///
    /// Card F5 split the walk by section: `-designLabSection <name>` makes the lab draw a single
    /// section, so no launch holds the whole tree. One screen holding thirteen glow cards, 46
    /// icons, five tab bars and two calendar sheets made XCUITest's queries time out on a lab that
    /// was otherwise correct — `testDesignLab` failed with "Timed out while evaluating UI query"
    /// and passed on the same tree's rerun three times after F2 (runs 39647481836, 39664474482 and
    /// the retries of 883bb5b and e2c6ed6), each one a 25 to 40 minute CI round. A launch now
    /// walks about a screenful of tree.
    ///
    /// Card F2's assertion is kept for every section, and is stronger here: the section sits at the
    /// top of its own scroll view when the launch settles, so the frame XCUITest reports is the one
    /// the window shows. (F2 measured 440 pt for a 362 pt section that was thousands of points down
    /// the old shared scroll view, and that frame did not move with the scroll.)
    ///
    /// The screenshots keep the names the whole-lab walk used — `U2-designlab-1`, … — and stay in
    /// layout order, because the counter runs across the sections instead of restarting at each
    /// launch. How many there are changes with the sections' heights.
    func testDesignLab() {
        // `-designLabStill` holds the glow cores still: a lab that animates for ever never goes
        // quiescent, and that is what makes an XCUITest query wait out its 30 s timeout.
        var shots = 0

        for section in Self.labSections {
            let app = XCUIApplication()
            app.launchArguments = ["-designLab", "-designLabStill", "-designLabSection", section]
            app.launch()

            // Card F5: the first query of a launch is the one that times out. A query waits for the
            // app to answer, the glow cards render on first appearance (one at a time, behind a
            // lock), and a query issued into that render waited 9.6 s in one green run and past the
            // 30 s snapshot timeout in run 39706541852, at the cards launch. The sections only
            // render the cards the window shows now (`LazyVStack`), and this pause lets those few
            // finish before the first query: `wait(for:)` reads the app's state, not its tree.
            XCTAssertTrue(app.wait(for: .runningForeground, timeout: 60), "the app never reached the foreground")
            Thread.sleep(forTimeInterval: 4)
            XCTAssertTrue(
                app.staticTexts["lab.title"].waitForExistence(timeout: 120),
                "the design lab did not open for -designLabSection \(section)"
            )
            Thread.sleep(forTimeInterval: 1)
            let window = app.windows.firstMatch
            XCTAssertTrue(window.waitForExistence(timeout: 60), "the app never drew a window")
            let windowFrame = window.frame

            // The title travels with the column, so a section wider than the window pushes it off
            // the left the way F2 saw (`lab.title` at x=-4.33); the section itself is the other
            // half of the same check.
            for identifier in ["lab.title", "lab.\(section)"] {
                let element = app.descendants(matching: .any).matching(identifier: identifier).firstMatch
                XCTAssertTrue(
                    element.waitForExistence(timeout: 60),
                    "the lab drew no \(identifier) for -designLabSection \(section)"
                )
                let frame = element.frame
                print("F2 \(identifier) frame=\(frame) window=\(windowFrame)")
                XCTAssertGreaterThanOrEqual(
                    frame.minX, 0,
                    "\(identifier) starts at x=\(frame.minX), off the left of the window \(windowFrame) "
                        + "(section frame \(frame))"
                )
                XCTAssertLessThanOrEqual(
                    frame.maxX, windowFrame.maxX,
                    "\(identifier) ends at x=\(frame.maxX), off the right of the window \(windowFrame) "
                        + "(section frame \(frame))"
                )
            }

            // One screenshot per screenful of this section, top to bottom. The walk stops when a
            // screenful is identical to the one before it, which is the scroll view saying it has
            // reached the bottom of the section — the lab holds its glow still, so two captures of
            // one scroll position are the same bytes.
            var previous: Data?
            var screenfuls = 0
            func capture() -> Bool {
                let screenshot = app.screenshot()
                guard screenshot.pngRepresentation != previous else { return false }
                previous = screenshot.pngRepresentation
                screenfuls += 1
                shots += 1
                attach(screenshot, "U2-designlab-\(shots)")
                // U1's eight screenfuls are the same lab, so the committed U1 set is refreshed from
                // the same captures instead of keeping the clipped layout F2 removed.
                if shots <= Self.u1Screenfuls {
                    attach(screenshot, "U1-designlab-\(shots)")
                }
                return true
            }

            _ = capture()
            while screenfuls < Self.screenfulsPerSection {
                scrollLabOneScreenful(app)
                Thread.sleep(forTimeInterval: 0.5)
                if !capture() { break }
            }
            if screenfuls >= Self.screenfulsPerSection {
                print("F5 lab.\(section) took all \(screenfuls) screenfuls; it may be taller than the walk")
            }
            app.terminate()
        }

        XCTAssertGreaterThanOrEqual(
            shots, Self.labSections.count,
            "\(shots) screenshots for \(Self.labSections.count) sections: a section drew nothing"
        )
    }

    /// One screenful down the lab: a slow drag from near the bottom of the window to just under
    /// half way up, held at the end. Holding is what stops the scroll view carrying on with
    /// momentum, so each capture is one drag's distance further down and no row can slip between
    /// two captures.
    private func scrollLabOneScreenful(_ app: XCUIApplication) {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.88))
            .press(
                forDuration: 0.1,
                thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.28)),
                withVelocity: .slow,
                thenHoldForDuration: 0.4
            )
    }

    /// Every section of the lab, in layout order: the names `-designLabSection` takes, which are
    /// `DesignLabView.LabSection`'s raw values and the section identifiers' suffixes (`lab.type`).
    /// A name the lab does not know draws `lab.unknown`, so a typo here fails rather than passes.
    private static let labSections = [
        "type",
        "colours",
        "cards",
        "motions",
        "dotmatrix",
        "icons",
        "charts",
        "gauges",
        "hypnogram",
        "parts",
        "quiet",
        "navbar",
        "calendar",
    ]

    /// How many screenfuls one section may take before the walk stops. The whole lab used to be
    /// nineteen; the tallest section (the type ramp) is a few.
    private static let screenfulsPerSection = 10

    /// How many screenfuls card U1 shipped (`U1-designlab-1` … `U1-designlab-8`).
    private static let u1Screenfuls = 8

    private func attach(_ app: XCUIApplication, _ name: String) {
        attach(app.screenshot(), name)
    }

    private func attach(_ screenshot: XCUIScreenshot, _ name: String) {
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        // The workflow cannot always export attachments from the .xcresult (that subcommand is
        // recent), so the same PNG is written straight to SCREENSHOT_DIR when it is set.
        write(screenshot, as: name)
    }

    private func write(_ screenshot: XCUIScreenshot, as name: String) {
        guard let directory = ProcessInfo.processInfo.environment["SCREENSHOT_DIR"], !directory.isEmpty else {
            return
        }
        let folder = URL(fileURLWithPath: directory, isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        try? screenshot.pngRepresentation.write(to: folder.appendingPathComponent("\(name).png"))
    }
}
