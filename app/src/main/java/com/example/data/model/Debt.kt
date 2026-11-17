package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DebtStatus(val label: String) {
    PENDIENTE("Pendiente"),
    PARCIAL("Abono Parcial"),
    PAGADO("Pagado"),
    VENCIDO("Vencido (Moroso)")
}

@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = Resident::class,
            parentColumns = ["id"],
            childColumns = ["residentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["residentId"])]
)
data class Debt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val residentId: Long,
    val concept: String,
    val amount: Double,
    val paidAmount: Double = 0.0,
    val dueDateMillis: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val createdBy: String,
    val lastModifiedBy: String = createdBy,
    val lastModifiedDateMillis: Long = createdAtMillis,
    val notes: String = "",
    val isCanceled: Boolean = false
) {
    val remainingBalance: Double
        get() = if (isCanceled) 0.0 else maxOf(0.0, amount - paidAmount)

    val isPaid: Boolean
        get() = !isCanceled && remainingBalance <= 0.001

    fun isOverdue(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return !isCanceled && !isPaid && currentTimeMillis > dueDateMillis
    }

    fun getStatus(currentTimeMillis: Long = System.currentTimeMillis()): DebtStatus {
        return when {
            isCanceled -> DebtStatus.PAGADO
            isPaid -> DebtStatus.PAGADO
            isOverdue(currentTimeMillis) -> DebtStatus.VENCIDO
            paidAmount > 0.001 -> DebtStatus.PARCIAL
            else -> DebtStatus.PENDIENTE
        }
    }
}
