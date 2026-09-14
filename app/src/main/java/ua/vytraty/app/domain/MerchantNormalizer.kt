package ua.vytraty.app.domain

/**
 * Turns raw merchant strings from notifications into a stable key so that
 * "ФОП Іваненко І.І. Київ", "FOP IVANENKO KYIV" and "Іваненко" from different
 * banks still map to the same learned category where possible.
 */
object MerchantNormalizer {
    private val legalForms = listOf(
        "фоп", "тов", "пп", "ат", "пат", "прат", "кп", "фо-п", "фо п",
        "fop", "tov", "pp", "llc", "ltd", "inc", "llp", "sp z o.o.", "s.r.o.",
    )
    private val noiseWords = setOf(
        "kyiv", "kiev", "київ", "lviv", "львів", "odesa", "odessa", "одеса", "kharkiv", "харків",
        "dnipro", "дніпро", "ukraine", "ukr", "ua", "україна", "magazyn", "магазин", "shop", "store",
        "market", "маркет", "supermarket", "супермаркет",
    )
    private val cityLike = Regex("""\b[a-zа-яіїєґ]+\s*$""")

    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var s = raw.lowercase()
            .replace('’', '\'').replace('`', '\'')
            .replace(Regex("""[•·*"«»()\[\]{}|/\\_,:;!?#№]"""), " ")
        legalForms.forEach { form ->
            s = s.replace(Regex("""(^|\s)${Regex.escape(form)}\.?(\s|$)"""), " ")
        }
        // Drop digits-only tokens (dates, card numbers, terminal ids) and single-char tokens
        s = s.split(Regex("""\s+"""))
            .map { it.trim('.', '-', '\'') }
            .filter { it.isNotBlank() }
            .filter { !it.all { c -> c.isDigit() || c == '.' || c == '-' } }
            .filter { it.length > 1 }
            .filter { it !in noiseWords }
            .joinToString(" ")
            .trim()
        return s.take(80)
    }

    /** Human readable version for display when we only have the raw string. */
    fun display(raw: String?): String = raw?.trim()?.replace(Regex("""\s+"""), " ").orEmpty()
}
