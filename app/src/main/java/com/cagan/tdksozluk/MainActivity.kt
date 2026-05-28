package com.cagan.tdksozluk

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cagan.tdksozluk.data.DictionaryRepository
import com.cagan.tdksozluk.data.WordIndexEntry
import com.cagan.tdksozluk.databinding.ActivityMainBinding
import com.cagan.tdksozluk.ui.ThemeManager
import com.cagan.tdksozluk.ui.WordAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: WordAdapter
    private val repo by lazy { DictionaryRepository.get(this) }

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = WordAdapter { entry -> openDetail(entry) }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                runSearch(s?.toString().orEmpty())
            }
        })

        binding.fabRandom.setOnClickListener {
            val e = repo.randomEntry() ?: return@setOnClickListener
            openDetail(e)
        }

        loadIndex()
    }

    private fun loadIndex() {
        binding.loadingGroup.visibility = View.VISIBLE
        binding.recycler.visibility = View.GONE
        binding.fabRandom.hide()
        binding.search.isEnabled = false
        binding.progressText.text = getString(R.string.loading_preparing)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repo.ensureIndex { current, total ->
                    val pct = if (total > 0) (current * 100 / total).toInt() else 0
                    runOnUiThread {
                        binding.progressBar.progress = pct.coerceIn(0, 100)
                        binding.progressText.text = getString(R.string.loading_indexing, pct)
                    }
                }
            }
            binding.progressBar.progress = 100
            binding.loadingGroup.visibility = View.GONE
            binding.recycler.visibility = View.VISIBLE
            binding.fabRandom.show()
            binding.search.isEnabled = true
            binding.subtitle.text = getString(R.string.subtitle_count, repo.count())
            runSearch("")
        }
    }

    private fun runSearch(q: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(80)
            val results = withContext(Dispatchers.Default) { repo.search(q) }
            adapter.submitList(results)
            binding.empty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openDetail(entry: WordIndexEntry) {
        val i = Intent(this, DetailActivity::class.java)
        i.putExtra(DetailActivity.EXTRA_OFFSET, entry.offset)
        i.putExtra(DetailActivity.EXTRA_LENGTH, entry.length)
        i.putExtra(DetailActivity.EXTRA_MADDE, entry.madde)
        startActivity(i)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_favorites -> {
            startActivity(Intent(this, FavoritesActivity::class.java)); true
        }
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
