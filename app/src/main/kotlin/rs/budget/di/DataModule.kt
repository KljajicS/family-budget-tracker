package rs.budget.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
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
import rs.budget.data.db.BudgetDb
import rs.budget.data.deviceId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun db(@ApplicationContext context: Context): BudgetDb =
        BudgetDb(AndroidSqliteDriver(BudgetDb.Schema, context, "budget.db"))

    @Provides @Singleton
    fun clock(): Clock = SystemClock()

    @Provides @Singleton
    fun repository(@ApplicationContext context: Context, db: BudgetDb, clock: Clock): BudgetRepository {
        val id = deviceId(context)
        return BudgetRepository(db, EventStore(db, id, clock, Projector(db)), clock, id)
    }
}
