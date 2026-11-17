package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AuditLog
import com.example.data.model.Resident
import com.example.data.model.ResidentWithFinancials
import com.example.data.model.Tardiness
import com.example.data.model.Visit
import com.example.data.model.VisitAccessStatus
import com.example.util.ReportExporter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import java.io.File
import java.io.OutputStream

@Implements(PdfDocument::class)
class TestShadowPdfDocument {
    private var pagesCount = 0

    @Implementation
    fun __constructor__() {}

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        pagesCount++
        return Shadow.newInstanceOf(PdfDocument.Page::class.java)
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {}

    @Implementation
    fun writeTo(out: OutputStream) {
        out.write("%PDF-1.4\n%Pages=$pagesCount\n%%EOF\n".toByteArray(Charsets.US_ASCII))
    }

    @Implementation
    fun close() {}
}

@Implements(PdfDocument.Page::class)
class TestShadowPdfPage {
    private val canvas = Canvas()

    @Implementation
    fun getCanvas(): Canvas = canvas
}

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    shadows = [TestShadowPdfDocument::class, TestShadowPdfPage::class]
)
class ReportExporterTest {

    private fun assertValidPdf(file: File, expectedMultiPage: Boolean = false) {
        assertTrue("Expected PDF file to exist: ${file.absolutePath}", file.exists())
        assertTrue("Expected non-empty PDF file", file.length() > 10L)
        val content = file.readText(Charsets.US_ASCII)
        assertTrue("Expected %PDF- header, got: $content", content.startsWith("%PDF-"))
        if (expectedMultiPage) {
            assertTrue("Expected multipage PDF, got: $content", !content.contains("%Pages=1\n"))
        }
    }

    @Test
    fun `exportTardinessPdf generates valid multipage PDF and resolves FileProvider uri`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val items = (1..48).map { i ->
            Tardiness(
                id = i.toLong(),
                residentId = (i % 10 + 1).toLong(),
                residentName = "Residente Prueba $i",
                houseNumber = "Torre A - Depto ${100 + i}",
                timestampMillis = 1731500000000L + i * 60_000L,
                reason = "Ingreso vehicular fuera de horario #$i",
                vehiclePlate = if (i % 2 == 0) "ABC-$i" else "",
                registeredBy = "Administrador"
            )
        }

        val pdfFile = ReportExporter.exportTardinessPdf(
            context = context,
            tardinessList = items,
            generatedBy = "Admin Test",
            filterSummary = "Historial completo"
        )
        assertValidPdf(pdfFile, expectedMultiPage = true)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        assertTrue(uri.toString().contains("reports/"))
    }

    @Test
    fun `exportTardinessPdf handles empty list cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfFile = ReportExporter.exportTardinessPdf(
            context = context,
            tardinessList = emptyList(),
            generatedBy = "Admin Test",
            filterSummary = "Sin filtros"
        )
        assertValidPdf(pdfFile, expectedMultiPage = false)
    }

    @Test
    fun `exportResidentsDirectoryPdf generates valid multipage PDF`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val residents = (1..45).map { i ->
            ResidentWithFinancials(
                resident = Resident(
                    id = i.toLong(),
                    fullName = "Propietario $i",
                    houseNumber = "Edificio B - Depto $i",
                    building = "Edificio B",
                    apartment = "$i",
                    accessCode = "CLV-${1000 + i}",
                    phone = "55-1234-56${i.toString().padStart(2, '0')}",
                    isMoroso = i % 3 == 0
                ),
                isMoroso = i % 3 == 0
            )
        }

        val pdfFile = ReportExporter.exportResidentsDirectoryPdf(
            context = context,
            residents = residents,
            generatedBy = "Admin Test"
        )
        assertValidPdf(pdfFile, expectedMultiPage = true)
    }

    @Test
    fun `exportVisitsPdf and exportAuditLogsPdf generate valid multipage PDFs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val visits = (1..42).map { i ->
            Visit(
                id = i.toLong(),
                visitorName = "Visitante $i",
                building = "Torre 1",
                apartment = "${200 + i}",
                residentId = i.toLong(),
                residentName = "Residente $i",
                visitorVehiclePlates = "XYZ-$i",
                isResidentMoroso = i % 4 == 0,
                accessStatus = if (i % 4 == 0) VisitAccessStatus.SIN_CLAVE_MOROSO else VisitAccessStatus.PERMITIDO,
                accessCodeUsed = "KEY-$i",
                entryTimestampMillis = 1731500000000L + i * 30_000L
            )
        }

        val visitsPdf = ReportExporter.exportVisitsPdf(
            context = context,
            visits = visits,
            generatedBy = "Admin Test"
        )
        assertValidPdf(visitsPdf, expectedMultiPage = true)

        val logs = (1..42).map { i ->
            AuditLog(
                id = i.toLong(),
                actionType = if (i % 3 == 0) "ELIMINAR" else "MODIFICAR",
                entityType = "RESIDENTE",
                entityId = i.toLong(),
                title = "Evento $i",
                details = "Detalle operativo del evento $i en Gavilán 181",
                userName = "Administrador",
                userRole = "ADMINISTRADOR",
                timestampMillis = 1731500000000L + i * 10_000L
            )
        }

        val auditPdf = ReportExporter.exportAuditLogsPdf(
            context = context,
            auditLogs = logs,
            generatedBy = "Admin Test"
        )
        assertValidPdf(auditPdf, expectedMultiPage = true)
    }

    @Test
    fun `exportMonthlyArchivePdf generates valid multipage consolidated PDF across all 4 sections`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val residents = (1..35).map { i ->
            ResidentWithFinancials(
                resident = Resident(
                    id = i.toLong(),
                    fullName = "Residente Mensual $i",
                    houseNumber = "Edificio A - Depto $i",
                    building = "Edificio A",
                    apartment = "$i",
                    accessCode = "MES-${100 + i}",
                    phone = "55-0000-11${i.toString().padStart(2, '0')}",
                    isMoroso = i % 5 == 0
                ),
                isMoroso = i % 5 == 0
            )
        }
        val visits = (1..35).map { i ->
            Visit(
                id = i.toLong(),
                visitorName = "Visitante Mensual $i",
                building = "Edificio A",
                apartment = "$i",
                residentId = i.toLong(),
                residentName = "Residente Mensual $i",
                visitorVehiclePlates = "MNS-$i",
                isResidentMoroso = i % 5 == 0,
                accessStatus = if (i % 5 == 0) VisitAccessStatus.SIN_CLAVE_MOROSO else VisitAccessStatus.PERMITIDO,
                accessCodeUsed = "MES-${100 + i}",
                entryTimestampMillis = 1731500000000L + i * 60_000L
            )
        }
        val tardiness = (1..35).map { i ->
            Tardiness(
                id = i.toLong(),
                residentId = i.toLong(),
                residentName = "Residente Mensual $i",
                houseNumber = "Edificio A - $i",
                timestampMillis = 1731500000000L + i * 45_000L,
                reason = "Motivo cierre mensual #$i",
                vehiclePlate = "PLQ-$i",
                registeredBy = "Admin"
            )
        }
        val logs = (1..35).map { i ->
            AuditLog(
                id = i.toLong(),
                actionType = "ACCESO",
                entityType = "Visita",
                entityId = i.toLong(),
                title = "Movimiento mensual $i",
                details = "Registro sincronizado en caseta #$i",
                userName = "Capturista",
                userRole = "CAPTURISTA",
                timestampMillis = 1731500000000L + i * 20_000L
            )
        }

        val archivePdf = ReportExporter.exportMonthlyArchivePdf(
            context = context,
            periodLabel = "Noviembre 2026",
            residents = residents,
            visits = visits,
            tardinessList = tardiness,
            auditLogs = logs,
            generatedBy = "Admin Gavilán"
        )
        assertValidPdf(archivePdf, expectedMultiPage = true)
        assertTrue(archivePdf.name.startsWith("Cierre_Mensual_"))
        assertTrue(archivePdf.parentFile?.canonicalPath == File(context.cacheDir, "reports").canonicalPath)
    }

    @Test
    fun `exportMonthlyArchivePdf handles empty lists cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val emptyArchivePdf = ReportExporter.exportMonthlyArchivePdf(
            context = context,
            periodLabel = "Diciembre 2026",
            residents = emptyList(),
            visits = emptyList(),
            tardinessList = emptyList(),
            auditLogs = emptyList(),
            generatedBy = "Admin Gavilán"
        )
        assertValidPdf(emptyArchivePdf, expectedMultiPage = false)
    }
}
