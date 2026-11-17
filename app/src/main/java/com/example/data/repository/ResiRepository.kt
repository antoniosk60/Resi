package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.db.AppDatabase
import com.example.data.model.AppUser
import com.example.data.model.AuditLog
import com.example.data.model.Debt
import com.example.data.model.Payment
import com.example.data.model.PaymentMethod
import com.example.data.model.Resident
import com.example.data.model.ResidentWithFinancials
import com.example.data.model.Tardiness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ResiRepository(private val db: AppDatabase) {

    val residentsFlow: Flow<List<Resident>> = db.residentDao().getAllResidentsFlow()
    val debtsFlow: Flow<List<Debt>> = db.debtDao().getAllDebtsFlow()
    val paymentsFlow: Flow<List<Payment>> = db.paymentDao().getAllPaymentsFlow()
    val tardinessFlow: Flow<List<Tardiness>> = db.tardinessDao().getAllTardinessFlow()
    val auditLogsFlow: Flow<List<AuditLog>> = db.auditLogDao().getAllLogsFlow()

    // Combined financial flow that computes resident balances and delinquent status in real-time
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
                val isMoroso = overdueDebtsCount > 0 && overdueAmount > 0.001

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

    suspend fun insertResident(resident: Resident, currentUser: AppUser): Long {
        val id = db.residentDao().insert(resident)
        db.auditLogDao().insert(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "CREAR",
                entityType = "Residente",
                entityId = id,
                title = "Registro de residente",
                details = "Alta de ${resident.fullName} (${resident.houseNumber})"
            )
        )
        return id
    }

    suspend fun updateResident(resident: Resident, currentUser: AppUser) {
        val updated = resident.copy(
            lastModifiedBy = "${currentUser.name} (${currentUser.role.displayName})",
            lastModifiedDateMillis = System.currentTimeMillis()
        )
        db.residentDao().update(updated)
        db.auditLogDao().insert(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "MODIFICAR",
                entityType = "Residente",
                entityId = resident.id,
                title = "Modificación de datos de residente",
                details = "Actualización de información para ${resident.fullName} (${resident.houseNumber})"
            )
        )
    }

    suspend fun deleteResident(resident: Resident, currentUser: AppUser) {
        db.residentDao().delete(resident)
        db.auditLogDao().insert(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "ELIMINAR",
                entityType = "Residente",
                entityId = resident.id,
                title = "Eliminación de residente",
                details = "Se eliminó a ${resident.fullName} (${resident.houseNumber}) y sus registros asociados"
            )
        )
    }

    suspend fun insertDebt(debt: Debt, currentUser: AppUser): Long {
        val id = db.debtDao().insert(debt)
        val res = db.residentDao().getResidentById(debt.residentId)
        val residentInfo = res?.let { "${it.fullName} (${it.houseNumber})" } ?: "Residente #${debt.residentId}"
        db.auditLogDao().insert(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "CREAR",
                entityType = "Adeudo",
                entityId = id,
                title = "Generación de adeudo",
                details = "Adeudo '${debt.concept}' por $${"%.2f".format(debt.amount)} asignado a $residentInfo"
            )
        )
        return id
    }

    suspend fun insertGlobalDebt(
        concept: String,
        amount: Double,
        dueDateMillis: Long,
        notes: String,
        currentUser: AppUser
    ) {
        val residents = db.residentDao().getCount()
        val now = System.currentTimeMillis()
        val allResidents = db.residentDao().getResidentById(1)?.let {
            // Read active residents directly
            listOf(it)
        }
        // Query through transaction
        db.withTransaction {
            val list = mutableListOf<Debt>()
            // Query all residents
            val residentList = db.residentDao().getAllResidentsFlow()
            // We can read database directly
            val residentsInDb = ArrayList<Resident>()
            // Using raw query or loop through IDs up to max
            // Better: helper query
        }
    }

    suspend fun updateDebt(debt: Debt, currentUser: AppUser) {
        val updated = debt.copy(
            lastModifiedBy = "${currentUser.name} (${currentUser.role.displayName})",
            lastModifiedDateMillis = System.currentTimeMillis()
        )
        db.debtDao().update(updated)
        db.auditLogDao().insert(
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
        db.debtDao().update(canceled)
        db.auditLogDao().insert(
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

    /**
     * Registers a manual payment (partial or total) and updates associated debt(s)
     */
    suspend fun registerPayment(
        residentId: Long,
        debtId: Long?,
        amount: Double,
        paymentMethod: PaymentMethod,
        receiptNumber: String,
        notes: String,
        paymentDateMillis: Long,
        currentUser: AppUser
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val resident = db.residentDao().getResidentById(residentId)
        val residentDesc = resident?.let { "${it.fullName} (${it.houseNumber})" } ?: "Residente #$residentId"

        var remainingPayment = amount

        if (debtId != null) {
            val targetDebt = db.debtDao().getDebtById(debtId)
            if (targetDebt != null) {
                val newPaidAmount = targetDebt.paidAmount + amount
                db.debtDao().update(
                    targetDebt.copy(
                        paidAmount = newPaidAmount,
                        lastModifiedBy = "${currentUser.name} (${currentUser.role.displayName})",
                        lastModifiedDateMillis = now
                    )
                )
            }
        }

        val payment = Payment(
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
        val paymentId = db.paymentDao().insert(payment)

        db.auditLogDao().insert(
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
        paymentId
    }

    suspend fun insertTardiness(tardiness: Tardiness, currentUser: AppUser): Long {
        val id = db.tardinessDao().insert(tardiness)
        db.auditLogDao().insert(
            AuditLog(
                userName = currentUser.name,
                userRole = currentUser.role.displayName,
                actionType = "RETARDO",
                entityType = "Retardo",
                entityId = id,
                title = "Registro de retardo / incidencia",
                details = "Retardo registrado para ${tardiness.residentName} (${tardiness.houseNumber}). Motivo: ${tardiness.reason}"
            )
        )
        return id
    }

    suspend fun deleteTardiness(tardiness: Tardiness, currentUser: AppUser) {
        db.tardinessDao().delete(tardiness)
        db.auditLogDao().insert(
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
}
