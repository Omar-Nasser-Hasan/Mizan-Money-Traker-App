// Copyright (c) 2026 Mizan contributors
package app.mizan.ui.v2

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.mizan.data.db.AppDatabase
import app.mizan.data.prices.DaleelakGoldPriceProvider
import app.mizan.data.repository.MizanRepository
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import java.util.UUID

/** Real Room, real repositories, real calculators, and an unused production price provider. */
internal class P15RoomFixture : AutoCloseable {
    private val base = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(base.cacheDir, "p15-${UUID.randomUUID()}").also { it.mkdirs() }
    val context =
        object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
        }
    val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val client = OkHttpClient()
    val repository =
        MizanRepository(
            database.currencyDao(),
            database,
            database.fxRateDao(),
            database.accountDao(),
            database.referenceDataDao(),
            database.incomeSetupDao(),
            database.inboxDao(),
            database.planningDao(),
            database.ledgerDao(),
            database.assetDao(),
            DaleelakGoldPriceProvider(client),
            context,
            client,
            Clock.systemUTC(),
        )

    override fun close() {
        // Detach test compositions before closing Room, including after an assertion fails.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry
                .getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .toList()
                .filter { it.javaClass == androidx.activity.ComponentActivity::class.java }
                .forEach { it.findViewById<android.view.ViewGroup>(android.R.id.content)?.removeAllViews() }
        }
        database.close()
        directory.deleteRecursively()
    }
}
