package com.saf.searchallfiles.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "indexed_files",
    indices = [Index(value = ["file_path"], unique = true)]
)
data class IndexedFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "file_type")
    val fileType: String,       // "document" | "image"

    @ColumnInfo(name = "content")
    val content: String,        // extracted text

    @ColumnInfo(name = "last_modified")
    val lastModified: Long,

    @ColumnInfo(name = "file_size")
    val fileSize: Long
)
