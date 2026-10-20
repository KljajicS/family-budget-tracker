package app.kinjar.domain

import java.security.SecureRandom
import java.util.UUID

private val random = SecureRandom()

/** UUID v7: 48-bit unix millis, then random. Sorts by creation time as a string. */
fun newId(now: Long = System.currentTimeMillis()): String {
    val rnd = ByteArray(10).also(random::nextBytes)
    val msb = (now shl 16) or (0x7000L or ((rnd[0].toLong() and 0x0F) shl 8) or (rnd[1].toLong() and 0xFF))
    var lsb = 0L
    for (i in 2 until 10) lsb = (lsb shl 8) or (rnd[i].toLong() and 0xFF)
    lsb = (lsb and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
    return UUID(msb, lsb).toString()
}

/** Same bill paid on two phones gets the same transaction id, so the log converges to one row. */
fun billTxId(ruleId: String, period: String): String =
    UUID.nameUUIDFromBytes("$ruleId:$period".toByteArray()).toString()
