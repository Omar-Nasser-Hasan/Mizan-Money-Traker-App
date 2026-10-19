// Copyright (c) 2026 Mizan contributors
@file:Suppress("ktlint:standard:max-line-length")

package app.mizan.ui.v2

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import app.mizan.data.db.BudgetEntity
import app.mizan.domain.ledger.ExpenseRequest
import app.mizan.domain.ledger.LedgerService
import app.mizan.notify.NotificationChannels
import app.mizan.worker.ReminderWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Runs the real reminder worker, real notification manager, and real in-memory Room repository. */
@RunWith(AndroidJUnit4::class)
class P15BudgetAlertInstrumentedTest {
    @Test fun crossing_80_and_100_posts_one_notification_per_threshold_per_period() =
        runBlocking {
            P15RoomFixture().use { fixture ->
                val repository = fixture.repository
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.uiAutomation.executeShellCommand("pm grant app.mizan android.permission.POST_NOTIFICATIONS").close()
                NotificationChannels.create(fixture.context)
                repository.setBudgetAlertsEnabled(true)
                repository.setQuietHours("00:00", "00:00")
                repository.seedReferenceData()
                val account =
                    repository.saveAccount(
                        name = "Budget cash",
                        type = "CASH",
                        color = "#0785DB",
                        icon = "wallet",
                        currency = "EGP",
                        openingMinor = 10_000,
                    )
                val budget =
                    BudgetEntity("p15-alert", "Release budget", "CATEGORY", "groceries", "MONTHLY", limitMinor = 1_000, rollover = "NONE")
                repository.saveBudget(budget)
                val today = LocalDate.now(ZoneId.of("Africa/Cairo"))

                suspend fun spend(amount: Long) =
                    repository.post(
                        LedgerService().postExpense(
                            ExpenseRequest(account, "EGP", amount, 2, BigDecimal.ONE, "groceries", Instant.now(), today),
                        ),
                    )

                suspend fun checkAlerts() {
                    val factory =
                        object : WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: WorkerParameters,
                            ): ListenableWorker = ReminderWorker(appContext, workerParameters, repository)
                        }
                    TestListenableWorkerBuilder<ReminderWorker>(fixture.context).setWorkerFactory(factory).build().doWork()
                }

                fun claims(): Int =
                    fixture.database.openHelper.readableDatabase
                        .query(
                            "SELECT COUNT(*) FROM budget_alerts WHERE budgetId = 'p15-alert'",
                        ).use {
                            it.moveToFirst()
                            it.getInt(0)
                        }
                spend(790)
                checkAlerts()
                assertEquals(0, claims())
                spend(10)
                checkAlerts()
                checkAlerts()
                assertEquals(1, claims())
                spend(200)
                checkAlerts()
                checkAlerts()
                assertEquals(2, claims())
                val notifications = fixture.context.getSystemService(NotificationManager::class.java).activeNotifications
                assertTrue(notifications.any { it.notification.channelId == NotificationChannels.BUDGETS })
                assertTrue(notifications.any { it.notification.extras.getString("android.title") == "Release budget is at 100%" })
                notifications.filter { it.notification.channelId == NotificationChannels.BUDGETS }.forEach {
                    fixture.context.getSystemService(NotificationManager::class.java).cancel(it.id)
                }
            }
        }
}
