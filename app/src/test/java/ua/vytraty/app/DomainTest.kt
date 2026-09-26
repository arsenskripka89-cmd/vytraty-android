package ua.vytraty.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.vytraty.app.data.db.Recurrence
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.DuplicatePayments
import ua.vytraty.app.domain.MerchantNormalizer
import ua.vytraty.app.domain.Money
import ua.vytraty.app.domain.Refunds
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

class RefundTest {
    @Test
    fun `income with an expense category is stored as a negative expense`() {
        assertEquals(TxKind.EXPENSE to -88700L, Refunds.stored(TxKind.INCOME, TxKind.EXPENSE, 88700L))
        // plain income stays income
        assertEquals(TxKind.INCOME to 88700L, Refunds.stored(TxKind.INCOME, TxKind.INCOME, 88700L))
        assertEquals(TxKind.INCOME to 88700L, Refunds.stored(TxKind.INCOME, null, 88700L))
        // an expense is untouched
        assertEquals(TxKind.EXPENSE to 88700L, Refunds.stored(TxKind.EXPENSE, TxKind.EXPENSE, 88700L))
    }

    @Test
    fun `a stored refund opens as income again`() {
        val (kind, amount) = Refunds.stored(TxKind.INCOME, TxKind.EXPENSE, 88700L)
        assertTrue(Refunds.isRefund(kind, amount))
        assertEquals(TxKind.INCOME, Refunds.formKind(kind, amount))
        assertEquals(TxKind.EXPENSE, Refunds.formKind(TxKind.EXPENSE, 88700L))
        assertTrue(!Refunds.isRefund(TxKind.INCOME, 88700L))
    }
}

class DuplicatePaymentsTest {
    private val bank = "ua.privatbank.ap24"
    private val gpay = "com.google.android.apps.walletnfcrel"
    private val t0 = 1_700_000_000_000L

    private fun tx(walletId: Long = 1, merchant: String? = null, categoryId: Long? = null, card: String? = null) =
        ua.vytraty.app.data.db.TransactionEntity(
            id = 10, walletId = walletId, kind = TxKind.EXPENSE, amountMinor = 25_000, currency = "UAH",
            timestamp = t0, merchant = merchant, categoryId = categoryId, cardLast4 = card,
            source = ua.vytraty.app.data.db.TxSource.NOTIFICATION,
        )

    private fun capture(pkg: String = gpay, amount: Long = 25_000, dt: Long = 20_000, wallet: Long? = null, card: String? = null) =
        DuplicatePayments.Capture(
            packageName = pkg, kind = TxKind.EXPENSE, amountMinor = amount, currency = "UAH",
            timestamp = t0 + dt, ruleWalletId = wallet, merchant = "Сільпо", categoryId = 7, cardLast4 = card,
        )

    @Test
    fun `bank and google wallet announcing one payment are the same payment`() {
        assertTrue(DuplicatePayments.isSamePayment(tx(), listOf(bank), txWalletByRule = true, new = capture()))
        // Wallet posts through two packages; both are Google Pay, so the reverse order works too
        assertTrue(DuplicatePayments.isSamePayment(tx(), listOf("com.google.android.gms"), false, capture(pkg = bank)))
    }

    @Test
    fun `different amount, late notification, same app or another card are separate payments`() {
        assertTrue(!DuplicatePayments.isSamePayment(tx(), listOf(bank), true, capture(amount = 25_001)))
        assertTrue(!DuplicatePayments.isSamePayment(tx(), listOf(bank), true, capture(dt = DuplicatePayments.WINDOW_MS + 1)))
        assertTrue(!DuplicatePayments.isSamePayment(tx(), listOf(bank), true, capture(pkg = bank)))
        assertTrue(!DuplicatePayments.isSamePayment(tx(), listOf(gpay, bank), true, capture(pkg = "com.google.android.gms")))
        assertTrue(!DuplicatePayments.isSamePayment(tx(walletId = 1), listOf(bank), true, capture(wallet = 2)))
        assertTrue(!DuplicatePayments.isSamePayment(tx(card = "1234"), listOf(bank), true, capture(card = "9999")))
        assertTrue(!DuplicatePayments.isSamePayment(tx(), emptyList(), true, capture()))
    }

    @Test
    fun `grouping fills the gaps and keeps what is known`() {
        val m = DuplicatePayments.merged(tx(merchant = null, card = "1234"), txWalletByRule = false, new = capture(wallet = 3, dt = -5_000))
        assertEquals(3L, m.walletId)
        assertEquals("Сільпо", m.merchant)
        assertEquals(7L, m.categoryId)
        assertEquals("1234", m.cardLast4)
        assertEquals(t0 - 5_000, m.timestamp)

        val kept = DuplicatePayments.merged(tx(merchant = "ATB", categoryId = 1), txWalletByRule = true, new = capture(wallet = 3))
        assertEquals(1L, kept.walletId)
        assertEquals("ATB", kept.merchant)
        assertEquals(1L, kept.categoryId)
        assertEquals(t0, kept.timestamp)
    }
}
