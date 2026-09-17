package ua.vytraty.app.domain.parser

import ua.vytraty.app.data.db.CaptureRuleEntity
import ua.vytraty.app.data.db.RuleType

/**
 * Decides whether a user rule applies to a notification: the notification must come from the app the
 * rule names (null = any app) and its text must contain the rule's identifier.
 *
 * The identifier is plain text, not a regex — it is typed by hand, so "•••• 4498", "Privat EUR" or
 * "Rayf UAH" all work as written.
 */
object RuleMatcher {

    fun matches(rule: CaptureRuleEntity, packageName: String, title: String?, text: String?): Boolean {
        val pattern = rule.pattern.trim()
        if (pattern.isEmpty()) return false
        if (rule.bank != null && BankSource.byPackage(packageName)?.name != rule.bank) return false
        return haystack(title, text).contains(normalize(pattern), ignoreCase = true)
    }

    /** The most specific rule of [type] wins: the longest identifier that still matches. */
    fun best(
        rules: List<CaptureRuleEntity>,
        type: RuleType,
        packageName: String,
        title: String?,
        text: String?,
    ): CaptureRuleEntity? = rules
        .filter { it.type == type && matches(it, packageName, title, text) }
        .maxByOrNull { it.pattern.trim().length }

    private fun haystack(title: String?, text: String?) = normalize("${title.orEmpty()}\n${text.orEmpty()}")

    /** Non-breaking spaces and the various bullet characters are not worth failing a match over. */
    private fun normalize(s: String) = s
        .replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
        .replace(Regex("""[•·*]"""), "")
        .replace(Regex(""" {2,}"""), " ")
}
