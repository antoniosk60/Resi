package com.example.ui.model

enum class ResidentStatusFilter(val label: String) {
    TODOS("Todos"),
    AL_CORRIENTE("Al Corriente"),
    MOROSOS("Morosos"),
    INACTIVOS("Inactivos")
}

data class ResidentFilterState(
    val searchQuery: String = "",
    val statusFilter: ResidentStatusFilter = ResidentStatusFilter.TODOS,
    val dateRegisteredFromMillis: Long? = null,
    val dateRegisteredToMillis: Long? = null,
    val minDebtAmount: Double? = null,
    val maxDebtAmount: Double? = null,
    val isAdvancedPanelOpen: Boolean = false
) {
    val isAnyFilterApplied: Boolean
        get() = statusFilter != ResidentStatusFilter.TODOS ||
                dateRegisteredFromMillis != null ||
                dateRegisteredToMillis != null ||
                minDebtAmount != null ||
                maxDebtAmount != null ||
                searchQuery.isNotBlank()
}

enum class DebtStatusFilter(val label: String) {
    TODOS("Todos"),
    PENDIENTES("Pendientes"),
    VENCIDOS_MOROSOS("Vencidos (Morosos)"),
    PARCIALES("Abonos Parciales"),
    PAGADOS("Pagados")
}

data class DebtFilterState(
    val searchQuery: String = "",
    val statusFilter: DebtStatusFilter = DebtStatusFilter.TODOS,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val dueDateFromMillis: Long? = null,
    val dueDateToMillis: Long? = null,
    val residentIdFilter: Long? = null,
    val isAdvancedPanelOpen: Boolean = false
) {
    val isAnyFilterApplied: Boolean
        get() = statusFilter != DebtStatusFilter.TODOS ||
                minAmount != null ||
                maxAmount != null ||
                dueDateFromMillis != null ||
                dueDateToMillis != null ||
                residentIdFilter != null ||
                searchQuery.isNotBlank()
}
