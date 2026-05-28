package com.cagan.tdksozluk

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.cagan.tdksozluk.data.DictionaryRepository
import com.cagan.tdksozluk.data.FavoritesStore
import com.cagan.tdksozluk.databinding.ActivityFavoritesBinding
import com.cagan.tdksozluk.ui.ThemeManager
import com.cagan.tdksozluk.ui.WordAdapter

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var adapter: WordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.favorites)

        adapter = WordAdapter { entry ->
            val i = Intent(this, DetailActivity::class.java)
            i.putExtra(DetailActivity.EXTRA_OFFSET, entry.offset)
            i.putExtra(DetailActivity.EXTRA_LENGTH, entry.length)
            i.putExtra(DetailActivity.EXTRA_MADDE, entry.madde)
            startActivity(i)
        }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        val repo = DictionaryRepository.get(this)
        val favs = FavoritesStore(this).all()
        val entries = if (repo.isReady())
            favs.mapNotNull { repo.findByMadde(it) }
        else emptyList()
        adapter.submitList(entries)
        binding.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        else -> super.onOptionsItemSelected(item)
    }
}
