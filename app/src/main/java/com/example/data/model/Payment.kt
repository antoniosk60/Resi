package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaymentMethod(val label: String) {
    EFECTIVO("Efectivo"),
    TRANSFERENCIA("Transferencia SPEI"),
    CHEQUE("Cheque"),
    DEPOSITO_BANCARIO("Depósito Bancario")
}

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Resident::class,
            parentColumns = ["id"],
            childColumns = ["residentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Debt::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["residentId"]),
        Index(value = ["debtId"])
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val residentId: Long,
    val debtId: Long? = null,
    val amount: Double,
    val paymentDateMillis: Long = System.currentTimeMillis(),
    val paymentMethod: PaymentMethod = PaymentMethod.EFECTIVO,
    val receiptNumber: String = "", // e.g. "REC-2026-0012" o "SPEI-948210"
    val notes: String = "",
    val registeredBy: String,
    val registeredAtMillis: Long = System.currentTimeMillis()
)
