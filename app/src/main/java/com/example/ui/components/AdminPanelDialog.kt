package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Resident
import com.example.data.model.ResidentWithFinancials
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.StatusMorosoRed
import com.example.ui.theme.StatusMorosoRedBg
import com.example.ui.theme.StatusSolventeGreen
import com.example.ui.theme.StatusSolventeGreenBg

@Composable
fun AdminPanelDialog(
    residents: List<ResidentWithFinancials>,
    tardinessCount: Int,
    visitsCount: Int,
    auditLogsCount: Int,
    capturistasCount: Int,
    isExportingPdf: Boolean,
    onExportTardinessPdf: () -> Unit,
    onExportResidentsPdf: () -> Unit,
    onExportVisitsPdf: () -> Unit,
    onExportAuditPdf: () -> Unit,
    onNewResidentClick: () -> Unit,
    onUploadExcelClick: () -> Unit,
    onEditResidentClick: (Resident) -> Unit,
    onDeleteResidentClick: (Resident) -> Unit,
    onManageAccountsClick: () -> Unit,
    onMonthlyCloseClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var residentPendingDelete by remember { mutableStateOf<Resident?>(null) }

    val filteredResidents = remember(residents, searchQuery) {
        if (searchQuery.isBlank()) residents
        else residents.filter { rf ->
            val r = rf.resident
            r.fullName.contains(searchQuery, ignoreCase = true) ||
                r.building.contains(searchQuery, ignoreCase = true) ||
                r.apartment.contains(searchQuery, ignoreCase = true) ||
                r.houseNumber.contains(searchQuery, ignoreCase = true) ||
                r.accessCode.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .testTag("admin_panel_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Surface(color = BrandNavy, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Centro de Administración",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Gavilán 181 • Exportación PDF y Padrón",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_admin_panel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar panel de administración",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (isExportingPdf) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section 1: PDF Reports Export
                    item {
                        Text(
                            text = "Exportar Reportes Oficiales (PDF)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AdminExportPdfCard(
                                title = "Retardos",
                                countLabel = "$tardinessCount incidencias",
                                icon = Icons.Default.Schedule,
                                accentColor = Color(0xFFB45309),
                                enabled = !isExportingPdf,
                                onClick = onExportTardinessPdf,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_export_tardiness_pdf_btn")
                            )
                            AdminExportPdfCard(
                                title = "Residentes",
                                countLabel = "${residents.size} deptos",
                                icon = Icons.Default.People,
                                accentColor = BrandNavy,
                                enabled = !isExportingPdf,
                                onClick = onExportResidentsPdf,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_export_residents_pdf_btn")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AdminExportPdfCard(
                                title = "Accesos y Visitas",
                                countLabel = "$visitsCount registros",
                                icon = Icons.Default.Security,
                                accentColor = BrandTeal,
                                enabled = !isExportingPdf,
                                onClick = onExportVisitsPdf,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_export_visits_pdf_btn")
                            )
                            AdminExportPdfCard(
                                title = "Auditoría",
                                countLabel = "$auditLogsCount eventos",
                                icon = Icons.Default.History,
                                accentColor = Color(0xFF475569),
                                enabled = !isExportingPdf,
                                onClick = onExportAuditPdf,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("admin_export_audit_pdf_btn")
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onDismiss()
                                onMonthlyCloseClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusMorosoRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("admin_monthly_close_button")
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cierre Mensual: PDF y Eliminar Mes",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item {
                        HorizontalDivider()
                    }

                    // Section 2: Google Access Control Shortcut
                    item {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onManageAccountsClick()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("admin_manage_accounts_shortcut_btn")
                        ) {
                            Icon(Icons.Default.ManageAccounts, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gestionar Cuentas Google (1 Admin + $capturistasCount/5 Capturistas)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    item {
                        HorizontalDivider()
                    }

                    // Section 3: Residents Update & Delete Management
                    item {
                        Text(
                            text = "Gestión de Residentes (Actualizar / Eliminar)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNewResidentClick,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("admin_new_resident_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nuevo Residente", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onUploadExcelClick,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("admin_upload_excel_btn")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Subir Excel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Filtrar residente por nombre, depto o clave") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = if (searchQuery.isNotBlank()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                                    }
                                }
                            } else null,
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_resident_search_input")
                        )
                    }

                    if (filteredResidents.isEmpty()) {
                        item {
                            Text(
                                text = "No hay residentes que coincidan con el filtro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(filteredResidents, key = { it.resident.id }) { rf ->
                            val res = rf.resident
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = res.displayUnit,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                color = BrandNavy
                                            )
                                            Text(
                                                text = res.fullName,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Clave: ${res.accessCode.ifBlank { "Sin clave" }}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            color = if (rf.isMoroso) StatusMorosoRedBg else StatusSolventeGreenBg,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (rf.isMoroso) "MOROSO" else "SÍ PAGA",
                                                color = if (rf.isMoroso) StatusMorosoRed else StatusSolventeGreen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onEditResidentClick(res) },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("admin_edit_resident_${res.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Actualizar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        Button(
                                            onClick = { residentPendingDelete = res },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = StatusMorosoRed,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("admin_delete_resident_${res.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Eliminar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    residentPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { residentPendingDelete = null },
            title = { Text("Eliminar Residente", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "¿Deseas eliminar permanentemente a ${target.fullName} (${target.displayUnit}) de Firestore? Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteResidentClick(target)
                        residentPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusMorosoRed),
                    modifier = Modifier.testTag("confirm_admin_delete_resident_btn")
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

@Composable
private fun AdminExportPdfCard(
    title: String,
    countLabel: String,
    icon: ImageVector,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier.heightIn(min = 76.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = "Exportar PDF $title",
                    tint = StatusMorosoRed,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "PDF $title",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = countLabel,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
