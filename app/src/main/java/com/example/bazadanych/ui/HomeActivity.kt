package com.example.bazadanych.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.bazadanych.R
import com.example.bazadanych.data.repository.RainRemoteRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

class HomeActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var tiles: MutableList<RainTile>
    private lateinit var adapter: RainTileAdapter

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var refreshRunnable: Runnable

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val remoteRepo = RainRemoteRepository() // Przeniosłem tutaj dla porządku

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        recycler = findViewById(R.id.rainRecycler)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        setSupportActionBar(toolbar)

        recycler.layoutManager = GridLayoutManager(this, 2)
        tiles = mutableListOf()

        adapter = RainTileAdapter(tiles) { tile ->
            if (tile.isAddButton) {
                val intent = Intent(this, CreateRainActivity::class.java)
                startActivityForResult(intent, 100)
            } else {
                val intent = Intent(this, RainDetailsActivity::class.java)
                intent.putExtra("id", tile.id)
                startActivity(intent)
            }
        }

        recycler.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            loadTiles()
        }

        val toggle = ActionBarDrawerToggle(
            this,
            drawer,
            toolbar,
            R.string.open,
            R.string.close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
                R.id.nav_profile -> goProfile()
                R.id.nav_logout -> logout()
                R.id.nav_map -> goFullMap()
                R.id.nav_weather -> goWeather()
               // R.id.nav_analytics -> goAnalytics()
                }
            drawer.closeDrawers()
            true
        }

        refreshRunnable = object : Runnable {
            override fun run() {
                loadTiles()
                handler.postDelayed(this, 30000)
            }
        }
    }

    private fun loadTiles() {
        val email = getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", "") ?: ""

        remoteRepo.getRains(email) { rains ->
            // ⚠️ KLUCZOWA ZMIANA: OkHttp działa w tle, więc musimy wrócić do wątku UI!
            runOnUiThread {
                tiles.clear()

                // Filtrowanie na pracujące i niepracujące
                val workingRains = rains.filter { it.isWorking }
                val stoppedRains = rains.filter { !it.isWorking }

                // Dodawanie do listy
                workingRains.forEach {
                    tiles.add(RainTile(it.id, it.name, it.hoseLength, it.comment, false, true))
                }

                stoppedRains.forEach {
                    tiles.add(RainTile(it.id, it.name, it.hoseLength, it.comment, false, false))
                }

                // Przycisk dodawania
                tiles.add(RainTile("", "Dodaj deszczownię", "", "", true, false))

                adapter.notifyDataSetChanged()
                swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            loadTiles()
            Toast.makeText(this, "Dodano deszczownię", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logout() {
        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        prefs.edit().putBoolean("logged_in", false).apply()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        Toast.makeText(this, "Wylogowano ✅", Toast.LENGTH_SHORT).show()
    }

    private fun goProfile() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
    }
    private fun goFullMap() {
        val intent = Intent(this, FullMapActivity::class.java)
        startActivity(intent)
    }


    private fun goWeather() {
        val intent = Intent(this, WeatherActivity::class.java)
        startActivity(intent)
    }
    private fun goAnalytics() {
        val intent = Intent(this, AnalyticsActivity::class.java)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
        loadTiles()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }
}