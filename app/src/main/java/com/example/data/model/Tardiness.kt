package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tardiness_records",
    foreignKeys = [
        ForeignKey(
            entity = Resident::class,
            parentColumns = ["id"],
            childColumns = ["residentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["residentId"])]
)
data class Tardiness(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val residentId: Long,
    val residentName: String,
    val houseNumber: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val reason: String, // Motivo o descripción del retardo
    val vehiclePlate: String = "",
    val registeredBy: String,
    val notes: String = ""
)
