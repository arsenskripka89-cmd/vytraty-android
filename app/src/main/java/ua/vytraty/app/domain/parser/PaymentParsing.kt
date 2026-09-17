package ua.vytraty.app.domain.parser

import ua.vytraty.app.data.db.TxKind
import java.math.BigDecimal

/** Result of parsing one notification. Amount is always positive; direction is in [kind]. */
data class ParsedPayment(
    val amountMinor: Long,
    val currency: String,
    val kind: TxKind,
    val merchant: String?,
    val cardLast4: String?,
    val bank: BankSource,
    val rawTitle: String?,
    val rawText: String?,
)

enum class BankSource(val displayName: String, val packages: List<String>) {
    GOOGLE_PAY("Google Pay / Wallet", listOf("com.google.android.apps.walletnfcrel", "com.google.android.gms")),
    MONOBANK("Monobank", listOf("com.ftband.mono")),
    PRIVATBANK("Приват24", listOf("ua.privatbank.ap24")),
    RAIFFEISEN("Raiffeisen (MyRaif)", listOf("ua.raiffeisen.myraif", "ua.aval.dbo.client.android")),
    REVOLUT("Revolut", listOf("com.revolut.revolut")),
    OSCHADBANK("Ощадбанк", listOf("ua.oschadbank.online")),
    PUMB("ПУМБ", listOf("com.pumb.online", "ua.pumb.pumbonline")),
    OTHER("Інший банк", emptyList());

    companion object {
        fun byPackage(pkg: String): BankSource? = entries.firstOrNull { pkg in it.packages }

        /** [ua.vytraty.app.data.db.WalletEntity.bankCode] → bank ("mono" comes from the Monobank connector). */
        fun byCode(code: String?): BankSource? = when {
            code.isNullOrBlank() -> null
            code.equals("mono", true) -> MONOBANK
            else -> entries.firstOrNull { it.name.equals(code, true) }
        }

        /** Banks a wallet can belong to (Google Pay is a payment app, not a bank). */
        val forWallets: List<BankSource> get() = entries.filter { it != GOOGLE_PAY && it != OTHER }
        val allPackages: Set<String> get() = entries.flatMap { it.packages }.toSet()
    }
}

/** A single amount+currency found in text. */
data class AmountMatch(val minor: Long, val currency: String, val negative: Boolean, val range: IntRange)

object AmountExtractor {
    // "1 234,56", "1,234.56", "1234", "250.00"; a comma followed by exactly 3 digits is a thousands separator.
    /** A space, including the non-breaking ones banks put between the number and the currency (java \s matches neither). */
    private const val SP = """[\s   ]"""
    private const val NUM = """(?:\d{1,3}(?:[ \u00A0\u202F,]\d{3})+(?!\d)|\d+)(?:[.,]\d{1,2}(?!\d))?"""
    private const val CUR_AFTER = """(₴|грн\.?|uah|\$|usd|€|eur|£|gbp|zł|pln|czk|kč|try|₺|chf)"""
    private const val CUR_BEFORE = """(₴|\$|€|£|₺)"""
    private val amountThenCurrency = Regex("""([+\-−–]?)$SP*($NUM)$SP*$CUR_AFTER(?![a-zа-яіїє])""", RegexOption.IGNORE_CASE)
    private val currencyThenAmount = Regex("""([+\-−–]?)$SP*$CUR_BEFORE$SP*($NUM)""", RegexOption.IGNORE_CASE)

    fun findAll(text: String): List<AmountMatch> {
        val out = mutableListOf<AmountMatch>()
        amountThenCurrency.findAll(text).forEach { m ->
            val minor = toMinor(m.groupValues[2]) ?: return@forEach
            out += AmountMatch(minor, normalizeCurrency(m.groupValues[3]), isNegative(m.groupValues[1]), m.range)
        }
        currencyThenAmount.findAll(text).forEach { m ->
            if (out.any { it.range.first <= m.range.last && m.range.first <= it.range.last }) return@forEach
            val minor = toMinor(m.groupValues[3]) ?: return@forEach
            out += AmountMatch(minor, normalizeCurrency(m.groupValues[2]), isNegative(m.groupValues[1]), m.range)
        }
        return out.sortedBy { it.range.first }
    }

    fun first(text: String): AmountMatch? = findAll(text).firstOrNull()

    private fun isNegative(sign: String) = sign.isNotEmpty() && sign != "+"

    fun toMinor(raw: String): Long? {
        var s = raw.replace(Regex("""[ \u00A0\u202F]"""), "")
        // "1,234.56" -> "1234.56"; "1234,56" -> "1234.56"; "1.234,56" -> "1234.56"
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        s = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastComma >= 0 -> {
                val after = s.length - lastComma - 1
                if (after == 3 && s.count { it == ',' } == 1 && s.length > 4) s.replace(",", "") else s.replace(',', '.')
            }
            else -> s
        }
        return try {
            BigDecimal(s).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    fun normalizeCurrency(raw: String): String = when (raw.lowercase().trimEnd('.')) {
        "₴", "грн", "uah" -> "UAH"
        "$", "usd" -> "USD"
        "€", "eur" -> "EUR"
        "£", "gbp" -> "GBP"
        "zł", "pln" -> "PLN"
        "czk", "kč" -> "CZK"
        "try", "₺" -> "TRY"
        "chf" -> "CHF"
        else -> raw.uppercase()
    }
}

object TextCues {
    private val cardPatterns = listOf(
        Regex("""(?:•{2,}|\*{1,4}|x{2,4}|\.{2,})\s*(\d{4})\b"""),
        Regex("""(?:картк[аиу]|карта|card|ending(?: in)?|закінчується на)\s*[:#№]?\s*(?:•+|\*+)?\s*(\d{4})\b""", RegexOption.IGNORE_CASE),
        Regex("""\((\d{4})\)"""),
    )
    private val incomeWords = Regex(
        """зарахуван|поповнен|надходжен|received|refund|повернен|кешбек|cashback|переказ від|from |отриман|income|deposit|credited""",
        RegexOption.IGNORE_CASE,
    )
    private val expenseWords = Regex(
        """списан|покупк|оплат|paid|payment|purchase|spent|withdraw|зняття|платіж|charged|debited|at |у |в """,
        RegexOption.IGNORE_CASE,
    )
    private val balanceWords = Regex("""(баланс|balance|доступно|available|залишок)\s*[:\-]?\s*""", RegexOption.IGNORE_CASE)

    private val cardLabelPattern = Regex("""(?iu)(?:з картки|с карты|з карти|from card|картка|карта|card)\s+([\p{L}\p{N}][\p{L}\p{N} .\-]{1,28})""")

    fun cardLast4(text: String): String? = cardPatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }

    /** "30,91 € с карты Privat EUR" → "Privat EUR": the card name a notification mentions. */
    fun cardLabel(text: String): String? = cardLabelPattern.find(text)?.groupValues?.get(1)?.trim()
        ?.takeIf { it.length >= 2 && !it.all { c -> c.isDigit() } }

    fun looksLikeIncome(text: String) = incomeWords.containsMatchIn(text)
    fun looksLikeExpense(text: String) = expenseWords.containsMatchIn(text)

    /** Removes the "Баланс: 1 000 ₴" tail so the balance is not mistaken for the payment amount. */
    fun stripBalance(text: String): String {
        val m = balanceWords.find(text) ?: return text
        return text.substring(0, m.range.first)
    }

    fun cleanupMerchant(text: String): String? {
        var s = text
        AmountExtractor.findAll(s).sortedByDescending { it.range.first }.forEach { s = s.removeRange(it.range) }
        cardPatterns.forEach { s = s.replace(it, " ") }
        s = s.replace(Regex("""(?iu)\b(visa|mastercard|master card|maestro|gpay|google pay|apple pay|debit|credit|з картки|with|за допомогою)\b"""), " ")
        s = s.replace(Regex("""(?iu)\b(списання|списано|покупка|оплата|оплачено|платіж|paid|payment|purchase|spent|charged|зарахування|зараховано|поповнення|received|refund|повернення|транзакція|операція)\b\s*[:\-–]?"""), " ")
        s = s.replace(Regex("""[•·|]+"""), " ")
        s = s.replace(Regex("""^[\s:\-–.,]+|[\s:\-–.,]+$"""), "")
        s = s.replace(Regex("""\s{2,}"""), " ").trim()
        return s.takeIf { it.length >= 2 }
    }
}

interface NotificationParser {
    val bank: BankSource
    fun parse(title: String?, text: String?): ParsedPayment?
}

/**
 * Generic parser used for every supported package. Bank-specific parsers refine merchant extraction;
 * anything they cannot handle falls back here.
 */
open class GenericParser(override val bank: BankSource) : NotificationParser {
    override fun parse(title: String?, text: String?): ParsedPayment? {
        val t = title.orEmpty().trim()
        val x = text.orEmpty().trim()
        val joined = TextCues.stripBalance("$t\n$x")
        val amounts = AmountExtractor.findAll(joined)
        val amount = pickAmount(amounts) ?: return null
        val kind = when {
            amount.negative -> TxKind.EXPENSE
            !amount.negative && amounts.any { it.range == amount.range } && joinedHasPlus(joined, amount) -> TxKind.INCOME
            TextCues.looksLikeIncome(joined) && !TextCues.looksLikeExpense(joined) -> TxKind.INCOME
            TextCues.looksLikeIncome(joined) && joined.contains(Regex("""(?iu)зарахуван|поповнен|refund|повернен|received""")) -> TxKind.INCOME
            else -> TxKind.EXPENSE
        }
        val merchant = extractMerchant(t, x, joined)
        return ParsedPayment(
            amountMinor = amount.minor,
            currency = amount.currency,
            kind = kind,
            merchant = merchant,
            cardLast4 = TextCues.cardLast4("$t $x"),
            bank = bank,
            rawTitle = title,
            rawText = text,
        )
    }

    private fun joinedHasPlus(joined: String, amount: AmountMatch): Boolean {
        val start = (amount.range.first - 2).coerceAtLeast(0)
        return joined.substring(start, amount.range.first + 1).contains('+')
    }

    protected open fun pickAmount(amounts: List<AmountMatch>): AmountMatch? = amounts.firstOrNull()

    protected open fun extractMerchant(title: String, text: String, joined: String): String? {
        val titleHasAmount = AmountExtractor.first(title) != null
        val textHasAmount = AmountExtractor.first(text) != null
        val candidates = when {
            titleHasAmount && !textHasAmount -> listOf(text, title)
            !titleHasAmount && textHasAmount -> listOf(title, text)
            else -> listOf(text, title)
        }
        for (c in candidates) {
            val cleaned = TextCues.cleanupMerchant(TextCues.stripBalance(c))
            if (!cleaned.isNullOrBlank() && !cleaned.matches(Regex("""(?iu)(google pay|google wallet|monobank|приват24|privat24|raiffeisen|revolut|oschadbank|пумб)"""))) {
                return firstSegment(cleaned)
            }
        }
        return null
    }

    /** "Сільпо. Баланс ..." or "Silpo, Kyiv" → first meaningful segment. */
    protected fun firstSegment(s: String): String {
        val seg = s.split(Regex("""\s*[.\n]\s+|\s{2,}""")).firstOrNull { it.isNotBlank() } ?: s
        return seg.trim().take(60)
    }
}

class GooglePayParser : GenericParser(BankSource.GOOGLE_PAY) {
    // Google Pay: title "₴250.00 з Visa •••• 1234" / "$12.34 with Visa •••• 1234", text "Сільпо"
    // or title "Оплачено у Сільпо", text "250,00 ₴ · Visa ••••1234"
    override fun extractMerchant(title: String, text: String, joined: String): String? {
        val fromText = text.split("·", "•").map { it.trim() }.firstOrNull { seg ->
            seg.isNotBlank() && AmountExtractor.first(seg) == null && !seg.contains(Regex("""\d{4}""")) &&
                !seg.contains(Regex("""(?iu)visa|mastercard|maestro"""))
        }
        if (!fromText.isNullOrBlank() && AmountExtractor.first(title) != null) return TextCues.cleanupMerchant(fromText)
        val fromTitle = Regex("""(?iu)(?:оплачено|paid|payment)\s+(?:у|в|at|to)\s+(.+)$""").find(title)?.groupValues?.get(1)
        if (!fromTitle.isNullOrBlank()) return TextCues.cleanupMerchant(fromTitle)
        return super.extractMerchant(title, text, joined)
    }
}

class MonobankParser : GenericParser(BankSource.MONOBANK) {
    // Monobank: title "Сільпо" text "-250.00 ₴  Баланс: 1 234.56 ₴" (sometimes emoji prefix)
    override fun extractMerchant(title: String, text: String, joined: String): String? {
        val t = title.replace(Regex("""^[^\p{L}\p{N}]+"""), "").trim()
        if (t.isNotBlank() && AmountExtractor.first(t) == null && !t.equals("monobank", true)) {
            return TextCues.cleanupMerchant(t)
        }
        val lead = TextCues.stripBalance(text).split(Regex("""\s{2,}|\n""")).map { it.trim() }
            .firstOrNull { AmountExtractor.first(it) == null && it.length > 1 }
        return lead?.let { TextCues.cleanupMerchant(it) } ?: super.extractMerchant(title, text, joined)
    }
}

class PrivatBankParser : GenericParser(BankSource.PRIVATBANK) {
    // Приват24: "Списання: 250.00 UAH. Картка *1234. Сільпо. Баланс: 1000.00 UAH"
    override fun extractMerchant(title: String, text: String, joined: String): String? {
        val body = TextCues.stripBalance("$title. $text")
        val segments = body.split(Regex("""[.\n;]\s+|\.$""")).map { it.trim() }.filter { it.isNotBlank() }
        val merchant = segments.firstOrNull { seg ->
            AmountExtractor.first(seg) == null && TextCues.cardLast4(seg) == null &&
                !seg.contains(Regex("""(?iu)^(списання|зарахування|покупка|оплата|переказ|приват24|privat24)$"""))
        }
        return merchant?.let { TextCues.cleanupMerchant(it) } ?: super.extractMerchant(title, text, joined)
    }
}

class RaiffeisenParser : GenericParser(BankSource.RAIFFEISEN) {
    // MyRaif: "Покупка 250.00 UAH SILPO KYIV. Картка *1234. Доступно 1000.00 UAH"
    override fun extractMerchant(title: String, text: String, joined: String): String? {
        val body = TextCues.stripBalance("$title\n$text")
        val amount = AmountExtractor.first(body)
        if (amount != null) {
            val after = body.substring(amount.range.last + 1)
            val seg = after.split(Regex("""[.\n]"""))
                .map { TextCues.cleanupMerchant(it) }
                .firstOrNull { !it.isNullOrBlank() }
            if (!seg.isNullOrBlank()) return seg
        }
        return super.extractMerchant(title, text, joined)
    }
}

class RevolutParser : GenericParser(BankSource.REVOLUT) {
    // Revolut: title "Silpo" text "-250,00 ₴ · Картка закінчується на 1234" or "Paid ₴250 at Silpo"
    override fun extractMerchant(title: String, text: String, joined: String): String? {
        val atPattern = Regex("""(?iu)(?:at|у|в|to|до)\s+(.+?)(?:\s*[·•]|$)""")
        for (s in listOf(title, text)) {
            if (AmountExtractor.first(s) != null) {
                val m = atPattern.find(s)?.groupValues?.get(1)
                if (!m.isNullOrBlank()) return TextCues.cleanupMerchant(m)
            }
        }
        return super.extractMerchant(title, text, joined)
    }
}

/** Chooses a parser by package and runs it. */
class ParserRegistry {
    private val parsers: Map<BankSource, NotificationParser> = listOf(
        GooglePayParser(), MonobankParser(), PrivatBankParser(), RaiffeisenParser(), RevolutParser(),
        GenericParser(BankSource.OSCHADBANK), GenericParser(BankSource.PUMB), GenericParser(BankSource.OTHER),
    ).associateBy { it.bank }

    fun parserFor(packageName: String): NotificationParser? {
        val bank = BankSource.byPackage(packageName) ?: return null
        return parsers[bank]
    }

    fun parse(packageName: String, title: String?, text: String?): ParsedPayment? {
        val parser = parserFor(packageName) ?: parsers.getValue(BankSource.OTHER)
        return parser.parse(title, text)
    }

    fun parseAs(bank: BankSource, title: String?, text: String?): ParsedPayment? = parsers.getValue(bank).parse(title, text)
}
