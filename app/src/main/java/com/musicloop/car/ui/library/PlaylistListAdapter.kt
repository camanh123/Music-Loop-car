package com.musicloop.car.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.musicloop.car.databinding.ItemPlaylistRowBinding
import com.musicloop.car.playlist.Playlist

class PlaylistListAdapter : BaseAdapter() {
    private val items = mutableListOf<Playlist>()

    fun submit(rows: List<Playlist>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Playlist = items[position]

    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (convertView != null) {
            ItemPlaylistRowBinding.bind(convertView)
        } else {
            ItemPlaylistRowBinding.inflate(inflater, parent, false)
        }
        val row = items[position]
        binding.playlistName.text = row.name
        binding.playlistCount.text = row.trackCount.toString()
        return binding.root
    }
}
