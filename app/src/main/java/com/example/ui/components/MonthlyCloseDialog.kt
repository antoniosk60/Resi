package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AuditLog
import com.example.data.model.ResidentWithFinancials
import com.example.data.model.Tardiness
import com.example.data.model.Visit
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.StatusMorosoRed
import com.example.ui.theme.StatusMorosoRedBg
import com.example.util.MonthlyArchiveHelper
import com.example.util.MonthlyCloseScope

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthlyCloseDialog(
    allResidents: List<ResidentWithFinancials>,
    allVisits: List<Visit>,
    allTardiness: List<Tardiness>,
    allAuditLogs: List<AuditLog>,
    isProcessing: Boolean,
    onExportPdfOnly: (scope: MonthlyCloseScope, includeResidents: Boolean, includeAllResidentsForReload: Boolean) -> Unit,
    onExportAndDeleteMonthly: (scope: MonthlyCloseScope, includeResidents: Boolean, includeAllResidentsForReload: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedScope by remember { mutableStateOf(MonthlyCloseScope.CURRENT_MONTH) }
    var includeResidents by remember { mutableStateOf(true) }
    var includeAllResidentsForReload by remember { mutableStateOf(true) }
    var confirmPermanentDelete by remember { mutableStateOf(false) }

    val currentMonthLabel = remember { MonthlyArchiveHelper.getMonthLabel(0) }
    val previousMonthLabel = remember { MonthlyArchiveHelper.getMonthLabel(1) }

    val preview = remember(
        selectedScope,
        includeResidents,
        includeAllResidentsForReload,
        allResidents,
        allVisits,
        allTardiness,
        allAuditLogs
    ) {
        MonthlyArchiveHelper.filterRecordsForClose(
            scope = selectedScope,
            includeResidents = includeResidents,
            includeAllResidentsForReload = includeAllResidentsForReload,
            allResidents = allResidents,
            allVisits = allVisits,
            allTardiness = allTardiness,
            allAuditLogs = allAuditLogs
        )
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .testTag("monthly_close_dialog")
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
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Cierre Mensual de Registros",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "PDF completo + borrado permanente para mes entrante",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        IconButton(
                            onClick = { if (!isProcessing) onDismiss() },
                            enabled = !isProcessing,
                            modifier = Modifier.testTag("close_monthly_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar diálogo de cierre mensual",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (isProcessing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. Selecciona el período a respaldar y limpiar:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BrandNavy
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedScope == MonthlyCloseScope.CURRENT_MONTH,
                            onClick = {
                                selectedScope = MonthlyCloseScope.CURRENT_MONTH
                                confirmPermanentDelete = false
                            },
                            label = { Text("Mes actual ($currentMonthLabel)", fontSize = 11.5.sp) },
                            modifier = Modifier.testTag("scope_current_month_chip")
                        )
                        FilterChip(
                            selected = selectedScope == MonthlyCloseScope.PREVIOUS_MONTH,
                            onClick = {
                                selectedScope = MonthlyCloseScope.PREVIOUS_MONTH
                                confirmPermanentDelete = false
                            },
                            label = { Text("Mes anterior ($previousMonthLabel)", fontSize = 11.5.sp) },
                            modifier = Modifier.testTag("scope_previous_month_chip")
                        )
                        FilterChip(
                            selected = selectedScope == MonthlyCloseScope.ALL_RECORDS,
                            onClick = {
                                selectedScope = MonthlyCloseScope.ALL_RECORDS
                                confirmPermanentDelete = false
                            },
                            label = { Text("Todo el historial", fontSize = 11.5.sp) },
                            modifier = Modifier.testTag("scope_all_records_chip")
                        )
                    }

                    // Resident inclusion toggle
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Incluir Residentes y Claves",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        text = "Se exportan al PDF y se borran para subir limpio el Excel del mes entrante.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = includeResidents,
                                    onCheckedChange = {
                                        includeResidents = it
                                        confirmPermanentDelete = false
                                    },
                                    modifier = Modifier.testTag("include_residents_switch")
                                )
                            }

                            if (includeResidents && selectedScope != MonthlyCloseScope.ALL_RECORDS) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Limpiar todo el padrón de Residentes",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "Recomendado si cargarás un nuevo Excel completo el próximo mes.",
                                            fontSize = 10.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = includeAllResidentsForReload,
                                        onCheckedChange = {
                                            includeAllResidentsForReload = it
                                            confirmPermanentDelete = false
                                        },
                                        modifier = Modifier.testTag("include_all_residents_switch")
                                    )
                                }
                            }
                        }
                    }

                    // Live Preview Counts
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = BrandNavy.copy(alpha = 0.06f)
                        ),
                        border = BorderStroke(1.dp, BrandNavy.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Registros en ${preview.periodLabel}:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = BrandNavy
                            )
                            Text("• Residentes y Claves a exportar/borrar: ${preview.residents.size}", fontSize = 12.sp)
                            Text("• Accesos y Visitas a exportar/borrar: ${preview.visits.size}", fontSize = 12.sp)
                            Text("• Retardos e Incidencias a exportar/borrar: ${preview.tardiness.size}", fontSize = 12.sp)
                            Text(
                                "• Eventos de Auditoría (respaldo en PDF, historial intacto): ${preview.auditLogs.size}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Destructive Warning Banner
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusMorosoRedBg),
                        border = BorderStroke(1.dp, StatusMorosoRed.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusMorosoRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "La app generará y abrirá primero el archivo PDF con todos los registros seleccionados y, al confirmarse el archivo, eliminará PERMANENTEMENTE los datos del mes en Firestore para todos los dispositivos.",
                                fontSize = 11.5.sp,
                                color = StatusMorosoRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Explicit confirmation checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isProcessing) {
                                confirmPermanentDelete = !confirmPermanentDelete
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = confirmPermanentDelete,
                            onCheckedChange = { confirmPermanentDelete = it },
                            enabled = !isProcessing,
                            modifier = Modifier.testTag("confirm_monthly_purge_checkbox")
                        )
                        Text(
                            text = "Confirmo hacer el PDF y eliminar permanentemente estos ${preview.totalDeletableCount} registros del mes.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Actions
                    OutlinedButton(
                        onClick = {
                            onExportPdfOnly(selectedScope, includeResidents, includeAllResidentsForReload)
                        },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("monthly_close_pdf_only_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Solo Generar PDF del Mes (Sin Borrar)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            onExportAndDeleteMonthly(selectedScope, includeResidents, includeAllResidentsForReload)
                        },
                        enabled = !isProcessing && confirmPermanentDelete && preview.totalDeletableCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusMorosoRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("monthly_close_confirm_delete_btn")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isProcessing) "Procesando cierre..." else "Hacer PDF y Eliminar del Mes",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}
