package com.example.bazadanych.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R
import com.example.bazadanych.data.db.PhoneNumberItem

class PhoneNumberAdapter(
    private val roles: Array<String>,
    private val onEdit: (PhoneNumberItem) -> Unit,
    private val onDelete: (PhoneNumberItem) -> Unit
) : RecyclerView.Adapter<PhoneNumberAdapter.ViewHolder>() {

    private val items = mutableListOf<PhoneNumberItem>()
    private var myLevel = 3

    fun submit(list: List<PhoneNumberItem>, currentLevel: Int) {
        items.clear()
        items.addAll(list)
        myLevel = currentLevel
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumber: TextView = view.findViewById(R.id.tvPhoneNumber)
        val tvRole: TextView = view.findViewById(R.id.tvPhoneRole)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditPhone)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePhone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phone_number, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        holder.tvNumber.text = item.pelnyNumer

        val parts = mutableListOf(roles.getOrElse(item.poziomDostepu) { "?" })
        if (item.isAdminNumber) parts.add(ctx.getString(R.string.phone_main_badge))
        if (item.potwierdzono == null) parts.add(ctx.getString(R.string.phone_unconfirmed))
        holder.tvRole.text = parts.joinToString(" • ")

        // Numer główny ustawia się na panelu maszyny, a numerów z wyższym poziomem
        // niż własny nie wolno zmieniać. Serwer sprawdza to samo.
        val canModify = !item.isAdminNumber && item.poziomDostepu >= myLevel
        val visibility = if (canModify) View.VISIBLE else View.INVISIBLE
        holder.btnEdit.visibility = visibility
        holder.btnDelete.visibility = visibility

        holder.btnEdit.setOnClickListener { onEdit(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }
}