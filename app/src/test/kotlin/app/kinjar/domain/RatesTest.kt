package app.kinjar.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class RatesTest {
    @Test fun `cross conversion rounds only final amount`() {
        val rates = Rates(mapOf(Currency.EUR to BigDecimal("1.49"), Currency.USD to BigDecimal("2")))
        assertEquals(Money.of("0.01", Currency.USD), rates.convert(Money.of("0.02", Currency.EUR), Currency.USD))
        assertEquals(Money.of("2", Currency.RSD), rates.convert(Money.of("1", Currency.USD)))
        assertEquals(Money.of("1", Currency.USD), rates.convert(Money.of("1", Currency.USD), Currency.USD))
        assertNull(Rates.EMPTY.convert(Money.of("1", Currency.EUR)))
    }

    @Test fun `rate inputs are bounded and RSD cannot change`() {
        listOf("0", "-1", "NaN", "1e2147483647", "1000001", "1.123456789", "9".repeat(1000)).forEach { assertNull(parseRate(it)) }
        assertEquals(BigDecimal("117.12345678"), parseRate("117.12345678"))
        assertTrue(runCatching { Rates(mapOf(Currency.EUR to BigDecimal.ZERO)) }.isFailure)
        assertTrue(runCatching { Rates(mapOf(Currency.RSD to BigDecimal("2"))) }.isFailure)
        val values = mutableMapOf(Currency.EUR to BigDecimal("117"))
        val rates = Rates(values)
        values[Currency.EUR] = BigDecimal.ONE
        assertEquals(BigDecimal("117"), rates.rate(Currency.EUR))
    }
}
