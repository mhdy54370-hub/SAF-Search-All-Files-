package com.saf.searchallfiles.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.saf.searchallfiles.data.IndexedFile
import com.saf.searchallfiles.databinding.ItemResultBinding
import java.io.File

class ResultsAdapter : ListAdapter<IndexedFile, ResultsAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<IndexedFile>() {
            override fun areItemsTheSame(old: IndexedFile, new: IndexedFile) = old.id == new.id
            override fun areContentsTheSame(old: IndexedFile, new: IndexedFile) = old == new
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class ViewHolder(val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val b    = holder.binding

        b.tvFileName.text = item.fileName
        b.tvFilePath.text  = shortenPath(item.filePath)
        b.tvFileType.text  = item.fileType.uppercase()
        b.tvPreview.text   = item.content.take(200).replace('\n', ' ')

        // Color-code the type badge
        val badgeColor = when (item.fileType) {
            "image"    -> 0xFF1976D2.toInt()  // blue
            "document" -> 0xFF388E3C.toInt()  // green
            else       -> 0xFF757575.toInt()  // grey
        }
        b.tvFileType.setBackgroundColor(badgeColor)

        holder.itemView.setOnClickListener {
            openFile(holder, item)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun openFile(holder: ViewHolder, item: IndexedFile) {
        val ctx  = holder.itemView.context
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(ctx, "File no longer exists", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, resolveMimeType(item.fileType, file.extension))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, "Open with…"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveMimeType(fileType: String, extension: String): String = when (fileType) {
        "image" -> "image/*"
        "document" -> when (extension.lowercase()) {
            "pdf"  -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "odt"  -> "application/vnd.oasis.opendocument.text"
            "txt", "md", "log", "csv" -> "text/plain"
            else   -> "text/*"
        }
        else -> "*/*"
    }

    private fun shortenPath(path: String): String {
        val parts = path.split("/")
        return if (parts.size > 4) "…/" + parts.takeLast(3).joinToString("/")
        else path
    }
}
