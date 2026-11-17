package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Visit
import com.example.data.model.VisitAccessStatus
import com.example.ui.components.AuthorizedKeyResultCard
import com.example.ui.components.DeniedNonPaymentResultCard
import com.example.ui.components.KeyLookupAndValidationPanel
import com.example.ui.components.formatDateTime
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.StatusMorosoRed
import com.example.ui.theme.StatusMorosoRedBg
import com.example.ui.theme.StatusMorosoRedBgDark
import com.example.ui.theme.StatusMorosoRedDark
import com.example.ui.theme.StatusSolventeGreen
import com.example.ui.theme.StatusSolventeGreenDark
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val visits by viewModel.filteredVisits.collectAsState()
    val allVisits by viewModel.allVisits.collectAsState()
    val residents by viewModel.allResidentsWithFinancials.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin = currentUser?.isAdmin == true
    val keySearchQuery by viewModel.keySearchQuery.collectAsState()
    val searchQuery by viewModel.visitSearchQuery.collectAsState()
    val filterStatus by viewModel.visitFilterStatus.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()

    val context = LocalContext.current
    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleSelectedExcelUri(context, it) }
    }

    // Optional collapsible lookup by EDIFICIO & DEPARTAMENTO
    var showBuildingLookup by remember { mutableStateOf(false) }
    var selectedBuilding by remember { mutableStateOf("") }
    var selectedApartment by remember { mutableStateOf("") }

    val availableBuildings = remember(residents) {
        residents.map { it.resident.building.ifBlank { "Edificio General" } }.distinct().sorted()
    }

    val verifiedResident = remember(selectedBuilding, selectedApartment, residents) {
        if (selectedBuilding.isNotBlank() && selectedApartment.isNotBlank()) {
            residents.find {
                it.resident.building.equals(selectedBuilding, ignoreCase = true) &&
                        it.resident.apartment.equals(selectedApartment, ignoreCase = true)
            }
        } else null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.preselectedVisitResidentId.value = null
                    viewModel.showAddVisitDialog.value = true
                },
                containerColor = BrandNavy,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_visit_fab")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Registrar Visita")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Registrar Acceso", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("visits_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Access Control Title & Excel Shortcut
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BrandNavy,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Control de Accesos • Gavilán 181",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Yaotecatl Seguridad Privada • Validación de Claves",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
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
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("upload_excel_visits_btn")
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Subir Excel (Claves y Deptos)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        TextButton(
                            onClick = { viewModel.shareSampleTemplate(context) }
                        ) {
                            Text(
                                "Descargar plantilla",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { viewModel.exportVisitsPdf(context, visits) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusMorosoRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusMorosoRed.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("visits_export_pdf_button")
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = "Exportar bitácora de accesos en PDF",
                                modifier = Modifier.size(18.dp),
                                tint = StatusMorosoRed
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Exportar Bitácora de Accesos PDF (${visits.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusMorosoRed
                            )
                        }
                    }
                }
            }

            // =================================================================
            // PRIMARY KEY LOOKUP & VISUAL STATUS VALIDATION PANEL
            // =================================================================
            item {
                KeyLookupAndValidationPanel(
                    query = keySearchQuery,
                    onQueryChange = { viewModel.keySearchQuery.value = it },
                    residents = residents,
                    isDark = isDark,
                    onLogAuthorizedVisit = { residentId ->
                        viewModel.preselectedVisitResidentId.value = residentId
                        viewModel.showAddVisitDialog.value = true
                    },
                    onLogDeniedAttempt = { residentId ->
                        viewModel.preselectedVisitResidentId.value = residentId
                        viewModel.showAddVisitDialog.value = true
                    }
                )
            }

            // =================================================================
            // COLLAPSIBLE SECONDARY LOOKUP BY EDIFICIO & DEPARTAMENTO
            // =================================================================
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Apartment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Consulta por Edificio y Depto",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = { showBuildingLookup = !showBuildingLookup },
                                modifier = Modifier.testTag("toggle_building_lookup_button")
                            ) {
                                Text(if (showBuildingLookup) "Ocultar" else "Abrir", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = if (showBuildingLookup) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = showBuildingLookup) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    var bldgDropdownExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = bldgDropdownExpanded,
                                        onExpandedChange = { bldgDropdownExpanded = it },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = selectedBuilding.ifBlank { "Seleccionar" },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("EDIFICIO") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bldgDropdownExpanded) },
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth(),
                                            singleLine = true
                                        )
                                        ExposedDropdownMenu(
                                            expanded = bldgDropdownExpanded,
                                            onDismissRequest = { bldgDropdownExpanded = false }
                                        ) {
                                            availableBuildings.forEach { bldg ->
                                                DropdownMenuItem(
                                                    text = { Text(bldg) },
                                                    onClick = {
                                                        selectedBuilding = bldg
                                                        selectedApartment = ""
                                                        bldgDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    val apartmentsInBuilding = remember(selectedBuilding, residents) {
                                        residents.filter {
                                            it.resident.building.equals(selectedBuilding, ignoreCase = true) ||
                                                    (selectedBuilding == "Edificio General" && it.resident.building.isBlank())
                                        }.map { it.resident.apartment.ifBlank { it.resident.houseNumber } }.distinct().sorted()
                                    }

                                    var aptDropdownExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = aptDropdownExpanded,
                                        onExpandedChange = { aptDropdownExpanded = it },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = selectedApartment.ifBlank { "Seleccionar" },
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("DEPARTAMENTO") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aptDropdownExpanded) },
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth(),
                                            singleLine = true
                                        )
                                        ExposedDropdownMenu(
                                            expanded = aptDropdownExpanded,
                                            onDismissRequest = { aptDropdownExpanded = false }
                                        ) {
                                            apartmentsInBuilding.forEach { apt ->
                                                DropdownMenuItem(
                                                    text = { Text(apt) },
                                                    onClick = {
                                                        selectedApartment = apt
                                                        aptDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (verifiedResident != null) {
                                    if (verifiedResident.isMoroso) {
                                        DeniedNonPaymentResultCard(
                                            isDark = isDark,
                                            onLogDeniedAttempt = {
                                                viewModel.preselectedVisitResidentId.value = verifiedResident.resident.id
                                                viewModel.showAddVisitDialog.value = true
                                            }
                                        )
                                    } else {
                                        AuthorizedKeyResultCard(
                                            rf = verifiedResident,
                                            isDark = isDark,
                                            onLogAuthorizedVisit = {
                                                viewModel.preselectedVisitResidentId.value = verifiedResident.resident.id
                                                viewModel.showAddVisitDialog.value = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar and Quick Status Chips for Registered Visits
            item {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.visitSearchQuery.value = it },
                        placeholder = { Text("Buscar visitante, placas, edificio, depto o residente...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                        trailingIcon = if (searchQuery.isNotBlank()) {
                            {
                                IconButton(onClick = { viewModel.visitSearchQuery.value = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                }
                            }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_visits_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filterOptions = listOf(
                            "TODOS" to "Todos (${allVisits.size})",
                            "ACTIVOS" to "Adentro (${allVisits.count { it.isInside }})",
                            "PERMITIDOS" to "Con Clave",
                            "MOROSOS" to "Acceso Denegado"
                        )
                        filterOptions.forEach { (key, label) ->
                            FilterChip(
                                selected = filterStatus == key,
                                onClick = { viewModel.visitFilterStatus.value = key },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // List of Visits
            if (visits.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No se encontraron registros de visitas",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Usa el botón '+ Registrar Acceso' para capturar la entrada de una visita en caseta.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(visits, key = { it.id }) { visit ->
                    VisitCard(
                        visit = visit,
                        isDark = isDark,
                        onRegisterExitClick = { viewModel.registerVisitExit(visit.id) }
                    )
                }
            }

            // Bottom spacer for FloatingActionButton
            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
fun VisitCard(
    visit: Visit,
    isDark: Boolean = false,
    onRegisterExitClick: () -> Unit
) {
    val isMoroso = visit.isResidentMoroso || visit.accessStatus == VisitAccessStatus.SIN_CLAVE_MOROSO
    val isInside = visit.isInside
    val redAccent = if (isDark) StatusMorosoRedDark else StatusMorosoRed
    val redBg = if (isDark) StatusMorosoRedBgDark else StatusMorosoRedBg
    val greenAccent = if (isDark) StatusSolventeGreenDark else StatusSolventeGreen

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("visit_card_${visit.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMoroso) redBg.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = visit.visitorName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Destino: ${visit.building} • Depto ${visit.apartment}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (visit.residentName.isNotBlank()) {
                        Text(
                            text = "Residente: ${visit.residentName}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge with Visual Indicator Icon
                Column(horizontalAlignment = Alignment.End) {
                    if (isMoroso) {
                        Surface(
                            color = redAccent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Acceso denegado",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Denegado • Falta de pago",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = greenAccent,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Acceso autorizado",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Clave: ${visit.accessCodeUsed.ifBlank { "OK" }}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details row (Vehicle, INE, Entry time)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (visit.visitorVehiclePlates.isNotBlank()) {
                        Text(
                            text = "Placas: ${visit.visitorVehiclePlates}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (visit.visitorIdOrIne.isNotBlank()) {
                        Text(
                            text = "Identificación: ${visit.visitorIdOrIne}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Entrada: ${formatDateTime(visit.entryTimestampMillis)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (visit.exitTimestampMillis != null) {
                        Text(
                            text = "Salida: ${formatDateTime(visit.exitTimestampMillis)}",
                            fontSize = 10.sp,
                            color = greenAccent
                        )
                    }
                }

                if (isInside) {
                    OutlinedButton(
                        onClick = onRegisterExitClick,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("register_exit_button_${visit.id}")
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Registrar Salida", fontSize = 11.sp)
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Concluido",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (visit.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Notas: ${visit.notes}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
