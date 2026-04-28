package com.saf.searchallfiles

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.saf.searchallfiles.adapter.ResultsAdapter
import com.saf.searchallfiles.data.AppDatabase
import com.saf.searchallfiles.databinding.ActivitySearchResultsBinding
import kotlinx.coroutines.launch

class SearchResultsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUERY          = "query"
        const val EXTRA_SEARCH_DOCS    = "search_documents"
        const val EXTRA_SEARCH_IMAGES  = "search_images"
    }

    private lateinit var binding: ActivitySearchResultsBinding
    private lateinit var adapter: ResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchResultsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val query        = intent.getStringExtra(EXTRA_QUERY) ?: return
        val searchDocs   = intent.getBooleanExtra(EXTRA_SEARCH_DOCS, true)
        val searchImages = intent.getBooleanExtra(EXTRA_SEARCH_IMAGES, false)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "\"$query\""
        }

        adapter = ResultsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        performSearch(query, searchDocs, searchImages)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun performSearch(query: String, searchDocs: Boolean, searchImages: Boolean) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvCount.text = "Searching…"

        lifecycleScope.launch {
            val db  = AppDatabase.getInstance(this@SearchResultsActivity)
            val dao = db.indexedFileDao()

            val types = buildList {
                if (searchDocs)   add("document")
                if (searchImages) add("image")
            }

            val results = if (types.isEmpty()) {
                emptyList()
            } else {
                dao.search("%$query%", types)
            }

            binding.progressBar.visibility = View.GONE
            binding.tvCount.text = "${results.size} result(s) for \"$query\""

            if (results.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
            } else {
                binding.tvEmpty.visibility = View.GONE
                adapter.submitList(results)
            }
        }
    }
}
