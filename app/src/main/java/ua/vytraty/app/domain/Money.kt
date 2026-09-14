package ua.vytraty.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object Money {
    private val symbols = DecimalFormatSymbols(Locale("uk", "UA")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }
    private val format = DecimalFormat("#,##0.00", symbols)
    private val formatNoCents = DecimalFormat("#,##0", symbols)

    fun symbol(currency: String): String = when (currency.uppercase()) {
        "UAH" -> "₴"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "PLN" -> "zł"
        else -> currency.uppercase()
    }

    fun format(minor: Long, currency: String = "UAH", withSign: Boolean = false): String {
        val abs = BigDecimal(kotlin.math.abs(minor)).movePointLeft(2)
        val body = if (minor % 100 == 0L) formatNoCents.format(abs) else format.format(abs)
        val sign = when {
            minor < 0 -> "−"
            withSign && minor > 0 -> "+"
            else -> ""
        }
        return "$sign$body ${symbol(currency)}"
    }

    /** Parses user input like "1 234,56" / "1234.5" into minor units. Returns null when not a number. */
    fun parseToMinor(input: String): Long? {
        val cleaned = input.trim()
            .replace(" ", "").replace(" ", "").replace(" ", "")
            .replace(',', '.')
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    fun minorToInput(minor: Long): String {
        val bd = BigDecimal(minor).movePointLeft(2)
        return if (minor % 100 == 0L) bd.setScale(0).toPlainString() else bd.stripTrailingZeros().toPlainString()
    }
}
