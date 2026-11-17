package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Tardiness
import kotlinx.coroutines.flow.Flow

@Dao
interface TardinessDao {
    @Query("SELECT * FROM tardiness_records ORDER BY timestampMillis DESC, id DESC")
    fun getAllTardinessFlow(): Flow<List<Tardiness>>

    @Query("SELECT * FROM tardiness_records WHERE residentId = :residentId ORDER BY timestampMillis DESC")
    fun getTardinessByResidentFlow(residentId: Long): Flow<List<Tardiness>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tardiness: Tardiness): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Tardiness>)

    @Update
    suspend fun update(tardiness: Tardiness)

    @Delete
    suspend fun delete(tardiness: Tardiness)

    @Query("SELECT COUNT(*) FROM tardiness_records")
    suspend fun getCount(): Int
}
