package ua.vytraty.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.vytraty.app.data.db.Recurrence
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.MerchantNormalizer
import ua.vytraty.app.domain.Money
import java.time.LocalDateTime

class DomainTest {
    @Test
    fun `merchant normalizer strips legal forms, cities and numbers`() {
        assertEquals("іваненко і.і", MerchantNormalizer.normalize("ФОП Іваненко І.І. Київ"))
        assertEquals(MerchantNormalizer.normalize("SILPO KYIV"), MerchantNormalizer.normalize("Silpo Kyiv 1234"))
        assertEquals("сільпо", MerchantNormalizer.normalize("Сільпо •••• 1234"))
        assertEquals("", MerchantNormalizer.normalize(null))
        assertEquals("", MerchantNormalizer.normalize("12345"))
    }

    @Test
    fun `money parse and format`() {
        assertEquals(123456L, Money.parseToMinor("1 234,56"))
        assertEquals(123450L, Money.parseToMinor("1234.5"))
        assertEquals(25000L, Money.parseToMinor("250"))
        assertNull(Money.parseToMinor("abc"))
        assertTrue(Money.format(25000, "UAH").endsWith("₴"))
        assertEquals("250", Money.minorToInput(25000))
        assertEquals("250.5", Money.minorToInput(25050))
    }

    @Test
    fun `next due moves forward past now`() {
        val due = Dates.toMillis(LocalDateTime.of(2026, 1, 31, 10, 0))
        val from = Dates.toMillis(LocalDateTime.of(2026, 3, 15, 0, 0))
        val next = Dates.nextDue(due, Recurrence.MONTHLY, from)!!
        assertEquals(LocalDateTime.of(2026, 3, 31, 10, 0), Dates.toLocalDateTime(next))
        assertNull(Dates.nextDue(due, Recurrence.NONE, from))
        val weekly = Dates.nextDue(due, Recurrence.WEEKLY, from)!!
        assertTrue(weekly > from)
    }
}

class UpdateVersionTest {
    @Test
    fun `version comparison is numeric`() {
        assertTrue(ua.vytraty.app.data.update.UpdateChecker.isNewer("1.1.0", "1.0"))
        assertTrue(ua.vytraty.app.data.update.UpdateChecker.isNewer("1.10.0", "1.9.2"))
        assertTrue(!ua.vytraty.app.data.update.UpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertTrue(!ua.vytraty.app.data.update.UpdateChecker.isNewer("1.0.9", "1.1"))
    }
}
