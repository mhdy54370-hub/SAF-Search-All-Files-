package com.saf.searchallfiles.parser

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object OcrParser {

    /** Image extensions this parser handles */
    val supportedExtensions = setOf(
        "jpg", "jpeg", "png", "bmp", "webp"
    )

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Runs on-device ML Kit OCR on the image.
     * Returns extracted text, or empty string on failure.
     * Suspend function — safe to call from coroutine.
     */
    suspend fun extractText(file: File): String = suspendCoroutine { continuation ->
        try {
            // Decode with inSampleSize to avoid OOM on large photos
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)

            if (bitmap == null) {
                continuation.resume("")
                return@suspendCoroutine
            }

            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    bitmap.recycle()
                    continuation.resume(result.text)
                }
                .addOnFailureListener {
                    bitmap.recycle()
                    continuation.resume("")
                }
        } catch (e: Exception) {
            continuation.resume("")
        }
    }
}
