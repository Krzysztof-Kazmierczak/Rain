package com.example.bazadanych.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R
import com.example.bazadanych.data.db.Worker

class WorkerAdapter(
    private var workers: List<Worker>,
    private val currentUserRole: String,
    private val currentUserLevel: Int,
    private val currentUserEmail: String,
    private val onSaveClick: (Worker, Int) -> Unit,
    private val onDeleteClick: (Worker) -> Unit
) : RecyclerView.Adapter<WorkerAdapter.WorkerViewHolder>() {

    fun updateData(newWorkers: List<Worker>) {
        workers = newWorkers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_worker, parent, false)
        return WorkerViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkerViewHolder, position: Int) {
        val worker = workers[position]
        val wLevel = worker.access_level
        val isMe = worker.worker_email == currentUserEmail
        val hasConfirmed = worker.access_confirm == 1

        // 1. Ustawienie tekstów podstawowych
        holder.tvEmail.text = if (isMe) "${worker.worker_email} (Ty)" else worker.worker_email
        holder.spinnerRole.setSelection(wLevel)

        // 2. Logika uprawnień (Kto co może widzieć/klikać)
        var canEdit = false
        var canDelete = false
        var isConfirmingAction = false

        if (currentUserRole == "owner") {
            canEdit = true
            canDelete = true
        } else if (currentUserRole == "worker") {
            if (isMe) {
                canEdit = true
                canDelete = true
                if (!hasConfirmed) isConfirmingAction = true
            } else if (currentUserLevel < wLevel) {
                // Możesz edytować kogoś o niższym statusie (wyższy numer levelu)
                canEdit = true
                canDelete = true
            }
        }

        // 3. Zarządzanie widocznością UI
        if (canEdit) {
            holder.spinnerRole.visibility = View.VISIBLE
            holder.tvRoleReadOnly.visibility = View.GONE
            holder.btnSave.visibility = View.VISIBLE

            // Zmiana tekstu przycisku jeśli wymagane potwierdzenie
            holder.btnSave.text = if (isConfirmingAction) "Potwierdź i Zapisz" else "Zapisz"
        } else {
            holder.spinnerRole.visibility = View.GONE
            holder.tvRoleReadOnly.visibility = View.VISIBLE
            holder.tvRoleReadOnly.text = holder.itemView.context.resources.getStringArray(R.array.roles_array)[wLevel]
            holder.btnSave.visibility = View.GONE
        }

        if (canDelete) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.text = if (isMe) "Odrzuć dostęp" else "Usuń"
        } else {
            holder.btnDelete.visibility = View.GONE
        }

        // 4. Akcje przycisków
        holder.btnSave.setOnClickListener {
            val selectedLevel = holder.spinnerRole.selectedItemPosition
            // WYWOŁUJEMY funkcję przekazaną w konstruktorze, a nie definiujemy jej na nowo!
            onSaveClick(worker, selectedLevel)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(worker)
        }
    }

    override fun getItemCount(): Int = workers.size

    class WorkerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvEmail: TextView = itemView.findViewById(R.id.tvWorkerEmail)
        val spinnerRole: Spinner = itemView.findViewById(R.id.spinnerRole)
        val tvRoleReadOnly: TextView = itemView.findViewById(R.id.tvRoleReadOnly)
        val btnSave: Button = itemView.findViewById(R.id.btnSave)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }
}