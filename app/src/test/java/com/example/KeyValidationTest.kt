package com.example

import com.example.data.model.Resident
import com.example.data.model.ResidentWithFinancials
import com.example.ui.components.KeyLookupResult
import com.example.ui.components.evaluateKeyLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyValidationTest {

    private val solventeResident = ResidentWithFinancials(
        resident = Resident(
            id = 1L,
            fullName = "Carlos Mendoza",
            building = "Edificio A",
            apartment = "101",
            accessCode = "CLV-A101",
            isMoroso = false
        ),
        totalDebtsAmount = 0.0,
        totalPaidAmount = 0.0,
        overdueDebtsCount = 0,
        overdueAmount = 0.0
    )

    private val morosoResident = ResidentWithFinancials(
        resident = Resident(
            id = 2L,
            fullName = "Sofía Garza",
            building = "Edificio A",
            apartment = "205",
            accessCode = "CLV-A205",
            isMoroso = true
        ),
        totalDebtsAmount = 0.0,
        totalPaidAmount = 0.0,
        overdueDebtsCount = 0,
        overdueAmount = 0.0
    )

    private val list = listOf(solventeResident, morosoResident)

    @Test
    fun `valid solvente key returns Authorized with building and apartment`() {
        val result = evaluateKeyLookup("clv-a101", list, forceShowNotFound = false)
        assertTrue(result is KeyLookupResult.Authorized)
        val authorized = result as KeyLookupResult.Authorized
        assertEquals("Edificio A", authorized.residentWithFinancials.resident.building)
        assertEquals("101", authorized.residentWithFinancials.resident.apartment)
    }

    @Test
    fun `valid key without hyphen still matches normalized code`() {
        val result = evaluateKeyLookup("CLVA101", list, forceShowNotFound = false)
        assertTrue(result is KeyLookupResult.Authorized)
    }

    @Test
    fun `moroso key returns DeniedNonPayment`() {
        val result = evaluateKeyLookup("CLV-A205", list, forceShowNotFound = false)
        assertTrue(result is KeyLookupResult.DeniedNonPayment)
        assertEquals(2L, (result as KeyLookupResult.DeniedNonPayment).residentId)
    }

    @Test
    fun `unknown key returns NotFound when submitted or length is at least 3`() {
        val result = evaluateKeyLookup("XYZ-999", list, forceShowNotFound = true)
        assertTrue(result is KeyLookupResult.NotFound)
    }

    @Test
    fun `blank key returns Idle`() {
        val result = evaluateKeyLookup("   ", list, forceShowNotFound = true)
        assertEquals(KeyLookupResult.Idle, result)
    }
}
