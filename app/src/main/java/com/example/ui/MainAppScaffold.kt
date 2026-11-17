package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AddResidentDialog
import com.example.ui.components.AddTardinessDialog
import com.example.ui.components.AddVisitDialog
import com.example.ui.components.AdminPanelDialog
import com.example.ui.components.AuthorizedAccountsDialog
import com.example.ui.components.ExcelImportDialog
import com.example.ui.components.MonthlyCloseDialog
import com.example.ui.components.ResidentDetailDialog
import com.example.ui.screens.AuditLogScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ResidentsScreen
import com.example.ui.screens.TardinessScreen
import com.example.ui.screens.VisitsScreen
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.StatusMorosoRed
import com.example.ui.viewmodel.AppDestination
import com.example.ui.viewmodel.MainViewModel

data class NavItem(
    val destination: AppDestination,
    val icon: ImageVector,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val currentDestination by viewModel.currentDestination.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val authorizedAccounts by viewModel.authorizedAccounts.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val residents by viewModel.allResidentsWithFinancials.collectAsState()
    val rawResidents by viewModel.allResidents.collectAsState()
    val allTardiness by viewModel.allTardiness.collectAsState()
    val allVisits by viewModel.allVisits.collectAsState()
    val allAuditLogs by viewModel.allAuditLogs.collectAsState()

    val morososCount = residents.count { it.isMoroso }
    val capturistasCount = authorizedAccounts.count { !it.role.name.equals("ADMINISTRADOR", ignoreCase = true) }

    // Dialog states
    val showAddResident by viewModel.showAddResidentDialog.collectAsState()
    val editingResident by viewModel.editingResident.collectAsState()
    val showAddTardiness by viewModel.showAddTardinessDialog.collectAsState()
    val showAddVisit by viewModel.showAddVisitDialog.collectAsState()
    val preselectedVisitResidentId by viewModel.preselectedVisitResidentId.collectAsState()
    val showManageAccounts by viewModel.showManageAccountsDialog.collectAsState()
    val isManagingAccounts by viewModel.isManagingAccounts.collectAsState()
    val showAdminPanel by viewModel.showAdminPanelDialog.collectAsState()
    val isExportingPdf by viewModel.isExportingPdf.collectAsState()
    val showMonthlyClose by viewModel.showMonthlyCloseDialog.collectAsState()
    val isProcessingMonthlyClose by viewModel.isProcessingMonthlyClose.collectAsState()
    val selectedResident by viewModel.selectedResident.collectAsState()

    val showExcelImport by viewModel.showExcelImportDialog.collectAsState()
    val excelImportResult by viewModel.excelImportResult.collectAsState()
    val isImportingExcel by viewModel.isImportingExcel.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncHealth by viewModel.syncHealth.collectAsState()

    val syncBadgeColor = when {
        syncHealth.hasPendingWrites || syncHealth.isFromCache -> Color(0xFFF59E0B)
        else -> Color(0xFF30D158)
    }
    val syncBadgeLabel = when {
        isSyncing -> "Sincronizando"
        syncHealth.hasPendingWrites -> "Pendiente"
        syncHealth.isFromCache -> "Caché local"
        else -> "En vivo"
    }

    val context = LocalContext.current
    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleSelectedExcelUri(context, it) }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Android Back button handling: return to dashboard if on sub-screen
    BackHandler(enabled = currentDestination != AppDestination.DASHBOARD) {
        viewModel.setDestination(AppDestination.DASHBOARD)
    }

    val navItems = listOf(
        NavItem(AppDestination.DASHBOARD, Icons.Default.Dashboard, "Inicio"),
        NavItem(AppDestination.VISITS, Icons.Default.Security, "Accesos"),
        NavItem(AppDestination.RESIDENTS, Icons.Default.People, "Residentes"),
        NavItem(AppDestination.TARDINESS, Icons.Default.Schedule, "Retardos"),
        NavItem(AppDestination.AUDIT, Icons.Default.History, "Auditoría")
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = BrandNavy,
                    contentColor = Color.White,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = "GAVILAN 181",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Seguridad Yaotecatl",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 9.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .testTag("rail_sync_status")
                                    .clickable { viewModel.refreshSync() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(9.dp),
                                            color = Color(0xFF64D2FF),
                                            strokeWidth = 1.5.dp
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(syncBadgeColor)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = syncBadgeLabel,
                                        color = Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    navItems.forEach { item ->
                        val isSelected = currentDestination == item.destination
                        NavigationRailItem(
                            selected = isSelected,
                            onClick = { viewModel.setDestination(item.destination) },
                            icon = {
                                if (item.destination == AppDestination.RESIDENTS && morososCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = StatusMorosoRed) {
                                                Text("$morososCount")
                                            }
                                        }
                                    ) {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = { Text(item.label, fontSize = 10.sp) },
                            colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                                selectedIconColor = BrandNavy,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                unselectedTextColor = Color.White.copy(alpha = 0.7f),
                                indicatorColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (currentUser?.isAdmin == true) {
                        IconButton(
                            onClick = { viewModel.showAdminPanelDialog.value = true },
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .testTag("rail_admin_panel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Centro de Administración y PDF",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { viewModel.showManageAccountsDialog.value = true },
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .testTag("rail_manage_google_accounts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ManageAccounts,
                                contentDescription = "Gestionar cuentas Google (1+5)",
                                tint = Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.toggleDarkMode() },
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Descanso Visual",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { viewModel.signOut() },
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("rail_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Cerrar sesión de Google",
                            tint = Color.White
                        )
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.weight(1f)
                ) { contentPadding ->
                    ScreenContent(
                        destination = currentDestination,
                        viewModel = viewModel,
                        modifier = Modifier.padding(contentPadding)
                    )
                }
            }
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "GAVILAN 181",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color.White.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .testTag("appbar_sync_badge")
                                            .clickable { viewModel.refreshSync() }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            if (isSyncing) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(9.dp),
                                                    color = Color(0xFF64D2FF),
                                                    strokeWidth = 1.5.dp
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.dp)
                                                        .clip(CircleShape)
                                                        .background(syncBadgeColor)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = syncBadgeLabel,
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Yaotecatl • ${currentUser?.email ?: ""}",
                                    fontSize = 10.5.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = BrandNavy,
                            titleContentColor = Color.White,
                            actionIconContentColor = Color.White
                        ),
                        actions = {
                            IconButton(
                                onClick = { viewModel.refreshSync() },
                                modifier = Modifier.testTag("manual_sync_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sincronizar ahora con todos los dispositivos",
                                        tint = Color.White
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.toggleDarkMode() },
                                modifier = Modifier.testTag("toggle_eye_comfort_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = if (isDarkMode) "Activar modo día" else "Activar modo oscuro (Descanso visual)",
                                    tint = Color.White
                                )
                            }

                            if (currentUser?.isAdmin == true) {
                                IconButton(
                                    onClick = { viewModel.showAdminPanelDialog.value = true },
                                    modifier = Modifier.testTag("admin_panel_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = "Centro de Administración y PDF",
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.showManageAccountsDialog.value = true },
                                    modifier = Modifier.testTag("manage_google_accounts_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ManageAccounts,
                                        contentDescription = "Gestionar cuentas Google (1 Admin + 5 Capturistas)",
                                        tint = Color.White
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.signOut() },
                                modifier = Modifier.testTag("scaffold_sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Cerrar sesión de Google",
                                    tint = Color.White
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("bottom_nav_bar")
                    ) {
                        val phoneItems = listOf(
                            NavItem(AppDestination.DASHBOARD, Icons.Default.Dashboard, "Inicio"),
                            NavItem(AppDestination.VISITS, Icons.Default.Security, "Accesos"),
                            NavItem(AppDestination.RESIDENTS, Icons.Default.People, "Residentes"),
                            NavItem(AppDestination.TARDINESS, Icons.Default.Schedule, "Retardos"),
                            NavItem(AppDestination.AUDIT, Icons.Default.History, "Auditoría")
                        )

                        phoneItems.forEach { item ->
                            val isSelected = currentDestination == item.destination
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.setDestination(item.destination) },
                                icon = {
                                    if (item.destination == AppDestination.RESIDENTS && morososCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = StatusMorosoRed) {
                                                    Text("$morososCount")
                                                }
                                            }
                                        ) {
                                            Icon(item.icon, contentDescription = item.label)
                                        }
                                    } else {
                                        Icon(item.icon, contentDescription = item.label)
                                    }
                                },
                                label = { Text(item.label, fontSize = 10.sp) },
                                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { contentPadding ->
                ScreenContent(
                    destination = currentDestination,
                    viewModel = viewModel,
                    modifier = Modifier.padding(contentPadding)
                )
            }
        }

        // =====================================================================
        // DIALOGS & OVERLAYS
        // =====================================================================

        if (showAddResident || editingResident != null) {
            AddResidentDialog(
                initialResident = editingResident,
                onDismiss = {
                    viewModel.showAddResidentDialog.value = false
                    viewModel.editingResident.value = null
                },
                onSave = { id, fullName, houseNumber, building, apartment, accessCode, blockOrSection, phone, email, plates, notes, isMoroso ->
                    viewModel.saveResident(id, fullName, houseNumber, building, apartment, accessCode, blockOrSection, phone, email, plates, notes, isMoroso)
                }
            )
        }

        if (showAddVisit) {
            AddVisitDialog(
                residents = residents,
                preselectedResidentId = preselectedVisitResidentId,
                onDismiss = {
                    viewModel.showAddVisitDialog.value = false
                    viewModel.preselectedVisitResidentId.value = null
                },
                onRegisterVisit = { visitorName, visitorIdOrIne, visitorVehiclePlates, building, apartment, residentId, residentName, reason, notes ->
                    viewModel.registerVisit(
                        visitorName = visitorName,
                        visitorIdOrIne = visitorIdOrIne,
                        visitorVehiclePlates = visitorVehiclePlates,
                        building = building,
                        apartment = apartment,
                        residentId = residentId,
                        residentName = residentName,
                        reason = reason,
                        notes = notes
                    )
                }
            )
        }

        if (showAddTardiness) {
            AddTardinessDialog(
                residents = rawResidents,
                onDismiss = { viewModel.showAddTardinessDialog.value = false },
                onRegisterTardiness = { resId, reason, plate, notes ->
                    viewModel.registerTardiness(resId, reason, plate, notes)
                }
            )
        }

        selectedResident?.let { rf ->
            ResidentDetailDialog(
                residentWithFinancials = rf,
                tardiness = allTardiness,
                isAdmin = currentUser?.isAdmin == true,
                onDismiss = { viewModel.selectedResident.value = null },
                onToggleMorosoClick = { resId, currMoroso ->
                    viewModel.toggleResidentMoroso(resId, currMoroso)
                },
                onEditResidentClick = { res ->
                    viewModel.editingResident.value = res
                    viewModel.showAddResidentDialog.value = true
                },
                onDeleteResidentClick = { res ->
                    viewModel.deleteResident(res)
                }
            )
        }

        if (showAdminPanel && currentUser?.isAdmin == true) {
            AdminPanelDialog(
                residents = residents,
                tardinessCount = allTardiness.size,
                visitsCount = allVisits.size,
                auditLogsCount = allAuditLogs.size,
                capturistasCount = capturistasCount,
                isExportingPdf = isExportingPdf,
                onExportTardinessPdf = { viewModel.exportTardinessPdf(context) },
                onExportResidentsPdf = { viewModel.exportResidentsPdf(context) },
                onExportVisitsPdf = { viewModel.exportVisitsPdf(context) },
                onExportAuditPdf = { viewModel.exportAuditLogsPdf(context) },
                onNewResidentClick = {
                    viewModel.editingResident.value = null
                    viewModel.showAddResidentDialog.value = true
                },
                onUploadExcelClick = {
                    excelPickerLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                            "text/csv",
                            "text/comma-separated-values",
                            "text/plain",
                            "*/*"
                        )
                    )
                },
                onEditResidentClick = { res ->
                    viewModel.editingResident.value = res
                    viewModel.showAddResidentDialog.value = true
                },
                onDeleteResidentClick = { res ->
                    viewModel.deleteResident(res)
                },
                onManageAccountsClick = {
                    viewModel.showManageAccountsDialog.value = true
                },
                onMonthlyCloseClick = {
                    viewModel.showMonthlyCloseDialog.value = true
                },
                onDismiss = { viewModel.showAdminPanelDialog.value = false }
            )
        }

        if (showMonthlyClose && currentUser?.isAdmin == true) {
            MonthlyCloseDialog(
                allResidents = residents,
                allVisits = allVisits,
                allTardiness = allTardiness,
                allAuditLogs = allAuditLogs,
                isProcessing = isProcessingMonthlyClose,
                onExportPdfOnly = { scope, includeResidents, includeAllResidentsForReload ->
                    viewModel.exportMonthlyArchiveOnly(
                        context = context,
                        scope = scope,
                        includeResidents = includeResidents,
                        includeAllResidentsForReload = includeAllResidentsForReload
                    )
                },
                onExportAndDeleteMonthly = { scope, includeResidents, includeAllResidentsForReload ->
                    viewModel.executeMonthlyArchiveAndDelete(
                        context = context,
                        scope = scope,
                        includeResidents = includeResidents,
                        includeAllResidentsForReload = includeAllResidentsForReload
                    )
                },
                onDismiss = { viewModel.showMonthlyCloseDialog.value = false }
            )
        }

        if (showManageAccounts && currentUser?.isAdmin == true) {
            AuthorizedAccountsDialog(
                accounts = authorizedAccounts,
                isSaving = isManagingAccounts,
                onAddCapturista = { email, name -> viewModel.addCapturistaAccount(email, name) },
                onRemoveCapturista = { acc -> viewModel.removeCapturistaAccount(acc) },
                onDismiss = { viewModel.showManageAccountsDialog.value = false }
            )
        }

        if (showExcelImport && excelImportResult != null) {
            ExcelImportDialog(
                importResult = excelImportResult!!,
                isImporting = isImportingExcel,
                onConfirmImport = { viewModel.confirmExcelImport() },
                onDownloadTemplate = { viewModel.shareSampleTemplate(context) },
                onDismiss = { viewModel.dismissExcelImport() }
            )
        }
    }
}

@Composable
fun ScreenContent(
    destination: AppDestination,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (destination) {
            AppDestination.DASHBOARD -> DashboardScreen(viewModel = viewModel)
            AppDestination.VISITS -> VisitsScreen(viewModel = viewModel)
            AppDestination.RESIDENTS -> ResidentsScreen(viewModel = viewModel)
            AppDestination.TARDINESS -> TardinessScreen(viewModel = viewModel)
            AppDestination.AUDIT -> AuditLogScreen(viewModel = viewModel)
        }
    }
}
