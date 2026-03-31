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
            holder.icon.setImageResource(android.R.drawable.ic_input_add)
        } else {
            holder.icon.setImageResource(android.R.drawable.ic_menu_compass)
        }

        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }
}