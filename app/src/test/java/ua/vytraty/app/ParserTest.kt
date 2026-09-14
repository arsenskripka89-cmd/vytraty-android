package ua.vytraty.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.domain.parser.AmountExtractor
import ua.vytraty.app.domain.parser.BankSource
import ua.vytraty.app.domain.parser.ParserRegistry

class ParserTest {
    private val registry = ParserRegistry()

    @Test
    fun `amount extractor handles ukrainian and english formats`() {
        assertEquals(25000L, AmountExtractor.first("250,00 ₴")!!.minor)
        assertEquals(25000L, AmountExtractor.first("250.00 UAH")!!.minor)
        assertEquals(123456L, AmountExtractor.first("1 234,56 грн")!!.minor)
        assertEquals(123456L, AmountExtractor.first("$1,234.56")!!.minor)
        assertEquals(1234L, AmountExtractor.first("€12.34")!!.minor)
        assertEquals(25000L, AmountExtractor.first("₴250")!!.minor)
        assertEquals("USD", AmountExtractor.first("$1,234.56")!!.currency)
        assertNull(AmountExtractor.first("Вітаємо! Ваш код 1234"))
    }

    @Test
    fun `google pay title with amount and card, text with merchant`() {
        val p = registry.parseAs(BankSource.GOOGLE_PAY, "₴250,00 з Visa •••• 1234", "Сільпо")!!
        assertEquals(25000L, p.amountMinor)
        assertEquals("UAH", p.currency)
        assertEquals(TxKind.EXPENSE, p.kind)
        assertEquals("1234", p.cardLast4)
        assertEquals("Сільпо", p.merchant)
    }

    @Test
    fun `google pay english format`() {
        val p = registry.parseAs(BankSource.GOOGLE_PAY, "$12.34 with Mastercard •••• 9876", "Starbucks")!!
        assertEquals(1234L, p.amountMinor)
        assertEquals("USD", p.currency)
        assertEquals("9876", p.cardLast4)
        assertEquals("Starbucks", p.merchant)
    }

    @Test
    fun `google pay paid at merchant in title`() {
        val p = registry.parseAs(BankSource.GOOGLE_PAY, "Оплачено у Rozetka", "250,00 ₴ · Visa ••••4321")!!
        assertEquals(25000L, p.amountMinor)
        assertEquals("Rozetka", p.merchant)
        assertEquals("4321", p.cardLast4)
    }

    @Test
    fun `monobank expense with balance tail`() {
        val p = registry.parseAs(BankSource.MONOBANK, "Сільпо", "-250.00 ₴  Баланс: 12 345.67 ₴")!!
        assertEquals(25000L, p.amountMinor)
        assertEquals(TxKind.EXPENSE, p.kind)
        assertEquals("Сільпо", p.merchant)
    }

    @Test
    fun `monobank income`() {
        val p = registry.parseAs(BankSource.MONOBANK, "Переказ від Іван І.", "+1 500.00 ₴  Баланс: 3 000.00 ₴")!!
        assertEquals(150000L, p.amountMinor)
        assertEquals(TxKind.INCOME, p.kind)
    }

    @Test
    fun `privatbank format`() {
        val p = registry.parseAs(BankSource.PRIVATBANK, "Приват24", "Списання: 149.99 UAH. Картка *5555. АТБ-Маркет. Баланс: 2 000.00 UAH")!!
        assertEquals(14999L, p.amountMinor)
        assertEquals(TxKind.EXPENSE, p.kind)
        assertEquals("5555", p.cardLast4)
        assertEquals("АТБ-Маркет", p.merchant)
    }

    @Test
    fun `privatbank income`() {
        val p = registry.parseAs(BankSource.PRIVATBANK, "Приват24", "Зарахування: 10 000.00 UAH. Картка *5555. Зарплата. Баланс: 12 000.00 UAH")!!
        assertEquals(1000000L, p.amountMinor)
        assertEquals(TxKind.INCOME, p.kind)
    }

    @Test
    fun `raiffeisen format`() {
        val p = registry.parseAs(BankSource.RAIFFEISEN, "MyRaif", "Покупка 320.50 UAH SILPO KYIV. Картка *7777. Доступно 5 000.00 UAH")!!
        assertEquals(32050L, p.amountMinor)
        assertEquals("7777", p.cardLast4)
        assertNotNull(p.merchant)
        assertEquals("SILPO KYIV", p.merchant)
    }

    @Test
    fun `revolut format`() {
        val p = registry.parseAs(BankSource.REVOLUT, "Paid £12.34 at Tesco", "Card ending 2468")!!
        assertEquals(1234L, p.amountMinor)
        assertEquals("GBP", p.currency)
        assertEquals("Tesco", p.merchant)
        assertEquals("2468", p.cardLast4)
    }

    @Test
    fun `revolut title merchant text amount`() {
        val p = registry.parseAs(BankSource.REVOLUT, "Glovo", "-320,00 ₴ · Картка закінчується на 1111")!!
        assertEquals(32000L, p.amountMinor)
        assertEquals(TxKind.EXPENSE, p.kind)
        assertEquals("Glovo", p.merchant)
        assertEquals("1111", p.cardLast4)
    }

    @Test
    fun `unknown package falls back to generic parser`() {
        val p = registry.parse("com.example.bank", "Оплата", "Списано 99,90 грн, магазин Rozetka")!!
        assertEquals(9990L, p.amountMinor)
        assertEquals(TxKind.EXPENSE, p.kind)
    }

    @Test
    fun `no amount means null`() {
        assertNull(registry.parse("com.ftband.mono", "Нагадування", "Не забудьте оплатити кредит"))
    }
}

