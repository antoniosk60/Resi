package com.example.data.db

import com.example.data.model.AuditLog
import com.example.data.model.Debt
import com.example.data.model.Payment
import com.example.data.model.PaymentMethod
import com.example.data.model.Resident
import com.example.data.model.Tardiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseInitializer {

    suspend fun seedDemoDataIfEmpty(db: AppDatabase) = withContext(Dispatchers.IO) {
        val residentCount = db.residentDao().getCount()
        if (residentCount > 0) return@withContext

        val now = System.currentTimeMillis()
        val oneDayMillis = 86_400_000L

        // 1. Initial Residents
        val residents = listOf(
            Resident(
                id = 1,
                fullName = "Ing. Roberto Garza Silva",
                houseNumber = "Casa 101",
                blockOrSection = "Manzana 1",
                phone = "55-4123-8899",
                email = "roberto.garza@gmail.com",
                vehiclePlates = "NLT-449-A",
                notes = "Propietario responsable. Contacto principal.",
                isActive = true,
                registeredDateMillis = now - (90 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                lastModifiedBy = "Carlos Mendoza (Admin)"
            ),
            Resident(
                id = 2,
                fullName = "Lic. Sofía Morales Fuentes",
                houseNumber = "Casa 102",
                blockOrSection = "Manzana 1",
                phone = "55-8822-1144",
                email = "sofia.morales@hotmail.com",
                vehiclePlates = "PZX-112-C",
                notes = "Inquilina con retrasos frecuentes en cuotas.",
                isActive = true,
                registeredDateMillis = now - (120 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                lastModifiedBy = "Elena Ramos (Capturista)"
            ),
            Resident(
                id = 3,
                fullName = "Dr. Alejandro Treviño Cruz",
                houseNumber = "Casa 204",
                blockOrSection = "Manzana 2",
                phone = "55-9933-2211",
                email = "dr.trevino@salud.org",
                vehiclePlates = "HRM-902-B",
                notes = "Tiene adeudo de cuota extraordinaria de pintura vencido.",
                isActive = true,
                registeredDateMillis = now - (180 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                lastModifiedBy = "Carlos Mendoza (Admin)"
            ),
            Resident(
                id = 4,
                fullName = "Mtra. Carmen Valenzuela",
                houseNumber = "Torre A - Depto 301",
                blockOrSection = "Edificio Los Encinos",
                phone = "55-1122-3344",
                email = "carmen.valenzuela@outlook.com",
                vehiclePlates = "VKF-771-A",
                notes = "Residente puntual. Paga vía transferencia bancaria.",
                isActive = true,
                registeredDateMillis = now - (200 * oneDayMillis),
                createdBy = "Elena Ramos (Capturista)",
                lastModifiedBy = "Elena Ramos (Capturista)"
            ),
            Resident(
                id = 5,
                fullName = "Arq. Fernando Lozano Peña",
                houseNumber = "Casa 108",
                blockOrSection = "Manzana 2",
                phone = "55-3344-5566",
                email = "fernando.lozano@gmail.com",
                vehiclePlates = "MXB-338-D",
                notes = "En proceso de liquidar abono parcial de mantenimiento.",
                isActive = true,
                registeredDateMillis = now - (60 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                lastModifiedBy = "Elena Ramos (Capturista)"
            ),
            Resident(
                id = 6,
                fullName = "Sra. Patricia Delgado Rey",
                houseNumber = "Torre B - Depto 102",
                blockOrSection = "Edificio Las Palmas",
                phone = "55-6677-8899",
                email = "paty.delgado@yahoo.com",
                vehiclePlates = "JYT-551-E",
                notes = "Al corriente en todos sus compromisos.",
                isActive = true,
                registeredDateMillis = now - (300 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                lastModifiedBy = "Carlos Mendoza (Admin)"
            ),
            Resident(
                id = 7,
                fullName = "Sr. Miguel Ángel Ramos",
                houseNumber = "Casa 305",
                blockOrSection = "Manzana 3",
                phone = "55-7788-9900",
                email = "miguel.ramos@gmail.com",
                vehiclePlates = "LKM-223-A",
                notes = "Presenta sanciones por ruido en horario nocturno y adeudo vencido.",
                isActive = true,
                registeredDateMillis = now - (150 * oneDayMillis),
                createdBy = "Elena Ramos (Capturista)",
                lastModifiedBy = "Carlos Mendoza (Admin)"
            )
        )
        db.residentDao().insertAll(residents)

        // 2. Initial Debts
        // Some paid, some partially paid, some pending (not overdue), some overdue (Morosos)
        val debts = listOf(
            // Resident 1 (Roberto Garza): Mantenimiento actual pagado totalmente
            Debt(
                id = 1,
                residentId = 1,
                concept = "Mantenimiento Mensual Noviembre",
                amount = 1800.0,
                paidAmount = 1800.0,
                dueDateMillis = now + (15 * oneDayMillis),
                createdAtMillis = now - (10 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                notes = "Cuota ordinaria del mes"
            ),
            // Resident 2 (Sofía Morales): Adeudo Octubre vencido (Moroso) y Noviembre pendiente
            Debt(
                id = 2,
                residentId = 2,
                concept = "Mantenimiento Mensual Octubre",
                amount = 1800.0,
                paidAmount = 0.0,
                dueDateMillis = now - (12 * oneDayMillis), // Vencido
                createdAtMillis = now - (42 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                notes = "Aviso de cobro enviado por correo"
            ),
            Debt(
                id = 3,
                residentId = 2,
                concept = "Mantenimiento Mensual Noviembre",
                amount = 1800.0,
                paidAmount = 0.0,
                dueDateMillis = now + (15 * oneDayMillis),
                createdAtMillis = now - (5 * oneDayMillis),
                createdBy = "Elena Ramos (Capturista)",
                notes = "Cuota ordinaria"
            ),
            // Resident 3 (Alejandro Treviño): Cuota Extraordinaria Pintura Vencida (Moroso)
            Debt(
                id = 4,
                residentId = 3,
                concept = "Cuota Extraordinaria Pintura y Fachada",
                amount = 2500.0,
                paidAmount = 0.0,
                dueDateMillis = now - (25 * oneDayMillis), // Vencido
                createdAtMillis = now - (60 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                notes = "Aprobada en asamblea ordinaria de septiembre"
            ),
            Debt(
                id = 5,
                residentId = 3,
                concept = "Mantenimiento Mensual Noviembre",
                amount = 1800.0,
                paidAmount = 1800.0,
                dueDateMillis = now + (15 * oneDayMillis),
                createdAtMillis = now - (8 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                notes = "Pagó el mantenimiento pero no la cuota extraordinaria"
            ),
            // Resident 4 (Carmen Valenzuela): Mantenimiento liquidado
            Debt(
                id = 6,
                residentId = 4,
                concept = "Mantenimiento Mensual Noviembre",
                amount = 1600.0,
                paidAmount = 1600.0,
                dueDateMillis = now + (15 * oneDayMillis),
                createdAtMillis = now - (9 * oneDayMillis),
                createdBy = "Elena Ramos (Capturista)",
                notes = "Cuota departamento edificio A"
            ),
            // Resident 5 (Fernando Lozano): Adeudo con pago parcial
            Debt(
                id = 7,
                residentId = 5,
                concept = "Mantenimiento Mensual Noviembre",
                amount = 1800.0,
                paidAmount = 1000.0,
                dueDateMillis = now + (10 * oneDayMillis), // Aún vigente, saldo pendiente 800
                createdAtMillis = now - (7 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                notes = "Abonó $1,000 en efectivo, resta $800"
            ),
            // Resident 6 (Patricia Delgado): Todo pagado + Renta de salón pagada
            Debt(
                id = 8,
                residentId = 6,
                concept = "Mantenimiento Mensual Noviembre",
                amount = 1600.0,
                paidAmount = 1600.0,
                dueDateMillis = now + (15 * oneDayMillis),
                createdAtMillis = now - (10 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                notes = "Cuota departamento edificio B"
            ),
            Debt(
                id = 9,
                residentId = 6,
                concept = "Renta Salón de Usos Múltiples",
                amount = 1200.0,
                paidAmount = 1200.0,
                dueDateMillis = now + (5 * oneDayMillis),
                createdAtMillis = now - (4 * oneDayMillis),
                createdBy = "Elena Ramos (Capturista)",
                notes = "Evento familiar reservado"
            ),
            // Resident 7 (Miguel Ángel Ramos): Sanción por retardo/ruido vencida (Moroso)
            Debt(
                id = 10,
                residentId = 7,
                concept = "Sanción por Ruido en Horario Nocturno",
                amount = 750.0,
                paidAmount = 0.0,
                dueDateMillis = now - (8 * oneDayMillis), // Vencido
                createdAtMillis = now - (20 * oneDayMillis),
                createdBy = "Carlos Mendoza (Admin)",
                notes = "Reporte de guardia nocturno folio R-304"
            ),
            Debt(
                id = 11,
                residentId = 7,
                concept = "Mantenimiento Mensual Noviembre",
                amount = 1800.0,
                paidAmount = 0.0,
                dueDateMillis = now + (15 * oneDayMillis),
                createdAtMillis = now - (6 * oneDayMillis),
                createdBy = "Elena Ramos (Capturista)",
                notes = "Pendiente de cobro"
            )
        )
        db.debtDao().insertAll(debts)

        // 3. Initial Payments
        val payments = listOf(
            Payment(
                id = 1,
                residentId = 1,
                debtId = 1,
                amount = 1800.0,
                paymentDateMillis = now - (8 * oneDayMillis),
                paymentMethod = PaymentMethod.TRANSFERENCIA,
                receiptNumber = "SPEI-992144",
                notes = "Pago íntegro mensualidad Noviembre",
                registeredBy = "Elena Ramos (Capturista)",
                registeredAtMillis = now - (8 * oneDayMillis)
            ),
            Payment(
                id = 2,
                residentId = 3,
                debtId = 5,
                amount = 1800.0,
                paymentDateMillis = now - (4 * oneDayMillis),
                paymentMethod = PaymentMethod.TRANSFERENCIA,
                receiptNumber = "SPEI-781290",
                notes = "Pago mantenimiento mensual",
                registeredBy = "Carlos Mendoza (Admin)",
                registeredAtMillis = now - (4 * oneDayMillis)
            ),
            Payment(
                id = 3,
                residentId = 4,
                debtId = 6,
                amount = 1600.0,
                paymentDateMillis = now - (6 * oneDayMillis),
                paymentMethod = PaymentMethod.EFECTIVO,
                receiptNumber = "REC-2026-0034",
                notes = "Recibo entregado en caseta administrativa",
                registeredBy = "Elena Ramos (Capturista)",
                registeredAtMillis = now - (6 * oneDayMillis)
            ),
            Payment(
                id = 4,
                residentId = 5,
                debtId = 7,
                amount = 1000.0,
                paymentDateMillis = now - (3 * oneDayMillis),
                paymentMethod = PaymentMethod.EFECTIVO,
                receiptNumber = "REC-2026-0038",
                notes = "Abono parcial mantenimiento. Restan $800",
                registeredBy = "Elena Ramos (Capturista)",
                registeredAtMillis = now - (3 * oneDayMillis)
            ),
            Payment(
                id = 5,
                residentId = 6,
                debtId = 8,
                amount = 1600.0,
                paymentDateMillis = now - (7 * oneDayMillis),
                paymentMethod = PaymentMethod.TRANSFERENCIA,
                receiptNumber = "SPEI-441029",
                notes = "Mantenimiento mensual departamento 102",
                registeredBy = "Carlos Mendoza (Admin)",
                registeredAtMillis = now - (7 * oneDayMillis)
            ),
            Payment(
                id = 6,
                residentId = 6,
                debtId = 9,
                amount = 1200.0,
                paymentDateMillis = now - (2 * oneDayMillis),
                paymentMethod = PaymentMethod.DEPOSITO_BANCARIO,
                receiptNumber = "DEP-BBVA-110",
                notes = "Depósito en ventanilla renta salón",
                registeredBy = "Elena Ramos (Capturista)",
                registeredAtMillis = now - (2 * oneDayMillis)
            )
        )
        db.paymentDao().insertAll(payments)

        // 4. Initial Tardiness Records (Retardos)
        val tardinessRecords = listOf(
            Tardiness(
                id = 1,
                residentId = 2,
                residentName = "Lic. Sofía Morales Fuentes",
                houseNumber = "Casa 102",
                timestampMillis = now - (3 * oneDayMillis) - (3600_000L * 4), // Hace 3 días 02:30 am
                reason = "Ingreso vehicular fuera de horario por portón norte sin código QR",
                vehiclePlate = "PZX-112-C",
                registeredBy = "Elena Ramos (Capturista)",
                notes = "Se le solicitó actualizar gafete de acceso"
            ),
            Tardiness(
                id = 2,
                residentId = 7,
                residentName = "Sr. Miguel Ángel Ramos",
                houseNumber = "Casa 305",
                timestampMillis = now - (5 * oneDayMillis) - (3600_000L * 2),
                reason = "Retraso de 2 horas en entrega de llaves de caseta de visitas",
                vehiclePlate = "LKM-223-A",
                registeredBy = "Carlos Mendoza (Admin)",
                notes = "Segunda incidencia del mes"
            ),
            Tardiness(
                id = 3,
                residentId = 3,
                residentName = "Dr. Alejandro Treviño Cruz",
                houseNumber = "Casa 204",
                timestampMillis = now - (9 * oneDayMillis),
                reason = "Demora en retiro de vehículo de zona de proveedores",
                vehiclePlate = "HRM-902-B",
                registeredBy = "Elena Ramos (Capturista)",
                notes = "Estuvo 3 horas excedidas obstaculizando acceso de pipa de agua"
            )
        )
        db.tardinessDao().insertAll(tardinessRecords)

        // 5. Initial Audit Logs
        val auditLogs = listOf(
            AuditLog(
                id = 1,
                timestampMillis = now - (90 * oneDayMillis),
                userName = "Carlos Mendoza",
                userRole = "Administrador",
                actionType = "CREAR",
                entityType = "Residente",
                entityId = 1,
                title = "Registro de nuevo residente",
                details = "Alta de Ing. Roberto Garza Silva en Casa 101"
            ),
            AuditLog(
                id = 2,
                timestampMillis = now - (60 * oneDayMillis),
                userName = "Carlos Mendoza",
                userRole = "Administrador",
                actionType = "CREAR",
                entityType = "Adeudo",
                entityId = 4,
                title = "Creación de cuota extraordinaria",
                details = "Asignación de cuota de pintura $2,500 a Casa 204"
            ),
            AuditLog(
                id = 3,
                timestampMillis = now - (20 * oneDayMillis),
                userName = "Carlos Mendoza",
                userRole = "Administrador",
                actionType = "CREAR",
                entityType = "Adeudo",
                entityId = 10,
                title = "Generación de sanción",
                details = "Sanción $750 a Casa 305 por ruido nocturno"
            ),
            AuditLog(
                id = 4,
                timestampMillis = now - (8 * oneDayMillis),
                userName = "Elena Ramos",
                userRole = "Capturista",
                actionType = "PAGO",
                entityType = "Pago",
                entityId = 1,
                title = "Recepción de pago SPEI",
                details = "Pago total $1,800 de Roberto Garza (Casa 101), Folio SPEI-992144"
            ),
            AuditLog(
                id = 5,
                timestampMillis = now - (5 * oneDayMillis),
                userName = "Carlos Mendoza",
                userRole = "Administrador",
                actionType = "RETARDO",
                entityType = "Retardo",
                entityId = 2,
                title = "Registro de retardo",
                details = "Retardo en entrega de llaves para Casa 305 (Miguel Ángel Ramos)"
            ),
            AuditLog(
                id = 6,
                timestampMillis = now - (3 * oneDayMillis),
                userName = "Elena Ramos",
                userRole = "Capturista",
                actionType = "PAGO",
                entityType = "Pago",
                entityId = 4,
                title = "Recepción de abono parcial",
                details = "Abono de $1,000 en efectivo para Casa 108 (Fernando Lozano), Folio REC-2026-0038"
            ),
            AuditLog(
                id = 7,
                timestampMillis = now - (3 * oneDayMillis),
                userName = "Elena Ramos",
                userRole = "Capturista",
                actionType = "RETARDO",
                entityType = "Retardo",
                entityId = 1,
                title = "Registro de retardo vehicular",
                details = "Ingreso nocturno fuera de horario para Casa 102 (Sofía Morales)"
            )
        )
        db.auditLogDao().insertAll(auditLogs)
    }
}
