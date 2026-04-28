package com.saf.searchallfiles.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.saf.searchallfiles.MainActivity
import com.saf.searchallfiles.R
import com.saf.searchallfiles.data.AppDatabase
import com.saf.searchallfiles.data.IndexedFile
import com.saf.searchallfiles.parser.DocumentParser
import com.saf.searchallfiles.parser.OcrParser

class IndexingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_INDEX_DOCUMENTS = "index_documents"
        const val KEY_INDEX_IMAGES    = "index_images"
        const val KEY_FOLDER_PATHS    = "folder_paths"

        private const val CHANNEL_ID    = "saf_indexing_channel"
        private const val NOTIF_DONE_ID = 2
    }

    override suspend fun doWork(): Result {
        val indexDocuments = inputData.getBoolean(KEY_INDEX_DOCUMENTS, true)
        val indexImages    = inputData.getBoolean(KEY_INDEX_IMAGES, false)
        val folderPaths    = inputData.getStringArray(KEY_FOLDER_PATHS)

        // Create notification channel (needed before sending any notification)
        createNotificationChannel()

        val db  = AppDatabase.getInstance(applicationContext)
        val dao = db.indexedFileDao()
        dao.deleteAll()

        val roots = if (folderPaths.isNullOrEmpty()) {
            listOf(Environment.getExternalStorageDirectory())
        } else {
            folderPaths.map { java.io.File(it) }.filter { it.exists() && it.isDirectory }
        }

        var processed = 0

        roots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && !isSensitivePath(it.absolutePath) }
                .toList()
        }.forEach { file ->
            val ext = file.extension.lowercase()

            val content: String
            val fileType: String

            when {
                indexDocuments && ext in DocumentParser.supportedExtensions -> {
                    content  = DocumentParser.extractText(file)
                    fileType = "document"
                }
                indexImages && ext in OcrParser.supportedExtensions -> {
                    content  = OcrParser.extractText(file)
                    fileType = "image"
                }
                else -> return@forEach
            }

            if (content.isNotBlank()) {
                dao.insert(
                    IndexedFile(
                        filePath     = file.absolutePath,
                        fileName     = file.name,
                        fileType     = fileType,
                        content      = content,
                        lastModified = file.lastModified(),
                        fileSize     = file.length()
                    )
                )
                processed++
            }
        }

        val scopeLabel = if (folderPaths.isNullOrEmpty()) "whole device" else "${roots.size} folder(s)"
        sendCompletionNotification(processed, scopeLabel)
        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SAF File Indexing",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "SAF indexing results" }
            applicationContext
                .getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun sendCompletionNotification(count: Int, scope: String) {
        // Skip notification if permission not granted (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.areNotificationsEnabled()) return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("SAF — Indexing complete ✅")
            .setContentText("$count files indexed ($scope). You can now search!")
            .setSmallIcon(R.drawable.ic_search_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_DONE_ID, notification)
    }

    private fun isSensitivePath(path: String): Boolean =
        path.contains("/Android/data/") ||
        path.contains("/Android/obb/")
}
