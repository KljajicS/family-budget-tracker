package rs.budget.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface Clock {
    fun now(): Instant
    fun today(): LocalDate
}

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun today(): LocalDate = LocalDate.now()
}

class FixedClock(var instant: Instant, private val zone: ZoneId) : Clock {
    override fun now(): Instant = instant
    override fun today(): LocalDate = instant.atZone(zone).toLocalDate()
}
