package org.openstrap.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import org.openstrap.app.data.db.DailyAggregate

class DayStatTest {
    @Test
    fun mapsSqliteDayToLocalDate() {
        val stat = DayStat.of(DailyAggregate("2026-10-17", 76.04, 57.0, 107.0, 1121))!!
        assertEquals(LocalDate(2026, 10, 17), stat.date)
        assertEquals(76.04f, stat.value, 0.001f)
        assertEquals(57f, stat.min, 0f)
        assertEquals(107f, stat.max, 0f)
        assertEquals(1121, stat.count)
    }

    @Test
    fun dropsRowsWithoutAValidDay() {
        assertNull(DayStat.of(DailyAggregate("", 1.0, 1.0, 1.0, 1)))
        assertNull(DayStat.parseDay("17/10/2026"))
    }
}
