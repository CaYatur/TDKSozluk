package com.cagan.tdksozluk.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cagan.tdksozluk.data.WordIndexEntry
import com.cagan.tdksozluk.databinding.ItemWordBinding

class WordAdapter(
    private val onClick: (WordIndexEntry) -> Unit
) : ListAdapter<WordIndexEntry, WordAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemWordBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onClick(getItem(pos))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemWordBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.binding.textWord.text = item.madde
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<WordIndexEntry>() {
            override fun areItemsTheSame(o: WordIndexEntry, n: WordIndexEntry) =
                o.offset == n.offset
            override fun areContentsTheSame(o: WordIndexEntry, n: WordIndexEntry) = o == n
        }
    }
}
