package com.musicloop.car.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.musicloop.car.databinding.ItemSongRowBinding

class SongListAdapter : BaseAdapter() {

    private val items = mutableListOf<SongRow>()

    fun submit(rows: List<SongRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): SongRow = items[position]

    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (convertView != null) {
            ItemSongRowBinding.bind(convertView)
        } else {
            ItemSongRowBinding.inflate(inflater, parent, false)
        }
        val row = items[position]
        binding.songTitle.text = row.title
        binding.songArtist.text = row.artist.ifBlank { "—" }
        binding.songDuration.text = row.durationLabel
        if (row.unplayable) {
            binding.songStatus.visibility = View.VISIBLE
            binding.root.alpha = 0.4f
        } else {
            binding.songStatus.visibility = View.GONE
            binding.root.alpha = 1f
        }
        return binding.root
    }
}
