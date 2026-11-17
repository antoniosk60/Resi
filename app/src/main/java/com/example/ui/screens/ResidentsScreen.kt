package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import com.example.data.model.Resident
import com.example.data.model.ResidentWithFinancials
import com.example.ui.components.ResidentStatusBadge
import com.example.ui.components.formatDate
import com.example.ui.model.ResidentStatusFilter
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.StatusMorosoRed
import com.example.ui.theme.StatusMorosoRedBg
import com.example.ui.theme.StatusMorosoRedBgDark
import com.example.ui.theme.StatusMorosoRedDark
import com.example.ui.theme.StatusSolventeGreen
import com.example.ui.theme.StatusSolventeGreenBg
import com.example.ui.theme.StatusSolventeGreenBgDark
import com.example.ui.theme.StatusSolventeGreenDark
import com.example.ui.viewmodel.MainViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResidentsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin = currentUser?.isAdmin == true
    val residents by viewModel.filteredResidents.collectAsState()
    val allResidents by viewModel.allResidentsWithFinancials.collectAsState()
    val filter by viewModel.residentFilter.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()

    var showAdvancedFilters by remember { mutableStateOf(false) }
    var residentPendingDelete by remember { mutableStateOf<Resident?>(null) }

    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.handleSelectedExcelUri(context, it) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.editingResident.value = null
                    viewModel.showAddResidentDialog.value = true
                },
                containerColor = BrandNavy,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_resident_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Registrar Residente")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("residents_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Screen Header & Search Bar
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Gestión de Residentes",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandNavy
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = BrandTeal.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF30D158), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "En vivo",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BrandTeal
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Gavilán 181 • Claves y deptos sincronizados en todos los celulares",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("upload_excel_residents_btn")
                        ) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = BrandNavy
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Subir Excel",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandNavy
                            )
                        }

                        if (isAdmin) {
                            OutlinedButton(
                                onClick = { viewModel.exportResidentsPdf(context, residents) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusMorosoRed),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusMorosoRed.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("residents_export_pdf_button")
                            ) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = "Exportar directorio PDF",
                                    modifier = Modifier.size(18.dp),
                                    tint = StatusMorosoRed
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Exportar PDF",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusMorosoRed
                                )
                            }
                        }
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.showMonthlyCloseDialog.value = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusMorosoRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("residents_monthly_close_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "Cierre mensual PDF y borrado permanente",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cierre de Mes: PDF y Eliminar Registros",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Main Search Input
                    OutlinedTextField(
                        value = filter.searchQuery,
                        onValueChange = {
                            viewModel.residentFilter.value = filter.copy(searchQuery = it)
                        },
                        label = { Text("Buscar por clave, edificio, depto o nombre") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (filter.searchQuery.isNotBlank()) {
                                    IconButton(onClick = {
                                        viewModel.residentFilter.value = filter.copy(searchQuery = "")
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                                    }
                                }
                                BadgedBox(
                                    badge = {
                                        if (filter.isAnyFilterApplied) {
                                            Badge(containerColor = BrandTeal) { Text("!") }
                                        }
                                    }
                                ) {
                                    IconButton(
                                        onClick = { showAdvancedFilters = !showAdvancedFilters },
                                        modifier = Modifier.testTag("toggle_resident_advanced_filters")
                                    ) {
                                        Icon(
                                            Icons.Default.FilterList,
                                            contentDescription = "Filtros Avanzados",
                                            tint = if (showAdvancedFilters) BrandTeal else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resident_search_field")
                    )
                }
            }

            // Quick Status Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResidentStatusFilter.values().forEach { status ->
                        FilterChip(
                            selected = filter.statusFilter == status,
                            onClick = {
                                viewModel.residentFilter.value = filter.copy(statusFilter = status)
                            },
                            label = { Text(status.label, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // ADVANCED SEARCH PANEL (Expandable)
            item {
                AnimatedVisibility(visible = showAdvancedFilters) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Búsqueda Avanzada de Residentes",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BrandNavy
                                )
                                TextButton(onClick = { viewModel.resetResidentFilters() }) {
                                    Text("Restablecer", fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Rango de Fechas de Registro
                            Text("Fecha de Registro:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                val sel = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                                                viewModel.residentFilter.value = filter.copy(dateRegisteredFromMillis = sel.timeInMillis)
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        filter.dateRegisteredFromMillis?.let { "Desde: ${formatDate(it)}" } ?: "Desde: Cualquier fecha",
                                        fontSize = 11.sp
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                val sel = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                                                viewModel.residentFilter.value = filter.copy(dateRegisteredToMillis = sel.timeInMillis)
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        filter.dateRegisteredToMillis?.let { "Hasta: ${formatDate(it)}" } ?: "Hasta: Cualquier fecha",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Results Counter
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Resultados: ${residents.size} de ${allResidents.size} residentes",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (filter.isAnyFilterApplied) {
                        TextButton(onClick = { viewModel.resetResidentFilters() }) {
                            Text("Limpiar filtros", fontSize = 11.sp)
                        }
                    }
                }
            }

            // List of Residents
            if (residents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No se encontraron residentes con estos criterios", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Intenta ajustar los filtros de búsqueda o registrar un nuevo residente.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(residents, key = { it.resident.id }) { rf ->
                    ResidentCard(
                        rf = rf,
                        isDark = isDark,
                        isAdmin = isAdmin,
                        onCardClick = { viewModel.selectedResident.value = rf },
                        onToggleMorosoClick = {
                            viewModel.toggleResidentMoroso(rf.resident.id, rf.isMoroso)
                        },
                        onEditClick = {
                            viewModel.editingResident.value = rf.resident
                            viewModel.showAddResidentDialog.value = true
                        },
                        onDeleteClick = {
                            residentPendingDelete = rf.resident
                        }
                    )
                }
            }
        }

        residentPendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { residentPendingDelete = null },
                title = { Text("Eliminar Residente", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "¿Seguro que deseas eliminar permanentemente a ${target.fullName} (${target.displayUnit})? Se eliminará en todos los dispositivos conectados."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteResident(target)
                            residentPendingDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusMorosoRed),
                        modifier = Modifier.testTag("confirm_delete_resident_card_btn")
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { residentPendingDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun ResidentCard(
    rf: ResidentWithFinancials,
    isDark: Boolean = false,
    isAdmin: Boolean = false,
    onCardClick: () -> Unit,
    onToggleMorosoClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val res = rf.resident
    val greenAccent = if (isDark) StatusSolventeGreenDark else StatusSolventeGreen
    val greenBg = if (isDark) StatusSolventeGreenBgDark else StatusSolventeGreenBg
    val redAccent = if (isDark) StatusMorosoRedDark else StatusMorosoRed
    val redBg = if (isDark) StatusMorosoRedBgDark else StatusMorosoRedBg

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: House Badge + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = res.displayUnit,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 13.sp
                        )
                    }
                }

                ResidentStatusBadge(
                    isMoroso = rf.isMoroso
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Resident Name
            Text(
                text = res.fullName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            // ACCESS & KEY STATUS BADGE
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = if (rf.isMoroso) redBg else greenBg,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (rf.isMoroso) redAccent else greenAccent
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (rf.isMoroso) Icons.Default.Warning else Icons.Default.Key,
                        contentDescription = null,
                        tint = if (rf.isMoroso) redAccent else greenAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (rf.isMoroso) {
                            "Acceso denegado por falta de pago (${res.accessCode.ifBlank { "Sin clave" }})"
                        } else {
                            "Clave: ${res.accessCode.ifBlank { "CLV-${res.id}" }} • Sí paga"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rf.isMoroso) redAccent else greenAccent
                    )
                }
            }
            if (res.blockOrSection.isNotBlank()) {
                Text(
                    text = res.blockOrSection,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Contact info and plates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (res.phone.isNotBlank()) {
                    Text(
                        text = "Tel: ${res.phone}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (res.vehiclePlates.isNotBlank()) {
                    Text(
                        text = "Placas: ${res.vehiclePlates}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.surfaceVariant)

            // Action Row: Detalle & Quick Status Toggle (No cobros)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCardClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Detalle", fontSize = 11.5.sp)
                }

                Button(
                    onClick = onToggleMorosoClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (rf.isMoroso) greenAccent else redAccent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 48.dp)
                        .testTag("toggle_moroso_card_btn_${res.id}")
                ) {
                    Icon(
                        imageVector = if (rf.isMoroso) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (rf.isMoroso) "Marcar Sí Paga" else "Marcar Moroso",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isAdmin) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onEditClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("edit_resident_card_btn_${res.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Actualizar", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onDeleteClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = redAccent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, redAccent.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("delete_resident_card_btn_${res.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
