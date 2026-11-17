package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VisitAccessStatus(val label: String) {
    PERMITIDO("Acceso Permitido"),
    SIN_CLAVE_MOROSO("Sin Clave (Moroso)"),
    ACCESO_DESEADO("Acceso Deseado"),
    AUTORIZADO_ADMIN("Autorizado por Administración")
}

@Entity(tableName = "visits")
data class Visit(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val visitorName: String,
    val visitorIdOrIne: String = "",
    val visitorVehiclePlates: String = "",
    val building: String, // EDIFICIO
    val apartment: String, // DEPARTAMENTO
    val residentId: Long = 0L,
    val residentName: String = "",
    val reason: String = "Visita general",
    val isResidentMoroso: Boolean = false,
    val accessStatus: VisitAccessStatus = VisitAccessStatus.PERMITIDO,
    val accessCodeUsed: String = "", // CLAVE
    val entryTimestampMillis: Long = System.currentTimeMillis(),
    val exitTimestampMillis: Long? = null,
    val registeredBy: String = "",
    val notes: String = ""
) {
    val isInside: Boolean get() = exitTimestampMillis == null
}
