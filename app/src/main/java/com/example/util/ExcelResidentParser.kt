package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ExcelResidentRow(
    val rowIndex: Int,
    val building: String,
    val apartment: String,
    val accessCode: String,
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val vehiclePlates: String = "",
    val notes: String = "",
    val isMoroso: Boolean = false
)

data class ExcelImportResult(
    val fileName: String,
    val rows: List<ExcelResidentRow>,
    val detectedHeaders: List<String>,
    val warnings: List<String> = emptyList()
)

object ExcelResidentParser {

    private const val TAG = "ExcelResidentParser"

    /**
     * Parses an uploaded file (.xlsx or .csv) from Uri.
     */
    fun parseFile(context: Context, uri: Uri, fileName: String): ExcelImportResult {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir el archivo seleccionado.")

        return inputStream.use { stream ->
            parseStream(stream, fileName)
        }
    }

    fun parseStream(inputStream: InputStream, fileName: String): ExcelImportResult {
        val lowerName = fileName.lowercase()

        // Read up to 8 bytes to check magic header
        val bufferedStream = inputStream.buffered()
        bufferedStream.mark(16)
        val header = ByteArray(4)
        val readBytes = bufferedStream.read(header, 0, 4)
        bufferedStream.reset()

        val isZipXlsx = readBytes >= 4 &&
                header[0] == 0x50.toByte() && // 'P'
                header[1] == 0x4B.toByte() && // 'K'
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()

        return if (isZipXlsx || lowerName.endsWith(".xlsx")) {
            parseXlsx(bufferedStream, fileName)
        } else {
            parseCsv(bufferedStream, fileName)
        }
    }

    /**
     * Parses OpenXML Spreadsheet (.xlsx) without external libraries using ZipInputStream and XmlPullParser.
     */
    private fun parseXlsx(stream: InputStream, fileName: String): ExcelImportResult {
        var sharedStringsXmlBytes: ByteArray? = null
        var sheet1XmlBytes: ByteArray? = null

        val zip = ZipInputStream(stream)
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            val name = entry.name.replace('\\', '/')
            if (name.equals("xl/sharedStrings.xml", ignoreCase = true)) {
                sharedStringsXmlBytes = zip.readBytes()
            } else if (name.equals("xl/worksheets/sheet1.xml", ignoreCase = true) ||
                (name.startsWith("xl/worksheets/sheet", ignoreCase = true) && sheet1XmlBytes == null)
            ) {
                sheet1XmlBytes = zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        if (sheet1XmlBytes == null) {
            throw IllegalArgumentException("El archivo Excel no contiene una hoja de cálculo válida (sheet1.xml).")
        }

        val sharedStrings = if (sharedStringsXmlBytes != null) {
            parseSharedStrings(sharedStringsXmlBytes)
        } else {
            emptyList()
        }

        val rawGrid = parseSheetRows(sheet1XmlBytes, sharedStrings)
        return convertRawGridToResidents(rawGrid, fileName)
    }

    private fun parseSharedStrings(xmlBytes: ByteArray): List<String> {
        val strings = ArrayList<String>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var eventType = parser.eventType
        var currentText = StringBuilder()
        var insideSi = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("si", ignoreCase = true)) {
                        insideSi = true
                        currentText.clear()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideSi) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("si", ignoreCase = true)) {
                        strings.add(currentText.toString())
                        insideSi = false
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private fun parseSheetRows(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var eventType = parser.eventType
        val currentRowCells = HashMap<Int, String>()
        var currentCellRef = ""
        var currentCellType = ""
        var currentText = StringBuilder()
        var insideValue = false
        var insideInlineStr = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name
                    if (tagName.equals("row", ignoreCase = true)) {
                        currentRowCells.clear()
                    } else if (tagName.equals("c", ignoreCase = true)) {
                        currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                        currentCellType = parser.getAttributeValue(null, "t") ?: ""
                        currentText.clear()
                    } else if (tagName.equals("v", ignoreCase = true)) {
                        insideValue = true
                    } else if (tagName.equals("is", ignoreCase = true)) {
                        insideInlineStr = true
                    } else if (tagName.equals("t", ignoreCase = true) && insideInlineStr) {
                        // inline string tag
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideValue || insideInlineStr) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tagName = parser.name
                    if (tagName.equals("v", ignoreCase = true)) {
                        insideValue = false
                    } else if (tagName.equals("is", ignoreCase = true)) {
                        insideInlineStr = false
                    } else if (tagName.equals("c", ignoreCase = true)) {
                        val colIndex = extractColIndex(currentCellRef)
                        val rawValue = currentText.toString().trim()
                        val finalValue = when (currentCellType) {
                            "s" -> {
                                val sIndex = rawValue.toIntOrNull()
                                if (sIndex != null && sIndex in sharedStrings.indices) {
                                    sharedStrings[sIndex]
                                } else {
                                    rawValue
                                }
                            }
                            else -> cleanNumericOrText(rawValue)
                        }
                        if (colIndex >= 0) {
                            currentRowCells[colIndex] = finalValue
                        }
                    } else if (tagName.equals("row", ignoreCase = true)) {
                        if (currentRowCells.isNotEmpty()) {
                            val maxCol = currentRowCells.keys.maxOrNull() ?: 0
                            val rowList = ArrayList<String>(maxCol + 1)
                            for (c in 0..maxCol) {
                                rowList.add(currentRowCells[c] ?: "")
                            }
                            // Only add if row is not entirely blank
                            if (rowList.any { it.isNotBlank() }) {
                                rows.add(rowList)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    private fun extractColIndex(cellRef: String): Int {
        var col = 0
        var foundLetters = false
        for (ch in cellRef.uppercase()) {
            if (ch in 'A'..'Z') {
                foundLetters = true
                col = col * 26 + (ch - 'A' + 1)
            } else {
                break
            }
        }
        return if (foundLetters) col - 1 else -1
    }

    private fun cleanNumericOrText(raw: String): String {
        // e.g., if Excel formats integer as "101.0", turn it into "101"
        if (raw.endsWith(".0")) {
            val intPart = raw.substringBefore(".0")
            if (intPart.toLongOrNull() != null) {
                return intPart
            }
        }
        return raw
    }

    /**
     * Parses standard CSV / TSV with automatic delimiter detection (, ; \t |).
     */
    private fun parseCsv(stream: InputStream, fileName: String): ExcelImportResult {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val lines = reader.readLines()
        if (lines.isEmpty()) {
            throw IllegalArgumentException("El archivo CSV está vacío.")
        }

        // Detect delimiter from first non-empty line
        val firstLine = lines.firstOrNull { it.isNotBlank() } ?: ""
        val delimiter = detectDelimiter(firstLine)

        val rawGrid = ArrayList<List<String>>()
        for (line in lines) {
            if (line.isBlank()) continue
            val tokens = parseCsvLine(line, delimiter)
            if (tokens.any { it.isNotBlank() }) {
                rawGrid.add(tokens)
            }
        }

        return convertRawGridToResidents(rawGrid, fileName)
    }

    private fun detectDelimiter(line: String): Char {
        val counts = mapOf(
            ',' to line.count { it == ',' },
            ';' to line.count { it == ';' },
            '\t' to line.count { it == '\t' },
            '|' to line.count { it == '|' }
        )
        return counts.maxByOrNull { it.value }?.key ?: ','
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            if (ch == '\"') {
                inQuotes = !inQuotes
            } else if (ch == delimiter && !inQuotes) {
                result.add(cur.toString().trim().removeSurrounding("\"").trim())
                cur.clear()
            } else {
                cur.append(ch)
            }
        }
        result.add(cur.toString().trim().removeSurrounding("\"").trim())
        return result
    }

    /**
     * Converts a raw 2D grid of strings into structured ExcelResidentRows
     * with smart column header matching.
     */
    private fun convertRawGridToResidents(grid: List<List<String>>, fileName: String): ExcelImportResult {
        if (grid.isEmpty()) {
            throw IllegalArgumentException("No se encontraron filas con datos en el archivo.")
        }

        val firstRow = grid[0]
        val headerMap = mapHeaders(firstRow)
        val hasRecognizedHeaders = headerMap.values.any { it >= 0 }

        val dataStartIndex = if (hasRecognizedHeaders) 1 else 0
        val warnings = ArrayList<String>()

        val buildingCol = if (hasRecognizedHeaders) headerMap["building"] ?: 0 else 0
        val apartmentCol = if (hasRecognizedHeaders) headerMap["apartment"] ?: 1 else 1
        val accessCodeCol = if (hasRecognizedHeaders) headerMap["accessCode"] ?: 2 else 2
        val fullNameCol = if (hasRecognizedHeaders) headerMap["fullName"] ?: 3 else 3
        val phoneCol = if (hasRecognizedHeaders) headerMap["phone"] ?: -1 else -1
        val emailCol = if (hasRecognizedHeaders) headerMap["email"] ?: -1 else -1
        val platesCol = if (hasRecognizedHeaders) headerMap["plates"] ?: -1 else -1
        val statusCol = if (hasRecognizedHeaders) headerMap["status"] ?: -1 else -1

        val residentRows = ArrayList<ExcelResidentRow>()

        for (i in dataStartIndex until grid.size) {
            val row = grid[i]
            if (row.all { it.isBlank() }) continue

            var building = row.getOrNull(buildingCol)?.trim().orEmpty()
            var apartment = row.getOrNull(apartmentCol)?.trim().orEmpty()
            var accessCode = row.getOrNull(accessCodeCol)?.trim().orEmpty()
            var fullName = if (fullNameCol >= 0) row.getOrNull(fullNameCol)?.trim().orEmpty() else ""
            val phone = if (phoneCol >= 0) row.getOrNull(phoneCol)?.trim().orEmpty() else ""
            val email = if (emailCol >= 0) row.getOrNull(emailCol)?.trim().orEmpty() else ""
            val plates = if (platesCol >= 0) row.getOrNull(platesCol)?.trim().orEmpty() else ""
            val rawStatus = if (statusCol >= 0) row.getOrNull(statusCol)?.trim().orEmpty().lowercase() else ""

            val isMoroso = rawStatus.contains("moro") || rawStatus.contains("debe") ||
                    rawStatus.contains("no paga") || rawStatus.contains("falta") ||
                    rawStatus.contains("denegad") || rawStatus.contains("adeud") ||
                    rawStatus == "no"

            // Clean common prefixes
            if (building.isBlank() && apartment.isNotBlank()) {
                building = "Gavilán 181"
            }
            if (accessCode.isBlank() && apartment.isNotBlank()) {
                accessCode = "CLV-${apartment.replace(" ", "")}"
            }
            if (fullName.isBlank()) {
                fullName = if (apartment.isNotBlank()) "Residente Depto $apartment" else "Residente Fila ${i + 1}"
            }

            if (apartment.isNotBlank() || accessCode.isNotBlank()) {
                residentRows.add(
                    ExcelResidentRow(
                        rowIndex = i + 1,
                        building = building,
                        apartment = apartment,
                        accessCode = accessCode,
                        fullName = fullName,
                        phone = phone,
                        email = email,
                        vehiclePlates = plates,
                        notes = "Importado desde $fileName",
                        isMoroso = isMoroso
                    )
                )
            } else {
                warnings.add("Fila ${i + 1} omitida: Departamento y Clave vacíos.")
            }
        }

        if (residentRows.isEmpty()) {
            throw IllegalArgumentException("No se encontraron registros válidos de departamentos o claves en el archivo.")
        }

        return ExcelImportResult(
            fileName = fileName,
            rows = residentRows,
            detectedHeaders = firstRow,
            warnings = warnings
        )
    }

    private fun mapHeaders(headerRow: List<String>): Map<String, Int> {
        val result = HashMap<String, Int>()

        headerRow.forEachIndexed { index, rawHeader ->
            val h = normalize(rawHeader)
            when {
                h.contains("edif") || h.contains("torre") || h.contains("bloque") || h.contains("bldg") -> {
                    result.putIfAbsent("building", index)
                }
                h.contains("tel") || h.contains("cel") || h.contains("movil") || h.contains("phone") -> {
                    result.putIfAbsent("phone", index)
                }
                h.contains("clav") || h.contains("codig") || h.contains("pass") ||
                        h.contains("pin") || h.contains("key") || h.contains("acceso") -> {
                    result.putIfAbsent("accessCode", index)
                }
                h.contains("moro") || h.contains("paga") || h.contains("adeud") ||
                        h.contains("estatus") || h.contains("estado") || h.contains("solven") -> {
                    result.putIfAbsent("status", index)
                }
                h.contains("nomb") || h.contains("resid") || h.contains("inquil") ||
                        h.contains("prop") || h.contains("titular") -> {
                    result.putIfAbsent("fullName", index)
                }
                h.contains("dep") || h.contains("apto") || h.contains("apart") ||
                        h.contains("num") || h == "no" || h.startsWith("no.") || h.startsWith("no ") ||
                        h.contains("casa") || h.contains("unidad") || h.contains("habitacion") -> {
                    result.putIfAbsent("apartment", index)
                }
                h.contains("corr") || h.contains("email") || h.contains("mail") -> {
                    result.putIfAbsent("email", index)
                }
                h.contains("plac") || h.contains("auto") || h.contains("vehic") -> {
                    result.putIfAbsent("plates", index)
                }
            }
        }
        return result
    }

    private fun normalize(str: String): String {
        return str.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .trim()
    }

    /**
     * Generates a sample CSV template file that users can download or share,
     * edit in Excel, and upload directly back to Gavilán 181.
     */
    fun generateSampleTemplate(context: Context): File {
        val dir = File(context.cacheDir, "templates").apply { mkdirs() }
        val file = File(dir, "Plantilla_Gavilan_181_Claves_Deptos.csv")
        file.bufferedWriter(Charsets.UTF_8).use { out ->
            out.write("Edificio,Departamento,Clave,Nombre_Residente,Estatus_Pago,Telefono,Placas_Vehiculo\n")
            out.write("Edificio A,101,CLV-A101,Carlos Mendoza,Si paga,55-1234-5678,ABC-123-A\n")
            out.write("Edificio A,102,CLV-A102,Sofía Garza,Moroso,55-9876-5432,XYZ-789-B\n")
            out.write("Edificio A,201,CLV-A201,Roberto Navarro,Si paga,55-2233-4455,DEF-456-Y\n")
            out.write("Edificio B,101,CLV-B101,Alejandra Torres,Si paga,55-3344-5566,GHI-789-C\n")
            out.write("Edificio B,102,CLV-B102,Javier Morales,Moroso,55-7788-9900,JKL-012-A\n")
            out.write("Torre 1,301,CLV-T301,Valeria Domínguez,Si paga,55-3322-1144,MNO-345-B\n")
        }
        return file
    }
}
