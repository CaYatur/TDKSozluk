package com.cagan.tdksozluk

import android.content.Intent
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cagan.tdksozluk.data.DictionaryRepository
import com.cagan.tdksozluk.data.FavoritesStore
import com.cagan.tdksozluk.data.Madde
import com.cagan.tdksozluk.databinding.ActivityDetailBinding
import com.cagan.tdksozluk.databinding.ItemAnlamBinding
import com.cagan.tdksozluk.databinding.ItemAtasozuBinding
import com.cagan.tdksozluk.ui.ThemeManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var favorites: FavoritesStore
    private var currentMadde: Madde? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        favorites = FavoritesStore(this)

        val offset = intent.getLongExtra(EXTRA_OFFSET, -1L)
        val length = intent.getIntExtra(EXTRA_LENGTH, -1)
        val maddeName = intent.getStringExtra(EXTRA_MADDE)
        val byId = intent.getStringExtra(EXTRA_MADDE_ID)
        val byName = intent.getStringExtra(EXTRA_MADDE_NAME)

        binding.title.text = maddeName ?: byName ?: ""

        val repo = DictionaryRepository.get(this)
        lifecycleScope.launch {
            val madde = withContext(Dispatchers.IO) {
                when {
                    offset >= 0 && length > 0 -> repo.loadMadde(
                        com.cagan.tdksozluk.data.WordIndexEntry(
                            maddeId = "",
                            madde = maddeName ?: "",
                            maddeDuz = (maddeName ?: "").lowercase(),
                            offset = offset,
                            length = length
                        )
                    )
                    byId != null -> repo.loadMaddeById(byId)
                    byName != null -> repo.findByMadde(byName)?.let { repo.loadMadde(it) }
                    else -> null
                }
            }
            if (madde == null) {
                Snackbar.make(binding.root, R.string.error_not_found, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            currentMadde = madde
            renderMadde(madde)
            invalidateOptionsMenu()
        }
    }

    private fun renderMadde(m: Madde) {
        binding.title.text = m.madde
        val subBits = mutableListOf<String>()
        m.telaffuz?.let { subBits.add(it) }
        m.lisan?.let { subBits.add(getString(R.string.from_lang, it)) }
        binding.subtitle.text = subBits.joinToString(" · ")
        binding.subtitle.visibility = if (subBits.isEmpty()) View.GONE else View.VISIBLE

        binding.anlamlarContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for ((idx, a) in m.anlamlar.withIndex()) {
            val item = ItemAnlamBinding.inflate(inflater, binding.anlamlarContainer, false)
            val n = (idx + 1).toString()
            val sb = SpannableStringBuilder()
            sb.append(n).append(". ")
            if (a.ozellikler.isNotEmpty()) {
                val pieces = a.ozellikler.joinToString(", ")
                val start = sb.length
                sb.append(pieces)
                sb.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), start, sb.length, 0)
                sb.append(" ")
            }
            sb.append(a.anlam)
            item.textAnlam.text = sb

            item.orneklerContainer.removeAllViews()
            if (a.ornekler.isEmpty()) {
                item.orneklerContainer.visibility = View.GONE
            } else {
                item.orneklerContainer.visibility = View.VISIBLE
                for (o in a.ornekler) {
                    val tv = TextView(this).apply {
                        setTextAppearance(R.style.TextAppearance_TDK_Example)
                        val text = SpannableStringBuilder()
                        text.append("“").append(o.ornek).append("”")
                        if (!o.yazar.isNullOrBlank()) {
                            text.append("  — ").append(o.yazar)
                        }
                        setText(text)
                        val lp = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        lp.topMargin = dp(4)
                        layoutParams = lp
                    }
                    item.orneklerContainer.addView(tv)
                }
            }
            binding.anlamlarContainer.addView(item.root)
        }

        if (m.atasozu.isEmpty()) {
            binding.atasozuTitle.visibility = View.GONE
            binding.atasozuContainer.visibility = View.GONE
        } else {
            binding.atasozuTitle.visibility = View.VISIBLE
            binding.atasozuContainer.visibility = View.VISIBLE
            binding.atasozuContainer.removeAllViews()
            for (a in m.atasozu) {
                val item = ItemAtasozuBinding.inflate(inflater, binding.atasozuContainer, false)
                item.textAtasozu.text = a.madde
                item.root.setOnClickListener {
                    val i = Intent(this, DetailActivity::class.java)
                    i.putExtra(EXTRA_MADDE_ID, a.maddeId)
                    i.putExtra(EXTRA_MADDE, a.madde)
                    startActivity(i)
                }
                binding.atasozuContainer.addView(item.root)
            }
        }

        if (!m.birlesikler.isNullOrBlank()) {
            binding.birlesiklerTitle.visibility = View.VISIBLE
            binding.birlesikler.visibility = View.VISIBLE
            binding.birlesikler.text = m.birlesikler
        } else {
            binding.birlesiklerTitle.visibility = View.GONE
            binding.birlesikler.visibility = View.GONE
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.detail, menu)
        val item = menu.findItem(R.id.action_favorite)
        val m = currentMadde
        if (m != null) {
            val fav = favorites.isFavorite(m.madde)
            item.setIcon(if (fav) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            item.setTitle(if (fav) R.string.remove_from_favorites else R.string.add_to_favorites)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_favorite -> {
            val m = currentMadde
            if (m != null) {
                val nowFav = favorites.toggle(m.madde)
                Snackbar.make(
                    binding.root,
                    if (nowFav) R.string.added_to_favorites else R.string.removed_from_favorites,
                    Snackbar.LENGTH_SHORT
                ).show()
                invalidateOptionsMenu()
            }
            true
        }
        R.id.action_share -> {
            val m = currentMadde
            if (m != null) {
                val sb = StringBuilder()
                sb.append(m.madde).append('\n')
                for ((i, a) in m.anlamlar.withIndex()) {
                    sb.append(i + 1).append(". ").append(a.anlam).append('\n')
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
                }
                startActivity(Intent.createChooser(send, getString(R.string.share)))
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_OFFSET = "offset"
        const val EXTRA_LENGTH = "length"
        const val EXTRA_MADDE = "madde"
        const val EXTRA_MADDE_ID = "madde_id"
        const val EXTRA_MADDE_NAME = "madde_name"
    }
}
