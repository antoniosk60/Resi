package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.AuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestampMillis DESC, id DESC")
    fun getAllLogsFlow(): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType ORDER BY timestampMillis DESC")
    fun getLogsByEntityFlow(entityType: String): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AuditLog>)

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun getCount(): Int
}
