package com.example.data.model

data class ResidentWithFinancials(
    val resident: Resident,
    val totalDebtsAmount: Double = 0.0,
    val totalPaidAmount: Double = 0.0,
    val pendingBalance: Double = 0.0,
    val overdueDebtsCount: Int = 0,
    val overdueAmount: Double = 0.0,
    val isMoroso: Boolean = resident.isMoroso,
    val activeDebtsCount: Int = 0,
    val tardinessCount: Int = 0,
    val lastPaymentDateMillis: Long? = null
) {
    val accessStatusLabel: String
        get() = if (isMoroso) "ACCESO DENEGADO POR FALTA DE PAGO" else "SÍ PAGA (Al corriente)"

    val displayedAccessCode: String
        get() = if (isMoroso) "ACCESO DENEGADO POR FALTA DE PAGO" else resident.accessCode.ifBlank { "Sin clave asignada" }
}
