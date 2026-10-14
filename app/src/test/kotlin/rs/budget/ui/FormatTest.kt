package rs.budget.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.budget.domain.Currency
import rs.budget.domain.Money
import java.util.Locale

class FormatTest {
    @Test
    fun `groups thousands and drops zero cents`() {
        assertEquals("72,510", formatAmount(Money.of("72510", Currency.RSD), locale = Locale.US))
        assertEquals("1,240.50", formatAmount(Money.of("1240.5", Currency.RSD), locale = Locale.US))
    }

    @Test
    fun `signed form`() {
        assertEquals("+245,000", formatAmount(Money.of("245000", Currency.RSD), sign = true, locale = Locale.US))
        assertEquals("-98,400", formatAmount(-Money.of("98400", Currency.RSD), sign = true, locale = Locale.US))
    }

    @Test
    fun `serbian locale uses dot grouping`() {
        assertEquals("72.510", formatAmount(Money.of("72510", Currency.RSD), locale = Locale.forLanguageTag("sr")))
    }
}
