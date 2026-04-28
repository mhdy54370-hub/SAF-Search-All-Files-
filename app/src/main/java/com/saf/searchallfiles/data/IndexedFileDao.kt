package com.saf.searchallfiles.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface IndexedFileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: IndexedFile)

    @Query("""
        SELECT * FROM indexed_files 
        WHERE (content LIKE :query OR file_name LIKE :query)
        AND file_type IN (:types)
        ORDER BY file_name ASC
    """)
    suspend fun search(query: String, types: List<String>): List<IndexedFile>

    @Query("SELECT * FROM indexed_files WHERE content LIKE :query OR file_name LIKE :query ORDER BY file_name ASC")
    suspend fun searchAll(query: String): List<IndexedFile>

    @Query("DELETE FROM indexed_files")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM indexed_files")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM indexed_files WHERE file_type = :type")
    suspend fun getCountByType(type: String): Int
}
