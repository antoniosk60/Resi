package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.AuditLog
import com.example.data.model.ResidentWithFinancials
import com.example.data.model.Tardiness
import com.example.data.model.Visit
import com.example.data.model.VisitAccessStatus
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    private val dateTimeFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val fileStampFormatter = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 30f
    private const val MARGIN_RIGHT = 565f
    private const val MAX_CONTENT_Y = 785f

    private const val REPORT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1000L

    private fun getReportsDir(context: Context): File {
        val dir = File(context.cacheDir, "reports")
        if (!dir.exists()) {
            dir.mkdirs()
        } else {
            val cutoff = System.currentTimeMillis() - REPORT_RETENTION_MILLIS
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) {
                    runCatching { file.delete() }
                }
            }
        }
        return dir
    }

    // =========================================================================
    // 1. REPORTE PDF DE RETARDOS E INCIDENCIAS (ADMIN)
    // =========================================================================

    fun exportTardinessPdf(
        context: Context,
        tardinessList: List<Tardiness>,
        generatedBy: String,
        filterSummary: String = "Historial completo"
    ): File {
        val document = PdfDocument()
        val styles = PdfStyles()
        var pageNum = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas

        var y = drawBrandHeader(
            canvas = canvas,
            styles = styles,
            reportTitle = "REPORTE OFICIAL DE RETARDOS E INCIDENCIAS",
            subtitle = "Filtro: $filterSummary • Generado por: $generatedBy"
        )

        // Summary KPI cards
        val uniqueResidents = tardinessList.map { it.residentId }.distinct().size
        val withPlates = tardinessList.count { it.vehiclePlate.isNotBlank() }

        y = drawThreeKpiBoxes(
            canvas = canvas,
            styles = styles,
            y = y,
            box1 = Triple("Total de Retardos", "${tardinessList.size}", Color.rgb(241, 245, 249) to Color.rgb(30, 58, 138)),
            box2 = Triple("Deptos. Involucrados", "$uniqueResidents", Color.rgb(254, 242, 242) to Color.rgb(185, 28, 28)),
            box3 = Triple("Con Placas Registradas", "$withPlates", Color.rgb(240, 253, 244) to Color.rgb(22, 101, 52))
        )

        fun drawTableHeader(c: Canvas, startY: Float): Float {
            styles.box.color = Color.rgb(30, 58, 138)
            c.drawRect(MARGIN_LEFT, startY, MARGIN_RIGHT, startY + 20f, styles.box)
            c.drawText("Fecha / Hora", 36f, startY + 13f, styles.tableHeader)
            c.drawText("Depto / Casa", 122f, startY + 13f, styles.tableHeader)
            c.drawText("Residente", 205f, startY + 13f, styles.tableHeader)
            c.drawText("Motivo del Retardo", 315f, startY + 13f, styles.tableHeader)
            c.drawText("Placas", 495f, startY + 13f, styles.tableHeader)
            return startY + 20f
        }

        canvas.drawText("DETALLE DE INCIDENCIAS REGISTRADAS", MARGIN_LEFT, y, styles.sectionHeader)
        y += 10f
        y = drawTableHeader(canvas, y)

        if (tardinessList.isEmpty()) {
            canvas.drawText("No hay retardos ni incidencias registradas.", 36f, y + 18f, styles.body)
        } else {
            tardinessList.forEachIndexed { index, t ->
                if (y > MAX_CONTENT_Y - 26f) {
                    drawFooter(canvas, styles, pageNum)
                    document.finishPage(page)
                    pageNum++
                    page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                    canvas = page.canvas
                    y = drawContinuationHeader(canvas, styles, "REPORTE DE RETARDOS E INCIDENCIAS (Cont.)")
                    y = drawTableHeader(canvas, y)
                }

                styles.box.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 24f, styles.box)
                canvas.drawLine(MARGIN_LEFT, y + 24f, MARGIN_RIGHT, y + 24f, styles.border)

                canvas.drawText(dateTimeFormatter.format(Date(t.timestampMillis)), 36f, y + 15f, styles.bodySmall)
                canvas.drawText(truncate(t.houseNumber, 14), 122f, y + 15f, styles.bodyBold)
                canvas.drawText(truncate(t.residentName, 19), 205f, y + 15f, styles.body)
                canvas.drawText(truncate(t.reason, 32), 315f, y + 15f, styles.body)
                canvas.drawText(t.vehiclePlate.ifBlank { "-" }.take(11), 495f, y + 15f, styles.bodySmall)
                y += 24f
            }
        }

        drawFooter(canvas, styles, pageNum)
        document.finishPage(page)

        val file = File(getReportsDir(context), "Reporte_Retardos_Gavilan181_${fileStampFormatter.format(Date())}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    // =========================================================================
    // 2. REPORTE PDF DE DIRECTORIO DE RESIDENTES Y CLAVES (ADMIN)
    // =========================================================================

    fun exportResidentsDirectoryPdf(
        context: Context,
        residents: List<ResidentWithFinancials>,
        generatedBy: String
    ): File {
        val document = PdfDocument()
        val styles = PdfStyles()
        var pageNum = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas

        var y = drawBrandHeader(
            canvas = canvas,
            styles = styles,
            reportTitle = "DIRECTORIO DE RESIDENTES, CLAVES Y ESTATUS",
            subtitle = "Generado por: $generatedBy"
        )

        val morososCount = residents.count { it.isMoroso }
        val authorizedCount = residents.size - morososCount

        y = drawThreeKpiBoxes(
            canvas = canvas,
            styles = styles,
            y = y,
            box1 = Triple("Total Deptos.", "${residents.size}", Color.rgb(241, 245, 249) to Color.rgb(30, 58, 138)),
            box2 = Triple("Sí Paga (Autorizados)", "$authorizedCount", Color.rgb(240, 253, 244) to Color.rgb(22, 101, 52)),
            box3 = Triple("Morosos (Denegados)", "$morososCount", Color.rgb(254, 242, 242) to Color.rgb(185, 28, 28))
        )

        fun drawTableHeader(c: Canvas, startY: Float): Float {
            styles.box.color = Color.rgb(30, 58, 138)
            c.drawRect(MARGIN_LEFT, startY, MARGIN_RIGHT, startY + 20f, styles.box)
            c.drawText("Edificio / Depto", 36f, startY + 13f, styles.tableHeader)
            c.drawText("Residente", 160f, startY + 13f, styles.tableHeader)
            c.drawText("Clave", 315f, startY + 13f, styles.tableHeader)
            c.drawText("Teléfono", 395f, startY + 13f, styles.tableHeader)
            c.drawText("Estatus Acceso", 480f, startY + 13f, styles.tableHeader)
            return startY + 20f
        }

        canvas.drawText("PADRON DE CLAVES Y DEPARTAMENTOS", MARGIN_LEFT, y, styles.sectionHeader)
        y += 10f
        y = drawTableHeader(canvas, y)

        if (residents.isEmpty()) {
            canvas.drawText("No hay residentes registrados.", 36f, y + 18f, styles.body)
        } else {
            residents.forEachIndexed { index, rf ->
                if (y > MAX_CONTENT_Y - 22f) {
                    drawFooter(canvas, styles, pageNum)
                    document.finishPage(page)
                    pageNum++
                    page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                    canvas = page.canvas
                    y = drawContinuationHeader(canvas, styles, "DIRECTORIO DE RESIDENTES Y CLAVES (Cont.)")
                    y = drawTableHeader(canvas, y)
                }

                val r = rf.resident
                styles.box.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, styles.box)
                canvas.drawLine(MARGIN_LEFT, y + 20f, MARGIN_RIGHT, y + 20f, styles.border)

                canvas.drawText(truncate(r.displayUnit, 22), 36f, y + 14f, styles.bodyBold)
                canvas.drawText(truncate(r.fullName, 27), 160f, y + 14f, styles.body)
                canvas.drawText(r.accessCode.ifBlank { "-" }.take(12), 315f, y + 14f, styles.bodyBold)
                canvas.drawText(r.phone.ifBlank { "-" }.take(13), 395f, y + 14f, styles.bodySmall)

                val statusPaint = if (rf.isMoroso) styles.redBold else styles.greenBold
                val statusText = if (rf.isMoroso) "DENEGADO" else "SÍ PAGA"
                canvas.drawText(statusText, 480f, y + 14f, statusPaint)
                y += 20f
            }
        }

        drawFooter(canvas, styles, pageNum)
        document.finishPage(page)

        val file = File(getReportsDir(context), "Directorio_Residentes_Gavilan181_${fileStampFormatter.format(Date())}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    // =========================================================================
    // 3. REPORTE PDF DE ACCESOS Y VISITAS EN CASETA (ADMIN)
    // =========================================================================

    fun exportVisitsPdf(
        context: Context,
        visits: List<Visit>,
        generatedBy: String
    ): File {
        val document = PdfDocument()
        val styles = PdfStyles()
        var pageNum = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas

        var y = drawBrandHeader(
            canvas = canvas,
            styles = styles,
            reportTitle = "BITÁCORA OFICIAL DE ACCESOS Y VISITAS",
            subtitle = "Caseta Yaotecatl • Generado por: $generatedBy"
        )

        val deniedCount = visits.count { it.isResidentMoroso || it.accessStatus == VisitAccessStatus.SIN_CLAVE_MOROSO }
        val insideCount = visits.count { it.isInside }

        y = drawThreeKpiBoxes(
            canvas = canvas,
            styles = styles,
            y = y,
            box1 = Triple("Total Accesos", "${visits.size}", Color.rgb(241, 245, 249) to Color.rgb(30, 58, 138)),
            box2 = Triple("Visitas Adentro", "$insideCount", Color.rgb(240, 253, 244) to Color.rgb(22, 101, 52)),
            box3 = Triple("Denegados (Moroso)", "$deniedCount", Color.rgb(254, 242, 242) to Color.rgb(185, 28, 28))
        )

        fun drawTableHeader(c: Canvas, startY: Float): Float {
            styles.box.color = Color.rgb(13, 148, 136)
            c.drawRect(MARGIN_LEFT, startY, MARGIN_RIGHT, startY + 20f, styles.box)
            c.drawText("Entrada", 36f, startY + 13f, styles.tableHeader)
            c.drawText("Visitante", 122f, startY + 13f, styles.tableHeader)
            c.drawText("Destino", 250f, startY + 13f, styles.tableHeader)
            c.drawText("Placas", 385f, startY + 13f, styles.tableHeader)
            c.drawText("Resultado", 460f, startY + 13f, styles.tableHeader)
            return startY + 20f
        }

        canvas.drawText("REGISTROS DE CONTROL DE ACCESOS", MARGIN_LEFT, y, styles.sectionHeader)
        y += 10f
        y = drawTableHeader(canvas, y)

        if (visits.isEmpty()) {
            canvas.drawText("No hay visitas registradas.", 36f, y + 18f, styles.body)
        } else {
            visits.forEachIndexed { index, v ->
                if (y > MAX_CONTENT_Y - 22f) {
                    drawFooter(canvas, styles, pageNum)
                    document.finishPage(page)
                    pageNum++
                    page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                    canvas = page.canvas
                    y = drawContinuationHeader(canvas, styles, "BITÁCORA DE ACCESOS Y VISITAS (Cont.)")
                    y = drawTableHeader(canvas, y)
                }

                styles.box.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, styles.box)
                canvas.drawLine(MARGIN_LEFT, y + 20f, MARGIN_RIGHT, y + 20f, styles.border)

                val denied = v.isResidentMoroso || v.accessStatus == VisitAccessStatus.SIN_CLAVE_MOROSO
                canvas.drawText(dateTimeFormatter.format(Date(v.entryTimestampMillis)), 36f, y + 14f, styles.bodySmall)
                canvas.drawText(truncate(v.visitorName, 22), 122f, y + 14f, styles.bodyBold)
                canvas.drawText(truncate("${v.building} ${v.apartment}", 22), 250f, y + 14f, styles.body)
                canvas.drawText(v.visitorVehiclePlates.ifBlank { "-" }.take(11), 385f, y + 14f, styles.bodySmall)
                canvas.drawText(
                    if (denied) "DENEGADO" else "AUTORIZADO",
                    460f,
                    y + 14f,
                    if (denied) styles.redBold else styles.greenBold
                )
                y += 20f
            }
        }

        drawFooter(canvas, styles, pageNum)
        document.finishPage(page)

        val file = File(getReportsDir(context), "Bitacora_Accesos_Gavilan181_${fileStampFormatter.format(Date())}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    // =========================================================================
    // 4. REPORTE PDF DE AUDITORÍA DEL SISTEMA (ADMIN)
    // =========================================================================

    fun exportAuditLogsPdf(
        context: Context,
        auditLogs: List<AuditLog>,
        generatedBy: String
    ): File {
        val document = PdfDocument()
        val styles = PdfStyles()
        var pageNum = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas

        var y = drawBrandHeader(
            canvas = canvas,
            styles = styles,
            reportTitle = "BITÁCORA DE AUDITORÍA Y TRAZABILIDAD",
            subtitle = "Total de eventos: ${auditLogs.size} • Generado por: $generatedBy"
        )

        fun drawTableHeader(c: Canvas, startY: Float): Float {
            styles.box.color = Color.rgb(30, 58, 138)
            c.drawRect(MARGIN_LEFT, startY, MARGIN_RIGHT, startY + 20f, styles.box)
            c.drawText("Fecha / Hora", 36f, startY + 13f, styles.tableHeader)
            c.drawText("Acción", 122f, startY + 13f, styles.tableHeader)
            c.drawText("Usuario", 195f, startY + 13f, styles.tableHeader)
            c.drawText("Evento / Detalle", 300f, startY + 13f, styles.tableHeader)
            return startY + 20f
        }

        canvas.drawText("HISTORIAL DE MOVIMIENTOS EN LA NUBE", MARGIN_LEFT, y, styles.sectionHeader)
        y += 10f
        y = drawTableHeader(canvas, y)

        if (auditLogs.isEmpty()) {
            canvas.drawText("No hay eventos de auditoría registrados.", 36f, y + 18f, styles.body)
        } else {
            auditLogs.forEachIndexed { index, log ->
                if (y > MAX_CONTENT_Y - 22f) {
                    drawFooter(canvas, styles, pageNum)
                    document.finishPage(page)
                    pageNum++
                    page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                    canvas = page.canvas
                    y = drawContinuationHeader(canvas, styles, "BITÁCORA DE AUDITORÍA (Cont.)")
                    y = drawTableHeader(canvas, y)
                }

                styles.box.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, styles.box)
                canvas.drawLine(MARGIN_LEFT, y + 20f, MARGIN_RIGHT, y + 20f, styles.border)

                canvas.drawText(dateTimeFormatter.format(Date(log.timestampMillis)), 36f, y + 14f, styles.bodySmall)
                canvas.drawText(truncate(log.actionType, 12), 122f, y + 14f, styles.bodyBold)
                canvas.drawText(truncate(log.userName, 18), 195f, y + 14f, styles.body)
                canvas.drawText(truncate("${log.title}: ${log.details}", 46), 300f, y + 14f, styles.bodySmall)
                y += 20f
            }
        }

        drawFooter(canvas, styles, pageNum)
        document.finishPage(page)

        val file = File(getReportsDir(context), "Auditoria_Gavilan181_${fileStampFormatter.format(Date())}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    // =========================================================================
    // 5. CIERRE MENSUAL CONSOLIDADO EN PDF (TODOS LOS REGISTROS DEL MES)
    // =========================================================================

    fun exportMonthlyArchivePdf(
        context: Context,
        periodLabel: String,
        residents: List<ResidentWithFinancials>,
        visits: List<Visit>,
        tardinessList: List<Tardiness>,
        auditLogs: List<AuditLog>,
        generatedBy: String
    ): File {
        val document = PdfDocument()
        val styles = PdfStyles()
        var pageNum = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = page.canvas

        var y = drawBrandHeader(
            canvas = canvas,
            styles = styles,
            reportTitle = "CIERRE MENSUAL INTEGRAL • TODOS LOS REGISTROS DEL MES",
            subtitle = "Período: $periodLabel • Generado por: $generatedBy"
        )

        val morososCount = residents.count { it.isMoroso }
        val authorizedCount = residents.size - morososCount

        y = drawThreeKpiBoxes(
            canvas = canvas,
            styles = styles,
            y = y,
            box1 = Triple("Residentes / Claves", "${residents.size}", Color.rgb(241, 245, 249) to Color.rgb(30, 58, 138)),
            box2 = Triple("Sí Paga (Autorizados)", "$authorizedCount", Color.rgb(240, 253, 244) to Color.rgb(22, 101, 52)),
            box3 = Triple("Morosos (Denegados)", "$morososCount", Color.rgb(254, 242, 242) to Color.rgb(185, 28, 28))
        )

        y = drawThreeKpiBoxes(
            canvas = canvas,
            styles = styles,
            y = y,
            box1 = Triple("Accesos y Visitas", "${visits.size}", Color.rgb(240, 253, 250) to Color.rgb(13, 148, 136)),
            box2 = Triple("Retardos / Incidencias", "${tardinessList.size}", Color.rgb(255, 247, 237) to Color.rgb(180, 83, 9)),
            box3 = Triple("Eventos Auditoría", "${auditLogs.size}", Color.rgb(248, 250, 252) to Color.rgb(51, 65, 85))
        )

        fun ensureSpace(neededHeight: Float, contTitle: String, headerDrawer: ((Canvas, Float) -> Float)? = null) {
            if (y > MAX_CONTENT_Y - neededHeight) {
                drawFooter(canvas, styles, pageNum)
                document.finishPage(page)
                pageNum++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = drawContinuationHeader(canvas, styles, contTitle)
                if (headerDrawer != null) {
                    y = headerDrawer(canvas, y)
                }
            }
        }

        // --- SECTION 1: RESIDENTES Y CLAVES ---
        fun drawResidentsHeader(c: Canvas, startY: Float): Float {
            styles.box.color = Color.rgb(30, 58, 138)
            c.drawRect(MARGIN_LEFT, startY, MARGIN_RIGHT, startY + 20f, styles.box)
            c.drawText("Edificio / Depto", 36f, startY + 13f, styles.tableHeader)
            c.drawText("Residente", 160f, startY + 13f, styles.tableHeader)
            c.drawText("Clave", 315f, startY + 13f, styles.tableHeader)
            c.drawText("Teléfono", 395f, startY + 13f, styles.tableHeader)
            c.drawText("Estatus Acceso", 480f, startY + 13f, styles.tableHeader)
            return startY + 20f
        }

        ensureSpace(50f, "CIERRE MENSUAL ($periodLabel) - Cont.")
        canvas.drawText("1. PADRÓN DE RESIDENTES Y CLAVES (${residents.size})", MARGIN_LEFT, y, styles.sectionHeader)
        y += 10f
        y = drawResidentsHeader(canvas, y)

        if (residents.isEmpty()) {
            canvas.drawText("Sin residentes incluidos en este cierre.", 36f, y + 16f, styles.body)
            y += 26f
        } else {
            residents.forEachIndexed { index, rf ->
                ensureSpace(24f, "1. PADRÓN DE RESIDENTES (Cont.)") { c, startY -> drawResidentsHeader(c, startY) }
                val r = rf.resident
                styles.box.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, styles.box)
                canvas.drawLine(MARGIN_LEFT, y + 20f, MARGIN_RIGHT, y + 20f, styles.border)

                canvas.drawText(truncate(r.displayUnit, 22), 36f, y + 14f, styles.bodyBold)
                canvas.drawText(truncate(r.fullName, 27), 160f, y + 14f, styles.body)
                canvas.drawText(r.accessCode.ifBlank { "-" }.take(12), 315f, y + 14f, styles.bodyBold)
                canvas.drawText(r.phone.ifBlank { "-" }.take(13), 395f, y + 14f, styles.bodySmall)
                canvas.drawText(
                    if (rf.isMoroso) "DENEGADO" else "SÍ PAGA",
                    480f,
                    y + 14f,
                    if (rf.isMoroso) styles.redBold else styles.greenBold
                )
                y += 20f
            }
            y += 16f
        }

        // --- SECTION 2: ACCESOS Y VISITAS ---
        fun drawVisitsHeader(c: Canvas, startY: Float): Float {
            styles.box.color = Color.rgb(13, 148, 136)
            c.drawRect(MARGIN_LEFT, startY, MARGIN_RIGHT, startY + 20f, styles.box)
            c.drawText("Entrada", 36f, startY + 13f, styles.tableHeader)
            c.drawText("Visitante", 122f, startY + 13f, styles.tableHeader)
            c.drawText("Destino", 250f, startY + 13f, styles.tableHeader)
            c.drawText("Placas", 385f, startY + 13f, styles.tableHeader)
            c.drawText("Resultado", 460f, startY + 13f, styles.tableHeader)
            return startY + 20f
        }

        ensureSpace(50f, "CIERRE MENSUAL ($periodLabel) - Cont.")
        canvas.drawText("2. BITÁCORA DE ACCESOS Y VISITAS (${visits.size})", MARGIN_LEFT, y, styles.sectionHeader)
        y += 10f
        y = drawVisitsHeader(canvas, y)

        if (visits.isEmpty()) {
            canvas.drawText("Sin accesos ni visitas registrados en el período.", 36f, y + 16f, styles.body)
            y += 26f
        } else {
            visits.forEachIndexed { index, v ->
                ensureSpace(24f, "2. ACCESOS Y VISITAS (Cont.)") { c, startY -> drawVisitsHeader(c, startY) }
                styles.box.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, styles.box)
                canvas.drawLine(MARGIN_LEFT, y + 20f, MARGIN_RIGHT, y + 20f, styles.border)

                val denied = v.isResidentMoroso || v.accessStatus == VisitAccessStatus.SIN_CLAVE_MOROSO
                canvas.drawText(dateTimeFormatter.format(Date(v.entryTimestampMillis)), 36f, y + 14f, styles.bodySmall)
                canvas.drawText(truncate(v.visitorName, 22), 122f, y + 14f, styles.bodyBold)
                canvas.drawText(truncate("${v.building} ${v.apartment}", 22), 250f, y + 14f, styles.body)
                canvas.drawText(v.visitorVehiclePlates.ifBlank { "-" }.take(11), 385f, y + 14f, styles.bodySmall)
                canvas.drawText(
                    if (denied) "DENEGADO" else "AUTORIZADO",
                    460f,
                    y + 14f,
                    if (denied) styles.redBold else styles.greenBold
                )
                y += 20f
            }
            y += 16f
        }

        // --- SECTION 3: RETARDOS E INCIDENCIAS ---
        fun drawTardinessHeader(c: Canvas, startY: Float): Float {
            styles.box.color = Color.rgb(180, 83, 9)
            c.drawRect(MARGIN_LEFT, startY, MARGIN_RIGHT, startY + 20f, styles.box)
            c.drawText("Fecha / Hora", 36f, startY + 13f, styles.tableHeader)
            c.drawText("Depto / Casa", 122f, startY + 13f, styles.tableHeader)
            c.drawText("Residente", 205f, startY + 13f, styles.tableHeader)
            c.drawText("Motivo del Retardo", 315f, startY + 13f, styles.tableHeader)
            c.drawText("Placas", 495f, startY + 13f, styles.tableHeader)
            return startY + 20f
        }

        ensureSpace(50f, "CIERRE MENSUAL ($periodLabel) - Cont.")
        canvas.drawText("3. RETARDOS E INCIDENCIAS (${tardinessList.size})", MARGIN_LEFT, y, styles.sectionHeader)
        y += 10f
        y = drawTardinessHeader(canvas, y)

        if (tardinessList.isEmpty()) {
            canvas.drawText("Sin retardos registrados en el período.", 36f, y + 16f, styles.body)
            y += 26f
        } else {
            tardinessList.forEachIndexed { index, t ->
                ensureSpace(26f, "3. RETARDOS E INCIDENCIAS (Cont.)") { c, startY -> drawTardinessHeader(c, startY) }
                styles.box.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 22f, styles.box)
                canvas.drawLine(MARGIN_LEFT, y + 22f, MARGIN_RIGHT, y + 22f, styles.border)

                canvas.drawText(dateTimeFormatter.format(Date(t.timestampMillis)), 36f, y + 14f, styles.bodySmall)
                canvas.drawText(truncate(t.houseNumber, 14), 122f, y + 14f, styles.bodyBold)
                canvas.drawText(truncate(t.residentName, 19), 205f, y + 14f, styles.body)
                canvas.drawText(truncate(t.reason, 32), 315f, y + 14f, styles.body)
                canvas.drawText(t.vehiclePlate.ifBlank { "-" }.take(11), 495f, y + 14f, styles.bodySmall)
                y += 22f
            }
            y += 16f
        }

        // --- SECTION 4: AUDITORÍA ---
        fun drawAuditHeader(c: Canvas, startY: Float): Float {
            styles.box.color = Color.rgb(51, 65, 85)
            c.drawRect(MARGIN_LEFT, startY, MARGIN_RIGHT, startY + 20f, styles.box)
            c.drawText("Fecha / Hora", 36f, startY + 13f, styles.tableHeader)
            c.drawText("Acción", 122f, startY + 13f, styles.tableHeader)
            c.drawText("Usuario", 195f, startY + 13f, styles.tableHeader)
            c.drawText("Evento / Detalle", 300f, startY + 13f, styles.tableHeader)
            return startY + 20f
        }

        ensureSpace(50f, "CIERRE MENSUAL ($periodLabel) - Cont.")
        canvas.drawText("4. BITÁCORA DE AUDITORÍA (${auditLogs.size})", MARGIN_LEFT, y, styles.sectionHeader)
        y += 10f
        y = drawAuditHeader(canvas, y)

        if (auditLogs.isEmpty()) {
            canvas.drawText("Sin eventos de auditoría en el período.", 36f, y + 16f, styles.body)
        } else {
            auditLogs.forEachIndexed { index, log ->
                ensureSpace(24f, "4. AUDITORÍA (Cont.)") { c, startY -> drawAuditHeader(c, startY) }
                styles.box.color = if (index % 2 == 0) Color.WHITE else Color.rgb(248, 250, 252)
                canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 20f, styles.box)
                canvas.drawLine(MARGIN_LEFT, y + 20f, MARGIN_RIGHT, y + 20f, styles.border)

                canvas.drawText(dateTimeFormatter.format(Date(log.timestampMillis)), 36f, y + 14f, styles.bodySmall)
                canvas.drawText(truncate(log.actionType, 12), 122f, y + 14f, styles.bodyBold)
                canvas.drawText(truncate(log.userName, 18), 195f, y + 14f, styles.body)
                canvas.drawText(truncate("${log.title}: ${log.details}", 46), 300f, y + 14f, styles.bodySmall)
                y += 20f
            }
        }

        drawFooter(canvas, styles, pageNum)
        document.finishPage(page)

        val safePeriod = periodLabel.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(getReportsDir(context), "Cierre_Mensual_${safePeriod}_${fileStampFormatter.format(Date())}.pdf")
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    // =========================================================================
    // SYSTEM SHARE INTENT HELPER
    // =========================================================================

    fun shareExportedFile(context: Context, file: File, mimeType: String, title: String) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "Adjunto reporte oficial de Gavilán 181 (Yaotecatl Seguridad Privada): ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Exportar / Compartir PDF ($title)").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (e: android.content.ActivityNotFoundException) {
            throw IllegalStateException("No hay una aplicación compatible para abrir o compartir el archivo PDF.", e)
        }
    }

    // =========================================================================
    // SHARED PDF DRAWING HELPERS
    // =========================================================================

    private class PdfStyles {
        val title = Paint().apply {
            color = Color.rgb(30, 58, 138)
            textSize = 16.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitle = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val sectionHeader = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val kpiValue = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val tableHeader = Paint().apply {
            color = Color.WHITE
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 8.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val bodyBold = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 8.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodySmall = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val greenBold = Paint().apply {
            color = Color.rgb(21, 128, 61)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val redBold = Paint().apply {
            color = Color.rgb(185, 28, 28)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val footer = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 8.2f
            isAntiAlias = true
        }
        val box = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val border = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
            color = Color.rgb(226, 232, 240)
            isAntiAlias = true
        }
    }

    private fun drawBrandHeader(
        canvas: Canvas,
        styles: PdfStyles,
        reportTitle: String,
        subtitle: String
    ): Float {
        var y = 34f
        styles.box.color = Color.rgb(30, 58, 138)
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 6f, styles.box)
        y += 24f

        canvas.drawText("GAVILÁN 181 • SEGURIDAD PRIVADA YAOTECATL", MARGIN_LEFT, y, styles.title)
        y += 15f
        canvas.drawText(reportTitle, MARGIN_LEFT, y, styles.sectionHeader)
        y += 13f
        canvas.drawText("Emisión: ${dateTimeFormatter.format(Date())} • $subtitle", MARGIN_LEFT, y, styles.subtitle)
        y += 18f
        return y
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        styles: PdfStyles,
        title: String
    ): Float {
        var y = 34f
        styles.box.color = Color.rgb(30, 58, 138)
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + 4f, styles.box)
        y += 20f
        canvas.drawText("GAVILÁN 181 • $title", MARGIN_LEFT, y, styles.sectionHeader)
        y += 14f
        return y
    }

    private fun drawThreeKpiBoxes(
        canvas: Canvas,
        styles: PdfStyles,
        y: Float,
        box1: Triple<String, String, Pair<Int, Int>>,
        box2: Triple<String, String, Pair<Int, Int>>,
        box3: Triple<String, String, Pair<Int, Int>>
    ): Float {
        val boxW = 171f
        val boxH = 44f
        val items = listOf(
            30f to box1,
            212f to box2,
            394f to box3
        )
        items.forEach { (x, data) ->
            val (label, value, colors) = data
            styles.box.color = colors.first
            canvas.drawRoundRect(x, y, x + boxW, y + boxH, 6f, 6f, styles.box)
            canvas.drawText(label, x + 10f, y + 16f, styles.subtitle)
            val valPaint = Paint(styles.kpiValue).apply { color = colors.second }
            canvas.drawText(value, x + 10f, y + 35f, valPaint)
        }
        return y + boxH + 18f
    }

    private fun drawFooter(canvas: Canvas, styles: PdfStyles, pageNum: Int) {
        canvas.drawText(
            "Documento operativo emitido por Condominio Gavilán 181 - Yaotecatl Seguridad Privada.",
            MARGIN_LEFT,
            815f,
            styles.footer
        )
        canvas.drawText("Página $pageNum", 520f, 815f, styles.footer)
    }

    private fun truncate(text: String, maxChars: Int): String {
        val clean = text.replace("\n", " ").trim()
        return if (clean.length > maxChars) clean.take(maxChars - 1) + "…" else clean
    }
}
