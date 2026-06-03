package com.nszconverter.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    @Query("SELECT * FROM jobs WHERE status IN ('QUEUED','RUNNING') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status IN ('SUCCESS','FAILED','CANCELLED') ORDER BY finishedAt DESC")
    fun observeHistory(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    suspend fun get(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    fun observe(id: String): Flow<JobEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(jobs: List<JobEntity>)

    @Update
    suspend fun update(job: JobEntity)

    @Query("UPDATE jobs SET status = :status, progress = :progress, bytesProcessed = :bytes, speedMBs = :speed, etaSeconds = :eta WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, progress: Float, bytes: Long, speed: Float, eta: Int)

    @Query("UPDATE jobs SET status = :status, outputPath = :outputPath, durationSeconds = :duration, finishedAt = :finishedAt, progress = 1.0 WHERE id = :id")
    suspend fun markSuccess(id: String, status: String, outputPath: String, duration: Float, finishedAt: Long)

    @Query("UPDATE jobs SET status = :status, failureReason = :reason, failureDetail = :detail, finishedAt = :finishedAt WHERE id = :id")
    suspend fun markFailure(id: String, status: String, reason: String, detail: String, finishedAt: Long)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM jobs WHERE status IN ('SUCCESS','FAILED','CANCELLED')")
    suspend fun clearHistory()
}
