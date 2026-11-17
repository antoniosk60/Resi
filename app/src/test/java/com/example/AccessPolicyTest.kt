package com.example

import com.example.data.model.AccessPolicy
import com.example.data.model.AppUser
import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {

    @Test
    fun `access policy enforces 1 admin and max 5 capturistas`() {
        assertEquals("angelfernandoantonioflores4@gmail.com", AccessPolicy.ADMIN_EMAIL)
        assertEquals(5, AccessPolicy.MAX_CAPTURISTAS)
        assertEquals(listOf("1", "2", "3", "4", "5"), AccessPolicy.CAPTURISTA_SLOTS)
    }

    @Test
    fun `app user role helpers accurately distinguish admin and capturista`() {
        val admin = AppUser(
            id = "uid_admin",
            name = "Admin",
            email = AccessPolicy.ADMIN_EMAIL,
            role = UserRole.ADMINISTRADOR
        )
        assertTrue(admin.isAdmin)
        assertFalse(admin.isCapturista)

        val capturista = AppUser(
            id = "uid_cap",
            name = "Capturista 1",
            email = "cap1@gmail.com",
            role = UserRole.CAPTURISTA
        )
        assertFalse(capturista.isAdmin)
        assertTrue(capturista.isCapturista)
    }
}
