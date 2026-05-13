package com.example.bazadanych.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R
import com.example.bazadanych.data.db.WorkerResponse
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException

class ProfileActivity : AppCompatActivity() {

    private val remoteRepo = RainRemoteRepository()
    private lateinit var userEmail: String

    // UI Elements
    private lateinit var tvEmptyWorkers: TextView
    private lateinit var rvWorkers: RecyclerView
    private lateinit var layoutAddUser: LinearLayout
    private lateinit var etNewUserEmail: EditText
    private lateinit var spinnerNewUserRole: Spinner
    private lateinit var btnAddUser: Button

    private var workerAdapter: WorkerAdapter? = null

    // Zmień na adres swojego serwera!
    private val BASE_URL = "https://rain-tech.pl/web"
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val sharedPrefs = getSharedPreferences("user_session", MODE_PRIVATE)
        userEmail = sharedPrefs.getString("user_email", "Brak maila") ?: "Brak maila"

        setupToolbar()
        initUI()
        loadMachineStats()

        // Inicjalizacja ładowania użytkowników
        loadWorkers()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun initUI() {
        val tvEmail = findViewById<TextView>(R.id.tvProfileEmail)
        tvEmail.text = userEmail

        tvEmptyWorkers = findViewById(R.id.tvEmptyWorkers)
        rvWorkers = findViewById(R.id.rvWorkers)
        rvWorkers.layoutManager = LinearLayoutManager(this)

        layoutAddUser = findViewById(R.id.layoutAddUser)
        etNewUserEmail = findViewById(R.id.etNewUserEmail)
        spinnerNewUserRole = findViewById(R.id.spinnerNewUserRole)
        btnAddUser = findViewById(R.id.btnAddUser)

        btnAddUser.setOnClickListener {
            val emailToAdd = etNewUserEmail.text.toString()
            val levelToSave = spinnerNewUserRole.selectedItemPosition

            if (emailToAdd.isNotEmpty()) {
                saveWorker(0, emailToAdd, levelToSave)
            } else {
                Toast.makeText(this, "Podaj email pracownika", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- LOGIKA ZARZĄDZANIA UŻYTKOWNIKAMI ---

    private fun loadWorkers() {
        val url = "$BASE_URL/get_workers.php?email=$userEmail"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvEmptyWorkers.text = "Błąd pobierania danych."
                    tvEmptyWorkers.visibility = View.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (responseData != null) {
                    try {
                        val gson = Gson()
                        val data = gson.fromJson(responseData, WorkerResponse::class.java)

                        runOnUiThread {
                            handleWorkersResponse(data)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvEmptyWorkers.text = "Błąd parsowania danych."
                            tvEmptyWorkers.visibility = View.VISIBLE
                        }
                    }
                }
            }
        })
    }

    private fun handleWorkersResponse(data: WorkerResponse) {
        val myLevel = data.my_level?.toIntOrNull() ?: 0
        val rolesArray = resources.getStringArray(R.array.roles_array)

        val customAddAdapter = RoleSpinnerAdapter(this, android.R.layout.simple_spinner_item, rolesArray, myLevel)
        customAddAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNewUserRole.adapter = customAddAdapter

        // Ustawiamy domyślnie najniższy możliwy poziom, aby uniknąć wybrania zablokowanego 0
        spinnerNewUserRole.setSelection(myLevel)

        // Widoczność formularza dodawania
        if (data.role == "none") {
            layoutAddUser.visibility = View.VISIBLE
            tvEmptyWorkers.text = "Brak powiązanych kont."
            tvEmptyWorkers.visibility = View.VISIBLE
            rvWorkers.visibility = View.GONE
            return
        }

        // Ukryj formularz jeśli użytkownik ma tylko "Odczyt" (Poziom 3)
        if (data.role == "worker" && myLevel == 3) {
            layoutAddUser.visibility = View.GONE
        } else {
            layoutAddUser.visibility = View.VISIBLE
        }

        if (data.workers.isEmpty()) {
            tvEmptyWorkers.text = "Brak dodanych użytkowników."
            tvEmptyWorkers.visibility = View.VISIBLE
            rvWorkers.visibility = View.GONE
        } else {
            tvEmptyWorkers.visibility = View.GONE
            rvWorkers.visibility = View.VISIBLE

            workerAdapter = WorkerAdapter(
                workers = data.workers,
                currentUserRole = data.role,
                currentUserLevel = myLevel, // Przekazujemy poziom
                currentUserEmail = userEmail,
                onSaveClick = { worker, newLevel ->
                    // Sprawdzamy czy to ja i czy nie mam jeszcze potwierdzonego dostępu
                    val isConfirming = (worker.worker_email == userEmail && worker.access_confirm == 0)
                    saveWorker(worker.id, worker.worker_email, newLevel, isConfirming)
                },
                onDeleteClick = { worker ->
                    confirmDeleteWorker(worker.id)
                }
            )
            rvWorkers.adapter = workerAdapter
        }
    }

    // Wewnątrz ProfileActivity:
    private fun saveWorker(id: Int, emailToSave: String, levelToSave: Int, isConfirming: Boolean = false) {
        val formBody = FormBody.Builder()
            .add("id", id.toString())
            .add("worker_email", emailToSave)
            .add("access_level", levelToSave.toString())
            .add("current_user", userEmail)
            .add("confirm_action", if (isConfirming) "1" else "0")
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/save_worker.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@ProfileActivity, "Błąd sieci", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                runOnUiThread {
                    if (result == "OK") {
                        if (id == 0) etNewUserEmail.text.clear()
                        loadWorkers() // To odświeży listę i zaktualizuje access_confirm w UI
                    } else {
                        Toast.makeText(this@ProfileActivity, "Błąd: $result", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }



    private fun confirmDeleteWorker(id: Int) {
        AlertDialog.Builder(this)
            .setTitle("Usuwanie dostępu")
            .setMessage("Czy na pewno chcesz usunąć dostęp?")
            .setPositiveButton("Tak") { _, _ -> deleteWorker(id) }
            .setNegativeButton("Nie", null)
            .show()
    }

    private fun deleteWorker(id: Int) {
        val formBody = FormBody.Builder()
            .add("id", id.toString())
            .add("current_user", userEmail)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/delete_worker.php")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@ProfileActivity, "Błąd sieci", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()?.trim()
                runOnUiThread {
                    if (result == "OK") {
                        loadWorkers() // Odśwież listę
                    } else {
                        Toast.makeText(this@ProfileActivity, "Błąd: $result", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    // --- LOGIKA MASZYN (Twoja istniejąca) ---
    private fun loadMachineStats() {
        val tvStats = findViewById<TextView>(R.id.tvProfileStats)
        val cachedTiles = com.example.bazadanych.data.local_db.CacheHelper.loadList<com.example.bazadanych.ui.RainTile>(this, "HOME_TILES_CACHE")

        if (cachedTiles != null && cachedTiles.isNotEmpty()) {
            val actualMachines = cachedTiles.filter { !it.isAddButton }
            val activeRains = actualMachines.count { it.isWorking == 1 }
            tvStats.text = "$activeRains / ${actualMachines.size}"
        } else {
            tvStats.text = "0 / 0"
        }

        remoteRepo.getRains(userEmail) { rains ->
            runOnUiThread {
                if (rains.isNotEmpty()) {
                    val totalRains = rains.size
                    val activeRains = rains.count { it.isWorking == 1 }
                    tvStats.text = "$activeRains / $totalRains"
                }
            }
        }
    }
}