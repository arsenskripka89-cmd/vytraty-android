package ua.vytraty.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.vytraty.app.data.db.CaptureRuleEntity
import ua.vytraty.app.data.db.RuleType
import ua.vytraty.app.data.rates.ExchangeRates
import ua.vytraty.app.domain.parser.AmountExtractor
import ua.vytraty.app.domain.parser.BankSource
import ua.vytraty.app.domain.parser.ParserRegistry
import ua.vytraty.app.domain.parser.RuleMatcher
import ua.vytraty.app.domain.parser.TextCues

class RulesTest {
    private val registry = ParserRegistry()

    private fun rule(pattern: String, bank: String? = null, type: RuleType = RuleType.WALLET, target: Long = 1) =
        CaptureRuleEntity(type = type, targetId = target, bank = bank, pattern = pattern)

    @Test
    fun `google wallet notification with a non-breaking space is a payment`() {
        // Google Wallet writes "30,91<NBSP>€"; before the fix no amount was found at all.
        val p = registry.parse("com.google.android.apps.walletnfcrel", "MERCADONA MERCAT CENTR", "30,91 € с карты Privat EUR")!!
        assertEquals(3091L, p.amountMinor)
        assertEquals("EUR", p.currency)
        assertEquals("MERCADONA MERCAT CENTR", p.merchant)
    }

    @Test
    fun `card label is taken from the notification text`() {
        assertEquals("Privat EUR", TextCues.cardLabel("30,91 € с карты Privat EUR"))
        assertEquals("Rayf UAH", TextCues.cardLabel("Списання 100 ₴ з картки Rayf UAH"))
        assertNull(TextCues.cardLabel("Списання 100 ₴"))
    }

    @Test
    fun `rule matches by text and by source app`() {
        val r = rule("Privat EUR", bank = BankSource.GOOGLE_PAY.name)
        assertTrue(RuleMatcher.matches(r, "com.google.android.apps.walletnfcrel", "MERCADONA", "30,91 € с карты Privat EUR"))
        // same text, other app
        assertFalse(RuleMatcher.matches(r, "com.revolut.revolut", "MERCADONA", "30,91 € с карты Privat EUR"))
        // any app
        assertTrue(RuleMatcher.matches(rule("Privat EUR"), "com.revolut.revolut", null, "с карты Privat EUR"))
    }

    @Test
    fun `bullets and non-breaking spaces do not break a card number rule`() {
        assertTrue(RuleMatcher.matches(rule("•••• 4498"), "ua.raiffeisen.myraif", "Оплата", "Картка •••• 4498"))
    }

    @Test
    fun `advertising is not matched by any rule`() {
        val rules = listOf(rule("Privat EUR"), rule("Rayf UAH", target = 2))
        val best = RuleMatcher.best(
            rules, RuleType.WALLET, "com.revolut.revolut",
            "Выиграйте 5000 €", "Переведите зарплату в Revolut и подтвердите участие в розыгрыше",
        )
        assertNull(best)
    }

    @Test
    fun `the most specific rule wins`() {
        val rules = listOf(rule("Privat", target = 1), rule("Privat EUR", target = 2))
        val best = RuleMatcher.best(rules, RuleType.WALLET, "com.google.android.apps.walletnfcrel", null, "с карты Privat EUR")
        assertEquals(2L, best!!.targetId)
    }

    @Test
    fun `an empty identifier never matches`() {
        assertFalse(RuleMatcher.matches(rule("   "), "com.ftband.mono", "Сільпо", "-250 ₴"))
    }

    @Test
    fun `rates are cached as text and convert both ways`() {
        val rates = ExchangeRates.decode(ExchangeRates.encode(mapOf("USD" to 44.6, "EUR" to 51.3)))
        assertEquals(1.0, rates["UAH"]!!, 0.0)
        // 10,00 EUR → 513,00 UAH
        assertEquals(51300L, ExchangeRates.convert(1000L, "EUR", "UAH", rates))
        // and back
        assertEquals(1000L, ExchangeRates.convert(51300L, "UAH", "EUR", rates))
        assertEquals(1000L, ExchangeRates.convert(1000L, "UAH", "UAH", rates))
        // no rate for the Polish zloty → no silent addition of zloty to hryvnia
        assertNull(ExchangeRates.convert(1000L, "PLN", "UAH", rates))
    }

    @Test
    fun `amounts survive every kind of space banks use`() {
        assertEquals(3091L, AmountExtractor.first("30,91 €")!!.minor)
        assertEquals(3091L, AmountExtractor.first("30,91 €")!!.minor)
        assertEquals(500000L, AmountExtractor.first("5 000 €")!!.minor)
    }

    @Test
    fun `wallet bank code maps to a bank, including the legacy monobank one`() {
        assertEquals(BankSource.MONOBANK, BankSource.byCode("mono"))
        assertEquals(BankSource.PRIVATBANK, BankSource.byCode("PRIVATBANK"))
        assertNull(BankSource.byCode(null))
        assertNull(BankSource.byCode("whatever"))
    }
}
