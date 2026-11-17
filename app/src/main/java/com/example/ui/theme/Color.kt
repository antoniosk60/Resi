package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// PALETA CUIDADO VISUAL (EYE-COMFORT / SOOTHING MUTED TONES)
// Diseñada con tonos suaves, baja saturación y contraste equilibrado
// para evitar fatiga ocular y deslumbramiento.
// =============================================================================

// Brand Primary: Pizarra nórdica suave (Deep Slate / Nordic Steel)
// Sustituye al azul eléctrico chillón por un tono relajante y profesional
val BrandNavy = Color(0xFF2C3E50)         // Slate azulado templado
val BrandNavyLight = Color(0xFF4A6278)    // Tono secundario suave
val BrandNavyDark = Color(0xFF1E2B37)

// Brand Secondary: Verde salvia / bosque suave (Sage Green)
// Reemplaza al verde/cian saturado por un tono natural que descansa la vista
val BrandTeal = Color(0xFF2D6A4F)         // Bosque salvia
val BrandTealLight = Color(0xFF4D8B70)    // Muted teal
val BrandAmber = Color(0xFF9E652A)        // Ocre cálido

// Estatus de Morosidad (Rojo terracota suave, no rojo fosforescente)
val StatusMorosoRed = Color(0xFFA83A3A)     // Terracota / rosa oscuro mate
val StatusMorosoRedBg = Color(0xFFF9EAE1)   // Fondo pastel muy suave
val StatusMorosoRedDark = Color(0xFFE57373)
val StatusMorosoRedBgDark = Color(0xFF382222)

// Estatus Solvente / Pagado (Verde salvia mate)
val StatusSolventeGreen = Color(0xFF2E6F40)   // Verde suave bosque
val StatusSolventeGreenBg = Color(0xFFE8F3EB) // Fondo pastel relajante
val StatusSolventeGreenDark = Color(0xFF81C784)
val StatusSolventeGreenBgDark = Color(0xFF1B3324)

// Estatus Parcial / Pendiente (Ocre tenue)
val StatusParcialAmber = Color(0xFF996515)
val StatusParcialAmberBg = Color(0xFFFAF1E4)

// =============================================================================
// FONDOS Y SUPERFICIES (Evitan el blanco puro #FFFFFF que encandila la vista)
// =============================================================================

// Modo Claro Suave (Warm Paper / Arena suave / Perla cálido)
val BackgroundLight = Color(0xFFF5F5F2)      // Tono papel mate tipo lectura
val SurfaceLight = Color(0xFFFCFCFA)         // Superficie marfil suave
val SurfaceVariantLight = Color(0xFFEBEBE6)  // Gris perla cálido
val BorderLight = Color(0xFFDCDCD5)
val TextPrimaryLight = Color(0xFF24292F)     // Carbón suave (no negro puro)
val TextSecondaryLight = Color(0xFF57606A)   // Gris medio agradable

// Modo Oscuro Suave (Deep Charcoal / Noche cálida, no negro abismal)
val BackgroundDark = Color(0xFF13171D)       // Carbón profundo suave
val SurfaceDark = Color(0xFF1C222B)          // Pizarra nocturna
val SurfaceVariantDark = Color(0xFF26303D)   // Gris azulado mate
val BorderDark = Color(0xFF303B49)
val TextPrimaryDark = Color(0xFFE3E8EC)      // Blanco hueso suave
val TextSecondaryDark = Color(0xFF94A1B2)    // Gris claro relajante
