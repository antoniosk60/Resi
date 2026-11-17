package com.example.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Debt
import com.example.data.model.Payment
import com.example.data.model.PaymentMethod
import com.example.data.model.Resident
import com.example.data.model.ResidentWithFinancials
import com.example.data.model.Tardiness
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.StatusMorosoRed
import com.example.ui.theme.StatusMorosoRedBg
import com.example.ui.theme.StatusSolventeGreen
import com.example.ui.theme.StatusSolventeGreenBg
import com.example.util.ReportExporter
import java.util.Calendar

// =============================================================================
// ADD / EDIT RESIDENT FORM
// =============================================================================

@Composable
fun AddResidentDialog(
    initialResident: Resident? = null,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        fullName: String,
        houseNumber: String,
        building: String,
        apartment: String,
        accessCode: String,
        blockOrSection: String,
        phone: String,
        email: String,
        vehiclePlates: String,
        notes: String,
        isMoroso: Boolean
    ) -> Unit
) {
    val residentKey = initialResident?.id ?: -1L
    var fullName by remember(residentKey) { mutableStateOf(initialResident?.fullName ?: "") }
    var building by remember(residentKey) { mutableStateOf(initialResident?.building ?: "") }
    var apartment by remember(residentKey) { mutableStateOf(initialResident?.apartment ?: "") }
    var accessCode by remember(residentKey) { mutableStateOf(initialResident?.accessCode ?: "") }
    var houseNumber by remember(residentKey) { mutableStateOf(initialResident?.houseNumber ?: "") }
    var blockOrSection by remember(residentKey) { mutableStateOf(initialResident?.blockOrSection ?: "") }
    var phone by remember(residentKey) { mutableStateOf(initialResident?.phone ?: "") }
    var email by remember(residentKey) { mutableStateOf(initialResident?.email ?: "") }
    var vehiclePlates by remember(residentKey) { mutableStateOf(initialResident?.vehiclePlates ?: "") }
    var notes by remember(residentKey) { mutableStateOf(initialResident?.notes ?: "") }
    var isMoroso by remember(residentKey) { mutableStateOf(initialResident?.isMoroso ?: false) }

    var fullNameError by remember(residentKey) { mutableStateOf(false) }
    var buildingError by remember(residentKey) { mutableStateOf(false) }
    var apartmentError by remember(residentKey) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialResident == null) "Registrar Nuevo Residente" else "Editar Datos de Residente",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Datos de ubicación, control de acceso y habitante:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        fullNameError = it.isBlank()
                    },
                    label = { Text("Nombre Completo *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    isError = fullNameError,
                    supportingText = if (fullNameError) { { Text("El nombre es obligatorio") } } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resident_fullname_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                // EDIFICIO y DEPARTAMENTO
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = building,
                        onValueChange = {
                            building = it
                            buildingError = it.isBlank()
                        },
                        label = { Text("EDIFICIO *") },
                        placeholder = { Text("Ej. Edificio A / Torre 1") },
                        isError = buildingError,
                        supportingText = if (buildingError) { { Text("Obligatorio") } } else null,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("resident_building_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = apartment,
                        onValueChange = {
                            apartment = it
                            apartmentError = it.isBlank()
                        },
                        label = { Text("DEPARTAMENTO *") },
                        placeholder = { Text("Ej. 104") },
                        isError = apartmentError,
                        supportingText = if (apartmentError) { { Text("Obligatorio") } } else null,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("resident_apartment_input")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // CLAVE DE ACCESO
                OutlinedTextField(
                    value = accessCode,
                    onValueChange = { accessCode = it.uppercase() },
                    label = { Text("CLAVE DE ACCESO (PIN / Tag)") },
                    placeholder = { Text("Ej. CLV-1049") },
                    supportingText = { Text("Clave asignada. Si el departamento es moroso, se denegará el acceso.") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resident_access_code_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                // ESTATUS DE PAGO (Sí paga vs Moroso)
                Text(
                    text = "Estatus de Pago y Acceso:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isMoroso,
                        onClick = { isMoroso = false },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (!isMoroso) StatusSolventeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Sí paga", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("resident_status_solvente_chip")
                    )
                    FilterChip(
                        selected = isMoroso,
                        onClick = { isMoroso = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isMoroso) StatusMorosoRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("Moroso", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("resident_status_moroso_chip")
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono / WhatsApp") },
                    placeholder = { Text("Ej. 55-1234-5678") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo Electrónico") },
                    placeholder = { Text("contacto@gmail.com") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = vehiclePlates,
                    onValueChange = { vehiclePlates = it },
                    label = { Text("Placas / Vehículo") },
                    placeholder = { Text("Ej. ABC-123-D") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas Adicionales") },
                    placeholder = { Text("Observaciones, contrato o mascotas") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validName = fullName.isNotBlank()
                    val validBuilding = building.isNotBlank()
                    val validApt = apartment.isNotBlank()

                    fullNameError = !validName
                    buildingError = !validBuilding
                    apartmentError = !validApt

                    if (validName && validBuilding && validApt) {
                        val computedHouse = "$building - Depto $apartment"
                        onSave(
                            initialResident?.id ?: 0L,
                            fullName,
                            computedHouse,
                            building,
                            apartment,
                            accessCode,
                            blockOrSection,
                            phone,
                            email,
                            vehiclePlates,
                            notes,
                            isMoroso
                        )
                    }
                },
                modifier = Modifier.testTag("save_resident_button")
            ) {
                Text(if (initialResident == null) "Guardar Residente" else "Actualizar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// =============================================================================
// ADD VISIT FORM (Caseta de vigilancia y control de accesos)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVisitDialog(
    residents: List<ResidentWithFinancials>,
    preselectedResidentId: Long? = null,
    onDismiss: () -> Unit,
    onRegisterVisit: (
        visitorName: String,
        visitorIdOrIne: String,
        visitorVehiclePlates: String,
        building: String,
        apartment: String,
        residentId: Long,
        residentName: String,
        reason: String,
        notes: String
    ) -> Unit
) {
    var visitorName by remember { mutableStateOf("") }
    var visitorIdOrIne by remember { mutableStateOf("") }
    var visitorVehiclePlates by remember { mutableStateOf("") }
    var selectedResidentId by remember(preselectedResidentId) {
        mutableStateOf(preselectedResidentId ?: residents.firstOrNull()?.resident?.id ?: 0L)
    }
    var reason by remember { mutableStateOf("Visita personal") }
    var notes by remember { mutableStateOf("") }

    var visitorNameError by remember { mutableStateOf(false) }
    var residentDropdownExpanded by remember { mutableStateOf(false) }

    val selectedResidentFinancial = remember(selectedResidentId, residents) {
        residents.find { it.resident.id == selectedResidentId }
    }
    val isResidentMoroso = selectedResidentFinancial?.isMoroso ?: false
    val res = selectedResidentFinancial?.resident

    val quickReasons = listOf(
        "Visita personal / familiar",
        "Paquetería / Mensajería",
        "Servicio técnico / Reparación",
        "Proveedor / Entrega",
        "Mudanza"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = BrandNavy,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registrar Acceso / Visita", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Resident / Unit Selector
                Text("Departamento de destino:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = residentDropdownExpanded,
                    onExpandedChange = { residentDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (res != null) "${res.building} Depto ${res.apartment} - ${res.fullName}" else "Seleccionar departamento...",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = residentDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("visit_resident_selector"),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = residentDropdownExpanded,
                        onDismissRequest = { residentDropdownExpanded = false }
                    ) {
                        residents.forEach { rf ->
                            val r = rf.resident
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${r.building.ifBlank { "Unidad" }} Depto ${r.apartment.ifBlank { r.houseNumber }}",
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(r.fullName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (rf.isMoroso) {
                                            Surface(color = StatusMorosoRed, shape = RoundedCornerShape(4.dp)) {
                                                Text(
                                                    "Moroso",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        } else {
                                            Surface(color = StatusSolventeGreen, shape = RoundedCornerShape(4.dp)) {
                                                Text(
                                                    "Al Corriente",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    selectedResidentId = r.id
                                    residentDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // REAL-TIME ACCESS STATUS BANNER FOR THIS DESTINATION
                if (res != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isResidentMoroso) StatusMorosoRedBg else StatusSolventeGreenBg
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isResidentMoroso) StatusMorosoRed else StatusSolventeGreen
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isResidentMoroso) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = if (isResidentMoroso) "Advertencia por falta de pago" else "Acceso autorizado",
                                tint = if (isResidentMoroso) StatusMorosoRed else StatusSolventeGreen,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isResidentMoroso) {
                                        "ACCESO DENEGADO POR FALTA DE PAGO"
                                    } else {
                                        "ACCESO AUTORIZADO • CLAVE: ${res.accessCode.ifBlank { "VÁLIDA" }}"
                                    },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = if (isResidentMoroso) StatusMorosoRed else StatusSolventeGreen
                                )
                                Text(
                                    text = if (isResidentMoroso) {
                                        "Departamento moroso. Se registrará intento de acceso denegado."
                                    } else {
                                        "Estatus: Sí paga • ${res.building} Depto ${res.apartment.ifBlank { res.houseNumber }}"
                                    },
                                    fontSize = 11.sp,
                                    color = if (isResidentMoroso) StatusMorosoRed else StatusSolventeGreen
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Visitor Full Name
                OutlinedTextField(
                    value = visitorName,
                    onValueChange = {
                        visitorName = it
                        visitorNameError = it.isBlank()
                    },
                    label = { Text("Nombre del Visitante *") },
                    placeholder = { Text("Ej. Juan Manuel Ruiz") },
                    isError = visitorNameError,
                    supportingText = if (visitorNameError) { { Text("El nombre del visitante es obligatorio") } } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("visitor_name_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                // INE and Vehicle plates
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = visitorIdOrIne,
                        onValueChange = { visitorIdOrIne = it },
                        label = { Text("Identificación / INE") },
                        placeholder = { Text("Terminación 4921") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = visitorVehiclePlates,
                        onValueChange = { visitorVehiclePlates = it.uppercase() },
                        label = { Text("Placas Vehículo") },
                        placeholder = { Text("Ej. PZX-112-C") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Reason of visit
                Text("Motivo del acceso:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickReasons.take(2).forEach { r ->
                        FilterChip(
                            selected = reason == r,
                            onClick = { reason = r },
                            label = { Text(r, fontSize = 10.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickReasons.drop(2).take(2).forEach { r ->
                        FilterChip(
                            selected = reason == r,
                            onClick = { reason = r },
                            label = { Text(r, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas de Caseta") },
                    placeholder = { Text("Observaciones del guardia, gafete asignado...") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validName = visitorName.isNotBlank()
                    visitorNameError = !validName

                    if (validName && res != null) {
                        onRegisterVisit(
                            visitorName,
                            visitorIdOrIne,
                            visitorVehiclePlates,
                            res.building,
                            res.apartment.ifBlank { res.houseNumber },
                            res.id,
                            res.fullName,
                            reason,
                            notes
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isResidentMoroso) StatusMorosoRed else BrandNavy
                ),
                modifier = Modifier.testTag("confirm_register_visit_button")
            ) {
                Text(if (isResidentMoroso) "Registrar Acceso Deseado (Moroso)" else "Permitir Acceso (Clave OK)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// =============================================================================
// ADD DEBT FORM
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtDialog(
    residents: List<Resident>,
    preselectedResidentId: Long? = null,
    onDismiss: () -> Unit,
    onCreateDebt: (residentId: Long, concept: String, amount: Double, dueDateMillis: Long, notes: String) -> Unit
) {
    val context = LocalContext.current
    var selectedResidentId by remember { mutableStateOf(preselectedResidentId ?: residents.firstOrNull()?.id ?: 0L) }
    var residentDropdownExpanded by remember { mutableStateOf(false) }

    var concept by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val calendar = remember { Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 15) } }
    var dueDateMillis by remember { mutableLongStateOf(calendar.timeInMillis) }

    var conceptError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    val quickConcepts = listOf(
        "Mantenimiento Mensual",
        "Cuota Extraordinaria",
        "Sanción por Retardo / Ruido",
        "Renta de Salón de Eventos",
        "Reposición de Tag / Llave"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Crear Nuevo Adeudo", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Asigna una cuota, servicio o sanción a cobrar:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Resident Selector
                ExposedDropdownMenuBox(
                    expanded = residentDropdownExpanded,
                    onExpandedChange = { residentDropdownExpanded = !residentDropdownExpanded }
                ) {
                    val currentResident = residents.find { it.id == selectedResidentId }
                    OutlinedTextField(
                        value = currentResident?.let { "${it.houseNumber} - ${it.fullName}" } ?: "Seleccionar residente",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Residente Asignado *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = residentDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = residentDropdownExpanded,
                        onDismissRequest = { residentDropdownExpanded = false }
                    ) {
                        residents.forEach { r ->
                            DropdownMenuItem(
                                text = { Text("${r.houseNumber} - ${r.fullName}") },
                                onClick = {
                                    selectedResidentId = r.id
                                    residentDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Concept Field
                OutlinedTextField(
                    value = concept,
                    onValueChange = {
                        concept = it
                        conceptError = it.isBlank()
                    },
                    label = { Text("Concepto del Adeudo *") },
                    placeholder = { Text("Ej. Mantenimiento Diciembre") },
                    isError = conceptError,
                    supportingText = if (conceptError) { { Text("Ingresa el concepto") } } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_concept_input")
                )

                // Quick concept chips
                Text(
                    text = "Sugerencias rápidas:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickConcepts.take(2).forEach { chip ->
                        FilterChip(
                            selected = concept == chip,
                            onClick = { concept = chip },
                            label = { Text(chip, fontSize = 10.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = it.toDoubleOrNull() == null || (it.toDoubleOrNull() ?: 0.0) <= 0
                    },
                    label = { Text("Importe a Pagar ($) *") },
                    placeholder = { Text("1800.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    supportingText = if (amountError) { { Text("Monto válido mayor a 0") } } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_amount_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Due Date Picker Button
                OutlinedTextField(
                    value = formatDate(dueDateMillis),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Fecha de Vencimiento *") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val c = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val sel = Calendar.getInstance().apply {
                                        set(y, m, d, 23, 59, 59)
                                    }
                                    dueDateMillis = sel.timeInMillis
                                },
                                c.get(Calendar.YEAR),
                                c.get(Calendar.MONTH),
                                c.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Seleccionar fecha")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Observaciones") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    val validConcept = concept.isNotBlank()
                    val validAmount = amount > 0.0

                    conceptError = !validConcept
                    amountError = !validAmount

                    if (validConcept && validAmount && selectedResidentId != 0L) {
                        onCreateDebt(selectedResidentId, concept, amount, dueDateMillis, notes)
                    }
                },
                modifier = Modifier.testTag("confirm_create_debt_button")
            ) {
                Text("Crear Adeudo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// =============================================================================
// ADD PAYMENT FORM (Total or Partial - Offline manual receipt)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentDialog(
    residents: List<Resident>,
    debts: List<Debt>,
    preselectedResidentId: Long? = null,
    preselectedDebtId: Long? = null,
    onDismiss: () -> Unit,
    onRegisterPayment: (
        residentId: Long,
        debtId: Long?,
        amount: Double,
        method: PaymentMethod,
        receiptNumber: String,
        notes: String
    ) -> Unit
) {
    var selectedResidentId by remember {
        mutableStateOf(
            preselectedResidentId
                ?: preselectedDebtId?.let { did -> debts.find { it.id == did }?.residentId }
                ?: residents.firstOrNull()?.id
                ?: 0L
        )
    }
    var residentDropdownExpanded by remember { mutableStateOf(false) }

    // Active pending debts of this resident
    val residentDebts = remember(selectedResidentId, debts) {
        debts.filter { it.residentId == selectedResidentId && !it.isPaid && !it.isCanceled }
    }

    var selectedDebtId by remember {
        mutableStateOf(preselectedDebtId ?: residentDebts.firstOrNull()?.id)
    }
    var debtDropdownExpanded by remember { mutableStateOf(false) }

    val chosenDebt = residentDebts.find { it.id == selectedDebtId }

    var amountText by remember {
        mutableStateOf(chosenDebt?.let { "%.2f".format(it.remainingBalance) } ?: "")
    }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.EFECTIVO) }
    var receiptNumber by remember {
        mutableStateOf("REC-${System.currentTimeMillis() % 100000}")
    }
    var notes by remember { mutableStateOf("") }

    var amountError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Registrar Pago Manual", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Registra un cobro en efectivo, transferencia o cheque. No procesa pagos en línea.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Resident Selector
                ExposedDropdownMenuBox(
                    expanded = residentDropdownExpanded,
                    onExpandedChange = { residentDropdownExpanded = !residentDropdownExpanded }
                ) {
                    val currentRes = residents.find { it.id == selectedResidentId }
                    OutlinedTextField(
                        value = currentRes?.let { "${it.houseNumber} - ${it.fullName}" } ?: "Seleccionar",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Residente que Paga *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = residentDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = residentDropdownExpanded,
                        onDismissRequest = { residentDropdownExpanded = false }
                    ) {
                        residents.forEach { r ->
                            DropdownMenuItem(
                                text = { Text("${r.houseNumber} - ${r.fullName}") },
                                onClick = {
                                    selectedResidentId = r.id
                                    selectedDebtId = null
                                    amountText = ""
                                    residentDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Debt Selector (optional, can pay general balance)
                ExposedDropdownMenuBox(
                    expanded = debtDropdownExpanded,
                    onExpandedChange = { debtDropdownExpanded = !debtDropdownExpanded }
                ) {
                    val debtLabel = if (selectedDebtId != null && chosenDebt != null) {
                        "${chosenDebt.concept} (Resta ${formatMoney(chosenDebt.remainingBalance)})"
                    } else if (residentDebts.isEmpty()) {
                        "Sin adeudos pendientes (Abono a favor)"
                    } else {
                        "Abono libre / Seleccionar adeudo"
                    }

                    OutlinedTextField(
                        value = debtLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Adeudo a Liquidar / Abonar") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = debtDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = debtDropdownExpanded,
                        onDismissRequest = { debtDropdownExpanded = false }
                    ) {
                        residentDebts.forEach { d ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(d.concept, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Saldo pendiente: ${formatMoney(d.remainingBalance)} (Original: ${formatMoney(d.amount)})",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedDebtId = d.id
                                    amountText = "%.2f".format(d.remainingBalance)
                                    debtDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Payment Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = it.toDoubleOrNull() == null || (it.toDoubleOrNull() ?: 0.0) <= 0
                    },
                    label = { Text("Importe Recibido ($) *") },
                    placeholder = { Text("1800.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    supportingText = if (chosenDebt != null) {
                        { Text("Saldo total del adeudo: ${formatMoney(chosenDebt.remainingBalance)}. Puedes registrar un abono parcial.") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_amount_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Payment Method Chips
                Text("Método de Pago:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PaymentMethod.values().take(2).forEach { m ->
                        FilterChip(
                            selected = selectedMethod == m,
                            onClick = { selectedMethod = m },
                            label = { Text(m.label, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PaymentMethod.values().drop(2).forEach { m ->
                        FilterChip(
                            selected = selectedMethod == m,
                            onClick = { selectedMethod = m },
                            label = { Text(m.label, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Receipt number
                OutlinedTextField(
                    value = receiptNumber,
                    onValueChange = { receiptNumber = it },
                    label = { Text("Folio de Comprobante / Recibo *") },
                    placeholder = { Text("Ej. REC-2026-0045 o SPEI-84920") },
                    leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas del Cobro") },
                    placeholder = { Text("Nombre de quien entregó o banco emisor") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0.0 && selectedResidentId != 0L) {
                        onRegisterPayment(
                            selectedResidentId,
                            selectedDebtId,
                            amount,
                            selectedMethod,
                            receiptNumber,
                            notes
                        )
                    } else {
                        amountError = true
                    }
                },
                modifier = Modifier.testTag("confirm_register_payment_button")
            ) {
                Text("Registrar Pago")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// =============================================================================
// ADD TARDINESS FORM (Retardos)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTardinessDialog(
    residents: List<Resident>,
    onDismiss: () -> Unit,
    onRegisterTardiness: (residentId: Long, reason: String, vehiclePlate: String, notes: String) -> Unit
) {
    var selectedResidentId by remember { mutableStateOf(residents.firstOrNull()?.id ?: 0L) }
    var residentDropdownExpanded by remember { mutableStateOf(false) }

    var reason by remember { mutableStateOf("") }
    var vehiclePlate by remember {
        mutableStateOf(residents.find { it.id == selectedResidentId }?.vehiclePlates ?: "")
    }
    var notes by remember { mutableStateOf("") }
    var reasonError by remember { mutableStateOf(false) }

    val quickReasons = listOf(
        "Ingreso fuera de horario / toque de queda",
        "Retardo en entrega de salón de eventos",
        "Exceso de tiempo en estacionamiento de visitas",
        "Demora en retiro de vehículo proveedor"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Registrar Retardo / Incidencia", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Registra demoras de acceso, áreas comunes o estacionamiento:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = residentDropdownExpanded,
                    onExpandedChange = { residentDropdownExpanded = !residentDropdownExpanded }
                ) {
                    val currentRes = residents.find { it.id == selectedResidentId }
                    OutlinedTextField(
                        value = currentRes?.let { "${it.houseNumber} - ${it.fullName}" } ?: "Seleccionar",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Residente Involucrado *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = residentDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = residentDropdownExpanded,
                        onDismissRequest = { residentDropdownExpanded = false }
                    ) {
                        residents.forEach { r ->
                            DropdownMenuItem(
                                text = { Text("${r.houseNumber} - ${r.fullName}") },
                                onClick = {
                                    selectedResidentId = r.id
                                    vehiclePlate = r.vehiclePlates
                                    residentDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        reasonError = it.isBlank()
                    },
                    label = { Text("Motivo del Retardo *") },
                    placeholder = { Text("Ej. Llegada tarde a portón principal") },
                    isError = reasonError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tardiness_reason_input")
                )

                Text(
                    text = "Motivos comunes:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
                quickReasons.forEach { r ->
                    FilterChip(
                        selected = reason == r,
                        onClick = { reason = r },
                        label = { Text(r, fontSize = 10.sp) },
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = vehiclePlate,
                    onValueChange = { vehiclePlate = it },
                    label = { Text("Placas / Vehículo Involucrado") },
                    placeholder = { Text("Ej. PZX-112-C") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas Adicionales") },
                    placeholder = { Text("Observaciones del guardia o caseta") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.isNotBlank() && selectedResidentId != 0L) {
                        onRegisterTardiness(selectedResidentId, reason, vehiclePlate, notes)
                    } else {
                        reasonError = true
                    }
                },
                modifier = Modifier.testTag("confirm_register_tardiness_button")
            ) {
                Text("Guardar Retardo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// =============================================================================
// RESIDENT ACCESS & KEY DETAIL DIALOG (Sin cobros ni adeudos)
// =============================================================================

@Composable
fun ResidentDetailDialog(
    residentWithFinancials: ResidentWithFinancials,
    tardiness: List<Tardiness>,
    isAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onToggleMorosoClick: (residentId: Long, currentIsMoroso: Boolean) -> Unit,
    onEditResidentClick: (Resident) -> Unit,
    onDeleteResidentClick: (Resident) -> Unit = {}
) {
    val res = residentWithFinancials.resident
    val isMoroso = residentWithFinancials.isMoroso
    val resTardiness = tardiness.filter { it.residentId == res.id }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = res.displayUnit,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                        Text(
                            text = res.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Prominent Status Indicator Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMoroso) StatusMorosoRedBg else StatusSolventeGreenBg
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        if (isMoroso) StatusMorosoRed else StatusSolventeGreen
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMoroso) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = if (isMoroso) "Acceso denegado por falta de pago" else "Acceso autorizado",
                            tint = if (isMoroso) StatusMorosoRed else StatusSolventeGreen,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isMoroso) "ACCESO DENEGADO POR FALTA DE PAGO" else "SÍ PAGA • ACCESO AUTORIZADO",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (isMoroso) StatusMorosoRed else StatusSolventeGreen
                            )
                            Text(
                                text = if (isMoroso) {
                                    "Clave bloqueada: ${res.accessCode.ifBlank { "Sin clave" }}"
                                } else {
                                    "Clave asignada: ${res.accessCode.ifBlank { "CLV-${res.id}" }}"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMoroso) StatusMorosoRed else StatusSolventeGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1-Tap Status Toggle & Edit Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onToggleMorosoClick(res.id, isMoroso)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMoroso) StatusSolventeGreen else StatusMorosoRed
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("toggle_resident_moroso_button")
                    ) {
                        Icon(
                            imageVector = if (isMoroso) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isMoroso) "Marcar como Sí Paga" else "Marcar como Moroso",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onEditResidentClick(res)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_resident_detail_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isAdmin) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusMorosoRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StatusMorosoRed.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("delete_resident_detail_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Eliminar Residente (Solo Administrador)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Clean Tabs: Contacto & Retardos
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Ubicación y Clave", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Retardos (${resTardiness.size})", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTabIndex) {
                        0 -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Edificio: ${res.building.ifBlank { "Gavilán 181" }}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Departamento: ${res.apartment.ifBlank { res.houseNumber }}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                        Icon(
                                            Icons.Default.Key,
                                            contentDescription = null,
                                            tint = if (isMoroso) StatusMorosoRed else StatusSolventeGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isMoroso) {
                                                "Clave: ${res.accessCode.ifBlank { "Sin clave" }} (Bloqueada - Moroso)"
                                            } else {
                                                "Clave de Acceso: ${res.accessCode.ifBlank { "Sin clave asignada" }}"
                                            },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMoroso) StatusMorosoRed else StatusSolventeGreen
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    Text("Teléfono: ${res.phone.ifBlank { "No registrado" }}", fontSize = 13.sp)
                                    Text("Correo: ${res.email.ifBlank { "No registrado" }}", fontSize = 13.sp)
                                    Text("Placas: ${res.vehiclePlates.ifBlank { "Sin placas registradas" }}", fontSize = 13.sp)
                                    Text("Fecha de Registro: ${formatDate(res.registeredDateMillis)}", fontSize = 13.sp)
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    Text("Creado por: ${res.createdBy}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Última modificación por: ${res.lastModifiedBy}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (res.notes.isNotBlank()) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                        Text("Notas: ${res.notes}", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (resTardiness.isEmpty()) {
                                Text(
                                    "No se han registrado retardos ni incidencias para este departamento.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                resTardiness.forEach { t ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(t.reason, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("Fecha: ${formatDateTime(t.timestampMillis)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (t.vehiclePlate.isNotBlank()) {
                                                Text("Placas: ${t.vehiclePlate}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("Registrado por: ${t.registeredBy}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirmar Eliminación", fontWeight = FontWeight.Bold) },
            text = {
                Text("¿Seguro que deseas eliminar permanentemente a ${res.fullName} (${res.displayUnit})? Esta acción sincronizará el borrado en todos los dispositivos.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteResidentClick(res)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusMorosoRed),
                    modifier = Modifier.testTag("confirm_delete_resident_detail_btn")
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

