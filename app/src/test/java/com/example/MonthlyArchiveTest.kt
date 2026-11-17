package com.example

import com.example.data.model.AuditLog
import com.example.data.model.Resident
import com.example.data.model.ResidentWithFinancials
import com.example.data.model.Tardiness
import com.example.data.model.Visit
import com.example.util.MonthlyArchiveHelper
import com.example.util.MonthlyCloseScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MonthlyArchiveTest {

    private fun millisFor(year: Int, monthZeroBased: Int, day: Int, hour: Int = 12): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `filterRecordsForClose isolates current month visits, tardiness, and audit logs correctly`() {
        val nowMillis = millisFor(2026, Calendar.NOVEMBER, 15, 14)
        val prevMonthMillis = millisFor(2026, Calendar.OCTOBER, 20, 10)
        val twoMonthsAgoMillis = millisFor(2026, Calendar.SEPTEMBER, 5, 9)

        val residents = listOf(
            ResidentWithFinancials(
                resident = Resident(
                    id = 1L,
                    fullName = "Residente Noviembre",
                    registeredDateMillis = nowMillis,
                    lastModifiedDateMillis = nowMillis
                )
            ),
            ResidentWithFinancials(
                resident = Resident(
                    id = 2L,
                    fullName = "Residente Octubre",
                    registeredDateMillis = prevMonthMillis,
                    lastModifiedDateMillis = prevMonthMillis
                )
            )
        )

        val visits = listOf(
            Visit(id = 10L, visitorName = "Visita Nov", building = "Edificio A", apartment = "101", entryTimestampMillis = nowMillis),
            Visit(id = 11L, visitorName = "Visita Oct", building = "Edificio A", apartment = "102", entryTimestampMillis = prevMonthMillis)
        )

        val tardiness = listOf(
            Tardiness(id = 20L, residentId = 1L, residentName = "A", houseNumber = "101", timestampMillis = nowMillis, reason = "R1", registeredBy = "Admin"),
            Tardiness(id = 21L, residentId = 2L, residentName = "B", houseNumber = "102", timestampMillis = prevMonthMillis, reason = "R2", registeredBy = "Admin"),
            Tardiness(id = 22L, residentId = 2L, residentName = "B", houseNumber = "102", timestampMillis = twoMonthsAgoMillis, reason = "R3", registeredBy = "Admin")
        )

        val auditLogs = listOf(
            AuditLog(id = 30L, userName = "Admin", userRole = "Admin", actionType = "CREAR", entityType = "Visita", entityId = 10L, title = "Nov", details = "D1", timestampMillis = nowMillis),
            AuditLog(id = 31L, userName = "Admin", userRole = "Admin", actionType = "CREAR", entityType = "Visita", entityId = 11L, title = "Oct", details = "D2", timestampMillis = prevMonthMillis)
        )

        val currentRes = MonthlyArchiveHelper.filterRecordsForClose(
            scope = MonthlyCloseScope.CURRENT_MONTH,
            includeResidents = true,
            includeAllResidentsForReload = false,
            allResidents = residents,
            allVisits = visits,
            allTardiness = tardiness,
            allAuditLogs = auditLogs,
            nowMillis = nowMillis
        )

        assertEquals(listOf(1L), currentRes.residents.map { it.resident.id })
        assertEquals(listOf(10L), currentRes.visits.map { it.id })
        assertEquals(listOf(20L), currentRes.tardiness.map { it.id })
        assertEquals(listOf(30L), currentRes.auditLogs.map { it.id })
        assertEquals(3, currentRes.totalDeletableCount)

        // When includeAllResidentsForReload = true, all residents are exported & queued for reload cleanup
        val currentWithAllResidents = MonthlyArchiveHelper.filterRecordsForClose(
            scope = MonthlyCloseScope.CURRENT_MONTH,
            includeResidents = true,
            includeAllResidentsForReload = true,
            allResidents = residents,
            allVisits = visits,
            allTardiness = tardiness,
            allAuditLogs = auditLogs,
            nowMillis = nowMillis
        )
        assertEquals(listOf(1L, 2L), currentWithAllResidents.residents.map { it.resident.id })
        assertEquals(4, currentWithAllResidents.totalDeletableCount)

        // When includeResidents = false, residents list is empty even if includeAllResidentsForReload is true
        val withoutResidents = MonthlyArchiveHelper.filterRecordsForClose(
            scope = MonthlyCloseScope.CURRENT_MONTH,
            includeResidents = false,
            includeAllResidentsForReload = true,
            allResidents = residents,
            allVisits = visits,
            allTardiness = tardiness,
            allAuditLogs = auditLogs,
            nowMillis = nowMillis
        )
        assertTrue(withoutResidents.residents.isEmpty())
        assertEquals(2, withoutResidents.totalDeletableCount)
    }

    @Test
    fun `filterRecordsForClose supports PREVIOUS_MONTH and ALL_RECORDS scopes`() {
        val nowMillis = millisFor(2026, Calendar.NOVEMBER, 15, 14)
        val prevMonthMillis = millisFor(2026, Calendar.OCTOBER, 20, 10)

        val residents = listOf(
            ResidentWithFinancials(Resident(id = 1L, fullName = "Nov", registeredDateMillis = nowMillis, lastModifiedDateMillis = nowMillis)),
            ResidentWithFinancials(Resident(id = 2L, fullName = "Oct", registeredDateMillis = prevMonthMillis, lastModifiedDateMillis = prevMonthMillis))
        )
        val visits = listOf(
            Visit(id = 10L, visitorName = "Nov", building = "Edificio A", apartment = "1", entryTimestampMillis = nowMillis),
            Visit(id = 11L, visitorName = "Oct", building = "Edificio A", apartment = "2", entryTimestampMillis = prevMonthMillis)
        )
        val tardiness = listOf(
            Tardiness(id = 20L, residentId = 1L, residentName = "Nov", houseNumber = "1", timestampMillis = nowMillis, reason = "A", registeredBy = "Admin"),
            Tardiness(id = 21L, residentId = 2L, residentName = "Oct", houseNumber = "2", timestampMillis = prevMonthMillis, reason = "B", registeredBy = "Admin")
        )
        val auditLogs = listOf(
            AuditLog(id = 30L, userName = "Admin", userRole = "Admin", actionType = "A", entityType = "X", entityId = 1L, title = "Nov", details = "D1", timestampMillis = nowMillis),
            AuditLog(id = 31L, userName = "Admin", userRole = "Admin", actionType = "B", entityType = "X", entityId = 2L, title = "Oct", details = "D2", timestampMillis = prevMonthMillis)
        )

        val previousRes = MonthlyArchiveHelper.filterRecordsForClose(
            scope = MonthlyCloseScope.PREVIOUS_MONTH,
            includeResidents = true,
            includeAllResidentsForReload = false,
            allResidents = residents,
            allVisits = visits,
            allTardiness = tardiness,
            allAuditLogs = auditLogs,
            nowMillis = nowMillis
        )
        assertEquals(listOf(2L), previousRes.residents.map { it.resident.id })
        assertEquals(listOf(11L), previousRes.visits.map { it.id })
        assertEquals(listOf(21L), previousRes.tardiness.map { it.id })
        assertEquals(listOf(31L), previousRes.auditLogs.map { it.id })

        val allRes = MonthlyArchiveHelper.filterRecordsForClose(
            scope = MonthlyCloseScope.ALL_RECORDS,
            includeResidents = true,
            includeAllResidentsForReload = false,
            allResidents = residents,
            allVisits = visits,
            allTardiness = tardiness,
            allAuditLogs = auditLogs,
            nowMillis = nowMillis
        )
        assertEquals(2, allRes.residents.size)
        assertEquals(2, allRes.visits.size)
        assertEquals(2, allRes.tardiness.size)
        assertEquals(2, allRes.auditLogs.size)
        assertEquals("Todo el historial activo", allRes.periodLabel)
    }
}
