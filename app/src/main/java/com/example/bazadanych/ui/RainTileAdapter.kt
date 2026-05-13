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

        // 1. Domyślny wygląd (reset dla kafelków z recyklingu)
        holder.itemView.alpha = 1.0f

        if (tile.isAddButton) {
            holder.lengthText.visibility = View.GONE
            holder.commentText.visibility = View.GONE
            holder.statusDot.visibility = View.GONE
            holder.titleText.text = "Dodaj"
            holder.icon.setImageResource(android.R.drawable.ic_input_add)
        } else {
            holder.lengthText.visibility = View.VISIBLE
            holder.commentText.visibility = View.VISIBLE
            holder.statusDot.visibility = View.VISIBLE
            holder.titleText.text = tile.title
            holder.lengthText.text = "Wąż: ${tile.hoseLength}m"
            holder.commentText.text = tile.comment
            holder.icon.setImageResource(R.drawable.ic_rain_reel)

            // 2. LOGIKA STATUSÓW (KOLORY I ZASZARZENIE)
            when (tile.isWorking) {
                0 -> {
                    // Szary/zaszarzały kafelek
                    holder.itemView.alpha = 0.4f // To sprawi, że kafelek będzie wyblakły
                    holder.statusDot.visibility = View.GONE // Dla 0 możemy ukryć kropkę lub dać szarą
                }
                1 -> holder.statusDot.setBackgroundResource(R.drawable.circle_red)
                2 -> holder.statusDot.setBackgroundResource(R.drawable.circle_green)
                5 -> holder.statusDot.setBackgroundResource(R.drawable.circle_dark_green)
                6 -> holder.statusDot.setBackgroundResource(R.drawable.circle_dark_red)
                7 -> holder.statusDot.setBackgroundResource(R.drawable.circle_dark_yellow)
                else -> holder.statusDot.setBackgroundResource(R.drawable.circle_orange)
            }
        }

        holder.itemView.setOnClickListener { onTileClick(tile) }
    }

    override fun getItemCount(): Int = tiles.size
}