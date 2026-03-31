package com.example.bazadanych.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R

class RainTileAdapter(
    private val items: List<RainTile>,
    private val onClick: (RainTile) -> Unit
) : RecyclerView.Adapter<RainTileAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tileTitle)
        val icon: ImageView = view.findViewById(R.id.tileIcon)

        // 🔽 DODANE
        val length: TextView = view.findViewById(R.id.tileLength)
        val comment: TextView = view.findViewById(R.id.tileComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rain_tile, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.title.text = item.title

        if (item.isAddButton) {
            // ➕ kafelek dodawania
            holder.icon.setImageResource(android.R.drawable.ic_input_add)

            holder.length.visibility = View.GONE
            holder.comment.visibility = View.GONE

        } else {
            // 🌧 normalny kafelek
            holder.icon.setImageResource(R.drawable.outline_agriculture_24)

            holder.length.visibility = View.VISIBLE
            holder.comment.visibility = View.VISIBLE

            holder.length.text = item.hoseLength + " m"
            holder.comment.text = item.comment
        }

        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }
}