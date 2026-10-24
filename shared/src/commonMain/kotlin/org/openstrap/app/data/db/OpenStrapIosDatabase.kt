package org.openstrap.app.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * The phone's database, the same tables as `OpenStrapDatabase.kt`
 * (`app/src/main/kotlin/org/openstrap/app/data/db/OpenStrapDatabase.kt` @ 19f2865) at version 1,
 * plus the `key_value` settings table and the `sync_log` table.
 *
 * Android's version 7 with six auto-migrations becomes version 1: this is a new database on a new
 * platform, so there is no history to migrate. The schema is exported to `shared/schemas`.
 */
@Database(
    entities = [
        HeartRateSample::class,
        ActivityMinute::class,
        SleepSession::class,
        SleepStage::class,
        Spo2Sample::class,
        StressSample::class,
        HrvSample::class,
        RestingHeartRate::class,
        SkinTemperature::class,
        BreathRate::class,
        PaiSample::class,
        HrvSdnnSample::class,
        Workout::class,
        WorkoutHeartRate::class,
        SportEffectRow::class,
        StrapReadinessRow::class,
        SleepSpo2Sample::class,
        OsaEventRow::class,
        OsaResultRow::class,
        DailyMaxHr::class,
        RecoveryHeartRateRow::class,
        ActivityExtendedMinute::class,
        RawRecord::class,
        HealthAlertRow::class,
        CheckInRow::class,
        DayTagRow::class,
        NightMetricsRow::class,
        KeyValue::class,
        SyncLog::class,
    ],
    version = 1,
)
@ConstructedBy(OpenStrapIosDatabaseConstructor::class)
abstract class OpenStrapIosDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao

    abstract fun settingsDao(): SettingsDao

    abstract fun syncLogDao(): SyncLogDao

    companion object {
        /** Opens the database file at [path]; `:memory:` opens one that lives as long as this object. */
        fun open(path: String): OpenStrapIosDatabase = build(path)

        /** The database name a Swift caller passes for the on-disk one, in the app's Documents dir. */
        const val FILE_NAME = "openstrap.db"

        const val IN_MEMORY = ":memory:"
    }
}

// Room generates the actual in each target's source set (kspJvm, kspIosArm64, kspIosSimulatorArm64).
@Suppress("NO_ACTUAL_CLASS_MEMBER_FUNCTIONS")
expect object OpenStrapIosDatabaseConstructor : RoomDatabaseConstructor<OpenStrapIosDatabase> {
    override fun initialize(): OpenStrapIosDatabase
}

/** One builder for the file and the in-memory case, so tests and the app open the same schema. */
internal fun build(path: String): OpenStrapIosDatabase {
    val builder = if (path == OpenStrapIosDatabase.IN_MEMORY) {
        Room.inMemoryDatabaseBuilder<OpenStrapIosDatabase>()
    } else {
        Room.databaseBuilder<OpenStrapIosDatabase>(name = path)
    }
    return builder
        .setDriver(BundledSQLiteDriver())
        // Room runs every query here; one worker keeps writes in order without blocking the main thread.
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}
