package com.example

import com.example.util.ExcelResidentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ExcelResidentParserTest {

    @Test
    fun testParseCsvWithStandardHeaders() {
        val csvData = """
            Edificio,Departamento,Clave,Nombre_Residente,Telefono
            Edificio A,101,CLV-A101,Juan Pérez,5512345678
            Edificio A,102,CLV-A102,María Gómez,5598765432
            Torre 1,301,CLV-T301,Roberto Navarro,5577889900
        """.trimIndent()

        val stream = ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8))
        val result = ExcelResidentParser.parseStream(stream, "claves_gavilan181.csv")

        assertEquals(3, result.rows.size)

        val row1 = result.rows[0]
        assertEquals("Edificio A", row1.building)
        assertEquals("101", row1.apartment)
        assertEquals("CLV-A101", row1.accessCode)
        assertEquals("Juan Pérez", row1.fullName)
        assertEquals("5512345678", row1.phone)

        val row3 = result.rows[2]
        assertEquals("Torre 1", row3.building)
        assertEquals("301", row3.apartment)
        assertEquals("CLV-T301", row3.accessCode)
    }

    @Test
    fun testParseCsvWithSemicolonDelimiter() {
        val csvData = """
            Torre;Unidad;Codigo_Acceso;Inquilino
            Edificio B;201;CLV-B201;Alejandra Torres
            Edificio B;202;CLV-B202;Javier Morales
        """.trimIndent()

        val stream = ByteArrayInputStream(csvData.toByteArray(Charsets.UTF_8))
        val result = ExcelResidentParser.parseStream(stream, "condominio_export.csv")

        assertEquals(2, result.rows.size)
        assertEquals("Edificio B", result.rows[0].building)
        assertEquals("201", result.rows[0].apartment)
        assertEquals("CLV-B201", result.rows[0].accessCode)
        assertEquals("Alejandra Torres", result.rows[0].fullName)
    }
}
