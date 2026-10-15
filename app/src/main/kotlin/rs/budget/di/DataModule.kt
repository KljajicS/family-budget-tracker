package rs.budget.di

import android.content.Context
import androidx.core.content.edit
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.data.EventStore
import rs.budget.data.Projector
import rs.budget.data.SystemClock
import rs.budget.data.ThemeStore
import rs.budget.data.db.BudgetDb
import rs.budget.data.deviceId
import rs.budget.data.receipt.FiscalReceiptClient
import rs.budget.data.receipt.ReceiptLookup
import rs.budget.data.rates.RateRepository
import rs.budget.data.rates.RateRefresh
import rs.budget.data.rates.RateAttempts
import rs.budget.data.rates.NbsClient
import java.time.Instant
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun rates(repo: BudgetRepository): RateRepository = repo.exchangeRates

    @Provides @Singleton
    fun rateRefresh(@ApplicationContext context: Context, rates: RateRepository, clock: Clock): RateRefresh {
        val preferences = context.getSharedPreferences("exchange_rates", Context.MODE_PRIVATE)
        val attempts = object : RateAttempts {
            override var lastAttempt: Instant?
                get() = preferences.getLong("last_attempt", 0).takeIf { it != 0L }?.let(Instant::ofEpochMilli)
                set(value) { preferences.edit { putLong("last_attempt", value?.toEpochMilli() ?: 0) } }
        }
        return RateRefresh(rates, NbsClient(), attempts, clock)
    }

    @Provides @Singleton
    fun receiptLookup(): ReceiptLookup = FiscalReceiptClient()

    @Provides @Singleton
    fun db(@ApplicationContext context: Context): BudgetDb =
        BudgetDb(AndroidSqliteDriver(BudgetDb.Schema, context, "budget.db"))

    @Provides @Singleton
    fun clock(): Clock = SystemClock()

    @Provides @Singleton
    fun themeStore(@ApplicationContext context: Context): ThemeStore = ThemeStore.fromPreferences(context)

    @Provides @Singleton
    fun repository(@ApplicationContext context: Context, db: BudgetDb, clock: Clock): BudgetRepository {
        val id = deviceId(context)
        val store = EventStore(db, id, clock, Projector(db))
        val prefs = context.getSharedPreferences("projection", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) != Projector.VERSION) {
            store.rebuild()
            prefs.edit { putInt("version", Projector.VERSION) }
        }
        return BudgetRepository(db, store, clock, id, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
