package org.openstrap.shared.health

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openstrap.app.DemoData
import org.openstrap.app.data.SampleData
import org.openstrap.app.data.db.OpenStrapIosDatabase

/*
 * The demo seed, and nothing else.
 *
 * Card U9 removed the four snapshot reads this class began with — `today`, `sleep`, `heart` and
 * `recovery`, and the six data classes they answered with (`TodaySnapshot`, `SleepSnapshot`,
 * `HeartSnapshot`, `RecoverySnapshot`, `StageTotals`, `StageSegment`, `HeartSample`, `TrendDay`,
 * `RecoveryContributor`, `ChargeSummary`). Cards U4 to U8 moved every screen onto `ScreenQueries` and
 * the shared Glow models, so nothing called them any more; `HealthExportQueue` never did. What
 * remains is the one job no screen owns: filling the database for a `-demoData` launch.
 */

/**
 * The `-demoData` seed. The screens' reads live in [org.openstrap.shared.screens.ScreenQueries].
 *
 * @param databasePath the same SQLite file `StrapEngine` syncs into (`:memory:` in tests).
 */
class HealthQueries(private val databasePath: String) {
    private val database = OpenStrapIosDatabase.open(databasePath)
    private val healthDao = database.healthDao()

    /** One worker, so a screen's two reads don't race each other over the database. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    /**
     * Fills the database with [DemoData] for the `-demoData` launch argument in debug builds. The
     * app calls this before the first screen appears; [onDone] gets a line for Diagnostics.
     */
    fun seedDemoData(onDone: (String) -> Unit) {
        scope.launch {
            val message = try {
                DemoData.fill(healthDao)
                "demo data: seeded"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "demo data: could not seed ($e)"
            }
            withContext(Dispatchers.Main) { onDone(message) }
        }
    }

    /** Closes the connection and ends the worker. The object is not reusable afterwards. */
    fun close() {
        scope.cancel()
        database.close()
    }

    companion object {
        /** Days the JVM tests seed with [SampleData.load]; the `-demoData` path fills [DemoData] instead. */
        const val DEMO_DAYS = SampleData.DEMO_DAYS
    }
}
