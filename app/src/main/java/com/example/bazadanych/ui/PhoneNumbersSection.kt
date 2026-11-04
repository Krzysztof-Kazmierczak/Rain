package com.example.bazadanych.ui

import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R
import com.example.bazadanych.data.db.PhoneNumberItem
import com.example.bazadanych.data.repository.PhoneNumberRepository
import com.google.android.material.button.MaterialButton

/**
 * Sekcja "Numery telefonów" w ustawieniach zaawansowanych maszyny.
 *
 * Widoczna tylko dla właściciela maszyny i pracowników z poziomem
 * Administrator (0) lub Kierownik (1). O widoczności decyduje serwer.
 */
class PhoneNumbersSection(
    private val activity: AppCompatActivity,
    private val machineId: String,
    private val email: String
) {
    private val repo = PhoneNumberRepository()
    private val roles: Array<String> = activity.resources.getStringArray(R.array.roles_array)

    private val card: View = activity.findViewById(R.id.cardPhoneNumbers)
    private val tvAdminNumber: TextView = activity.findViewById(R.id.tvAdminNumber)
    private val tvEmpty: TextView = activity.findViewById(R.id.tvPhoneEmpty)
    private val rvNumbers: RecyclerView = activity.findViewById(R.id.rvPhoneNumbers)
    private val btnAdd: MaterialButton = activity.findViewById(R.id.btnAddPhone)

    private var myLevel = 3

    private val adapter = PhoneNumberAdapter(
        roles = roles,
        onEdit = { showEditDialog(it) },
        onDelete = { confirmDelete(it) }
    )

    fun init() {
        card.visibility = View.GONE
        rvNumbers.layoutManager = LinearLayoutManager(activity)
        rvNumbers.adapter = adapter
        btnAdd.setOnClickListener { showEditDialog(null) }
        load()
    }

    fun load() {
        if (machineId.isEmpty() || email.isEmpty()) return

        repo.getNumbers(machineId, email) { response ->
            if (activity.isFinishing || activity.isDestroyed) return@getNumbers

            if (response == null || !response.canManage) {
                card.visibility = View.GONE
                return@getNumbers
            }

            myLevel = response.myLevel.coerceIn(0, 3)
            card.visibility = View.VISIBLE

            if (response.adminNumber.isNotEmpty()) {
                tvAdminNumber.text = activity.getString(R.string.phone_admin_number, response.adminNumber)
                tvAdminNumber.visibility = View.VISIBLE
            } else {
                tvAdminNumber.visibility = View.GONE
            }

            adapter.submit(response.numbers, myLevel)
            tvEmpty.visibility = if (response.numbers.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /** existing == null -> dodawanie nowego numeru. */
    private fun showEditDialog(existing: PhoneNumberItem?) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_phone_number, null)
        val etPrefix = view.findViewById<EditText>(R.id.etPhonePrefix)
        val etNumber = view.findViewById<EditText>(R.id.etPhoneNumber)
        val spinner = view.findViewById<Spinner>(R.id.spinnerPhoneLevel)

        // Można nadać tylko poziom równy własnemu albo niższy (większa liczba).
        val allowedLevels = (myLevel..3).toList()
        val spinnerAdapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            allowedLevels.map { roles.getOrElse(it) { "?" } }
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        if (existing != null) {
            etPrefix.setText(existing.kierunkowy)
            etNumber.setText(existing.numer)
            val index = allowedLevels.indexOf(existing.poziomDostepu)
            spinner.setSelection(if (index >= 0) index else allowedLevels.lastIndex)
        } else {
            etPrefix.setText("48")
            spinner.setSelection(allowedLevels.lastIndex) // domyślnie najniższy poziom: Odczyt
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (existing == null) R.string.phone_dialog_add_title else R.string.phone_dialog_edit_title)
            .setView(view)
            .setPositiveButton(R.string.phone_save, null)
            .setNegativeButton(R.string.phone_cancel, null)
            .create()

        // Własny listener, żeby okno nie zamykało się przy błędnych danych.
        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val prefix = etPrefix.text.toString().filter { it.isDigit() }
                val number = etNumber.text.toString().filter { it.isDigit() }

                if (prefix.length !in 1..4) {
                    etPrefix.error = activity.getString(R.string.phone_invalid_prefix)
                    return@setOnClickListener
                }
                if (number.length !in 6..15) {
                    etNumber.error = activity.getString(R.string.phone_invalid_number)
                    return@setOnClickListener
                }

                val level = allowedLevels[spinner.selectedItemPosition.coerceAtLeast(0)]
                btnSave.isEnabled = false

                repo.saveNumber(existing?.id ?: 0, machineId, email, prefix, number, level) { success, message ->
                    if (activity.isFinishing || activity.isDestroyed) return@saveNumber
                    btnSave.isEnabled = true
                    if (success) {
                        dialog.dismiss()
                        Toast.makeText(activity, R.string.phone_saved, Toast.LENGTH_SHORT).show()
                        load()
                    } else {
                        showError(message)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun confirmDelete(item: PhoneNumberItem) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.phone_delete_title)
            .setMessage(activity.getString(R.string.phone_delete_msg, item.pelnyNumer))
            .setPositiveButton(R.string.phone_delete_yes) { _, _ ->
                repo.deleteNumber(item.id, machineId, email) { success, message ->
                    if (activity.isFinishing || activity.isDestroyed) return@deleteNumber
                    if (success) {
                        Toast.makeText(activity, R.string.phone_deleted, Toast.LENGTH_SHORT).show()
                        load()
                    } else {
                        showError(message)
                    }
                }
            }
            .setNegativeButton(R.string.phone_cancel, null)
            .show()
    }

    private fun showError(message: String?) {
        val text = if (message == null) {
            activity.getString(R.string.phone_error_network)
        } else {
            activity.getString(R.string.phone_error, message)
        }
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }
}