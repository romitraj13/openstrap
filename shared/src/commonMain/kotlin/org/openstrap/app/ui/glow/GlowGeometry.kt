package org.openstrap.app.ui.glow

/*
 * The arithmetic behind the glow charts, copied out of the Compose code so Swift draws the same
 * pixels instead of re-implementing it (standing instruction 5).
 *
 * Every function here is the body of an expression in `GlowCharts.kt` (or `DayCalendar.kt` for the
 * calendar grid), with the canvas size and the dp values passed in as plain pixels: Compose works in
 * px inside `DrawScope`, and Swift's Canvas does too. The formulas and the order of the operations
 * are Compose's, down to the clamps and the `.coerceAtLeast` guards, so the two builds put a mark in
 * exactly the same place.
 *
 * Nothing here is a Compose dependency: a `Float` in, a `Float` out, callable from Swift as
 * `GlowGeometry.shared.…`.
 */

/** A point on the canvas, in pixels. */
data class Pt(val x: Float, val y: Float)

/** A rectangle on the canvas: top-left corner, width and height, in pixels. */
data class Box2(val x: Float, val y: Float, val width: Float, val height: Float)

/** A value range: `lo` at the bottom of the plot, `hi` at the top. */
data class Range2(val lo: Float, val hi: Float)

/**
 * One cubic of a smooth line: the two handles and the end point, which is what Compose's
 * `Path.cubicTo` receives. The start point is the previous cubic's end (or the first point).
 */
data class Cubic(
    val c1x: Float,
    val c1y: Float,
    val c2x: Float,
    val c2y: Float,
    val x: Float,
    val y: Float,
)

object GlowGeometry {
    /* ------------------------------------------------------------------------------------------ */
    /* GlowLineChart — GlowCharts.kt:126-188                                                       */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * The value axis, from `GlowCharts.kt:129-130`: a fixed `yRange` wins, otherwise the readings fit
     * the band and the data; a domain flatter than 1e-3 gets one unit of room so it can be drawn.
     *
     * `hasYRange`/`hasBand` stand in for Compose's nullable arguments: Kotlin `Float?` reaches Swift
     * as a boxed `KotlinFloat`, which is not worth the call-site noise for two flags.
     */
    fun lineChartDomain(
        knownMin: Float,
        knownMax: Float,
        hasBand: Boolean,
        bandLo: Float,
        bandHi: Float,
        hasYRange: Boolean,
        yRangeLo: Float,
        yRangeHi: Float,
    ): Range2 {
        // Compose: minOf(known.min(), band?.start ?: Float.MAX_VALUE), and the mirror for the top.
        val lo = if (hasYRange) yRangeLo else minOf(knownMin, if (hasBand) bandLo else Float.MAX_VALUE)
        val hi = (if (hasYRange) yRangeHi else maxOf(knownMax, if (hasBand) bandHi else -Float.MAX_VALUE))
            .let { if (it - lo < 1e-3f) lo + 1f else it }
        return Range2(lo, hi)
    }

    /**
     * A reading's x: evenly spaced, inset by the end dot's halo at both edges (`GlowCharts.kt:133`).
     * `count` is the number of readings including the nulls, because a gap still takes its slot.
     */
    fun lineChartX(index: Int, count: Int, width: Float, inset: Float): Float =
        inset + index / (count - 1f).coerceAtLeast(1f) * (width - inset * 2)

    /** A value's y, from `top` at the top of the domain to `h` at the bottom (`GlowCharts.kt:134`). */
    fun lineChartY(value: Float, lo: Float, hi: Float, top: Float, h: Float): Float =
        top + (h - top) * (1f - (value - lo) / (hi - lo))

    /**
     * Where an axis label's anchor sits across the canvas, 0..1 (`GlowCharts.kt:92`). The text is
     * aligned to that fraction of its own width, so 0 is flush left and 1 flush right.
     */
    fun axisLabelFraction(index: Int, count: Int): Float =
        if (count <= 1) 0f else index / (count - 1f)

    /**
     * The touch readout's bubble (`GlowCharts.kt:71-80`): `text` measured, padded by `padX`/`padY`,
     * centred on `x` and clamped inside the canvas, then placed `gap` above `anchorY` — or below it
     * when there is no room above.
     */
    fun readoutBox(
        textWidth: Float,
        textHeight: Float,
        x: Float,
        anchorY: Float,
        width: Float,
        padX: Float,
        padY: Float,
        gap: Float,
    ): Box2 {
        val bubbleWidth = textWidth + padX
        val bubbleHeight = textHeight + padY
        val bx = (x - bubbleWidth / 2).coerceIn(0f, (width - bubbleWidth).coerceAtLeast(0f))
        val by = (anchorY - bubbleHeight - gap).let { if (it < 0f) anchorY + gap else it }
        return Box2(bx, by, bubbleWidth, bubbleHeight)
    }

    /* ------------------------------------------------------------------------------------------ */
    /* GlowDotBars — GlowCharts.kt:212-237                                                         */
    /* ------------------------------------------------------------------------------------------ */

    /** One column per value across the canvas (`GlowCharts.kt:216`). */
    fun dotBarPitchX(width: Float, count: Int): Float = width / count

    /** One row of dots per unit of height (`GlowCharts.kt:217`). */
    fun dotBarPitchY(h: Float, rows: Int): Float = h / rows

    /**
     * The dot diameter: 32% of the smaller pitch, so the grid keeps its gaps whichever axis is
     * tighter (`GlowCharts.kt:218`).
     */
    fun dotBarRadius(pitchX: Float, pitchY: Float): Float = minOf(pitchX, pitchY) * 0.32f

    /** The scale a column is measured against: the tallest one, and never zero (line 219). */
    fun dotBarMax(maxValue: Float): Float = maxValue.coerceAtLeast(1f)

    /** How many rows of a column are lit, from `GlowCharts.kt:222`: anything above zero lights one. */
    fun dotBarLit(value: Float, max: Float, rows: Int): Int {
        fun roundHalfUp(v: Float): Int = kotlin.math.round(v).toInt()
        return if (value > 0f) roundHalfUp(value / max * rows).coerceIn(1, rows) else 0
    }

    /** The centre of a column (`GlowCharts.kt:225`). */
    fun dotBarCenterX(index: Int, pitchX: Float): Float = pitchX * index + pitchX / 2

    /** A row's centre, counting up from the bottom of the plot (`GlowCharts.kt:233`). */
    fun dotBarDotY(row: Int, pitchY: Float, h: Float): Float = h - pitchY * row - pitchY / 2

    /** Lit dots brighten towards the top of the column (`GlowCharts.kt:231`). */
    fun dotBarAlpha(row: Int, rows: Int): Float = 0.55f + 0.45f * (row + 1f) / rows

    /* ------------------------------------------------------------------------------------------ */
    /* GlowTrend — GlowCharts.kt:260-292                                                           */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * The trend's value axis (`GlowCharts.kt:265-266`): the bottom is 85% of the lowest day, so no
     * stem is a zero-height line, and the top is the highest day with a unit of room.
     *
     * `knownMin`/`knownMax` include the average line when there is one, as Compose's `known` list
     * does; pass 0 and 1 when there are no days at all, which is what its `?:` fallbacks give.
     */
    fun trendDomain(knownMin: Float, knownMax: Float): Range2 {
        val lo = knownMin * 0.85f
        val hi = knownMax.coerceAtLeast(lo + 1f)
        return Range2(lo, hi)
    }

    /**
     * A day's y (`GlowCharts.kt:267`): `top` for the highest, with `inset` left under the bottom so
     * a null day's dot and the axis labels have room.
     */
    fun trendY(value: Float, lo: Float, hi: Float, top: Float, h: Float, inset: Float): Float =
        top + (h - top - inset) * (1f - (value - lo) / (hi - lo))

    /** A day's x: the middle of its slot (`GlowCharts.kt:271`). */
    fun trendCenterX(index: Int, count: Int, width: Float): Float {
        val slot = width / count
        return slot * index + slot / 2
    }

    /* ------------------------------------------------------------------------------------------ */
    /* GlowArcGauge — GlowCharts.kt:316-336                                                        */
    /* ------------------------------------------------------------------------------------------ */

    /** The ring's radius: half the width, inset by 10 (`GlowCharts.kt:317`). */
    fun arcGaugeRadius(width: Float, inset: Float): Float = width / 2 - inset

    /** The arc centre: the middle of the canvas, `bottom` above its bottom edge (`GlowCharts.kt:318`). */
    fun arcGaugeCentre(width: Float, height: Float, bottom: Float): Pt = Pt(width / 2, height - bottom)

    /** How far the arc runs, 0..180 degrees, from 9 o'clock over the top (`GlowCharts.kt:321`). */
    fun arcGaugeSweep(progress: Float): Float = 180f * progress.coerceIn(0f, 1f)

    /**
     * The leading dot's position (`GlowCharts.kt:333-335`): Compose's angles, where 180° is 9
     * o'clock and 360° is 3 o'clock, measured with `cos`/`sin` exactly as `drawArc` does.
     */
    fun arcGaugePoint(progress: Float, r: Float, width: Float, height: Float, bottom: Float): Pt {
        val c = arcGaugeCentre(width, height, bottom)
        val a = kotlin.math.PI / 180 * (180f + arcGaugeSweep(progress))
        return Pt(
            c.x + r * kotlin.math.cos(a).toFloat(),
            c.y + r * kotlin.math.sin(a).toFloat(),
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* GlowHypnogram — GlowCharts.kt:363-407                                                       */
    /* ------------------------------------------------------------------------------------------ */

    /** One lane per stage name (`GlowCharts.kt:369`). */
    fun hypnogramRowH(h: Float, rows: Int): Float = h / rows

    /** A stage's bar is 46% of its lane, so the dotted guide shows between rows (`GlowCharts.kt:370`). */
    fun hypnogramPillH(rowH: Float): Float = rowH * 0.46f

    /** A lane's centre line (`GlowCharts.kt:371`). */
    fun hypnogramRowY(row: Int, rowH: Float): Float = rowH * row + rowH / 2

    /** A fraction of the night, measured right of the name column (`GlowCharts.kt:372`). */
    fun hypnogramX(fraction: Float, nameWidth: Float, w: Float): Float = nameWidth + fraction * w

    /** The fraction of the night at an x, the inverse of [hypnogramX]. */
    fun hypnogramFraction(x: Float, nameWidth: Float, w: Float): Float = (x - nameWidth) / w

    /** Deeper lanes are brighter (`GlowCharts.kt:385`); with one lane the guard makes it full. */
    fun hypnogramPillAlpha(row: Int, rows: Int): Float =
        0.55f + 0.45f * row / (rows - 1).coerceAtLeast(1)

    /** A stage's bar: start and end fractions of the night, on its lane (`GlowCharts.kt:386-391`). */
    fun hypnogramPill(
        from: Float,
        to: Float,
        row: Int,
        nameWidth: Float,
        w: Float,
        rowH: Float,
    ): Box2 {
        val pillHeight = hypnogramPillH(rowH)
        val x0 = hypnogramX(from, nameWidth, w)
        val x1 = hypnogramX(to, nameWidth, w)
        return Box2(
            x = x0,
            y = hypnogramRowY(row, rowH) - pillHeight / 2,
            width = (x1 - x0).coerceAtLeast(pillHeight),
            height = pillHeight,
        )
    }

    /* ------------------------------------------------------------------------------------------ */
    /* GlowHorizon — GlowCharts.kt:426-458                                                         */
    /* ------------------------------------------------------------------------------------------ */

    /** The circle behind a shallow arc of `sweep` degrees whose chord is `width - inset` (line 429-430). */
    fun horizonRadius(width: Float, inset: Float, sweep: Float): Float {
        val chord = width - inset
        return chord / 2f / kotlin.math.sin(kotlin.math.PI / 180 * (sweep / 2.0)).toFloat()
    }

    /** How far the arc dips below the ends of its chord (`GlowCharts.kt:431`). */
    fun horizonSag(r: Float, sweep: Float): Float =
        r * (1f - kotlin.math.cos(kotlin.math.PI / 180 * (sweep / 2.0)).toFloat())

    /**
     * A point along the arc (`GlowCharts.kt:427,436-439`): 0 is the left end, 1 the right, and the
     * arc's highest point is in the middle. The circle's centre sits `top + r` down the canvas, so
     * the middle of the arc lands on `top`.
     */
    fun horizonPoint(fraction: Float, sweep: Float, width: Float, top: Float, r: Float): Pt {
        val start = 270f - sweep / 2
        val a = kotlin.math.PI / 180 * (start + sweep * fraction)
        val cx = width / 2f
        val cy = top + r
        return Pt(cx + r * kotlin.math.cos(a).toFloat(), cy + r * kotlin.math.sin(a).toFloat())
    }

    /* ------------------------------------------------------------------------------------------ */
    /* smoothPath — Charts.kt:292-319                                                              */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * A monotone cubic (Fritsch–Carlson) through `points`, as Compose's `smoothPath` builds it: a
     * curve like a spline but one that never overshoots the data, so a smoothed heart rate cannot
     * show a peak or a dip that did not happen. Two points give a straight line. A run of fewer
     * than two gives nothing to draw.
     */
    fun smoothCubics(points: List<Pt>): List<Cubic> {
        if (points.size < 2) return emptyList()
        val n = points.size
        // Compose's `d`: the slope of each segment, with x made non-zero so a vertical run stays finite.
        val d = FloatArray(n - 1) { i ->
            (points[i + 1].y - points[i].y) / (points[i + 1].x - points[i].x).coerceAtLeast(0.001f)
        }
        val m = FloatArray(n)
        m[0] = d[0]
        m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) {
                m[i] = 0f
                m[i + 1] = 0f
                continue
            }
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / kotlin.math.sqrt(s)
                m[i] = t * a * d[i]
                m[i + 1] = t * b * d[i]
            }
        }
        return (0 until n - 1).map { i ->
            val dx = (points[i + 1].x - points[i].x) / 3f
            Cubic(
                c1x = points[i].x + dx,
                c1y = points[i].y + m[i] * dx,
                c2x = points[i + 1].x - dx,
                c2y = points[i + 1].y - m[i + 1] * dx,
                x = points[i + 1].x,
                y = points[i + 1].y,
            )
        }
    }

    /* ------------------------------------------------------------------------------------------ */
    /* DayCalendar month grid — DayCalendar.kt:138-153                                             */
    /* ------------------------------------------------------------------------------------------ */

    /**
     * Empty cells before the 1st (`DayCalendar.kt:138`): the days of the week between the first day
     * of the week and the 1st's own weekday, both as `DayOfWeek.value` (Monday 1 … Sunday 7).
     */
    fun calendarLead(firstDayOfMonthWeekday: Int, firstDayOfWeek: Int): Int =
        (firstDayOfMonthWeekday - firstDayOfWeek + 7) % 7

    /** How many week rows the month needs (`DayCalendar.kt:139,141`). */
    fun calendarWeekCount(lead: Int, daysInMonth: Int): Int {
        val cells = lead + daysInMonth
        return (cells + 6) / 7
    }

    /**
     * The day number in a cell, which is 0 or less in the leading padding and past the month's last
     * day in the trailing one (`DayCalendar.kt:144`).
     */
    fun calendarDayNumber(week: Int, column: Int, lead: Int): Int = week * 7 + column - lead + 1

    /*
     * `DayCalendar.kt:148`'s enabled test — `date <= today && (date in available || date == today)` —
     * stays in Swift: it is one comparison against the day's own set, and `Set<Int>` reaches Swift as
     * a boxed collection. The Swift side keeps the same expression, in the same order.
     */
}
