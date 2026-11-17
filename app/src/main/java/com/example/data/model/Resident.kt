package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "residents")
data class Resident(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fullName: String,
    val houseNumber: String = "", // e.g. "Casa 104" or legacy text
    val building: String = "",    // EDIFICIO (e.g. "Edificio A", "Torre 1")
    val apartment: String = "",   // DEPARTAMENTO (e.g. "101", "302")
    val accessCode: String = "",  // CLAVE (e.g. "CLV-4821")
    val blockOrSection: String = "", // e.g. "Manzana 3" or "Etapa 1"
    val phone: String = "",
    val email: String = "",
    val vehiclePlates: String = "", // e.g. "XYZ-789-A"
    val notes: String = "",
    val isMoroso: Boolean = false, // false = Sí paga (Al corriente), true = Moroso (Falta de pago)
    val isActive: Boolean = true,
    val registeredDateMillis: Long = System.currentTimeMillis(),
    val createdBy: String = "Carlos Mendoza (Admin)",
    val lastModifiedBy: String = "Carlos Mendoza (Admin)",
    val lastModifiedDateMillis: Long = System.currentTimeMillis()
) {
    val displayUnit: String
        get() = when {
            building.isNotBlank() && apartment.isNotBlank() -> "$building - Depto $apartment"
            building.isNotBlank() -> building
            apartment.isNotBlank() -> "Depto $apartment"
            houseNumber.isNotBlank() -> houseNumber
            else -> "Unidad #$id"
        }
}
