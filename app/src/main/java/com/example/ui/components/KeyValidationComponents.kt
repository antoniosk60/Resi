package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ResidentWithFinancials
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.StatusMorosoRed
import com.example.ui.theme.StatusMorosoRedBg
import com.example.ui.theme.StatusMorosoRedBgDark
import com.example.ui.theme.StatusMorosoRedDark
import com.example.ui.theme.StatusParcialAmber
import com.example.ui.theme.StatusParcialAmberBg
import com.example.ui.theme.StatusSolventeGreen
import com.example.ui.theme.StatusSolventeGreenBg
import com.example.ui.theme.StatusSolventeGreenBgDark
import com.example.ui.theme.StatusSolventeGreenDark

sealed class KeyLookupResult {
    data object Idle : KeyLookupResult()
    data class Authorized(val residentWithFinancials: ResidentWithFinancials) : KeyLookupResult()
    data class DeniedNonPayment(val residentId: Long) : KeyLookupResult()
    data class NotFound(val searchedKey: String) : KeyLookupResult()
}

fun findResidentByAccessKey(
    rawQuery: String,
    residents: List<ResidentWithFinancials>
): ResidentWithFinancials? {
    val trimmed = rawQuery.trim().uppercase()
    if (trimmed.isBlank()) return null

    // 1. Exact case-insensitive match
    val exact = residents.firstOrNull { rf ->
        val code = rf.resident.accessCode.trim().uppercase()
        code.isNotBlank() && code == trimmed
    }
    if (exact != null) return exact

    // 2. Normalized match ignoring hyphens/spaces (e.g. "CLVA101" matches "CLV-A101")
    val compactQuery = trimmed.replace("-", "").replace(" ", "")
    if (compactQuery.length >= 3) {
        return residents.firstOrNull { rf ->
            val compactCode = rf.resident.accessCode.trim().uppercase()
                .replace("-", "")
                .replace(" ", "")
            compactCode.isNotBlank() && compactCode == compactQuery
        }
    }
    return null
}

fun evaluateKeyLookup(
    rawQuery: String,
    residents: List<ResidentWithFinancials>,
    forceShowNotFound: Boolean
): KeyLookupResult {
    val trimmed = rawQuery.trim()
    if (trimmed.isBlank()) return KeyLookupResult.Idle

    val matched = findResidentByAccessKey(trimmed, residents)
    return when {
        matched != null && (matched.isMoroso || matched.resident.isMoroso) -> KeyLookupResult.DeniedNonPayment(matched.resident.id)
        matched != null -> KeyLookupResult.Authorized(matched)
        forceShowNotFound || trimmed.length >= 3 -> KeyLookupResult.NotFound(trimmed.uppercase())
        else -> KeyLookupResult.Idle
    }
}

@Composable
fun KeyLookupAndValidationPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    residents: List<ResidentWithFinancials>,
    isDark: Boolean,
    onLogAuthorizedVisit: (Long) -> Unit,
    onLogDeniedAttempt: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var forceSubmit by remember { mutableStateOf(false) }

    val lookupResult = remember(query, residents, forceSubmit) {
        evaluateKeyLookup(query, residents, forceSubmit)
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("key_lookup_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Buscar y Validar Clave",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Caseta Yaotecatl • Edificio, Depto y Estatus",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = BrandTeal.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${residents.size} claves",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) StatusSolventeGreenDark else BrandTeal,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = {
                    forceSubmit = false
                    onQueryChange(it.uppercase())
                },
                label = { Text("Ingresa la CLAVE de acceso") },
                placeholder = { Text("Ej. CLV-A101, CLV-A205") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Clave de acceso"
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(
                            onClick = {
                                forceSubmit = false
                                onQueryChange("")
                            },
                            modifier = Modifier.testTag("clear_key_search_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar clave")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        forceSubmit = true
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("key_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    forceSubmit = true
                    focusManager.clearFocus()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("validate_key_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Buscar Clave",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            AnimatedContent(
                targetState = lookupResult,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "key_validation_result_transition"
            ) { result ->
                when (result) {
                    is KeyLookupResult.Idle -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Escribe una clave para consultar al instante edificio, departamento y si está al corriente, o si tiene acceso denegado por falta de pago.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    is KeyLookupResult.Authorized -> {
                        AuthorizedKeyResultCard(
                            rf = result.residentWithFinancials,
                            isDark = isDark,
                            onLogAuthorizedVisit = { onLogAuthorizedVisit(result.residentWithFinancials.resident.id) }
                        )
                    }

                    is KeyLookupResult.DeniedNonPayment -> {
                        DeniedNonPaymentResultCard(
                            isDark = isDark,
                            onLogDeniedAttempt = onLogDeniedAttempt?.let { cb ->
                                { cb(result.residentId) }
                            }
                        )
                    }

                    is KeyLookupResult.NotFound -> {
                        NotFoundKeyResultCard(
                            searchedKey = result.searchedKey,
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuthorizedKeyResultCard(
    rf: ResidentWithFinancials,
    isDark: Boolean,
    onLogAuthorizedVisit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val res = rf.resident
    val greenAccent = if (isDark) StatusSolventeGreenDark else StatusSolventeGreen
    val greenBg = if (isDark) StatusSolventeGreenBgDark else StatusSolventeGreenBg
    val checkTint = if (isDark) Color(0xFF0E2214) else Color.White

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .testTag("key_result_authorized_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = greenBg),
        border = BorderStroke(2.dp, greenAccent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Prominent Visual Status Indicator Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(greenAccent)
                        .testTag("status_icon_authorized"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Acceso autorizado: clave correcta y al corriente",
                        tint = checkTint,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = greenAccent,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("status_badge_authorized")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = checkTint,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ACCESO AUTORIZADO",
                                color = checkTint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "CLAVE CORRECTA • SÍ PAGA",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = greenAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Structured Result Details: EDIFICIO & DEPARTAMENTO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, greenAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Apartment,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EDIFICIO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = res.building.ifBlank { "Gavilán 181" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("result_building")
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, greenAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DEPARTAMENTO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = res.apartment.ifBlank { res.houseNumber },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("result_apartment")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Payment Status & Validated Key Row
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, greenAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = greenAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "ESTATUS DE PAGO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "SÍ PAGA (Al corriente)",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = greenAccent,
                                modifier = Modifier.testTag("result_payment_status")
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CLAVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = res.accessCode.ifBlank { "OK" },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("result_access_code")
                        )
                    }
                }
            }

            if (res.fullName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Titular: ${res.fullName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            if (onLogAuthorizedVisit != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onLogAuthorizedVisit,
                    colors = ButtonDefaults.buttonColors(containerColor = greenAccent),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("log_authorized_visit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = checkTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Registrar Entrada",
                        color = checkTint,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DeniedNonPaymentResultCard(
    isDark: Boolean,
    onLogDeniedAttempt: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val redAccent = if (isDark) StatusMorosoRedDark else StatusMorosoRed
    val redBg = if (isDark) StatusMorosoRedBgDark else StatusMorosoRedBg
    val iconTint = if (isDark) Color(0xFF381010) else Color.White

    // Strict business rule: When moroso, show ONLY "ACCESO DENEGADO POR FALTA DE PAGO"
    // with prominent red warning visual status indicator (no unit/building/debt details).
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .testTag("key_result_denied_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = redBg),
        border = BorderStroke(2.dp, redAccent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(redAccent)
                    .testTag("status_icon_denied"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Advertencia: acceso denegado por falta de pago",
                    tint = iconTint,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = redAccent,
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("status_badge_denied")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "MOROSO • SIN ACCESO",
                        color = iconTint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "ACCESO DENEGADO POR FALTA DE PAGO",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = redAccent,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.testTag("denied_nonpayment_headline")
            )

            if (onLogDeniedAttempt != null) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onLogDeniedAttempt,
                    border = BorderStroke(1.5.dp, redAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = redAccent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("log_denied_visit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Registrar Intento Denegado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun NotFoundKeyResultCard(
    searchedKey: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val amberAccent = StatusParcialAmber
    val amberBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else StatusParcialAmberBg

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .testTag("key_result_not_found_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = amberBg),
        border = BorderStroke(1.5.dp, amberAccent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(amberAccent)
                    .testTag("status_icon_not_found"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyOff,
                    contentDescription = "Clave no encontrada",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CLAVE NO ENCONTRADA",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isDark) MaterialTheme.colorScheme.onSurface else amberAccent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "La clave \"$searchedKey\" no coincide con ningún departamento registrado en Gavilán 181.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
