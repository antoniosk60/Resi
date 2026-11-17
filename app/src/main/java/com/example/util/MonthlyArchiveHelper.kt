package com.example.util

import com.example.data.model.AuditLog
import com.example.data.model.ResidentWithFinancials
import com.example.data.model.Tardiness
import com.example.data.model.Visit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class MonthlyCloseScope {
    CURRENT_MONTH,
    PREVIOUS_MONTH,
    ALL_RECORDS
}

data class MonthlyArchiveFilterResult(
    val periodLabel: String,
    val residents: List<ResidentWithFinancials>,
    val visits: List<Visit>,
    val tardiness: List<Tardiness>,
    val auditLogs: List<AuditLog>
) {
    val totalDeletableCount: Int
        get() = residents.size + visits.size + tardiness.size
}

object MonthlyArchiveHelper {

    private val spanishLocale = Locale("es", "MX")

    fun getMonthBounds(monthsAgo: Int, nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val startCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.MONTH, -monthsAgo)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            timeInMillis = startCal.timeInMillis
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }

    fun getMonthLabel(monthsAgo: Int, nowMillis: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.MONTH, -monthsAgo)
        }
        val formatter = SimpleDateFormat("MMMM yyyy", spanishLocale)
        val raw = formatter.format(Date(cal.timeInMillis))
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(spanishLocale) else it.toString() }
    }

    fun filterRecordsForClose(
        scope: MonthlyCloseScope,
        includeResidents: Boolean,
        includeAllResidentsForReload: Boolean,
        allResidents: List<ResidentWithFinancials>,
        allVisits: List<Visit>,
        allTardiness: List<Tardiness>,
        allAuditLogs: List<AuditLog>,
        nowMillis: Long = System.currentTimeMillis()
    ): MonthlyArchiveFilterResult {
        val (startMillis, endMillis, label) = when (scope) {
            MonthlyCloseScope.CURRENT_MONTH -> {
                val (s, e) = getMonthBounds(0, nowMillis)
                Triple(s, e, getMonthLabel(0, nowMillis))
            }
            MonthlyCloseScope.PREVIOUS_MONTH -> {
                val (s, e) = getMonthBounds(1, nowMillis)
                Triple(s, e, getMonthLabel(1, nowMillis))
            }
            MonthlyCloseScope.ALL_RECORDS -> {
                Triple(0L, Long.MAX_VALUE, "Todo el historial activo")
            }
        }

        val visits = if (scope == MonthlyCloseScope.ALL_RECORDS) {
            allVisits
        } else {
            allVisits.filter { it.entryTimestampMillis in startMillis..endMillis }
        }

        val tardiness = if (scope == MonthlyCloseScope.ALL_RECORDS) {
            allTardiness
        } else {
            allTardiness.filter { it.timestampMillis in startMillis..endMillis }
        }

        val auditLogs = if (scope == MonthlyCloseScope.ALL_RECORDS) {
            allAuditLogs
        } else {
            allAuditLogs.filter { it.timestampMillis in startMillis..endMillis }
        }

        val residents = when {
            !includeResidents -> emptyList()
            scope == MonthlyCloseScope.ALL_RECORDS || includeAllResidentsForReload -> allResidents
            else -> allResidents.filter { rf ->
                val r = rf.resident
                r.registeredDateMillis in startMillis..endMillis ||
                    r.lastModifiedDateMillis in startMillis..endMillis
            }
        }

        return MonthlyArchiveFilterResult(
            periodLabel = label,
            residents = residents,
            visits = visits,
            tardiness = tardiness,
            auditLogs = auditLogs
        )
    }
}
