package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampMillis: Long = System.currentTimeMillis(),
    val userName: String,
    val userRole: String,
    val actionType: String, // "CREAR", "MODIFICAR", "PAGO", "RETARDO", "CANCELAR"
    val entityType: String, // "Residente", "Adeudo", "Pago", "Retardo"
    val entityId: Long,
    val title: String,
    val details: String
)
