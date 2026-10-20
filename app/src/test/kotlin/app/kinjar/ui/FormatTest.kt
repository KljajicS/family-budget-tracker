package app.kinjar.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import app.kinjar.domain.Currency
import app.kinjar.domain.Money
import java.util.Locale
import java.time.LocalDate

class FormatTest {
    @Test
    fun `transaction dates keep today and show calendar dates for every other day`() {
        val today = LocalDate.of(2026, 1, 1)
        assertEquals("Today", formatTransactionDate(today, today, "Today", Locale.US))
        assertEquals("31 Dec", formatTransactionDate(today.minusDays(1), today, "Today", Locale.US))
        assertEquals("2 Jan", formatTransactionDate(today.plusDays(1), today, "Today", Locale.US))
        assertEquals("31 Dec 2024", formatTransactionDate(LocalDate.of(2024, 12, 31), today, "Today", Locale.US))
        assertEquals("1 Jan 2027", formatTransactionDate(LocalDate.of(2027, 1, 1), today, "Today", Locale.US))
    }

    @Test
    fun `transaction dates preserve Serbian labels and Latin month names`() {
        val today = LocalDate.of(2026, 10, 17)
        val locale = Locale.forLanguageTag("sr")
        assertEquals("Danas", formatTransactionDate(today, today, "Danas", locale))
        assertEquals("16 okt", formatTransactionDate(today.minusDays(1), today, "Danas", locale))
    }

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

    @Test
    fun `compact form rounds to one decimal thousand`() {
        assertEquals("98.4k", formatCompact(Money.of("98400", Currency.RSD), Locale.US))
        assertEquals("1,250k", formatCompact(Money.of("1250000", Currency.RSD), Locale.US))
        assertEquals("950", formatCompact(Money.of("950", Currency.RSD), Locale.US))
        assertEquals("-2k", formatCompact(-Money.of("2000", Currency.RSD), Locale.US))
    }

    @Test
    fun `serbian month names come out in latin script`() {
        assertEquals("Oktobar", monthName(java.time.YearMonth.of(2026, 10), Locale.forLanguageTag("sr")))
        assertEquals("Okt", monthName(java.time.YearMonth.of(2026, 10), Locale.forLanguageTag("sr"), java.time.format.TextStyle.SHORT_STANDALONE))
        assertEquals("Oct", monthName(java.time.YearMonth.of(2026, 10), Locale.US, java.time.format.TextStyle.SHORT_STANDALONE))
    }
}
