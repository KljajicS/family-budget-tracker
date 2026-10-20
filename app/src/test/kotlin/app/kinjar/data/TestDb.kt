package app.kinjar.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.kinjar.data.db.KinjarDb

fun testDb(): KinjarDb {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    KinjarDb.Schema.create(driver)
    return KinjarDb(driver)
}
