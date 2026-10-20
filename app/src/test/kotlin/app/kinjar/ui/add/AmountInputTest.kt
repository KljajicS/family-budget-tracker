package app.kinjar.ui.add

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AmountInputTest {
    private fun type(s: String) = s.fold(AmountInput()) { acc, c -> acc.press(c) }

    @Test fun `digits and one decimal point`() = assertEquals(BigDecimal("1240.5"), type("1240.5").value)
    @Test fun `second point ignored`() = assertEquals("1.5", type("1.5.").text)
    @Test fun `max two decimals`() = assertEquals("1.25", type("1.259").text)
    @Test fun `leading zeros collapse`() = assertEquals("7", type("007").text)
    @Test fun `backspace`() = assertEquals("12", type("123<").text)
    @Test fun `empty and zero are null`() { assertNull(type("").value); assertNull(type("0.00").value) }
    @Test fun `length cap at 12 characters`() = assertEquals(12, type("1234567890123456").text.length)
}
