package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.model.AppUser
import com.example.data.model.AuditLog
import com.example.data.model.Debt
import com.example.data.model.Payment
import com.example.data.model.PaymentMethod
import com.example.data.model.Resident
import com.example.data.model.ResidentWithFinancials
import com.example.data.model.Tardiness
import com.example.data.model.Visit
import com.example.data.model.VisitAccessStatus
import com.example.util.ExcelResidentRow
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

data class BulkImportSummary(
    val totalProcessed: Int,
    val newInserted: Int,
    val updatedExisting: Int,
    val errors: List<String> = emptyList()
)

data class MonthlyPurgeSummary(
    val deletedResidents: Int,
    val deletedVisits: Int,
    val deletedTardiness: Int,
    val deletedLegacyDebts: Int
) {
    val totalDeleted: Int
        get() = deletedResidents + deletedVisits + deletedTardiness
}

data class SyncHealthState(
    val isFromCache: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val lastSyncedMillis: Long = System.currentTimeMillis()
)

class FirestoreResiRepository(
    context: Context,
    private val injectedFirestore: FirebaseFirestore? = null
) {
    private val tag = "FirestoreResiRepo"
    private val appContext = context.applicationContext
    private val idGenerator = AtomicLong(System.currentTimeMillis() * 100L)

    private val _syncHealth = MutableStateFlow(SyncHealthState())
    val syncHealth: StateFlow<SyncHealthState> = _syncHealth.asStateFlow()

    private val firestore: FirebaseFirestore by lazy {
        injectedFirestore ?: FirestoreProvider.getInstance(appContext)
    }

    suspend fun triggerManualSync(): Boolean {
        return try {
            withTimeout(6_000L) {
                firestore.collection("visits").limit(1).get(Source.SERVER).await()
                firestore.collection("residents").limit(1).get(Source.SERVER).await()
            }
            _syncHealth.value = _syncHealth.value.copy(
                isFromCache = false,
                lastSyncedMillis = System.currentTimeMillis()
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(tag, "Manual server sync unavailable, offline cache active: ${e.message}")
            false
        }
    }

    private val residentsCollection = firestore.collection("residents")
    private val debtsCollection = firestore.collection("debts")
    private val paymentsCollection = firestore.collection("payments")
    private val tardinessCollection = firestore.collection("tardiness")
    private val auditLogsCollection = firestore.collection("audit_logs")
    private val visitsCollection = firestore.collection("visits")

    // =========================================================================
    // REAL-TIME FLOWS (Multi-Device Cloud Sync)
    // =========================================================================

    val residentsFlow: Flow<List<Resident>> = callbackFlow {
        val listener = residentsCollection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "residents")
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                _syncHealth.value = SyncHealthState(
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                    lastSyncedMillis = if (!snapshot.metadata.isFromCache) System.currentTimeMillis() else _syncHealth.value.lastSyncedMillis
                )
            }
            val list = snapshot?.documents?.mapNotNull { it.toResident() }
                ?.sortedBy { it.fullName } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    val debtsFlow: Flow<List<Debt>> = callbackFlow {
        val listener = debtsCollection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "debts")
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.toDebt() }
                ?.sortedByDescending { it.createdAtMillis } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    val paymentsFlow: Flow<List<Payment>> = callbackFlow {
        val listener = paymentsCollection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "payments")
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.toPayment() }
                ?.sortedByDescending { it.paymentDateMillis } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    val tardinessFlow: Flow<List<Tardiness>> = callbackFlow {
        val listener = tardinessCollection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "tardiness")
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.toTardiness() }
                ?.sortedByDescending { it.timestampMillis } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    val auditLogsFlow: Flow<List<AuditLog>> = callbackFlow {
        val listener = auditLogsCollection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "audit_logs")
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.toAuditLog() }
                ?.sortedByDescending { it.timestampMillis } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    val visitsFlow: Flow<List<Visit>> = callbackFlow {
        val listener = visitsCollection.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "visits")
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                _syncHealth.value = _syncHealth.value.copy(
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites() || _syncHealth.value.hasPendingWrites,
                    lastSyncedMillis = if (!snapshot.metadata.isFromCache) System.currentTimeMillis() else _syncHealth.value.lastSyncedMillis
                )
            }
            val list = snapshot?.documents?.mapNotNull { it.toVisit() }
                ?.sortedByDescending { it.entryTimestampMillis } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    val residentsWithFinancialsFlow: Flow<List<ResidentWithFinancials>> =
        combine(residentsFlow, debtsFlow, paymentsFlow, tardinessFlow) { residents, debts, payments, tardiness ->
            val now = System.currentTimeMillis()
            residents.map { res ->
                val resDebts = debts.filter { it.residentId == res.id && !it.isCanceled }
                val resPayments = payments.filter { it.residentId == res.id }
                val resTardiness = tardiness.filter { it.residentId == res.id }

                val totalDebts = resDebts.sumOf { it.amount }
                val totalPaid = resDebts.sumOf { it.paidAmount }
                val balance = maxOf(0.0, totalDebts - totalPaid)

                val overdueDebts = resDebts.filter { it.isOverdue(now) }
                val overdueDebtsCount = overdueDebts.size
                val overdueAmount = overdueDebts.sumOf { it.remainingBalance }
                val isMoroso = res.isMoroso || (overdueDebtsCount > 0 && overdueAmount > 0.001)

                val activeDebtsCount = resDebts.count { !it.isPaid }
                val lastPayment = resPayments.maxByOrNull { it.paymentDateMillis }?.paymentDateMillis

                ResidentWithFinancials(
                    resident = res,
                    totalDebtsAmount = totalDebts,
                    totalPaidAmount = totalPaid,
                    pendingBalance = balance,
                    overdueDebtsCount = overdueDebtsCount,
                    overdueAmount = overdueAmount,
                    isMoroso = isMoroso,
                    activeDebtsCount = activeDebtsCount,
                    tardinessCount = resTardiness.size,
                    lastPaymentDateMillis = lastPayment
                )
            }
        }

    // =========================================================================
    // RESIDENTS OPERATIONS (Synced to Firestore)
    // =========================================================================

    private suspend fun <T> commitWithOfflineQueue(
        task: Task<T>,
        operationType: OperationType,
        path: String
    ): Boolean {
        return try {
            val completed = withTimeoutOrNull(3_500L) {
                task.await()
                true
            }
            completed == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, operationType, path)
            throw e
        }
    }

    private fun Resident.sanitizedForRules(): Resident {
        return copy(
            fullName = fullName.trim().ifBlank { "Residente ${apartment.ifBlank { "#$id" }}" }.take(160),
            building = building.trim().take(80),
            apartment = apartment.trim().take(80),
            accessCode = accessCode.trim().uppercase().take(64),
            houseNumber = houseNumber.trim().take(120),
            blockOrSection = blockOrSection.trim().take(80),
            phone = phone.trim().take(40),
            email = email.trim().take(120),
            vehiclePlates = vehiclePlates.trim().uppercase().take(60),
            notes = notes.trim().take(500)
        )
    }

    suspend fun insertResident(resident: Resident, currentUser: AppUser): Long {
        val newId = if (resident.id > 0L) resident.id else generateUniqueId()
        val toSave = resident.copy(id = newId).sanitizedForRules()
        val path = "residents/res_$newId"
        val docRef = residentsCollection.document("res_$newId")
        commitWithOfflineQueue(
            docRef.set(toSave.toMap(), SetOptions.merge()),
            OperationType.CREATE,
            path
        )

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "CREAR",
                entityType = "Residente",
                entityId = newId,
                title = "Registro de residente en la nube",
                details = "Alta en Firestore de ${toSave.fullName} (${toSave.houseNumber})"
            )
        )
        return newId
    }

    suspend fun updateResident(resident: Resident, currentUser: AppUser) {
        val updated = resident.copy(
            lastModifiedBy = "${currentUser.name} (${currentUser.role.displayName})",
            lastModifiedDateMillis = System.currentTimeMillis()
        ).sanitizedForRules()
        val path = "residents/res_${resident.id}"
        val docRef = residentsCollection.document("res_${resident.id}")
        commitWithOfflineQueue(
            docRef.set(updated.toMap(), SetOptions.merge()),
            OperationType.UPDATE,
            path
        )

        if (!updated.isMoroso) {
            clearLegacyDebtsForResident(updated.id)
        }

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "MODIFICAR",
                entityType = "Residente",
                entityId = updated.id,
                title = "Modificación de residente",
                details = "Actualización en la nube para ${updated.fullName} (${updated.houseNumber})"
            )
        )
    }

    suspend fun updateResidentMorosoStatus(residentId: Long, isMoroso: Boolean, currentUser: AppUser) {
        val path = "residents/res_$residentId"
        val docRef = residentsCollection.document("res_$residentId")
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "isMoroso" to isMoroso,
            "lastModifiedBy" to "${currentUser.name} (${currentUser.role.displayName})",
            "lastModifiedDateMillis" to now
        )
        commitWithOfflineQueue(
            docRef.set(updates, SetOptions.merge()),
            OperationType.UPDATE,
            path
        )

        if (!isMoroso) {
            clearLegacyDebtsForResident(residentId)
        }

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "ACTUALIZAR",
                entityType = "Residente",
                entityId = residentId,
                title = if (isMoroso) "Marcado como MOROSO (Acceso Denegado)" else "Marcado como AL CORRIENTE (Sí paga)",
                details = "Estatus de morosidad cambiado a ${if (isMoroso) "MOROSO" else "AL CORRIENTE"} para residente #$residentId"
            )
        )
    }

    private suspend fun clearLegacyDebtsForResident(residentId: Long) {
        try {
            val snap = debtsCollection.whereEqualTo("residentId", residentId).get().await()
            if (!snap.isEmpty) {
                val batch = firestore.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not clear legacy debts for resident $residentId: ${e.message}")
        }
    }

    suspend fun deleteResident(resident: Resident, currentUser: AppUser) {
        residentsCollection.document("res_${resident.id}").delete().await()

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "ELIMINAR",
                entityType = "Residente",
                entityId = resident.id,
                title = "Eliminación de residente",
                details = "Se eliminó de Firestore a ${resident.fullName} (${resident.houseNumber})"
            )
        )
    }

    suspend fun bulkImportResidents(
        rows: List<ExcelResidentRow>,
        currentUser: AppUser
    ): BulkImportSummary {
        val snapshot = residentsCollection.get().await()
        val existingResidents = snapshot.documents.mapNotNull { it.toResident() }

        var insertedCount = 0
        var updatedCount = 0
        val errors = ArrayList<String>()

        var batch = firestore.batch()
        var batchCount = 0
        val now = System.currentTimeMillis()

        for (row in rows) {
            try {
                // Find match by Building and Apartment OR HouseNumber
                val match = existingResidents.find { ex ->
                    val sameBldgAndApt = ex.building.isNotBlank() && row.building.isNotBlank() &&
                            ex.building.equals(row.building, ignoreCase = true) &&
                            ex.apartment.equals(row.apartment, ignoreCase = true)
                    val sameAptOnly = ex.apartment.isNotBlank() && row.apartment.isNotBlank() &&
                            ex.apartment.equals(row.apartment, ignoreCase = true) &&
                            (ex.building.isBlank() || row.building.isBlank() || ex.building.equals(row.building, ignoreCase = true))
                    val sameHouse = ex.houseNumber.isNotBlank() && row.apartment.isNotBlank() &&
                            (ex.houseNumber.equals(row.apartment, ignoreCase = true) ||
                             ex.houseNumber.equals("${row.building} Depto ${row.apartment}", ignoreCase = true) ||
                             ex.houseNumber.equals("${row.building} - Depto ${row.apartment}", ignoreCase = true))

                    sameBldgAndApt || sameAptOnly || sameHouse
                }

                if (match != null) {
                    // Update existing resident's key and details without losing historical data
                    val updated = match.copy(
                        building = if (row.building.isNotBlank()) row.building else match.building,
                        apartment = if (row.apartment.isNotBlank()) row.apartment else match.apartment,
                        accessCode = if (row.accessCode.isNotBlank()) row.accessCode else match.accessCode,
                        fullName = if (row.fullName.isNotBlank() && !row.fullName.startsWith("Residente Depto") && !row.fullName.startsWith("Residente Fila")) row.fullName else match.fullName,
                        phone = if (row.phone.isNotBlank()) row.phone else match.phone,
                        email = if (row.email.isNotBlank()) row.email else match.email,
                        vehiclePlates = if (row.vehiclePlates.isNotBlank()) row.vehiclePlates else match.vehiclePlates,
                        isMoroso = if (row.isMoroso) true else match.isMoroso,
                        lastModifiedBy = "${currentUser.name} (${currentUser.role.displayName})",
                        lastModifiedDateMillis = now
                    ).sanitizedForRules()
                    val docRef = residentsCollection.document("res_${match.id}")
                    batch.set(docRef, updated.toMap(), SetOptions.merge())
                    updatedCount++
                } else {
                    // Create new resident with strictly monotonic unique ID
                    val newId = generateUniqueId()
                    val newResident = Resident(
                        id = newId,
                        fullName = row.fullName.ifBlank { "Residente Depto ${row.apartment}" },
                        houseNumber = if (row.building.isNotBlank()) "${row.building} Depto ${row.apartment}" else "Depto ${row.apartment}",
                        building = row.building,
                        apartment = row.apartment,
                        accessCode = row.accessCode,
                        phone = row.phone,
                        email = row.email,
                        vehiclePlates = row.vehiclePlates,
                        notes = row.notes,
                        isMoroso = row.isMoroso,
                        registeredDateMillis = now,
                        createdBy = "${currentUser.name} (${currentUser.role.displayName})",
                        lastModifiedBy = "${currentUser.name} (${currentUser.role.displayName})",
                        lastModifiedDateMillis = now
                    ).sanitizedForRules()
                    val docRef = residentsCollection.document("res_$newId")
                    batch.set(docRef, newResident.toMap(), SetOptions.merge())
                    insertedCount++
                }
                batchCount++

                if (batchCount >= 450) {
                    batch.commit().await()
                    batch = firestore.batch()
                    batchCount = 0
                }
            } catch (e: Exception) {
                Log.e(tag, "Error importing row ${row.rowIndex}: ${e.message}", e)
                errors.add("Fila ${row.rowIndex}: ${e.message}")
            }
        }

        if (batchCount > 0) {
            batch.commit().await()
        }

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "IMPORTAR",
                entityType = "Residente",
                entityId = 0L,
                title = "Importación masiva Excel de Claves",
                details = "Se procesaron ${rows.size} registros ($insertedCount nuevos, $updatedCount actualizados con clave) en Gavilán 181"
            )
        )

        return BulkImportSummary(
            totalProcessed = rows.size,
            newInserted = insertedCount,
            updatedExisting = updatedCount,
            errors = errors
        )
    }

    // =========================================================================
    // DEBTS OPERATIONS (Synced to Firestore)
    // =========================================================================

    suspend fun insertDebt(debt: Debt, currentUser: AppUser): Long {
        val newId = if (debt.id > 0L) debt.id else generateUniqueId()
        val toSave = debt.copy(id = newId)
        debtsCollection.document("debt_$newId").set(toSave.toMap(), SetOptions.merge()).await()

        val resident = getResidentById(debt.residentId)
        val residentInfo = resident?.let { "${it.fullName} (${it.houseNumber})" } ?: "Residente #${debt.residentId}"

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "CREAR",
                entityType = "Adeudo",
                entityId = newId,
                title = "Generación de adeudo",
                details = "Adeudo '${debt.concept}' por $${"%.2f".format(debt.amount)} asignado a $residentInfo"
            )
        )
        return newId
    }

    suspend fun updateDebt(debt: Debt, currentUser: AppUser) {
        val updated = debt.copy(
            lastModifiedBy = "${currentUser.name} (${currentUser.role.displayName})",
            lastModifiedDateMillis = System.currentTimeMillis()
        )
        debtsCollection.document("debt_${debt.id}").set(updated.toMap(), SetOptions.merge()).await()

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "MODIFICAR",
                entityType = "Adeudo",
                entityId = debt.id,
                title = "Modificación de adeudo",
                details = "Edición en adeudo '${debt.concept}' - Importe: $${"%.2f".format(debt.amount)}"
            )
        )
    }

    suspend fun cancelDebt(debt: Debt, reason: String, currentUser: AppUser) {
        val canceled = debt.copy(
            isCanceled = true,
            notes = if (debt.notes.isBlank()) "Cancelado: $reason" else "${debt.notes} | Cancelado: $reason",
            lastModifiedBy = "${currentUser.name} (${currentUser.role.displayName})",
            lastModifiedDateMillis = System.currentTimeMillis()
        )
        debtsCollection.document("debt_${debt.id}").set(canceled.toMap(), SetOptions.merge()).await()

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "CANCELAR",
                entityType = "Adeudo",
                entityId = debt.id,
                title = "Cancelación de adeudo",
                details = "Cancelado '${debt.concept}' de $${"%.2f".format(debt.amount)}. Motivo: $reason"
            )
        )
    }

    // =========================================================================
    // PAYMENTS OPERATIONS (Synced to Firestore)
    // =========================================================================

    suspend fun registerPayment(
        residentId: Long,
        debtId: Long?,
        amount: Double,
        paymentMethod: PaymentMethod,
        receiptNumber: String,
        notes: String,
        paymentDateMillis: Long,
        currentUser: AppUser
    ): Long {
        val now = System.currentTimeMillis()
        val paymentId = generateUniqueId()

        // 1. If linked to a specific debt, update debt's paidAmount in Firestore
        if (debtId != null) {
            val debtDoc = debtsCollection.document("debt_$debtId").get().await()
            val existingDebt = debtDoc.toDebt()
            if (existingDebt != null) {
                val newPaidAmount = existingDebt.paidAmount + amount
                val updatedDebt = existingDebt.copy(
                    paidAmount = newPaidAmount,
                    lastModifiedBy = "${currentUser.name} (${currentUser.role.displayName})",
                    lastModifiedDateMillis = now
                )
                debtsCollection.document("debt_$debtId").set(updatedDebt.toMap(), SetOptions.merge()).await()
            }
        }

        // 2. Save payment record
        val payment = Payment(
            id = paymentId,
            residentId = residentId,
            debtId = debtId,
            amount = amount,
            paymentDateMillis = paymentDateMillis,
            paymentMethod = paymentMethod,
            receiptNumber = receiptNumber.ifBlank { "REC-${System.currentTimeMillis() % 100000}" },
            notes = notes,
            registeredBy = "${currentUser.name} (${currentUser.role.displayName})",
            registeredAtMillis = now
        )
        paymentsCollection.document("pay_$paymentId").set(payment.toMap(), SetOptions.merge()).await()

        // 3. Log payment in audit
        val resident = getResidentById(residentId)
        val residentDesc = resident?.let { "${it.fullName} (${it.houseNumber})" } ?: "Residente #$residentId"

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "PAGO",
                entityType = "Pago",
                entityId = paymentId,
                title = "Registro de pago (${paymentMethod.label})",
                details = "Monto $${"%.2f".format(amount)} recibido de $residentDesc. Folio: ${payment.receiptNumber}"
            )
        )
        return paymentId
    }

    // =========================================================================
    // TARDINESS OPERATIONS (Synced to Firestore)
    // =========================================================================

    suspend fun insertTardiness(tardiness: Tardiness, currentUser: AppUser): Long {
        val newId = if (tardiness.id > 0L) tardiness.id else generateUniqueId()
        val toSave = tardiness.copy(
            id = newId,
            residentName = tardiness.residentName.trim().ifBlank { "Residente #$newId" }.take(160),
            houseNumber = tardiness.houseNumber.trim().take(120),
            reason = tardiness.reason.trim().ifBlank { "Incidencia registrada en caseta" }.take(300),
            registeredBy = tardiness.registeredBy.trim().ifBlank { currentUser.name }.take(120)
        )
        val path = "tardiness/tard_$newId"
        commitWithOfflineQueue(
            tardinessCollection.document("tard_$newId").set(toSave.toMap(), SetOptions.merge()),
            OperationType.CREATE,
            path
        )

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "RETARDO",
                entityType = "Retardo",
                entityId = newId,
                title = "Registro de retardo / incidencia",
                details = "Retardo registrado para ${toSave.residentName} (${toSave.houseNumber}). Motivo: ${toSave.reason}"
            )
        )
        return newId
    }

    suspend fun deleteTardiness(tardiness: Tardiness, currentUser: AppUser) {
        val path = "tardiness/tard_${tardiness.id}"
        try {
            tardinessCollection.document("tard_${tardiness.id}").delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "ELIMINAR",
                entityType = "Retardo",
                entityId = tardiness.id,
                title = "Eliminación de retardo",
                details = "Se eliminó el retardo de ${tardiness.residentName} (${tardiness.houseNumber})"
            )
        )
    }

    // =========================================================================
    // VISITS & ACCESS CONTROL OPERATIONS (Synced to Firestore)
    // =========================================================================

    suspend fun insertVisit(visit: Visit, currentUser: AppUser): Long {
        val newId = if (visit.id > 0L) visit.id else generateUniqueId()
        val toSave = visit.copy(
            id = newId,
            visitorName = visit.visitorName.trim().ifBlank { "Visitante" }.take(160),
            building = visit.building.trim().take(80),
            apartment = visit.apartment.trim().take(80)
        )
        val path = "visits/visit_$newId"
        commitWithOfflineQueue(
            visitsCollection.document("visit_$newId").set(toSave.toMap(), SetOptions.merge()),
            OperationType.CREATE,
            path
        )

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "VISITA",
                entityType = "Acceso/Visita",
                entityId = newId,
                title = "Registro de acceso de visita",
                details = "Visita '${toSave.visitorName}' a ${toSave.building} Depto ${toSave.apartment}. Estatus: ${toSave.accessStatus.label}"
            )
        )
        return newId
    }

    suspend fun registerVisitExit(visitId: Long, currentUser: AppUser) {
        val now = System.currentTimeMillis()
        val path = "visits/visit_$visitId"
        val visitDoc = visitsCollection.document("visit_$visitId").get().await()
        val visit = visitDoc.toVisit()
        if (visit != null) {
            val updated = visit.copy(exitTimestampMillis = now)
            commitWithOfflineQueue(
                visitsCollection.document("visit_$visitId").set(updated.toMap(), SetOptions.merge()),
                OperationType.UPDATE,
                path
            )
            insertAuditLog(
                AuditLog(
                    userName = currentUser.name,
                    userRole = currentUser.role.displayName,
                    actionType = "SALIDA",
                    entityType = "Acceso/Visita",
                    entityId = visitId,
                    title = "Registro de salida de visita",
                    details = "Salida confirmada para ${visit.visitorName} (${visit.building} Depto ${visit.apartment})"
                )
            )
        }
    }

    // =========================================================================
    // ADMIN MONTHLY PURGE (Delete selected residents, visits, tardiness in batches)
    // =========================================================================

    suspend fun deleteMonthlyRecords(
        residentIds: Set<Long>,
        visitIds: Set<Long>,
        tardinessIds: Set<Long>,
        periodLabel: String,
        currentUser: AppUser
    ): MonthlyPurgeSummary {
        require(currentUser.isAdmin) { "Solo el Administrador puede eliminar permanentemente los registros del mes." }

        val refsToDelete = ArrayList<DocumentReference>()
        var deletedResidents = 0
        var deletedVisits = 0
        var deletedTardiness = 0
        var deletedLegacyDebts = 0

        if (residentIds.isNotEmpty()) {
            val resSnap = residentsCollection.get().await()
            resSnap.documents.forEach { doc ->
                val idVal = doc.getLong("id")
                if (idVal != null && idVal in residentIds) {
                    refsToDelete.add(doc.reference)
                    deletedResidents++
                }
            }

            val debtSnap = debtsCollection.get().await()
            debtSnap.documents.forEach { doc ->
                val resId = doc.getLong("residentId")
                if (resId != null && resId in residentIds) {
                    refsToDelete.add(doc.reference)
                    deletedLegacyDebts++
                }
            }
        }

        if (visitIds.isNotEmpty()) {
            val visitSnap = visitsCollection.get().await()
            visitSnap.documents.forEach { doc ->
                val idVal = doc.getLong("id")
                if (idVal != null && idVal in visitIds) {
                    refsToDelete.add(doc.reference)
                    deletedVisits++
                }
            }
        }

        if (tardinessIds.isNotEmpty()) {
            val tardSnap = tardinessCollection.get().await()
            tardSnap.documents.forEach { doc ->
                val idVal = doc.getLong("id")
                if (idVal != null && idVal in tardinessIds) {
                    refsToDelete.add(doc.reference)
                    deletedTardiness++
                }
            }
        }

        // Commit deletions in Firestore WriteBatch chunks (max 500 ops per batch)
        refsToDelete.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { ref -> batch.delete(ref) }
            batch.commit().await()
        }

        insertAuditLog(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "CIERRE_MENSUAL",
                entityType = "Sistema",
                entityId = 0L,
                title = "Cierre Mensual: PDF y Borrado Permanente ($periodLabel)",
                details = "Eliminados permanentemente: $deletedResidents residentes/claves, $deletedVisits accesos y $deletedTardiness retardos tras exportar PDF."
            )
        )

        return MonthlyPurgeSummary(
            deletedResidents = deletedResidents,
            deletedVisits = deletedVisits,
            deletedTardiness = deletedTardiness,
            deletedLegacyDebts = deletedLegacyDebts
        )
    }

    // =========================================================================
    // AUDIT LOG HELPER
    // =========================================================================

    suspend fun insertAuditLog(log: AuditLog) {
        val newId = if (log.id > 0L) log.id else generateUniqueId()
        val toSave = log.copy(
            id = newId,
            userName = log.userName.ifBlank { "Sistema" }.take(120),
            actionType = log.actionType.ifBlank { "EVENTO" }.take(40),
            entityType = log.entityType.ifBlank { "Sistema" }.take(60),
            title = log.title.ifBlank { "Movimiento" }.take(180)
        )
        val path = "audit_logs/log_$newId"
        try {
            auditLogsCollection.document("log_$newId").set(toSave.toMap(), SetOptions.merge())
                .addOnFailureListener { err ->
                    handleFirestoreError(err, OperationType.CREATE, path)
                }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun getResidentById(residentId: Long): Resident? {
        return try {
            val doc = residentsCollection.document("res_$residentId").get().await()
            doc.toResident()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "residents/res_$residentId")
            null
        }
    }

    // =========================================================================
    // INITIAL CLOUD SEEDING (Only on verified online server check when DB is fresh)
    // =========================================================================

    suspend fun seedDemoDataIfEmpty() {
        try {
            // Must verify against SERVER so an offline cold-start never overwrites existing cloud data
            val existingResidents = withTimeout(8_000L) {
                residentsCollection.limit(1).get(Source.SERVER).await()
            }
            val existingAudit = withTimeout(8_000L) {
                auditLogsCollection.limit(1).get(Source.SERVER).await()
            }
            if (!existingResidents.isEmpty || !existingAudit.isEmpty) {
                Log.d(tag, "Firestore already initialized or cleared by Admin. Skipping initial seeding.")
                return
            }

            Log.d(tag, "Seeding initial condominium demo data into Firestore...")
            val now = System.currentTimeMillis()
            val dayMillis = 86400000L

            val demoResidents = listOf(
                Resident(
                    id = 1,
                    fullName = "Sofía Garza Ortiz",
                    houseNumber = "Casa 104",
                    building = "Edificio A",
                    apartment = "104",
                    accessCode = "CLV-A104",
                    blockOrSection = "Manzana 1",
                    phone = "55-1234-5678",
                    email = "sofia.garza@email.com",
                    vehiclePlates = "ABC-123-X",
                    notes = "Propietaria habitante",
                    isActive = true,
                    registeredDateMillis = now - 60 * dayMillis,
                    createdBy = "Carlos Mendoza (Administrador)",
                    lastModifiedBy = "Carlos Mendoza (Administrador)"
                ),
                Resident(
                    id = 2,
                    fullName = "Roberto Navarro Silva",
                    houseNumber = "Casa 205",
                    building = "Edificio A",
                    apartment = "205",
                    accessCode = "CLV-A205",
                    blockOrSection = "Manzana 2",
                    phone = "55-9876-5432",
                    email = "roberto.navarro@email.com",
                    vehiclePlates = "DEF-456-Y",
                    notes = "Tiene dos vehículos en cochera",
                    isMoroso = true,
                    isActive = true,
                    registeredDateMillis = now - 90 * dayMillis,
                    createdBy = "Elena Ramos (Capturista)",
                    lastModifiedBy = "Elena Ramos (Capturista)"
                ),
                Resident(
                    id = 3,
                    fullName = "Alejandra Torres Peña",
                    houseNumber = "Torre B - Depto 302",
                    building = "Edificio B",
                    apartment = "302",
                    accessCode = "CLV-B302",
                    blockOrSection = "Etapa 1",
                    phone = "55-5555-4433",
                    email = "atorres@condominio.mx",
                    vehiclePlates = "GHI-789-Z",
                    notes = "Inquilina. Contrato vigente hasta dic 2026",
                    isMoroso = false,
                    isActive = true,
                    registeredDateMillis = now - 35 * dayMillis,
                    createdBy = "Carlos Mendoza (Administrador)",
                    lastModifiedBy = "Carlos Mendoza (Administrador)"
                ),
                Resident(
                    id = 4,
                    fullName = "Javier Morales Cruz",
                    houseNumber = "Casa 310",
                    building = "Edificio B",
                    apartment = "310",
                    accessCode = "CLV-B310",
                    blockOrSection = "Manzana 3",
                    phone = "55-7788-9900",
                    email = "j.morales@empresa.com",
                    vehiclePlates = "JKL-012-A",
                    notes = "Viaja frecuentemente por trabajo",
                    isMoroso = true,
                    isActive = true,
                    registeredDateMillis = now - 120 * dayMillis,
                    createdBy = "Carlos Mendoza (Administrador)",
                    lastModifiedBy = "Elena Ramos (Capturista)"
                ),
                Resident(
                    id = 5,
                    fullName = "Valeria Domínguez Vega",
                    houseNumber = "Torre A - Depto 101",
                    building = "Torre 1",
                    apartment = "101",
                    accessCode = "CLV-T101",
                    blockOrSection = "Etapa 2",
                    phone = "55-3322-1144",
                    email = "valeria.d@condominio.mx",
                    vehiclePlates = "MNO-345-B",
                    notes = "Comité de Vigilancia",
                    isMoroso = false,
                    isActive = true,
                    registeredDateMillis = now - 15 * dayMillis,
                    createdBy = "Elena Ramos (Capturista)",
                    lastModifiedBy = "Elena Ramos (Capturista)"
                )
            )

            demoResidents.forEach { res ->
                residentsCollection.document("res_${res.id}").set(res.toMap()).await()
            }

            val demoTardiness = listOf(
                Tardiness(
                    id = 301,
                    residentId = 2,
                    residentName = "Roberto Navarro Silva",
                    houseNumber = "Casa 205",
                    timestampMillis = now - 8 * dayMillis,
                    reason = "Ingreso vehicular fuera de horario autorizado",
                    vehiclePlate = "DEF-456-Y",
                    registeredBy = "Elena Ramos (Capturista)",
                    notes = "Llegó a las 02:40 AM con música alta en portón"
                ),
                Tardiness(
                    id = 302,
                    residentId = 4,
                    residentName = "Javier Morales Cruz",
                    houseNumber = "Casa 310",
                    timestampMillis = now - 2 * dayMillis,
                    reason = "Demora en entrega de salón de eventos comunal",
                    vehiclePlate = "JKL-012-A",
                    registeredBy = "Carlos Mendoza (Administrador)",
                    notes = "3 horas de retraso en la entrega de llaves"
                )
            )

            demoTardiness.forEach { t ->
                tardinessCollection.document("tard_${t.id}").set(t.toMap()).await()
            }

            val demoAudit = listOf(
                AuditLog(
                    id = 401,
                    timestampMillis = now - 60 * dayMillis,
                    userName = "Carlos Mendoza",
                    userRole = "Administrador",
                    actionType = "CREAR",
                    entityType = "Residente",
                    entityId = 1,
                    title = "Registro inicial de residente",
                    details = "Alta de Sofía Garza Ortiz (Casa 104)"
                ),
                AuditLog(
                    id = 402,
                    timestampMillis = now - 18 * dayMillis,
                    userName = "Caseta Yaotecatl",
                    userRole = "Capturista",
                    actionType = "IMPORTAR",
                    entityType = "Residente",
                    entityId = 1,
                    title = "Carga inicial de claves Gavilán 181",
                    details = "Sincronización de claves de acceso para Edificio A, Edificio B y Torre 1"
                ),
                AuditLog(
                    id = 403,
                    timestampMillis = now - 8 * dayMillis,
                    userName = "Elena Ramos",
                    userRole = "Capturista",
                    actionType = "RETARDO",
                    entityType = "Retardo",
                    entityId = 301,
                    title = "Registro de retardo en caseta",
                    details = "Ingreso nocturno registrado a Roberto Navarro Silva (Casa 205)"
                )
            )

            demoAudit.forEach { a ->
                auditLogsCollection.document("log_${a.id}").set(a.toMap()).await()
            }

            val demoVisits = listOf(
                Visit(
                    id = 501,
                    visitorName = "Marcos Linares",
                    visitorIdOrIne = "INE Term. 4921",
                    visitorVehiclePlates = "PZX-112-C",
                    building = "Edificio A",
                    apartment = "104",
                    residentId = 1,
                    residentName = "Sofía Garza Ortiz",
                    reason = "Visita familiar",
                    isResidentMoroso = false,
                    accessStatus = VisitAccessStatus.PERMITIDO,
                    accessCodeUsed = "CLV-A104",
                    entryTimestampMillis = now - 2 * 3600 * 1000L,
                    registeredBy = "Caseta Yaotecatl (Elena Ramos)",
                    notes = "Ingresó a estacionamiento de visitas cajón 3"
                ),
                Visit(
                    id = 502,
                    visitorName = "Técnico Telmex (Reparación)",
                    visitorIdOrIne = "Gafete TEL-8490",
                    visitorVehiclePlates = "ABC-884-M",
                    building = "Edificio A",
                    apartment = "205",
                    residentId = 2,
                    residentName = "Roberto Navarro Silva",
                    reason = "Servicio de telecomunicaciones",
                    isResidentMoroso = true,
                    accessStatus = VisitAccessStatus.SIN_CLAVE_MOROSO,
                    accessCodeUsed = "Sin Clave",
                    entryTimestampMillis = now - 45 * 60 * 1000L,
                    registeredBy = "Caseta Yaotecatl (Elena Ramos)",
                    notes = "Alerta: Departamento moroso. Acceso deseado condicionado a escolta de caseta."
                ),
                Visit(
                    id = 503,
                    visitorName = "Paquetería Amazon",
                    visitorIdOrIne = "Placas VTR-902-A",
                    visitorVehiclePlates = "VTR-902-A",
                    building = "Edificio B",
                    apartment = "302",
                    residentId = 3,
                    residentName = "Alejandra Torres Peña",
                    reason = "Entrega de paquetería",
                    isResidentMoroso = false,
                    accessStatus = VisitAccessStatus.PERMITIDO,
                    accessCodeUsed = "CLV-B302",
                    entryTimestampMillis = now - 15 * 60 * 1000L,
                    exitTimestampMillis = now - 5 * 60 * 1000L,
                    registeredBy = "Carlos Mendoza (Administrador)",
                    notes = "Entregó paquete en caseta y se retiró"
                )
            )

            demoVisits.forEach { v ->
                visitsCollection.document("visit_${v.id}").set(v.toMap()).await()
            }

            Log.d(tag, "Seeding of Firestore condominium demo data completed successfully!")
        } catch (e: Exception) {
            Log.e(tag, "Error seeding Firestore demo data: ${e.message}", e)
        }
    }

    private fun generateUniqueId(): Long {
        val candidate = System.currentTimeMillis() * 100L
        return idGenerator.updateAndGet { prev -> maxOf(prev + 1L, candidate) }
    }
}

// =============================================================================
// FIRESTORE SERIALIZERS / MAPPERS
// =============================================================================

fun Resident.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "fullName" to fullName,
    "houseNumber" to houseNumber,
    "building" to building,
    "apartment" to apartment,
    "accessCode" to accessCode,
    "blockOrSection" to blockOrSection,
    "phone" to phone,
    "email" to email,
    "vehiclePlates" to vehiclePlates,
    "notes" to notes,
    "isMoroso" to isMoroso,
    "isActive" to isActive,
    "registeredDateMillis" to registeredDateMillis,
    "createdBy" to createdBy,
    "lastModifiedBy" to lastModifiedBy,
    "lastModifiedDateMillis" to lastModifiedDateMillis
)

fun DocumentSnapshot.toResident(): Resident? {
    if (!exists()) return null
    return try {
        Resident(
            id = getLong("id") ?: 0L,
            fullName = getString("fullName") ?: "",
            houseNumber = getString("houseNumber") ?: "",
            building = getString("building") ?: "",
            apartment = getString("apartment") ?: "",
            accessCode = getString("accessCode") ?: "",
            blockOrSection = getString("blockOrSection") ?: "",
            phone = getString("phone") ?: "",
            email = getString("email") ?: "",
            vehiclePlates = getString("vehiclePlates") ?: "",
            notes = getString("notes") ?: "",
            isMoroso = getBoolean("isMoroso") ?: false,
            isActive = getBoolean("isActive") ?: true,
            registeredDateMillis = getLong("registeredDateMillis") ?: System.currentTimeMillis(),
            createdBy = getString("createdBy") ?: "Sistema",
            lastModifiedBy = getString("lastModifiedBy") ?: "Sistema",
            lastModifiedDateMillis = getLong("lastModifiedDateMillis") ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}

fun Visit.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "visitorName" to visitorName,
    "visitorIdOrIne" to visitorIdOrIne,
    "visitorVehiclePlates" to visitorVehiclePlates,
    "building" to building,
    "apartment" to apartment,
    "residentId" to residentId,
    "residentName" to residentName,
    "reason" to reason,
    "isResidentMoroso" to isResidentMoroso,
    "accessStatus" to accessStatus.name,
    "accessCodeUsed" to accessCodeUsed,
    "entryTimestampMillis" to entryTimestampMillis,
    "exitTimestampMillis" to exitTimestampMillis,
    "registeredBy" to registeredBy,
    "notes" to notes
)

fun DocumentSnapshot.toVisit(): Visit? {
    if (!exists()) return null
    return try {
        val statusName = getString("accessStatus") ?: VisitAccessStatus.PERMITIDO.name
        val status = try {
            VisitAccessStatus.valueOf(statusName)
        } catch (e: Exception) {
            VisitAccessStatus.PERMITIDO
        }
        Visit(
            id = getLong("id") ?: 0L,
            visitorName = getString("visitorName") ?: "",
            visitorIdOrIne = getString("visitorIdOrIne") ?: "",
            visitorVehiclePlates = getString("visitorVehiclePlates") ?: "",
            building = getString("building") ?: "",
            apartment = getString("apartment") ?: "",
            residentId = getLong("residentId") ?: 0L,
            residentName = getString("residentName") ?: "",
            reason = getString("reason") ?: "Visita general",
            isResidentMoroso = getBoolean("isResidentMoroso") ?: false,
            accessStatus = status,
            accessCodeUsed = getString("accessCodeUsed") ?: "",
            entryTimestampMillis = getLong("entryTimestampMillis") ?: System.currentTimeMillis(),
            exitTimestampMillis = getLong("exitTimestampMillis"),
            registeredBy = getString("registeredBy") ?: "Caseta",
            notes = getString("notes") ?: ""
        )
    } catch (e: Exception) {
        null
    }
}

fun Debt.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "residentId" to residentId,
    "concept" to concept,
    "amount" to amount,
    "paidAmount" to paidAmount,
    "dueDateMillis" to dueDateMillis,
    "createdAtMillis" to createdAtMillis,
    "createdBy" to createdBy,
    "lastModifiedBy" to lastModifiedBy,
    "lastModifiedDateMillis" to lastModifiedDateMillis,
    "notes" to notes,
    "isCanceled" to isCanceled
)

fun DocumentSnapshot.toDebt(): Debt? {
    if (!exists()) return null
    return try {
        Debt(
            id = getLong("id") ?: 0L,
            residentId = getLong("residentId") ?: 0L,
            concept = getString("concept") ?: "",
            amount = getDouble("amount") ?: (getLong("amount")?.toDouble() ?: 0.0),
            paidAmount = getDouble("paidAmount") ?: (getLong("paidAmount")?.toDouble() ?: 0.0),
            dueDateMillis = getLong("dueDateMillis") ?: System.currentTimeMillis(),
            createdAtMillis = getLong("createdAtMillis") ?: System.currentTimeMillis(),
            createdBy = getString("createdBy") ?: "Sistema",
            lastModifiedBy = getString("lastModifiedBy") ?: "Sistema",
            lastModifiedDateMillis = getLong("lastModifiedDateMillis") ?: System.currentTimeMillis(),
            notes = getString("notes") ?: "",
            isCanceled = getBoolean("isCanceled") ?: false
        )
    } catch (e: Exception) {
        null
    }
}

fun Payment.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "residentId" to residentId,
    "debtId" to debtId,
    "amount" to amount,
    "paymentDateMillis" to paymentDateMillis,
    "paymentMethod" to paymentMethod.name,
    "receiptNumber" to receiptNumber,
    "notes" to notes,
    "registeredBy" to registeredBy,
    "registeredAtMillis" to registeredAtMillis
)

fun DocumentSnapshot.toPayment(): Payment? {
    if (!exists()) return null
    return try {
        val methodName = getString("paymentMethod") ?: PaymentMethod.EFECTIVO.name
        val method = try {
            PaymentMethod.valueOf(methodName)
        } catch (e: Exception) {
            PaymentMethod.EFECTIVO
        }
        Payment(
            id = getLong("id") ?: 0L,
            residentId = getLong("residentId") ?: 0L,
            debtId = getLong("debtId"),
            amount = getDouble("amount") ?: (getLong("amount")?.toDouble() ?: 0.0),
            paymentDateMillis = getLong("paymentDateMillis") ?: System.currentTimeMillis(),
            paymentMethod = method,
            receiptNumber = getString("receiptNumber") ?: "",
            notes = getString("notes") ?: "",
            registeredBy = getString("registeredBy") ?: "Sistema",
            registeredAtMillis = getLong("registeredAtMillis") ?: System.currentTimeMillis()
        )
    } catch (e: Exception) {
        null
    }
}

fun Tardiness.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "residentId" to residentId,
    "residentName" to residentName,
    "houseNumber" to houseNumber,
    "timestampMillis" to timestampMillis,
    "reason" to reason,
    "vehiclePlate" to vehiclePlate,
    "registeredBy" to registeredBy,
    "notes" to notes
)

fun DocumentSnapshot.toTardiness(): Tardiness? {
    if (!exists()) return null
    return try {
        Tardiness(
            id = getLong("id") ?: 0L,
            residentId = getLong("residentId") ?: 0L,
            residentName = getString("residentName") ?: "",
            houseNumber = getString("houseNumber") ?: "",
            timestampMillis = getLong("timestampMillis") ?: System.currentTimeMillis(),
            reason = getString("reason") ?: "",
            vehiclePlate = getString("vehiclePlate") ?: "",
            registeredBy = getString("registeredBy") ?: "Sistema",
            notes = getString("notes") ?: ""
        )
    } catch (e: Exception) {
        null
    }
}

fun AuditLog.toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "timestampMillis" to timestampMillis,
    "userName" to userName,
    "userRole" to userRole,
    "actionType" to actionType,
    "entityType" to entityType,
    "entityId" to entityId,
    "title" to title,
    "details" to details
)

fun DocumentSnapshot.toAuditLog(): AuditLog? {
    if (!exists()) return null
    return try {
        AuditLog(
            id = getLong("id") ?: 0L,
            timestampMillis = getLong("timestampMillis") ?: System.currentTimeMillis(),
            userName = getString("userName") ?: "",
            userRole = getString("userRole") ?: "",
            actionType = getString("actionType") ?: "",
            entityType = getString("entityType") ?: "",
            entityId = getLong("entityId") ?: 0L,
            title = getString("title") ?: "",
            details = getString("details") ?: ""
        )
    } catch (e: Exception) {
        null
    }
}
