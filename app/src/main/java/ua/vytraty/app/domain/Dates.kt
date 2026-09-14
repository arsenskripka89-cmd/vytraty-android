package ua.vytraty.app.domain

import ua.vytraty.app.data.db.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val uk = Locale("uk", "UA")
    private val dayFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", uk)
    private val dayShortFmt = DateTimeFormatter.ofPattern("d MMM", uk)
    private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", uk)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", uk)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", uk)

    fun toLocalDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun toLocalDateTime(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
    fun toMillis(d: LocalDateTime): Long = d.atZone(zone).toInstant().toEpochMilli()
    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endOfDay(d: LocalDate): Long = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun monthRange(ym: YearMonth): LongRange = startOfDay(ym.atDay(1))..endOfDay(ym.atEndOfMonth())
    fun monthKey(ym: YearMonth): String = "%04d-%02d".format(ym.year, ym.monthValue)
    fun currentMonthKey(): String = monthKey(YearMonth.now())

    fun formatDay(millis: Long): String {
        val d = toLocalDate(millis)
        val today = LocalDate.now()
        return when (d) {
            today -> "Сьогодні"
            today.minusDays(1) -> "Вчора"
            else -> d.format(dayFmt)
        }
    }
    fun formatDayShort(d: LocalDate): String = d.format(dayShortFmt)
    fun formatMonth(ym: YearMonth): String = ym.format(monthFmt).replaceFirstChar { it.titlecase(uk) }
    fun formatTime(millis: Long): String = toLocalDateTime(millis).format(timeFmt)
    fun formatDateTime(millis: Long): String = toLocalDateTime(millis).format(dateTimeFmt)
    fun formatDate(millis: Long): String = toLocalDate(millis).format(dayFmt)

    /** Next occurrence strictly after [from] for a plan whose last due date was [due]. */
    fun nextDue(due: Long, recurrence: Recurrence, from: Long = System.currentTimeMillis()): Long? {
        if (recurrence == Recurrence.NONE) return null
        val origin = toLocalDateTime(due)
        val fromDt = toLocalDateTime(from)
        var n = 1L
        var next = origin
        // Always step from the original date so "31st of month" is preserved across short months.
        while (!next.isAfter(fromDt) && n < 5000) {
            next = when (recurrence) {
                Recurrence.WEEKLY -> origin.plusWeeks(n)
                Recurrence.MONTHLY -> origin.plusMonths(n)
                Recurrence.YEARLY -> origin.plusYears(n)
                Recurrence.NONE -> return null
            }
            n++
        }
        return toMillis(next)
    }
}
