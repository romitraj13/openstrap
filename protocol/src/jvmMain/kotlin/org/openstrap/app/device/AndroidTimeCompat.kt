package org.openstrap.app.device

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinTimeZone
import org.openstrap.protocol.history.GdspClient
import org.openstrap.protocol.history.GdspFetch
import org.openstrap.protocol.history.GdspTime
import org.openstrap.protocol.history.HistoryDataType
import org.openstrap.protocol.history.SleepResult
import org.openstrap.protocol.history.WorkoutFetch
import org.openstrap.protocol.services.TimeSync
import kotlin.time.Instant

/**
 * Source compatibility for the Android app, which was frozen at commit 19f2865 for this work.
 *
 * `protocol/` now speaks kotlinx-datetime (`Instant` + `TimeZone`) so it compiles for iOS, but
 * `HistorySync.kt` and `StrapManager.kt` still pass `java.time.ZoneId` / `ZonedDateTime`. These thin
 * overloads convert and delegate, so `app/` stays byte-identical and no call site changed.
 *
 * They are declared in the app's own package because that is where they are called: Kotlin only sees
 * a top-level extension without an import when it is in the caller's package.
 *
 * Remove this file once the app moves to the shared module and kotlinx-datetime.
 */

fun ZoneId.asProtocolTimeZone(): TimeZone = toKotlinTimeZone()

fun GdspTime.Companion.of(time: ZonedDateTime): GdspTime =
    of(Instant.fromEpochMilliseconds(time.toInstant().toEpochMilli()), time.zone.asProtocolTimeZone())

fun org.openstrap.protocol.history.FetchCodec.startFetch(
    type: HistoryDataType,
    since: ZonedDateTime,
    flag: Int? = org.openstrap.protocol.history.FetchCodec.START_FLAG_HEALTH,
): ByteArray = startFetch(type, Instant.fromEpochMilliseconds(since.toInstant().toEpochMilli()), since.zone.asProtocolTimeZone(), flag)

fun org.openstrap.protocol.history.FetchCodec.startFetch(
    type: HistoryDataType,
    sinceEpochSecond: Long,
    zone: ZoneId,
    flag: Int? = org.openstrap.protocol.history.FetchCodec.START_FLAG_HEALTH,
): ByteArray = startFetch(type, sinceEpochSecond, zone.asProtocolTimeZone(), flag)

fun TimeSync.struct(now: ZonedDateTime): ByteArray =
    struct(Instant.fromEpochMilliseconds(now.toInstant().toEpochMilli()), now.zone.asProtocolTimeZone())

fun TimeSync.setRequest(now: ZonedDateTime): ByteArray =
    setRequest(Instant.fromEpochMilliseconds(now.toInstant().toEpochMilli()), now.zone.asProtocolTimeZone())

fun TimeSync.nextDstRequest(now: ZonedDateTime): ByteArray =
    nextDstRequest(Instant.fromEpochMilliseconds(now.toInstant().toEpochMilli()), now.zone.asProtocolTimeZone())

fun SleepResult.day(zone: ZoneId): Long = day(zone.asProtocolTimeZone())

fun SleepResult.resolveTime(raw: Int, zone: ZoneId): Long = resolveTime(raw, zone.asProtocolTimeZone())

suspend fun GdspClient.fetch(typeCode: Int, since: Long, zone: ZoneId): GdspFetch =
    fetch(typeCode, since, zone.asProtocolTimeZone())

suspend fun GdspClient.fetchWorkout(since: Long, zone: ZoneId): WorkoutFetch =
    fetchWorkout(since, zone.asProtocolTimeZone())
