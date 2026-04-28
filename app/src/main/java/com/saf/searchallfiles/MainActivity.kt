package com.saf.searchallfiles

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.saf.searchallfiles.data.AppDatabase
import com.saf.searchallfiles.databinding.ActivityMainBinding
import com.saf.searchallfiles.worker.IndexingWorker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val selectedFolders = mutableListOf<String>()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.btnIndex.setOnClickListener     { requestPermissionsThenIndex() }
        binding.btnSearch.setOnClickListener    { startSearch() }
        binding.btnGrantPermission.setOnClickListener { requestStoragePermission() }
        binding.rgScope.setOnCheckedChangeListener { _, checkedId ->
            binding.llFolderPicker.visibility =
                if (checkedId == R.id.rb_specific) View.VISIBLE else View.GONE
        }
        binding.btnAddFolder.setOnClickListener { openFolderPicker() }

        // Request permission immediately on first launch
        if (!hasStoragePermission()) {
            requestStoragePermission()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission banner and index count every time user returns to app
        updatePermissionBanner()
        updateIndexStatus()
    }

    // ── Permission check ──────────────────────────────────────────────────────

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updatePermissionBanner() {
        if (hasStoragePermission()) {
            binding.layoutPermissionBanner.visibility = View.GONE
            binding.btnIndex.isEnabled = true
        } else {
            binding.layoutPermissionBanner.visibility = View.VISIBLE
            binding.btnIndex.isEnabled = false
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage(
                    "SAF needs \"All Files Access\" to index your files.\n\n" +
                    "In the next screen:\n" +
                    "1. Find SAF in the list\n" +
                    "2. Enable \"Allow access to manage all files\"\n" +
                    "3. Come back to the app"
                )
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    manageStorageLauncher.launch(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // ── Indexing ──────────────────────────────────────────────────────────────

    private fun requestPermissionsThenIndex() {
        if (!hasStoragePermission()) {
            requestStoragePermission()
            return
        }

        // Android 13+ notification permission (optional)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        startIndexing()
    }

    private fun startIndexing() {
        val indexDocs   = binding.cbDocuments.isChecked
        val indexImages = binding.cbImages.isChecked

        if (!indexDocs && !indexImages) {
            Toast.makeText(this, "Select at least one file type to index", Toast.LENGTH_SHORT).show()
            return
        }

        val useSpecific = binding.rbSpecific.isChecked
        if (useSpecific && selectedFolders.isEmpty()) {
            Toast.makeText(this, "Add at least one folder, or choose Whole Device", Toast.LENGTH_SHORT).show()
            return
        }

        val dataBuilder = Data.Builder()
            .putBoolean(IndexingWorker.KEY_INDEX_DOCUMENTS, indexDocs)
            .putBoolean(IndexingWorker.KEY_INDEX_IMAGES, indexImages)

        if (useSpecific && selectedFolders.isNotEmpty()) {
            dataBuilder.putStringArray(IndexingWorker.KEY_FOLDER_PATHS, selectedFolders.toTypedArray())
        }

        val request = OneTimeWorkRequestBuilder<IndexingWorker>()
            .setInputData(dataBuilder.build())
            .build()

        WorkManager.getInstance(this).enqueue(request)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id)
            .observe(this) { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING   -> {
                        binding.tvStatus.text = "⏳ Indexing in progress…"
                        binding.btnIndex.isEnabled = false
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        binding.btnIndex.isEnabled = true
                        updateIndexStatus()
                    }
                    WorkInfo.State.FAILED    -> {
                        binding.tvStatus.text = "❌ Indexing failed — check storage permission"
                        binding.btnIndex.isEnabled = true
                    }
                    else -> {}
                }
            }

        val scopeMsg = if (useSpecific) "${selectedFolders.size} folder(s)" else "whole device"
        binding.tvStatus.text = "⏳ Indexing $scopeMsg…"
        Toast.makeText(this, "Indexing started in background", Toast.LENGTH_SHORT).show()
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun startSearch() {
        val query = binding.etSearch.text.toString().trim()
        if (query.isEmpty()) {
            binding.etSearch.error = "Enter a search term"
            return
        }

        // Block search if index is empty
        lifecycleScope.launch {
            val count = AppDatabase.getInstance(this@MainActivity)
                .indexedFileDao().getCount()

            if (count == 0) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Index is empty")
                    .setMessage(
                        "No files have been indexed yet.\n\n" +
                        "Tap \"Index Files\" first and wait for it to complete, " +
                        "then search."
                    )
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }

            val searchDocs   = binding.cbDocuments.isChecked
            val searchImages = binding.cbImages.isChecked

            if (!searchDocs && !searchImages) {
                Toast.makeText(this@MainActivity, "Select at least one file type", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val intent = Intent(this@MainActivity, SearchResultsActivity::class.java).apply {
                putExtra(SearchResultsActivity.EXTRA_QUERY,         query)
                putExtra(SearchResultsActivity.EXTRA_SEARCH_DOCS,   searchDocs)
                putExtra(SearchResultsActivity.EXTRA_SEARCH_IMAGES, searchImages)
            }
            startActivity(intent)
        }
    }

    // ── Status ────────────────────────────────────────────────────────────────

    private fun updateIndexStatus() {
        lifecycleScope.launch {
            val dao   = AppDatabase.getInstance(this@MainActivity).indexedFileDao()
            val total = dao.getCount()
            val docs  = dao.getCountByType("document")
            val imgs  = dao.getCountByType("image")

            binding.tvStatus.text = if (total == 0) {
                "⚠️ No index yet — tap \"Index Files\" to build one"
            } else {
                "✅ Index ready: $total files ($docs docs, $imgs images)"
            }
        }
    }

    // ── Folder picker ─────────────────────────────────────────────────────────

    private fun openFolderPicker() {
        folderPickerLauncher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    private fun addFolderToList(path: String) {
        if (selectedFolders.contains(path)) {
            Toast.makeText(this, "Folder already added", Toast.LENGTH_SHORT).show()
            return
        }
        selectedFolders.add(path)
        refreshFolderChips()
    }

    private fun refreshFolderChips() {
        binding.chipGroupFolders.removeAllViews()
        selectedFolders.forEachIndexed { index, path ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = path.substringAfterLast("/")
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    selectedFolders.removeAt(index)
                    refreshFolderChips()
                }
                setOnClickListener {
                    Toast.makeText(this@MainActivity, path, Toast.LENGTH_LONG).show()
                }
            }
            binding.chipGroupFolders.addView(chip)
        }
    }

    // ── Activity result launchers ─────────────────────────────────────────────

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val docId = androidx.documentfile.provider.DocumentFile
                .fromTreeUri(this, uri)?.uri?.lastPathSegment ?: return@registerForActivityResult
            val realPath = if (docId.startsWith("primary:")) {
                Environment.getExternalStorageDirectory().absolutePath +
                "/" + docId.removePrefix("primary:")
            } else {
                "/storage/" + docId.replace(":", "/")
            }
            addFolderToList(realPath)
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionBanner()
        updateIndexStatus()
    }

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissionBanner()
        if (!granted) Toast.makeText(this, "Storage permission denied", Toast.LENGTH_LONG).show()
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional — silent */ }
}



