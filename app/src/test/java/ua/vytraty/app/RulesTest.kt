package ua.vytraty.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.vytraty.app.data.db.CaptureRuleEntity
import ua.vytraty.app.data.db.RuleType
import ua.vytraty.app.data.db.TransactionEntity
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.domain.usecase.MergeTransfersUseCase
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

class TransferMergeTest {
    private fun tx(
        kind: TxKind,
        amount: Long,
        currency: String = "UAH",
        source: TxSource = TxSource.NOTIFICATION,
        categoryId: Long? = null,
        received: Long? = null,
        receivedCurrency: String? = null,
    ) = TransactionEntity(
        id = 1, walletId = 1, categoryId = categoryId, kind = kind, amountMinor = amount, currency = currency,
        timestamp = 0, source = source, receivedMinor = received, receivedCurrency = receivedCurrency,
    )

    @Test
    fun `only an uncategorized captured payment can be half of a transfer`() {
        assertTrue(MergeTransfersUseCase.isMergeable(tx(TxKind.EXPENSE, 2_600_000)))
        assertTrue(MergeTransfersUseCase.isMergeable(tx(TxKind.INCOME, 50_153, "EUR")))
        // a payment the rules already classified is a normal expense, not a transfer
        assertFalse(MergeTransfersUseCase.isMergeable(tx(TxKind.EXPENSE, 2_600_000, categoryId = 5)))
        // typed by hand
        assertFalse(MergeTransfersUseCase.isMergeable(tx(TxKind.EXPENSE, 2_600_000, source = TxSource.MANUAL)))
        // a refund
        assertFalse(MergeTransfersUseCase.isMergeable(tx(TxKind.EXPENSE, -88_700)))
    }

    @Test
    fun `the rate the bank used is derived from both amounts`() {
        // 26 000,00 ₴ left, 501,53 € arrived
        val transfer = tx(TxKind.TRANSFER, 2_600_000, "UAH", received = 50_153, receivedCurrency = "EUR")
        assertEquals("Курс: 1 € = 51,8414 ₴", MergeTransfersUseCase.rateLine(transfer))
        // same currency on both sides — nothing to show
        assertNull(MergeTransfersUseCase.rateLine(tx(TxKind.TRANSFER, 100_000, "UAH", received = 99_000, receivedCurrency = "UAH")))
        assertNull(MergeTransfersUseCase.rateLine(tx(TxKind.TRANSFER, 100_000, "UAH")))
    }
}

class TransferFeeTest {
    @Test
    fun `inside one currency what the bank kept is a fee`() {
        // 404 € left the card, 400 € arrived
        assertEquals(400L, MergeTransfersUseCase.feeFor(40_400, 40_000, sameCurrency = true))
        // nothing kept
        assertNull(MergeTransfersUseCase.feeFor(40_000, 40_000, sameCurrency = true))
        // more arrived than left — not a fee
        assertNull(MergeTransfersUseCase.feeFor(40_000, 40_400, sameCurrency = true))
    }

    @Test
    fun `between currencies the difference is the rate, not a fee`() {
        // 26 000 ₴ → 501,53 €: the gap is the exchange, it must not become an expense
        assertNull(MergeTransfersUseCase.feeFor(2_600_000, 50_153, sameCurrency = false))
    }
}

class TelegramNotificationTest {
    private val registry = ParserRegistry()

    @Test
    fun `privatbank messages in telegram are read as payments`() {
        val debit = registry.parse(
            "org.telegram.messenger", "PrivatBank",
            "-15 050.78₴ Валютообмін між своїми рахунками\n4*34 23:42\nБал. 1 137.85₴",
        )!!
        assertEquals(1_505_078L, debit.amountMinor)
        assertEquals("UAH", debit.currency)
        assertEquals(TxKind.EXPENSE, debit.kind)

        val credit = registry.parse(
            "org.telegram.messenger", "PrivatBank",
            "+290.48€ Валютообмін між своїми рахунками\n5*95 23:42\nКомісія 0.98€\nБал. 638.17€",
        )!!
        assertEquals(29_048L, credit.amountMinor)
        assertEquals("EUR", credit.currency)
        assertEquals(TxKind.INCOME, credit.kind)
    }

    @Test
    fun `a wallet without a chosen bank still gets its logo from the name`() {
        assertEquals(BankSource.PRIVATBANK, BankSource.guessByName("Приват EUR"))
        assertEquals(BankSource.RAIFFEISEN, BankSource.guessByName("Rayf UAH"))
        assertEquals(BankSource.REVOLUT, BankSource.guessByName("Revolut EUR"))
        assertNull(BankSource.guessByName("Готівка"))
    }
}
