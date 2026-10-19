// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.graphics.HardwareRendererCompat
import app.mizan.domain.ports.RecordQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** One provisional emulator run. Never include this class in a retry or the general journey suite. */
@RunWith(AndroidJUnit4::class)
class P15PerformanceInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var page by mutableStateOf("Home")

    @Test fun one_50k_screen_envelope() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("p15MeasureOnce") == "true")
        val report = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "p15-performance.txt")
        assumeTrue("A measurement attempt already exists; never retry it", !report.exists())
        report.writeText("Provisional (emulator): measurement attempt started; no completed result yet. No retries.\n")
        P15RoomFixture().use { fixture ->
            val repository = fixture.repository
            val db = fixture.database
            val today = LocalDate.now(ZoneId.of("Africa/Cairo")).toString()
            runBlocking {
                repository.seedReferenceData()
                repository.saveAccount(
                    name = "Performance cash",
                    type = "CASH",
                    color = "#0785DB",
                    icon = "wallet",
                    currency = "EGP",
                    openingMinor = 100_000,
                )
                val account = repository.activeAccounts().single().id
                db.runInTransaction {
                    val sql = db.openHelper.writableDatabase
                    val txn =
                        sql.compileStatement(
                            "INSERT INTO transactions (id,kind,bookedAt,bookedDate,categoryId,note,status,paymentType,createdAt,updatedAt) VALUES (?,'EXPENSE',1,?,'groceries','','CLEARED','CASH',1,1)",
                        )
                    val entry = sql.compileStatement("INSERT INTO entries VALUES (?,?,?,'EGP',-1,'1',-1)")
                    txn.use { t ->
                        entry.use { e ->
                            repeat(50_000) { index ->
                                val id = "perf-$index"
                                t.bindString(1, id)
                                t.bindString(2, today)
                                t.executeInsert()
                                e.bindString(1, "entry-$index")
                                e.bindString(2, id)
                                e.bindString(3, account)
                                e.executeInsert()
                            }
                        }
                    }
                }
                assertEquals(50_000, repository.observeRecordCount(RecordQuery()).first())
            }
            var worstMillis = 0L
            var worstPage = ""
            HardwareRendererCompat.setDrawingEnabled(true)
            val firstScreenStarted = android.os.SystemClock.elapsedRealtime()
            compose.setContent {
                MizanTheme {
                    if (page == "Home") {
                        V2HomeScreen(repository, false) {}
                    } else {
                        V2StatsScreen(repository, PeriodState(), null, false, page, {}, {}, {}, {})
                    }
                }
            }
            for (selected in listOf("Home", "Cash flow", "Spending", "Balance", "Outlook", "Reports")) {
                val start = if (selected == "Home") firstScreenStarted else android.os.SystemClock.elapsedRealtime()
                compose.runOnIdle { page = selected }
                val tag = if (selected == "Home") "home-data-50000" else "stats-ready-$selected-50000-50000-1-1"
                compose.waitUntil(120_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                val elapsed = android.os.SystemClock.elapsedRealtime() - start
                if (elapsed > worstMillis) {
                    worstMillis = elapsed
                    worstPage = selected
                }
            }
            report.writeText(
                "Provisional (emulator): one 50k Room run, worst Home/Stats ready latency = $worstMillis ms ($worstPage). Includes queries, computation and Compose synchronization. Not a phone budget pass. No retries.\n",
            )
        }
    }
}
