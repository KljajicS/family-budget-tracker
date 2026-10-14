package rs.budget.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import rs.budget.data.db.BudgetDb

fun testDb(): BudgetDb {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BudgetDb.Schema.create(driver)
    return BudgetDb(driver)
}
