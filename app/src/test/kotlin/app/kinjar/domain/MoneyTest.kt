package app.kinjar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {
    @Test
    fun `adds same currency`() {
        val sum = Money.of("10.50", Currency.RSD) + Money.of("0.50", Currency.RSD)
        assertEquals(Money.of("11.00", Currency.RSD), sum)
    }

    @Test
    fun `refuses to add different currencies`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.of("1", Currency.RSD) + Money.of("1", Currency.EUR)
        }
    }

    @Test
    fun `normalizes scale to two decimals`() {
        assertEquals(BigDecimal("1.00"), Money.of("1", Currency.EUR).amount)
        assertEquals(Money.of("1.00", Currency.EUR), Money.of("1", Currency.EUR))
    }

    @Test
    fun `rounds half up beyond two decimals`() {
        assertEquals(BigDecimal("1.01"), Money.of("1.005", Currency.RSD).amount)
    }

    @Test
    fun `zero and negative checks`() {
        assertTrue(Money.zero(Currency.RSD).isZero)
        assertTrue((-Money.of("5", Currency.RSD)).isNegative)
    }
}
