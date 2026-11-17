package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Resident
import kotlinx.coroutines.flow.Flow

@Dao
interface ResidentDao {
    @Query("SELECT * FROM residents ORDER BY houseNumber ASC")
    fun getAllResidentsFlow(): Flow<List<Resident>>

    @Query("SELECT * FROM residents WHERE id = :id")
    suspend fun getResidentById(id: Long): Resident?

    @Query("SELECT * FROM residents WHERE id = :id")
    fun getResidentFlow(id: Long): Flow<Resident?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(resident: Resident): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(residents: List<Resident>)

    @Update
    suspend fun update(resident: Resident)

    @Delete
    suspend fun delete(resident: Resident)

    @Query("SELECT COUNT(*) FROM residents")
    suspend fun getCount(): Int
}
