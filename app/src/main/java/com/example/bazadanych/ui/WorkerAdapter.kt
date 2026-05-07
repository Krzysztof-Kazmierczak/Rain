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

    // TA METODA BYŁA BRAKUJĄCA:
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_worker, parent, false)
        return WorkerViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkerViewHolder, position: Int) {
        val worker = workers[position]
        val wLevel = worker.access_level
        val isMe = worker.worker_email == currentUserEmail
        val hasConfirmed = worker.access_confirm == 1

        holder.tvEmail.text = if (isMe) "${worker.worker_email} (Ty)" else worker.worker_email

        // 1. Ustawienie niestandardowego adaptera dla Spinnera (blokowanie poziomów)
        val rolesArray = holder.itemView.context.resources.getStringArray(R.array.roles_array)
        val customAdapter = RoleSpinnerAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_item,
            rolesArray,
            currentUserLevel
        )
        customAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.spinnerRole.adapter = customAdapter
        holder.spinnerRole.setSelection(wLevel)

        // 2. Logika uprawnień
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
                canEdit = true
                canDelete = true
            }
        }

        // 3. Widoczność UI
        if (canEdit) {
            holder.spinnerRole.visibility = View.VISIBLE
            holder.tvRoleReadOnly.visibility = View.GONE
            holder.btnSave.visibility = View.VISIBLE
            holder.btnSave.text = if (isConfirmingAction) "Potwierdź i Zapisz" else "Zapisz"
        } else {
            holder.spinnerRole.visibility = View.GONE
            holder.tvRoleReadOnly.visibility = View.VISIBLE
            holder.tvRoleReadOnly.text = rolesArray[wLevel]
            holder.btnSave.visibility = View.GONE
        }

        if (canDelete) {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.text = if (isMe) "Odrzuć dostęp" else "Usuń"
        } else {
            holder.btnDelete.visibility = View.GONE
        }

        // 4. Akcje
        holder.btnSave.setOnClickListener {
            val selectedLevel = holder.spinnerRole.selectedItemPosition
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