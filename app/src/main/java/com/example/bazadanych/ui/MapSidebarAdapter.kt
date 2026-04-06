package com.example.bazadanych.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Color

class MapSidebarAdapter(
    private val items: List<SidebarItem>,
    private val onItemClick: (SidebarItem) -> Unit
) : RecyclerView.Adapter<MapSidebarAdapter.ViewHolder>() {

    data class SidebarItem(val id: String, val name: String, val lat: Double, val lng: Double)

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textView.text = item.name
        holder.textView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}