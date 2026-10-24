package org.openstrap.app.data

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The `java.time` method names the copied Android code uses, over kotlinx-datetime, so the copies
 * read line for line like their originals. `Instant` here is `kotlin.time.Instant`, which
 * kotlinx-datetime 0.7 builds on.
 */
internal fun LocalDate.plusDays(days: Long): LocalDate = plus(days.toInt(), DateTimeUnit.DAY)

internal fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

internal fun LocalDate.minusDays(days: Long): LocalDate = minus(days.toInt(), DateTimeUnit.DAY)

internal fun LocalDate.minusDays(days: Int): LocalDate = minus(days, DateTimeUnit.DAY)

internal fun LocalDate.atStartOfDay(zone: TimeZone): Instant = atStartOfDayIn(zone)

internal fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime = LocalDateTime(this, LocalTime(hour, minute))

internal fun LocalDateTime.atZone(zone: TimeZone): Instant = toInstant(zone)

internal fun Instant.atZone(zone: TimeZone): LocalDateTime = toLocalDateTime(zone)

internal fun LocalDateTime.toLocalDate(): LocalDate = date

internal fun LocalDateTime.toLocalTime(): LocalTime = time

internal fun Instant.toEpochSecond(): Long = epochSeconds

/** `java.time.LocalDate.toEpochDay()`: days since 1970-01-01. */
internal fun LocalDate.toEpochDay(): Long = toEpochDays()

/** `java.time.LocalDateTime.toEpochSecond(ZoneId)`. */
internal fun LocalDateTime.toEpochSecond(zone: TimeZone): Long = toInstant(zone).epochSeconds

/** `TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)`: the Monday of this date's week. */
internal fun LocalDate.mondayOfWeek(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
