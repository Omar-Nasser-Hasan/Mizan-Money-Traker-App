// Copyright (c) 2026 Mizan contributors
package app.mizan.di

import android.content.Context
import androidx.room.Room
import app.mizan.data.db.AccountDao
import app.mizan.data.db.AppDatabase
import app.mizan.data.db.CurrencyDao
import app.mizan.data.db.FxRateDao
import app.mizan.data.db.IncomeSetupDao
import app.mizan.data.db.LedgerDao
import app.mizan.data.db.PlanningDao
import app.mizan.data.db.ReferenceDataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "mizan.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    fun provideCurrencyDao(database: AppDatabase): CurrencyDao = database.currencyDao()

    @Provides
    fun provideFxRateDao(database: AppDatabase): FxRateDao = database.fxRateDao()

    @Provides
    fun provideAccountDao(database: AppDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideReferenceDataDao(database: AppDatabase): ReferenceDataDao = database.referenceDataDao()

    @Provides
    fun provideIncomeSetupDao(database: AppDatabase): IncomeSetupDao = database.incomeSetupDao()

    @Provides
    fun providePlanningDao(database: AppDatabase): PlanningDao = database.planningDao()

    @Provides
    fun provideLedgerDao(database: AppDatabase): LedgerDao = database.ledgerDao()
}
