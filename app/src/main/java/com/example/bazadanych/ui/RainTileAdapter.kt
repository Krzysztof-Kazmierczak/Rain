package com.example.bazadanych.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R

class RainTileAdapter(
    private val tiles: List<RainTile>,
    private val onTileClick: (RainTile) -> Unit
) : RecyclerView.Adapter<RainTileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.tileTitle)
        val lengthText: TextView = view.findViewById(R.id.tileLength)
        val commentText: TextView = view.findViewById(R.id.tileComment)
        val statusDot: View = view.findViewById(R.id.statusDot) // 🔴 Mapujemy naszą kropkę
        val icon: ImageView = view.findViewById(R.id.tileIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rain_tile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tile = tiles[position]

        if (tile.isAddButton) {
            holder.lengthText.visibility = View.GONE
            holder.commentText.visibility = View.GONE
            holder.titleText.text = "Dodaj nową"
            holder.icon.setImageResource(android.R.drawable.ic_input_add)
        } else {
            holder.lengthText.visibility = View.VISIBLE
            holder.commentText.visibility = View.VISIBLE

            // Używamy pól z ViewHoldera!
            holder.titleText.text = tile.title // Jeśli w klasie RainTile masz 'name' zamiast 'title'
            holder.lengthText.text = "Wąż: ${tile.hoseLength}m"
            holder.commentText.text = tile.comment
            holder.icon.setImageResource(R.drawable.ic_rain_reel)
        }

        // Status kropki
        if (tile.isAddButton) {
            holder.statusDot.visibility = View.GONE
        } else {
            holder.statusDot.visibility = View.VISIBLE
            val colorRes = if (tile.isWorking) R.drawable.circle_green else R.drawable.circle_red
            holder.statusDot.setBackgroundResource(colorRes)
        }

        holder.itemView.setOnClickListener { onTileClick(tile) }
    }

    override fun getItemCount(): Int = tiles.size
}