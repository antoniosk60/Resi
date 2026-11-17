package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AuthSessionState
import com.example.data.firebase.BulkImportSummary
import com.example.data.firebase.FirebaseAuthManager
import com.example.data.firebase.FirestoreResiRepository
import com.example.data.firebase.SyncHealthState
import com.example.data.firebase.unwrapFirestoreException
import com.example.data.model.AppUser
import com.example.data.model.AuditLog
import com.example.data.model.AuthorizedAccount
import com.example.data.model.Debt
import com.example.data.model.DebtStatus
import com.example.data.model.Payment
import com.example.data.model.PaymentMethod
import com.example.data.model.Resident
import com.example.data.model.ResidentWithFinancials
import com.example.data.model.Tardiness
import com.example.data.model.Visit
import com.example.data.model.VisitAccessStatus
import com.example.ui.model.DebtFilterState
import com.example.ui.model.DebtStatusFilter
import com.example.ui.model.ResidentFilterState
import com.example.ui.model.ResidentStatusFilter
import com.example.util.ExcelImportResult
import com.example.util.ExcelResidentParser
import com.example.util.MonthlyArchiveHelper
import com.example.util.MonthlyCloseScope
import com.example.util.ReportExporter
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppDestination(val title: String) {
    DASHBOARD("Inicio"),
    VISITS("Accesos y Visitas"),
    RESIDENTS("Residentes"),
    TARDINESS("Retardos"),
    AUDIT("Auditoría")
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Firebase Auth and Cloud Firestore live repository
    val authManager = FirebaseAuthManager(application, viewModelScope)
    val firestoreRepository = FirestoreResiRepository(application)

    // Google Auth session state (strictly gated)
    val authSessionState: StateFlow<AuthSessionState> = authManager.authSessionState
    val currentUser: StateFlow<AppUser?> = authManager.currentUser
    val firebaseUser: StateFlow<FirebaseUser?> = authManager.firebaseUser
    val isAuthenticating: StateFlow<Boolean> = authManager.isAuthenticating

    // Authorized Google accounts stream (1 Administrador + up to 5 Capturistas, Admin only)
    val authorizedAccounts: StateFlow<List<AuthorizedAccount>> = authSessionState
        .flatMapLatest { state ->
            if (state is AuthSessionState.Authorized && state.user.isAdmin) {
                authManager.authorizedAccountsFlow.safeFirestoreFlow()
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time cloud sync health across devices
    val syncHealth: StateFlow<SyncHealthState> = firestoreRepository.syncHealth
    val isCloudConnected = MutableStateFlow(true)
    val isSyncing = MutableStateFlow(false)
    val lastSyncTimestampMillis = MutableStateFlow(System.currentTimeMillis())

    private fun <T> Flow<List<T>>.safeFirestoreFlow(): Flow<List<T>> = catch { error ->
        val firestoreError = error.unwrapFirestoreException()
        if (firestoreError == null && error is CancellationException) throw error
        emit(emptyList())
    }

    fun refreshSync() {
        viewModelScope.launch {
            if (currentUser.value == null) return@launch
            isSyncing.value = true
            try {
                val success = firestoreRepository.triggerManualSync()
                isCloudConnected.value = success
                lastSyncTimestampMillis.value = System.currentTimeMillis()
                if (success) {
                    showMessage("Sincronizado en tiempo real: ${allVisits.value.size} accesos y ${allResidents.value.size} departamentos al día con todos los dispositivos.")
                } else {
                    showMessage("Sin conexión al servidor. Caché local activa: tus registros se sincronizarán automáticamente al reconectar.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isCloudConnected.value = false
                showMessage("Caché local activa. Se sincronizará al recuperar red.")
            } finally {
                isSyncing.value = false
            }
        }
    }

    // Active screen navigation
    private val _currentDestination = MutableStateFlow(AppDestination.DASHBOARD)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    // Notification message events (for Snackbars)
    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // Eye-care dark mode toggle
    val isDarkMode = MutableStateFlow(false)

    fun toggleDarkMode() {
        isDarkMode.value = !isDarkMode.value
        showMessage(if (isDarkMode.value) "Modo Oscuro (Descanso Visual) activado" else "Modo Cálido de Día activado")
    }

    // Filter states
    val residentFilter = MutableStateFlow(ResidentFilterState())
    val debtFilter = MutableStateFlow(DebtFilterState())

    // Real-time Cloud Firestore Flows (strictly gated behind AuthSessionState.Authorized)
    val allResidents: StateFlow<List<Resident>> = authSessionState
        .flatMapLatest { state ->
            if (state is AuthSessionState.Authorized) firestoreRepository.residentsFlow.safeFirestoreFlow()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDebts: StateFlow<List<Debt>> = authSessionState
        .flatMapLatest { state ->
            if (state is AuthSessionState.Authorized) firestoreRepository.debtsFlow.safeFirestoreFlow()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPayments: StateFlow<List<Payment>> = authSessionState
        .flatMapLatest { state ->
            if (state is AuthSessionState.Authorized) firestoreRepository.paymentsFlow.safeFirestoreFlow()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTardiness: StateFlow<List<Tardiness>> = authSessionState
        .flatMapLatest { state ->
            if (state is AuthSessionState.Authorized) firestoreRepository.tardinessFlow.safeFirestoreFlow()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuditLogs: StateFlow<List<AuditLog>> = authSessionState
        .flatMapLatest { state ->
            if (state is AuthSessionState.Authorized) firestoreRepository.auditLogsFlow.safeFirestoreFlow()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVisits: StateFlow<List<Visit>> = authSessionState
        .flatMapLatest { state ->
            if (state is AuthSessionState.Authorized) firestoreRepository.visitsFlow.safeFirestoreFlow()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allResidentsWithFinancials: StateFlow<List<ResidentWithFinancials>> =
        authSessionState
            .flatMapLatest { state ->
                if (state is AuthSessionState.Authorized) firestoreRepository.residentsWithFinancialsFlow.safeFirestoreFlow()
                else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Key Lookup & Access control
    val keySearchQuery = MutableStateFlow("")
    val preselectedVisitResidentId = MutableStateFlow<Long?>(null)
    val visitSearchQuery = MutableStateFlow("")
    val visitFilterStatus = MutableStateFlow("TODOS")

    val filteredVisits: StateFlow<List<Visit>> =
        combine(allVisits, visitSearchQuery, visitFilterStatus) { visits, query, statusFilter ->
            visits.filter { v ->
                val matchesText = query.isBlank() ||
                    v.visitorName.contains(query, ignoreCase = true) ||
                    v.building.contains(query, ignoreCase = true) ||
                    v.apartment.contains(query, ignoreCase = true) ||
                    v.residentName.contains(query, ignoreCase = true) ||
                    v.visitorVehiclePlates.contains(query, ignoreCase = true) ||
                    v.visitorIdOrIne.contains(query, ignoreCase = true)

                val matchesStatus = when (statusFilter) {
                    "ACTIVOS" -> v.isInside
                    "PERMITIDOS" -> v.accessStatus == VisitAccessStatus.PERMITIDO
                    "MOROSOS" -> v.isResidentMoroso || v.accessStatus == VisitAccessStatus.SIN_CLAVE_MOROSO
                    else -> true
                }
                matchesText && matchesStatus
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected resident for detail inspection
    val selectedResident = MutableStateFlow<ResidentWithFinancials?>(null)

    // Dialog & UI Visibility states
    val showAddResidentDialog = MutableStateFlow(false)
    val editingResident = MutableStateFlow<Resident?>(null)
    val showAddDebtDialog = MutableStateFlow(false)
    val targetDebtResidentId = MutableStateFlow<Long?>(null)
    val showAddPaymentDialog = MutableStateFlow(false)
    val targetPaymentResidentId = MutableStateFlow<Long?>(null)
    val targetPaymentDebtId = MutableStateFlow<Long?>(null)
    val showAddTardinessDialog = MutableStateFlow(false)
    val showAddVisitDialog = MutableStateFlow(false)
    val showManageAccountsDialog = MutableStateFlow(false)
    val isManagingAccounts = MutableStateFlow(false)
    val showAdminPanelDialog = MutableStateFlow(false)
    val isExportingPdf = MutableStateFlow(false)
    val showMonthlyCloseDialog = MutableStateFlow(false)
    val isProcessingMonthlyClose = MutableStateFlow(false)

    // Excel & CSV Bulk Import states for Claves, Edificios y Deptos
    val isImportingExcel = MutableStateFlow(false)
    val excelImportResult = MutableStateFlow<ExcelImportResult?>(null)
    val showExcelImportDialog = MutableStateFlow(false)
    val excelImportSummary = MutableStateFlow<BulkImportSummary?>(null)

    // Filtered Residents using Advanced Search filters
    val filteredResidents: StateFlow<List<ResidentWithFinancials>> =
        combine(allResidentsWithFinancials, residentFilter) { list, filter ->
            list.filter { rf ->
                val res = rf.resident
                val matchesText = filter.searchQuery.isBlank() ||
                    res.fullName.contains(filter.searchQuery, ignoreCase = true) ||
                    res.houseNumber.contains(filter.searchQuery, ignoreCase = true) ||
                    res.building.contains(filter.searchQuery, ignoreCase = true) ||
                    res.apartment.contains(filter.searchQuery, ignoreCase = true) ||
                    res.accessCode.contains(filter.searchQuery, ignoreCase = true) ||
                    res.blockOrSection.contains(filter.searchQuery, ignoreCase = true) ||
                    res.phone.contains(filter.searchQuery, ignoreCase = true) ||
                    res.vehiclePlates.contains(filter.searchQuery, ignoreCase = true)

                val matchesStatus = when (filter.statusFilter) {
                    ResidentStatusFilter.TODOS -> true
                    ResidentStatusFilter.AL_CORRIENTE -> !rf.isMoroso && res.isActive
                    ResidentStatusFilter.MOROSOS -> rf.isMoroso
                    ResidentStatusFilter.INACTIVOS -> !res.isActive
                }

                val matchesDateFrom = filter.dateRegisteredFromMillis?.let { res.registeredDateMillis >= it } ?: true
                val matchesDateTo = filter.dateRegisteredToMillis?.let { res.registeredDateMillis <= it } ?: true
                val matchesMinDebt = filter.minDebtAmount?.let { rf.pendingBalance >= it } ?: true
                val matchesMaxDebt = filter.maxDebtAmount?.let { rf.pendingBalance <= it } ?: true

                matchesText && matchesStatus && matchesDateFrom && matchesDateTo && matchesMinDebt && matchesMaxDebt
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Debts using Advanced Search filters
    val filteredDebts: StateFlow<List<Debt>> =
        combine(allDebts, allResidents, debtFilter) { debts, residents, filter ->
            val residentMap = residents.associateBy { it.id }
            val now = System.currentTimeMillis()

            debts.filter { d ->
                val resident = residentMap[d.residentId]
                val matchesText = filter.searchQuery.isBlank() ||
                    d.concept.contains(filter.searchQuery, ignoreCase = true) ||
                    (resident?.fullName?.contains(filter.searchQuery, ignoreCase = true) == true) ||
                    (resident?.houseNumber?.contains(filter.searchQuery, ignoreCase = true) == true)

                val dStatus = d.getStatus(now)
                val matchesStatus = when (filter.statusFilter) {
                    DebtStatusFilter.TODOS -> true
                    DebtStatusFilter.PENDIENTES -> dStatus == DebtStatus.PENDIENTE
                    DebtStatusFilter.VENCIDOS_MOROSOS -> dStatus == DebtStatus.VENCIDO
                    DebtStatusFilter.PARCIALES -> dStatus == DebtStatus.PARCIAL
                    DebtStatusFilter.PAGADOS -> dStatus == DebtStatus.PAGADO
                }

                val matchesMinAmount = filter.minAmount?.let { d.amount >= it } ?: true
                val matchesMaxAmount = filter.maxAmount?.let { d.amount <= it } ?: true
                val matchesDateFrom = filter.dueDateFromMillis?.let { d.dueDateMillis >= it } ?: true
                val matchesDateTo = filter.dueDateToMillis?.let { d.dueDateMillis <= it } ?: true
                val matchesResident = filter.residentIdFilter?.let { d.residentId == it } ?: true

                matchesText && matchesStatus && matchesMinAmount && matchesMaxAmount && matchesDateFrom && matchesDateTo && matchesResident
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var hasSeededAfterAuth = false

    init {
        // Seed initial data ONLY after an authorized Google account signs in
        viewModelScope.launch {
            authSessionState.collect { state ->
                if (state is AuthSessionState.Authorized && !hasSeededAfterAuth) {
                    hasSeededAfterAuth = true
                    firestoreRepository.seedDemoDataIfEmpty()
                } else if (state is AuthSessionState.Unauthenticated) {
                    hasSeededAfterAuth = false
                    _currentDestination.value = AppDestination.DASHBOARD
                    showManageAccountsDialog.value = false
                    showAdminPanelDialog.value = false
                    showMonthlyCloseDialog.value = false
                }
            }
        }
    }

    fun setDestination(dest: AppDestination) {
        _currentDestination.value = dest
    }

    // Google-Only Authentication Actions
    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            authManager.signInWithGoogle(activityContext) { success, errorMsg ->
                if (success) {
                    showMessage("Acceso autorizado con Google. Sincronización en la nube activada.")
                } else if (errorMsg != null) {
                    showMessage(errorMsg)
                }
            }
        }
    }

    fun attemptAutoSignIn(activityContext: Context) {
        viewModelScope.launch {
            authManager.attemptAutoSignIn(activityContext)
        }
    }

    fun retryAuthVerification() {
        authManager.retryVerification()
    }

    fun signOut() {
        authManager.signOut()
        showMessage("Sesión de Google cerrada")
    }

    override fun onCleared() {
        super.onCleared()
        authManager.dispose()
    }

    // Admin-only Capturista Whitelist Management (Max 5 Capturistas)
    fun addCapturistaAccount(email: String, displayName: String) {
        viewModelScope.launch {
            isManagingAccounts.value = true
            val result = authManager.addCapturistaAccount(email, displayName)
            isManagingAccounts.value = false
            result.onSuccess { slot ->
                showMessage("Capturista autorizado en espacio $slot/5: ${email.trim().lowercase()}")
            }.onFailure { err ->
                showMessage(err.localizedMessage ?: "No se pudo autorizar la cuenta")
            }
        }
    }

    fun removeCapturistaAccount(account: AuthorizedAccount) {
        viewModelScope.launch {
            isManagingAccounts.value = true
            val result = authManager.removeCapturistaAccount(account)
            isManagingAccounts.value = false
            result.onSuccess {
                showMessage("Acceso revocado para ${account.email}")
            }.onFailure { err ->
                showMessage(err.localizedMessage ?: "No se pudo revocar el acceso")
            }
        }
    }

    fun showMessage(msg: String) {
        viewModelScope.launch {
            _userMessage.emit(msg)
        }
    }

    private fun requireUser(): AppUser? {
        val user = currentUser.value
        if (user == null) {
            showMessage("Inicia sesión con una cuenta de Google autorizada.")
        }
        return user
    }

    // Resident Actions (Cloud Firestore sync)
    fun saveResident(
        id: Long = 0,
        fullName: String,
        houseNumber: String = "",
        building: String = "",
        apartment: String = "",
        accessCode: String = "",
        blockOrSection: String = "",
        phone: String = "",
        email: String = "",
        vehiclePlates: String = "",
        notes: String = "",
        isMoroso: Boolean = false
    ) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            try {
                val resolvedHouseNumber = if (houseNumber.isNotBlank()) houseNumber else "$building - Depto $apartment"
                if (id == 0L) {
                    val newRes = Resident(
                        fullName = fullName.trim(),
                        houseNumber = resolvedHouseNumber.trim(),
                        building = building.trim(),
                        apartment = apartment.trim(),
                        accessCode = accessCode.trim().uppercase(),
                        blockOrSection = blockOrSection.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        vehiclePlates = vehiclePlates.trim().uppercase(),
                        notes = notes.trim(),
                        isMoroso = isMoroso,
                        registeredDateMillis = System.currentTimeMillis(),
                        createdBy = "${user.name} (${user.email})"
                    )
                    firestoreRepository.insertResident(newRes, user)
                    showMessage("Departamento registrado: ${if (isMoroso) "Moroso (Acceso Denegado)" else "Sí paga (Acceso Permitido)"}")
                } else {
                    val existing = firestoreRepository.getResidentById(id)
                        ?: allResidents.value.find { it.id == id }
                        ?: run {
                            showMessage("No se encontró el departamento para actualizar.")
                            return@launch
                        }
                    val updated = existing.copy(
                        fullName = fullName.trim(),
                        houseNumber = resolvedHouseNumber.trim(),
                        building = building.trim(),
                        apartment = apartment.trim(),
                        accessCode = accessCode.trim().uppercase(),
                        blockOrSection = blockOrSection.trim(),
                        phone = phone.trim(),
                        email = email.trim(),
                        vehiclePlates = vehiclePlates.trim().uppercase(),
                        notes = notes.trim(),
                        isMoroso = isMoroso
                    )
                    firestoreRepository.updateResident(updated, user)
                    if (selectedResident.value?.resident?.id == id) {
                        selectedResident.value = selectedResident.value?.copy(resident = updated, isMoroso = isMoroso)
                    }
                    showMessage("Departamento actualizado: ${if (isMoroso) "Moroso (Acceso Denegado)" else "Sí paga (Acceso Permitido)"}")
                }
                showAddResidentDialog.value = false
                editingResident.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("No se pudo guardar el residente: ${e.localizedMessage ?: "error de conexión"}")
            }
        }
    }

    fun toggleResidentMoroso(residentId: Long, currentIsMoroso: Boolean) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            try {
                val newStatus = !currentIsMoroso
                firestoreRepository.updateResidentMorosoStatus(residentId, newStatus, user)
                if (newStatus) {
                    showMessage("Marcado como MOROSO: Acceso denegado por falta de pago")
                } else {
                    showMessage("Marcado como AL CORRIENTE: Sí paga, acceso permitido")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("No se pudo actualizar el estatus de pago: ${e.localizedMessage ?: "revisa tu conexión"}")
            }
        }
    }

    fun deleteResident(resident: Resident) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            if (!user.isAdmin) {
                showMessage("Solo el Administrador puede eliminar residentes.")
                return@launch
            }
            try {
                firestoreRepository.deleteResident(resident, user)
                if (selectedResident.value?.resident?.id == resident.id) {
                    selectedResident.value = null
                }
                showMessage("Residente eliminado de Firestore")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Error al eliminar residente: ${e.localizedMessage ?: "verifica tu conexión"}")
            }
        }
    }

    // Tardiness Actions (Cloud Firestore sync)
    fun registerTardiness(
        residentId: Long,
        reason: String,
        vehiclePlate: String,
        notes: String,
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            try {
                val resident = firestoreRepository.getResidentById(residentId)
                    ?: allResidents.value.find { it.id == residentId }
                    ?: run {
                        showMessage("No se encontró el residente seleccionado.")
                        return@launch
                    }
                val tardiness = Tardiness(
                    residentId = residentId,
                    residentName = resident.fullName,
                    houseNumber = resident.houseNumber,
                    timestampMillis = timestampMillis,
                    reason = reason.trim(),
                    vehiclePlate = vehiclePlate.trim().uppercase(),
                    registeredBy = "${user.name} (${user.email})",
                    notes = notes.trim()
                )
                firestoreRepository.insertTardiness(tardiness, user)
                showAddTardinessDialog.value = false
                showMessage("Retardo registrado y sincronizado en Firestore")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Error al registrar retardo: ${e.localizedMessage ?: "revisa tu conexión"}")
            }
        }
    }

    fun deleteTardiness(tardiness: Tardiness) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            if (!user.isAdmin) {
                showMessage("Solo el Administrador puede eliminar retardos.")
                return@launch
            }
            try {
                firestoreRepository.deleteTardiness(tardiness, user)
                showMessage("Retardo eliminado de la nube")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("Error al eliminar retardo: ${e.localizedMessage ?: "revisa tu conexión"}")
            }
        }
    }

    // Visits and Access Control Actions (Cloud Firestore sync)
    fun registerVisit(
        visitorName: String,
        visitorIdOrIne: String,
        visitorVehiclePlates: String,
        building: String,
        apartment: String,
        residentId: Long,
        residentName: String,
        reason: String,
        notes: String
    ) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            try {
                val residentFinancial = allResidentsWithFinancials.value.find {
                    it.resident.id == residentId ||
                        (it.resident.building.equals(building, ignoreCase = true) &&
                            it.resident.apartment.equals(apartment, ignoreCase = true))
                }

                val isMoroso = residentFinancial?.isMoroso ?: false
                val status = if (isMoroso) VisitAccessStatus.SIN_CLAVE_MOROSO else VisitAccessStatus.PERMITIDO
                val codeUsed = if (isMoroso) "Sin clave (Moroso)" else residentFinancial?.resident?.accessCode?.ifBlank { "Sin clave" } ?: "Sin clave"

                val visit = Visit(
                    visitorName = visitorName.trim(),
                    visitorIdOrIne = visitorIdOrIne.trim(),
                    visitorVehiclePlates = visitorVehiclePlates.trim().uppercase(),
                    building = building.trim(),
                    apartment = apartment.trim(),
                    residentId = residentId,
                    residentName = residentName.ifBlank { residentFinancial?.resident?.fullName ?: "Residente" },
                    reason = reason.trim(),
                    isResidentMoroso = isMoroso,
                    accessStatus = status,
                    accessCodeUsed = codeUsed,
                    entryTimestampMillis = System.currentTimeMillis(),
                    registeredBy = "${user.name} (${user.email})",
                    notes = notes.trim()
                )
                firestoreRepository.insertVisit(visit, user)
                showAddVisitDialog.value = false
                preselectedVisitResidentId.value = null
                if (isMoroso) {
                    showMessage("ACCESO DENEGADO POR FALTA DE PAGO • Incidencia registrada en caseta.")
                } else {
                    showMessage("ACCESO AUTORIZADO • Clave validada: $codeUsed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("No se pudo registrar el acceso: ${e.localizedMessage ?: "revisa tu conexión"}")
            }
        }
    }

    fun registerVisitExit(visitId: Long) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            try {
                firestoreRepository.registerVisitExit(visitId, user)
                showMessage("Salida de visita registrada en caseta")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("No se pudo registrar la salida: ${e.localizedMessage ?: "revisa tu conexión"}")
            }
        }
    }

    // Filter helpers
    fun resetResidentFilters() {
        residentFilter.value = ResidentFilterState()
    }

    fun resetDebtFilters() {
        debtFilter.value = DebtFilterState()
    }

    // =========================================================================
    // EXCEL / CSV IMPORT OPERATIONS (Claves, Edificios, Departamentos)
    // =========================================================================

    fun handleSelectedExcelUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            isImportingExcel.value = true
            try {
                var fileName = "claves_gavilan181.xlsx"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }

                val result = withContext(Dispatchers.IO) {
                    ExcelResidentParser.parseFile(context, uri, fileName)
                }

                excelImportResult.value = result
                showExcelImportDialog.value = true
            } catch (e: Exception) {
                showMessage("Error al leer archivo Excel: ${e.message}")
            } finally {
                isImportingExcel.value = false
            }
        }
    }

    fun confirmExcelImport() {
        val result = excelImportResult.value ?: return
        val user = requireUser() ?: return
        viewModelScope.launch {
            isImportingExcel.value = true
            try {
                val summary = withContext(Dispatchers.IO) {
                    firestoreRepository.bulkImportResidents(result.rows, user)
                }
                excelImportSummary.value = summary
                showExcelImportDialog.value = false
                excelImportResult.value = null
                showMessage(
                    "Se procesaron ${summary.totalProcessed} registros (${summary.newInserted} nuevos departamentos, ${summary.updatedExisting} claves actualizadas) en Gavilán 181."
                )
            } catch (e: Exception) {
                showMessage("Error al sincronizar con Firestore: ${e.message}")
            } finally {
                isImportingExcel.value = false
            }
        }
    }

    fun dismissExcelImport() {
        showExcelImportDialog.value = false
        excelImportResult.value = null
    }

    fun shareSampleTemplate(context: Context) {
        try {
            val file = ExcelResidentParser.generateSampleTemplate(context)
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Plantilla Excel/CSV - Gavilán 181 (Claves y Deptos)")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Plantilla oficial para subir claves, edificios y departamentos a Gavilán 181 (Yaotecatl Seguridad Privada)."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Descargar / Compartir Plantilla"))
        } catch (e: Exception) {
            showMessage("No se pudo compartir la plantilla: ${e.message}")
        }
    }

    // =========================================================================
    // ADMIN-ONLY PDF REPORT EXPORTS (Retardos, Residentes, Accesos, Auditoría)
    // =========================================================================

    fun exportTardinessPdf(
        context: Context,
        filteredList: List<Tardiness>? = null,
        filterSummary: String = "Historial completo"
    ) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            if (!user.isAdmin) {
                showMessage("Solo el Administrador puede exportar reportes PDF.")
                return@launch
            }
            isExportingPdf.value = true
            try {
                val targetList = filteredList ?: allTardiness.value
                val file = withContext(Dispatchers.IO) {
                    ReportExporter.exportTardinessPdf(
                        context = context,
                        tardinessList = targetList,
                        generatedBy = "${user.name} (${user.email})",
                        filterSummary = filterSummary
                    )
                }
                ReportExporter.shareExportedFile(
                    context = context,
                    file = file,
                    mimeType = "application/pdf",
                    title = "Reporte de Retardos (${targetList.size})"
                )
                showMessage("PDF de retardos generado (${targetList.size} registros).")
            } catch (e: Exception) {
                showMessage("Error al generar PDF de retardos: ${e.message}")
            } finally {
                isExportingPdf.value = false
            }
        }
    }

    fun exportResidentsPdf(
        context: Context,
        filteredList: List<ResidentWithFinancials>? = null
    ) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            if (!user.isAdmin) {
                showMessage("Solo el Administrador puede exportar reportes PDF.")
                return@launch
            }
            isExportingPdf.value = true
            try {
                val targetList = filteredList ?: allResidentsWithFinancials.value
                val file = withContext(Dispatchers.IO) {
                    ReportExporter.exportResidentsDirectoryPdf(
                        context = context,
                        residents = targetList,
                        generatedBy = "${user.name} (${user.email})"
                    )
                }
                ReportExporter.shareExportedFile(
                    context = context,
                    file = file,
                    mimeType = "application/pdf",
                    title = "Directorio de Residentes y Claves (${targetList.size})"
                )
                showMessage("PDF de residentes generado (${targetList.size} departamentos).")
            } catch (e: Exception) {
                showMessage("Error al generar PDF de residentes: ${e.message}")
            } finally {
                isExportingPdf.value = false
            }
        }
    }

    fun exportVisitsPdf(
        context: Context,
        filteredList: List<Visit>? = null
    ) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            if (!user.isAdmin) {
                showMessage("Solo el Administrador puede exportar reportes PDF.")
                return@launch
            }
            isExportingPdf.value = true
            try {
                val targetList = filteredList ?: allVisits.value
                val file = withContext(Dispatchers.IO) {
                    ReportExporter.exportVisitsPdf(
                        context = context,
                        visits = targetList,
                        generatedBy = "${user.name} (${user.email})"
                    )
                }
                ReportExporter.shareExportedFile(
                    context = context,
                    file = file,
                    mimeType = "application/pdf",
                    title = "Bitácora de Accesos y Visitas (${targetList.size})"
                )
                showMessage("PDF de accesos generado (${targetList.size} registros).")
            } catch (e: Exception) {
                showMessage("Error al generar PDF de accesos: ${e.message}")
            } finally {
                isExportingPdf.value = false
            }
        }
    }

    fun exportAuditLogsPdf(
        context: Context,
        filteredList: List<AuditLog>? = null
    ) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            if (!user.isAdmin) {
                showMessage("Solo el Administrador puede exportar reportes PDF.")
                return@launch
            }
            isExportingPdf.value = true
            try {
                val targetList = filteredList ?: allAuditLogs.value
                val file = withContext(Dispatchers.IO) {
                    ReportExporter.exportAuditLogsPdf(
                        context = context,
                        auditLogs = targetList,
                        generatedBy = "${user.name} (${user.email})"
                    )
                }
                ReportExporter.shareExportedFile(
                    context = context,
                    file = file,
                    mimeType = "application/pdf",
                    title = "Bitácora de Auditoría (${targetList.size})"
                )
                showMessage("PDF de auditoría generado (${targetList.size} eventos).")
            } catch (e: Exception) {
                showMessage("Error al generar PDF de auditoría: ${e.message}")
            } finally {
                isExportingPdf.value = false
            }
        }
    }

    // =========================================================================
    // ADMIN-ONLY MONTHLY CLOSE: GENERATE FULL PDF + PERMANENTLY DELETE MONTH
    // =========================================================================

    fun exportMonthlyArchiveOnly(
        context: Context,
        scope: MonthlyCloseScope,
        includeResidents: Boolean,
        includeAllResidentsForReload: Boolean
    ) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            if (!user.isAdmin) {
                showMessage("Solo el Administrador puede generar el cierre mensual en PDF.")
                return@launch
            }
            isProcessingMonthlyClose.value = true
            try {
                val filtered = MonthlyArchiveHelper.filterRecordsForClose(
                    scope = scope,
                    includeResidents = includeResidents,
                    includeAllResidentsForReload = includeAllResidentsForReload,
                    allResidents = allResidentsWithFinancials.value,
                    allVisits = allVisits.value,
                    allTardiness = allTardiness.value,
                    allAuditLogs = allAuditLogs.value
                )
                val file = withContext(Dispatchers.IO) {
                    ReportExporter.exportMonthlyArchivePdf(
                        context = context,
                        periodLabel = filtered.periodLabel,
                        residents = filtered.residents,
                        visits = filtered.visits,
                        tardinessList = filtered.tardiness,
                        auditLogs = filtered.auditLogs,
                        generatedBy = "${user.name} (${user.email})"
                    )
                }
                ReportExporter.shareExportedFile(
                    context = context,
                    file = file,
                    mimeType = "application/pdf",
                    title = "Cierre Mensual Gavilán 181 (${filtered.periodLabel})"
                )
                showMessage("PDF completo de ${filtered.periodLabel} generado sin eliminar registros.")
            } catch (e: Exception) {
                showMessage("Error al generar PDF del mes: ${e.message}")
            } finally {
                isProcessingMonthlyClose.value = false
            }
        }
    }

    fun executeMonthlyArchiveAndDelete(
        context: Context,
        scope: MonthlyCloseScope,
        includeResidents: Boolean,
        includeAllResidentsForReload: Boolean
    ) {
        viewModelScope.launch {
            val user = requireUser() ?: return@launch
            if (!user.isAdmin) {
                showMessage("Solo el Administrador puede eliminar permanentemente los registros del mes.")
                return@launch
            }
            isProcessingMonthlyClose.value = true
            try {
                val filtered = MonthlyArchiveHelper.filterRecordsForClose(
                    scope = scope,
                    includeResidents = includeResidents,
                    includeAllResidentsForReload = includeAllResidentsForReload,
                    allResidents = allResidentsWithFinancials.value,
                    allVisits = allVisits.value,
                    allTardiness = allTardiness.value,
                    allAuditLogs = allAuditLogs.value
                )

                if (filtered.totalDeletableCount == 0 && filtered.auditLogs.isEmpty()) {
                    showMessage("No hay registros en ${filtered.periodLabel} para exportar o eliminar.")
                    return@launch
                }

                // 1. Always generate and verify the PDF archive BEFORE deleting anything
                val pdfFile = withContext(Dispatchers.IO) {
                    val generated = ReportExporter.exportMonthlyArchivePdf(
                        context = context,
                        periodLabel = filtered.periodLabel,
                        residents = filtered.residents,
                        visits = filtered.visits,
                        tardinessList = filtered.tardiness,
                        auditLogs = filtered.auditLogs,
                        generatedBy = "${user.name} (${user.email})"
                    )
                    require(generated.exists() && generated.length() > 0L) {
                        "No se pudo verificar el archivo PDF generado."
                    }
                    generated
                }

                // 2. Permanently delete matched records from Cloud Firestore
                val residentIds = filtered.residents.map { it.resident.id }.toSet()
                val visitIds = filtered.visits.map { it.id }.toSet()
                val tardinessIds = filtered.tardiness.map { it.id }.toSet()

                val summary = withContext(Dispatchers.IO) {
                    firestoreRepository.deleteMonthlyRecords(
                        residentIds = residentIds,
                        visitIds = visitIds,
                        tardinessIds = tardinessIds,
                        periodLabel = filtered.periodLabel,
                        currentUser = user
                    )
                }

                if (selectedResident.value?.resident?.id in residentIds) {
                    selectedResident.value = null
                }
                showMonthlyCloseDialog.value = false

                // 3. Open Share / Save sheet so Admin can store the PDF immediately
                ReportExporter.shareExportedFile(
                    context = context,
                    file = pdfFile,
                    mimeType = "application/pdf",
                    title = "Cierre Mensual Gavilán 181 (${filtered.periodLabel})"
                )

                showMessage(
                    "PDF generado y eliminados permanentemente ${summary.deletedResidents} residentes, ${summary.deletedVisits} accesos y ${summary.deletedTardiness} retardos. Listo para cargar el Excel del mes entrante."
                )
            } catch (e: Exception) {
                showMessage("Error en cierre mensual: ${e.message}")
            } finally {
                isProcessingMonthlyClose.value = false
            }
        }
    }
}
