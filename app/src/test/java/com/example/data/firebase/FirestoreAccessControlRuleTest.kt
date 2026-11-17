package com.example.data.firebase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AccessPolicy
import com.example.data.model.AppUser
import com.example.data.model.Resident
import com.example.data.model.UserRole
import com.example.data.model.Visit
import com.example.data.model.VisitAccessStatus
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class FirestoreAccessControlRuleTest : FirestoreEmulatorTestBase() {

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private fun adminAppUser(uid: String) = AppUser(
        id = uid,
        name = "Administrador Gavilán",
        email = AccessPolicy.ADMIN_EMAIL,
        role = UserRole.ADMINISTRADOR
    )

    private fun capturistaAppUser(uid: String, email: String) = AppUser(
        id = uid,
        name = "Capturista Caseta",
        email = email,
        role = UserRole.CAPTURISTA
    )

    private suspend fun ensureAdminInitialized(adminUid: String) {
        val now = System.currentTimeMillis()
        withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("authorized_users")
                .document(AccessPolicy.ADMIN_EMAIL)
                .set(
                    mapOf(
                        "email" to AccessPolicy.ADMIN_EMAIL,
                        "displayName" to "Administrador Gavilán",
                        "role" to UserRole.ADMINISTRADOR.name,
                        "slotId" to "0",
                        "uid" to adminUid,
                        "photoUrl" to "",
                        "addedBy" to AccessPolicy.ADMIN_EMAIL,
                        "addedAtMillis" to now,
                        "lastActiveMillis" to now
                    )
                )
                .await()
        }
    }

    private suspend fun resetCapturistaSlotsAsAdmin() {
        withTimeout(DEFAULT_TIMEOUT_MS) {
            val slots = firestore.collection("capturista_slots").get().await()
            val users = firestore.collection("authorized_users").get().await()
            val batch = firestore.batch()
            slots.documents.forEach { batch.delete(it.reference) }
            users.documents.forEach {
                if (it.id != AccessPolicy.ADMIN_EMAIL) {
                    batch.delete(it.reference)
                }
            }
            batch.commit().await()
        }
    }

    private suspend fun authorizeCapturistaAsAdmin(email: String, slotId: String) {
        val now = System.currentTimeMillis()
        withTimeout(DEFAULT_TIMEOUT_MS) {
            val batch = firestore.batch()
            batch.set(
                firestore.collection("capturista_slots").document(slotId),
                mapOf(
                    "email" to email,
                    "slotId" to slotId,
                    "assignedAtMillis" to now
                )
            )
            batch.set(
                firestore.collection("authorized_users").document(email),
                mapOf(
                    "email" to email,
                    "displayName" to "Capturista $slotId",
                    "role" to UserRole.CAPTURISTA.name,
                    "slotId" to slotId,
                    "uid" to "",
                    "photoUrl" to "",
                    "addedBy" to AccessPolicy.ADMIN_EMAIL,
                    "addedAtMillis" to now,
                    "lastActiveMillis" to 0L
                )
            )
            batch.commit().await()
        }
    }

    @Test
    fun unauthenticatedUser_cannotReadResidentsOrAuthorizedUsers() = runBlocking {
        auth.signOut()
        val repository = FirestoreResiRepository(appContext(), firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.residentsFlow.first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated residents listener")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    @Test
    fun unauthorizedGoogleEmail_isRejectedFromResidentsAndAuthorizedUsers() = runBlocking {
        signInTestUser("intruso_${UUID.randomUUID()}@gmail.com")
        val repository = FirestoreResiRepository(appContext(), firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.residentsFlow.first()
            }
            fail("Expected PERMISSION_DENIED for unauthorized Google email")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    @Test
    fun admin_canInitializeSeedAndAuthorizeUpTo5Capturistas() = runBlocking {
        val adminUid = signInTestUser(AccessPolicy.ADMIN_EMAIL)
        ensureAdminInitialized(adminUid)
        resetCapturistaSlotsAsAdmin()

        val repository = FirestoreResiRepository(appContext(), firestore)
        val adminUser = adminAppUser(adminUid)

        val uniqueResId = System.currentTimeMillis()
        val resident = Resident(
            id = uniqueResId,
            fullName = "Sofía Garza Ortiz",
            houseNumber = "Edificio A Depto 104",
            building = "Edificio A",
            apartment = "104",
            accessCode = "CLV-A104",
            isMoroso = false
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.insertResident(resident, adminUser)
        }

        val residents = withTimeout(FLOW_TIMEOUT_MS) {
            repository.residentsFlow.first { list -> list.any { it.id == uniqueResId } }
        }
        assertTrue(residents.any { it.accessCode == "CLV-A104" })

        // Authorize 5 Capturistas
        for (slot in 1..5) {
            authorizeCapturistaAsAdmin("cap$slot@gmail.com", slot.toString())
        }

        // Verify a 6th Capturista with invalid slot "6" is rejected by rules
        try {
            authorizeCapturistaAsAdmin("cap6@gmail.com", "6")
            fail("Expected PERMISSION_DENIED when exceeding 5 Capturistas")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    @Test
    fun authorizedCapturista_canRegisterVisitsAndLookupResidents_butCannotDeleteResident() = runBlocking {
        val adminUid = signInTestUser(AccessPolicy.ADMIN_EMAIL)
        ensureAdminInitialized(adminUid)
        resetCapturistaSlotsAsAdmin()
        val capEmail = "cap_operativo@gmail.com"
        authorizeCapturistaAsAdmin(capEmail, "1")

        val uniqueResId = System.currentTimeMillis() + 10
        val repoAsAdmin = FirestoreResiRepository(appContext(), firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repoAsAdmin.insertResident(
                Resident(
                    id = uniqueResId,
                    fullName = "Roberto Navarro",
                    houseNumber = "Edificio B Depto 205",
                    building = "Edificio B",
                    apartment = "205",
                    accessCode = "CLV-B205",
                    isMoroso = true
                ),
                adminAppUser(adminUid)
            )
        }

        // Now sign in as the authorized Capturista
        val capUid = signInTestUser(capEmail)
        val capUser = capturistaAppUser(capUid, capEmail)
        val repoAsCapturista = FirestoreResiRepository(appContext(), firestore)

        val fetched = withTimeout(FLOW_TIMEOUT_MS) {
            repoAsCapturista.residentsFlow.first { list -> list.any { it.id == uniqueResId } }
        }
        assertTrue(fetched.any { it.id == uniqueResId && it.isMoroso })

        val visitId = System.currentTimeMillis() + 20
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repoAsCapturista.insertVisit(
                Visit(
                    id = visitId,
                    visitorName = "Visitante Test",
                    building = "Edificio B",
                    apartment = "205",
                    residentId = uniqueResId,
                    residentName = "Roberto Navarro",
                    isResidentMoroso = true,
                    accessStatus = VisitAccessStatus.SIN_CLAVE_MOROSO,
                    accessCodeUsed = "Sin clave (Moroso)",
                    registeredBy = capEmail
                ),
                capUser
            )
        }

        val visits = withTimeout(FLOW_TIMEOUT_MS) {
            repoAsCapturista.visitsFlow.first { list -> list.any { it.id == visitId } }
        }
        assertTrue(visits.any { it.id == visitId })

        // Capturista cannot delete resident (Admin only)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("residents").document("res_$uniqueResId").delete().await()
            }
            fail("Expected PERMISSION_DENIED when Capturista attempts to delete resident")
        } catch (e: Exception) {
            val firestoreException = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
