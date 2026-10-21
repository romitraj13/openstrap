package org.openstrap.app.ui.glow

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The geometry the glow charts draw with, tested the way `GlowCharts.kt` computes it: the same
 * formulas, the same inputs, at a density of 1 (dp == px) so the numbers are readable.
 *
 * Every expected value here is worked out by hand from the Compose source line it names, so a
 * drift in either side shows up as a failure rather than as two charts that quietly disagree.
 */
class GlowGeometryTest {
    private val eps = 0.0001f

    // ---------------------------------------------------------------- GlowLineChart (GlowCharts.kt:126-188)

    /** `lo`/`hi` fit the readings when there is no axis or band. */
    @Test
    fun lineChartDomainFitsTheReadings() {
        assertEquals(Range2(52f, 70f), GlowGeometry.lineChartDomain(52f, 70f, false, 0f, 0f, false, 0f, 0f))
    }

    /** A band widens the domain on whichever side it sits (line 129-130). */
    @Test
    fun lineChartDomainIncludesTheBand() {
        assertEquals(Range2(48f, 66f), GlowGeometry.lineChartDomain(52f, 61f, true, 48f, 66f, false, 0f, 0f))
        assertEquals(Range2(52f, 80f), GlowGeometry.lineChartDomain(52f, 70f, true, 70f, 80f, false, 0f, 0f))
        assertEquals(Range2(50f, 66f), GlowGeometry.lineChartDomain(70f, 66f, true, 50f, 60f, false, 0f, 0f))
    }

    /** A fixed axis wins over the data, and a flat domain gets a unit of room (line 129-130). */
    @Test
    fun lineChartDomainUsesTheAxisOrGrowsAFlatOne() {
        assertEquals(Range2(0f, 100f), GlowGeometry.lineChartDomain(52f, 70f, false, 0f, 0f, true, 0f, 100f))
        assertEquals(Range2(60f, 61f), GlowGeometry.lineChartDomain(60f, 60f, false, 0f, 0f, false, 0f, 0f))
        // A 0.0005 axis still reads as flat: hi - lo < 1e-3.
        assertEquals(Range2(50f, 51f), GlowGeometry.lineChartDomain(60f, 60f, false, 0f, 0f, true, 50f, 50.0005f))
    }

    /** Readings are evenly spaced, inset by the end dot's halo at both edges (line 133). */
    @Test
    fun lineChartXInsertsTheHalo() {
        assertEquals(7f, GlowGeometry.lineChartX(index = 0, count = 25, width = 320f, inset = 7f), eps)
        assertEquals(313f, GlowGeometry.lineChartX(index = 24, count = 25, width = 320f, inset = 7f), eps)
        assertEquals(160f, GlowGeometry.lineChartX(index = 12, count = 25, width = 320f, inset = 7f), eps)
        // A single reading sits on the left inset: i / max(count - 1, 1) is 0.
        assertEquals(7f, GlowGeometry.lineChartX(index = 0, count = 1, width = 320f, inset = 7f), eps)
    }

    /** The value axis runs from `h` at `lo` to `top` at `hi` (line 134). */
    @Test
    fun lineChartYIsTopAtTheTopOfTheDomain() {
        val top = 10f
        val h = 98f
        assertEquals(h, GlowGeometry.lineChartY(40f, 40f, 100f, top, h), eps)
        assertEquals(top, GlowGeometry.lineChartY(100f, 40f, 100f, top, h), eps)
        assertEquals(54f, GlowGeometry.lineChartY(70f, 40f, 100f, top, h), eps)
    }

    /** Axis labels spread from the left edge to the right edge (line 89-95). */
    @Test
    fun axisLabelsSpreadFromEdgeToEdge() {
        assertEquals(0f, GlowGeometry.axisLabelFraction(0, 5), eps)
        assertEquals(1f, GlowGeometry.axisLabelFraction(4, 5), eps)
        assertEquals(0.5f, GlowGeometry.axisLabelFraction(2, 5), eps)
        assertEquals(0f, GlowGeometry.axisLabelFraction(0, 1), eps)
    }

    /** The touch readout sits above the reading, centred, clamped to the canvas (line 71-80). */
    @Test
    fun readoutSitsAboveTheReadingAndStaysInsideTheCanvas() {
        // Text 40x14: bubble 58x24, 12 above the anchor.
        assertEquals(Box2(111f, 44f, 58f, 24f), GlowGeometry.readoutBox(40f, 14f, 140f, 80f, 320f, 18f, 10f, 12f))
        // Near the left edge the bubble is pushed in rather than cut off.
        assertEquals(0f, GlowGeometry.readoutBox(40f, 14f, 4f, 80f, 320f, 18f, 10f, 12f).x, eps)
        // Near the top there is no room above, so it goes below the reading: 20 - 24 - 12 < 0.
        assertEquals(32f, GlowGeometry.readoutBox(40f, 14f, 140f, 20f, 320f, 18f, 10f, 12f).y, eps)
    }

    /** The average line lands where the average value would (line 161-165). */
    @Test
    fun averageLineUsesTheValueAxis() {
        // 10 + (98 - 10) * (1 - (60 - 40) / 60) = 68.667.
        assertEquals(68.6667f, GlowGeometry.lineChartY(60f, 40f, 100f, 10f, 98f), eps)
    }

    // ---------------------------------------------------------------- GlowDotBars (GlowCharts.kt:212-237)

    /** One column per value, one row of dots per unit of height (line 216-218). */
    @Test
    fun dotBarGridIsSquareCellsThatFillThePlot() {
        assertEquals(80f, GlowGeometry.dotBarPitchX(width = 240f, count = 3), eps)
        assertEquals(9.6f, GlowGeometry.dotBarPitchY(h = 96f, rows = 10), eps)
        // The dot is 32% of the smaller pitch, so the grid keeps its gaps on both axes.
        assertEquals(3.072f, GlowGeometry.dotBarRadius(pitchX = 80f, pitchY = 9.6f), eps)
    }

    /** The lit rows are the value's share of the tallest column, at least one (line 219-222). */
    @Test
    fun dotBarLitRowsRoundAndKeepOneDotForAnythingAboveZero() {
        assertEquals(0, GlowGeometry.dotBarLit(value = 0f, max = 500f, rows = 10))
        assertEquals(5, GlowGeometry.dotBarLit(value = 250f, max = 500f, rows = 10))
        assertEquals(10, GlowGeometry.dotBarLit(value = 500f, max = 500f, rows = 10))
        // 400/500 * 12 = 9.6 -> 10.
        assertEquals(10, GlowGeometry.dotBarLit(value = 400f, max = 500f, rows = 12))
        // A barely-there hour still gets one dot rather than none.
        assertEquals(1, GlowGeometry.dotBarLit(value = 10f, max = 500f, rows = 10))
    }

    /** The scale is the tallest column, and never zero (`values.max().coerceAtLeast(1f)`). */
    @Test
    fun dotBarScaleNeverDividesByZero() {
        assertEquals(500f, GlowGeometry.dotBarMax(500f), eps)
        assertEquals(1f, GlowGeometry.dotBarMax(0f), eps)
    }

    /** Columns are centred in their slot and rows are stacked from the bottom (line 225-233). */
    @Test
    fun dotBarCentresAndStacksFromTheBottom() {
        assertEquals(40f, GlowGeometry.dotBarCenterX(index = 0, pitchX = 80f), eps)
        assertEquals(120f, GlowGeometry.dotBarCenterX(index = 1, pitchX = 80f), eps)
        assertEquals(91.2f, GlowGeometry.dotBarDotY(row = 0, pitchY = 9.6f, h = 96f), eps)
        assertEquals(4.8f, GlowGeometry.dotBarDotY(row = 9, pitchY = 9.6f, h = 96f), eps)
    }

    /** Lit dots brighten towards the top of the column (line 231). */
    @Test
    fun dotBarAlphaRisesUpTheColumn() {
        assertEquals(0.595f, GlowGeometry.dotBarAlpha(row = 0, rows = 10), eps)
        assertEquals(1f, GlowGeometry.dotBarAlpha(row = 9, rows = 10), eps)
    }

    // ---------------------------------------------------------------- GlowTrend (GlowCharts.kt:260-292)

    /** `lo` is 85% of the lowest day so the shortest stem still has a base (line 265). */
    @Test
    fun trendDomainStartsBelowTheLowestDay() {
        assertEquals(Range2(34f, 66f), GlowGeometry.trendDomain(knownMin = 40f, knownMax = 66f))
        // The same value everywhere still gets a unit of room to draw in.
        assertEquals(Range2(42.5f, 50f), GlowGeometry.trendDomain(knownMin = 50f, knownMax = 50f))
        // No days at all: 0 and 1, as Compose's `?: 0f` / `?: 1f` fallbacks give.
        assertEquals(Range2(0f, 1f), GlowGeometry.trendDomain(knownMin = 0f, knownMax = 1f))
    }

    /** A day's y leaves room for the value printed above it (line 267). */
    @Test
    fun trendYLeavesRoomForTheValueLabel() {
        val top = 22f
        val h = 112f
        val inset = 6f
        assertEquals(top, GlowGeometry.trendY(66f, 34f, 66f, top, h, inset), eps)
        // 22 + (112 - 22 - 6) * (1 - (40 - 34) / 32) = 90.25.
        assertEquals(90.25f, GlowGeometry.trendY(40f, 34f, 66f, top, h, inset), eps)
    }

    /** Days sit in the middle of equal slots, so the first and last don't touch the edges (line 271). */
    @Test
    fun trendCentresEachDayInItsSlot() {
        val slot = 320f / 7
        assertEquals(slot / 2, GlowGeometry.trendCenterX(index = 0, count = 7, width = 320f), eps)
        assertEquals(slot * 6 + slot / 2, GlowGeometry.trendCenterX(index = 6, count = 7, width = 320f), eps)
    }

    // ---------------------------------------------------------------- GlowArcGauge (GlowCharts.kt:316-336)

    /** The gauge sweeps left to right over the top: 180° at a full arc (line 321). */
    @Test
    fun arcGaugeSweepIsHalfATurnAndClamped() {
        assertEquals(0f, GlowGeometry.arcGaugeSweep(0f), eps)
        assertEquals(90f, GlowGeometry.arcGaugeSweep(0.5f), eps)
        assertEquals(131.4f, GlowGeometry.arcGaugeSweep(0.73f), eps)
        assertEquals(180f, GlowGeometry.arcGaugeSweep(1.5f), eps)
        assertEquals(0f, GlowGeometry.arcGaugeSweep(-0.2f), eps)
    }

    /** The ring is inset 10 from the sides and sits 3 above the bottom edge (line 317-318). */
    @Test
    fun arcGaugeRingIsInsetInTheWidth() {
        assertEquals(150f, GlowGeometry.arcGaugeRadius(width = 320f, inset = 10f), eps)
        val c = GlowGeometry.arcGaugeCentre(width = 320f, height = 320f / 1.9f, bottom = 3f)
        assertEquals(160f, c.x, eps)
        assertEquals(320f / 1.9f - 3f, c.y, eps)
    }

    /** The leading dot starts at 9 o'clock, tops out at half, and ends at 3 (line 333-335). */
    @Test
    fun arcGaugeEndDotFollowsTheSweep() {
        val r = 150f
        val width = 320f
        val height = 320f / 1.9f
        val c = GlowGeometry.arcGaugeCentre(width, height, 3f)
        fun end(progress: Float) = GlowGeometry.arcGaugePoint(progress, r, width, height, 3f)

        // 180° is 9 o'clock: the arc starts at the left edge of the ring.
        val start = end(0f)
        assertEquals(c.x - r, start.x, 0.001f)
        assertEquals(c.y, start.y, 0.001f)
        // Swept clockwise, half way is straight up.
        val half = end(0.5f)
        assertEquals(c.x, half.x, 0.001f)
        assertEquals(c.y - r, half.y, 0.001f)
        // 360° is 3 o'clock: a full arc ends at the right edge.
        val full = end(1f)
        assertEquals(c.x + r, full.x, 0.001f)
        assertEquals(c.y, full.y, 0.001f)
    }

    // ---------------------------------------------------------------- GlowHypnogram (GlowCharts.kt:363-407)

    /** Lanes run from the top (row 0) down, with a name column on the left (line 366-371). */
    @Test
    fun hypnogramLanesSplitThePlotUnderTheNames() {
        val h = 112f
        assertEquals(28f, GlowGeometry.hypnogramRowH(h = h, rows = 4), eps)
        assertEquals(12.88f, GlowGeometry.hypnogramPillH(rowH = 28f), eps)
        assertEquals(14f, GlowGeometry.hypnogramRowY(row = 0, rowH = 28f), eps)
        assertEquals(98f, GlowGeometry.hypnogramRowY(row = 3, rowH = 28f), eps)
    }

    /** A fraction of the night maps onto the plot right of the 46dp name column (line 372). */
    @Test
    fun hypnogramFractionsMapRightOfTheNames() {
        assertEquals(183f, GlowGeometry.hypnogramX(fraction = 0.5f, nameWidth = 46f, w = 274f), eps)
        assertEquals(46f, GlowGeometry.hypnogramX(fraction = 0f, nameWidth = 46f, w = 274f), eps)
        assertEquals(0.5f, GlowGeometry.hypnogramFraction(x = 183f, nameWidth = 46f, w = 274f), eps)
    }

    /** Deeper rows are brighter, so deep sleep stands out (line 385). */
    @Test
    fun hypnogramPillsBrightenWithDepth() {
        assertEquals(0.55f, GlowGeometry.hypnogramPillAlpha(row = 0, rows = 4), eps)
        assertEquals(1f, GlowGeometry.hypnogramPillAlpha(row = 3, rows = 4), eps)
        // One row: the divisor is guarded, so that row keeps the base 55%.
        assertEquals(0.55f, GlowGeometry.hypnogramPillAlpha(row = 0, rows = 1), eps)
    }

    /** A stage is a rounded bar centred on its lane, never thinner than a dot (line 386-391). */
    @Test
    fun hypnogramPillRunsFromStartToEnd() {
        val pill = GlowGeometry.hypnogramPill(
            from = 0.1f, to = 0.3f, row = 2, nameWidth = 46f, w = 274f, rowH = 28f,
        )
        assertEquals(73.4f, pill.x, 0.001f)
        assertEquals(70f - 6.44f, pill.y, 0.001f)
        assertEquals(54.8f, pill.width, 0.001f)
        assertEquals(12.88f, pill.height, 0.001f)
        // A blink of a stage still draws a dot-wide pill.
        val blink = GlowGeometry.hypnogramPill(
            from = 0.5f, to = 0.5f, row = 0, nameWidth = 46f, w = 274f, rowH = 28f,
        )
        assertEquals(12.88f, blink.width, 0.001f)
    }

    // ---------------------------------------------------------------- GlowHorizon (GlowCharts.kt:426-458)

    /** A shallow 84° arc across the card, sized from its chord (line 427-431). */
    @Test
    fun horizonArcIsSizedFromItsChord() {
        assertEquals(218.19f, GlowGeometry.horizonRadius(width = 320f, inset = 28f, sweep = 84f), 0.02f)
        assertEquals(56.05f, GlowGeometry.horizonSag(r = GlowGeometry.horizonRadius(320f, 28f, 84f), sweep = 84f), 0.05f)
    }

    /** The arc's ends sit on the chord, its middle on the top line (line 436-439). */
    @Test
    fun horizonEndsSitOnTheChordAndMiddleAtTheTop() {
        val width = 320f
        val top = 14f
        val r = GlowGeometry.horizonRadius(width, 28f, 84f)
        val sag = GlowGeometry.horizonSag(r, 84f)
        val left = GlowGeometry.horizonPoint(fraction = 0f, sweep = 84f, width = width, top = top, r = r)
        assertEquals(14f, left.x, 0.02f)
        assertEquals(top + sag, left.y, 0.02f)
        val right = GlowGeometry.horizonPoint(fraction = 1f, sweep = 84f, width = width, top = top, r = r)
        assertEquals(306f, right.x, 0.02f)
        assertEquals(top + sag, right.y, 0.02f)
        // Progress is measured over the top, so half way is the highest point.
        val middle = GlowGeometry.horizonPoint(fraction = 0.5f, sweep = 84f, width = width, top = top, r = r)
        assertEquals(160f, middle.x, 0.02f)
        assertEquals(top, middle.y, 0.02f)
    }

    // ---------------------------------------------------------------- smoothPath (Charts.kt:292-319)

    /** Assert a cubic's six numbers, since a data class compares floats exactly. */
    private fun assertCubic(c: Cubic, c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        assertEquals(c1x, c.c1x, 0.01f)
        assertEquals(c1y, c.c1y, 0.01f)
        assertEquals(c2x, c.c2x, 0.01f)
        assertEquals(c2y, c.c2y, 0.01f)
        assertEquals(x, c.x, 0.01f)
        assertEquals(y, c.y, 0.01f)
    }

    /** Fritsch–Carlson: a two-point run is a straight line (line 296). */
    @Test
    fun smoothCubicsJoinTwoPointsInALine() {
        val cubics = GlowGeometry.smoothCubics(listOf(Pt(0f, 0f), Pt(30f, 10f)))
        assertEquals(1, cubics.size)
        // Both handles lie on the straight line: a third and two thirds of the rise.
        assertCubic(cubics[0], 10f, 3.3333f, 20f, 6.6667f, 30f, 10f)
    }

    /** An up, over and down run keeps its flat top and does not overshoot (line 297-318). */
    @Test
    fun smoothCubicsFlattenAtATurningPoint() {
        val cubics = GlowGeometry.smoothCubics(listOf(Pt(0f, 0f), Pt(30f, 10f), Pt(60f, 0f)))
        assertEquals(2, cubics.size)
        assertCubic(cubics[0], 10f, 3.3333f, 20f, 10f, 30f, 10f)
        assertCubic(cubics[1], 40f, 10f, 50f, 3.3333f, 60f, 0f)
    }

    /** A steep-then-flat run is clamped so the curve never rises above the data (line 302-312). */
    @Test
    fun smoothCubicsClampASteepJoin() {
        val pts = listOf(Pt(0f, 0f), Pt(100f, 1f), Pt(101f, 11f))
        val cubics = GlowGeometry.smoothCubics(pts)
        assertEquals(2, cubics.size)
        // The tangent at the join is pulled back to 3 / sqrt(a² + b²) of its slope.
        assertEquals(0.002f, cubics[0].c1y, 0.001f)
        assertEquals(0f, cubics[0].c2y, 0.01f)
        // Nothing goes below the first point or above the last.
        cubics.forEach {
            assertEquals(true, it.c1y >= -0.001f && it.c2y >= -0.001f && it.c1y <= 11.001f && it.c2y <= 11.001f)
        }
    }

    // ---------------------------------------------------------------- DayCalendar grid (DayCalendar.kt:138-153)

    /** The first of the month is placed on its weekday, and the month is padded out to whole weeks. */
    @Test
    fun calendarGridPadsTheFirstWeekAndCountsWholeWeeks() {
        // Wednesday the 1st, weeks starting Monday: two empty cells first.
        assertEquals(2, GlowGeometry.calendarLead(firstDayOfMonthWeekday = 3, firstDayOfWeek = 1))
        // Weeks starting Sunday: three.
        assertEquals(3, GlowGeometry.calendarLead(firstDayOfMonthWeekday = 3, firstDayOfWeek = 7))
        // Wednesday the 1st, weeks starting Wednesday: none.
        assertEquals(0, GlowGeometry.calendarLead(firstDayOfMonthWeekday = 3, firstDayOfWeek = 3))
        // 2 + 31 = 33 cells -> 5 rows; 0 + 28 -> 4 rows; 6 + 31 -> 6 rows.
        assertEquals(5, GlowGeometry.calendarWeekCount(lead = 2, daysInMonth = 31))
        assertEquals(4, GlowGeometry.calendarWeekCount(lead = 0, daysInMonth = 28))
        assertEquals(6, GlowGeometry.calendarWeekCount(lead = 6, daysInMonth = 31))
    }

    /** Cells outside the month have no day number (line 144-146). */
    @Test
    fun calendarDayNumbersFallOutsideTheMonthInThePadding() {
        // Two empty cells before the 1st, so the first cell's number is -1.
        assertEquals(-1, GlowGeometry.calendarDayNumber(week = 0, column = 0, lead = 2))
        assertEquals(1, GlowGeometry.calendarDayNumber(week = 0, column = 2, lead = 2))
        assertEquals(31, GlowGeometry.calendarDayNumber(week = 4, column = 4, lead = 2))
        assertEquals(32, GlowGeometry.calendarDayNumber(week = 4, column = 5, lead = 2))
    }
}
