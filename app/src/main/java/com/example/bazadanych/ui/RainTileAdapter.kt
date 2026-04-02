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
        val statusDot: View = view.findViewById(R.id.statusDot) // 🔴 Mapujemy naszą kropkę
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rain_tile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tile = tiles[position]

        // 1. USTAWIANIE TEKSTÓW Z BAZY
        holder.titleText.text = tile.title

        val commentText = holder.itemView.findViewById<TextView>(R.id.tileComment) // Pobieramy pole komentarza
        val lengthText = holder.itemView.findViewById<TextView>(R.id.tileLength)

        if (tile.isAddButton) {

            lengthText.visibility = View.GONE
            commentText.visibility = View.GONE

            // 🚜 ZMIANA IKONY NA PLUS (Żeby nie było traktora na przycisku DODAJ)
            holder.itemView.findViewById<ImageView>(R.id.tileIcon).setImageResource(android.R.drawable.ic_input_add)

        } else {
            holder.lengthText.text = "Wąż: ${tile.hoseLength}m"
            commentText.text = tile.comment // 👈 TUTAJ WPISUJEMY PRAWDZIWY KOMENTARZ Z BAZY!

            // 🚜 PRZYWRACAMY TRAKTORA/DESZCZOWNIĘ DLA ZWYKŁYCH KAFELKÓW
            holder.itemView.findViewById<ImageView>(R.id.tileIcon).setImageResource(R.drawable.outline_agriculture_24)
        }

        // 2. LOGIKA KROPKI STATUSU (Zostaje bez zmian)
        if (tile.isAddButton) {
            holder.statusDot.visibility = View.GONE
        } else {
            holder.statusDot.visibility = View.VISIBLE
            if (tile.isWorking) {
                holder.statusDot.setBackgroundResource(R.drawable.circle_green)
            } else {
                holder.statusDot.setBackgroundResource(R.drawable.circle_red)
            }
        }

        holder.itemView.setOnClickListener {
            onTileClick(tile)
        }
    }

    override fun getItemCount(): Int = tiles.size
}