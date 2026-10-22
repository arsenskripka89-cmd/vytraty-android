package ua.vytraty.app

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.vytraty.app.data.db.CategorySum
import ua.vytraty.app.data.db.DaySum
import ua.vytraty.app.data.db.MonthSum
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.WalletEntity
import ua.vytraty.app.data.rates.Converted
import ua.vytraty.app.data.rates.ExchangeRates
import ua.vytraty.app.domain.usecase.MergeTransfersUseCase
import ua.vytraty.app.ui.analytics.convertAnalytics
import ua.vytraty.app.ui.transaction.TxForm
import ua.vytraty.app.ui.transaction.sentCurrency

class CurrencyTest {
    private val rates = mapOf("UAH" to 1.0, "EUR" to 50.0, "USD" to 40.0)

    @Test
    fun `amounts in different currencies are added up in one`() {
        // 100 ₴ + 10 € + 5 $ = 100 + 500 + 200 ₴
        assertEquals(Converted(80_000), ExchangeRates.sum(listOf(10_000L to "UAH", 1_000L to "EUR", 500L to "USD"), "UAH", rates))
        // the same in euros
        assertEquals(Converted(1_600), ExchangeRates.sum(listOf(10_000L to "UAH", 1_000L to "EUR", 500L to "USD"), "EUR", rates))
        // a currency without a rate is reported, not added as if it were hryvnias
        assertEquals(Converted(10_000, setOf("PLN")), ExchangeRates.sum(listOf(10_000L to "UAH", 1_000L to "pln"), "UAH", rates))
    }

    @Test
    fun `analytics adds up every category across currencies`() {
        val s = convertAnalytics(
            "UAH", rates,
            byCategory = listOf(
                CategorySum(null, "UAH", 100_000),
                CategorySum(null, "EUR", 10_000), // 100 € = 5 000 ₴
                CategorySum(2, "UAH", 200_000),
                CategorySum(3, "PLN", 5_000), // no rate: nothing to show
            ),
            months = listOf(MonthSum("2026-10", "UAH", 300_000, 0), MonthSum("2026-10", "EUR", 10_000, 2_000)),
            days = listOf(DaySum("2026-10-22", "EUR", 10_000, 0)),
        )
        // "no category" 1 000 ₴ + 100 € is now the biggest, ahead of 2 000 ₴
        assertEquals(listOf(CategorySum(null, "UAH", 600_000), CategorySum(2, "UAH", 200_000)), s.byCategory)
        assertEquals(listOf(MonthSum("2026-10", "UAH", 800_000, 100_000)), s.months)
        assertEquals(listOf(DaySum("2026-10-22", "UAH", 500_000, 0)), s.days)
        assertEquals(setOf("EUR"), s.converted)
        assertEquals(setOf("PLN"), s.missing)
    }

    @Test
    fun `analytics in a wallet's own currency`() {
        val s = convertAnalytics("EUR", rates, listOf(CategorySum(1, "EUR", 1_000), CategorySum(1, "UAH", 5_000)), emptyList(), emptyList())
        assertEquals(listOf(CategorySum(1, "EUR", 1_100)), s.byCategory)
        assertEquals(setOf("UAH"), s.converted)
    }

    @Test
    fun `a transfer leaves in the currency of its card`() {
        val eur = WalletEntity(id = 1, name = "Приват EUR", currency = "EUR")
        // the form still remembers a currency picked for an expense, the card wins
        assertEquals("EUR", TxForm(kind = TxKind.TRANSFER, currency = "USD").sentCurrency(eur))
        // an expense may be paid in another currency than the card's
        assertEquals("USD", TxForm(kind = TxKind.EXPENSE, currency = "USD").sentCurrency(eur))
        assertEquals("EUR", TxForm(kind = TxKind.EXPENSE).sentCurrency(eur))
        assertEquals("UAH", TxForm(kind = TxKind.TRANSFER).sentCurrency(null))
    }

    @Test
    fun `the rate counts the dearer currency in units, whichever way the money went`() {
        // 100 € → 5 102,04 ₴
        assertEquals("1 € = 51,0204 ₴", MergeTransfersUseCase.rateText(10_000, "EUR", 510_204, "UAH"))
        // 5 184,14 ₴ → 100 €
        assertEquals("1 € = 51,8414 ₴", MergeTransfersUseCase.rateText(518_414, "UAH", 10_000, "EUR"))
    }
}
