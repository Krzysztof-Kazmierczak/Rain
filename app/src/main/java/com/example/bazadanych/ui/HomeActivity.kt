package com.example.bazadanych.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R
import com.example.bazadanych.data.db.RainStorage
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

class HomeActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var tiles: MutableList<RainTile>
    private lateinit var adapter: RainTileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        recycler = findViewById(R.id.rainRecycler)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)

        setSupportActionBar(toolbar)

        recycler.layoutManager = GridLayoutManager(this, 2)

        tiles = mutableListOf()

        adapter = RainTileAdapter(tiles) { tile ->
            if (tile.isAddButton) {
                val intent = Intent(this, CreateRainActivity::class.java)
                startActivityForResult(intent, 100)
            } else {
                val intent = Intent(this, RainDetailsActivity::class.java)
                intent.putExtra("name", tile.title)
                intent.putExtra("length", tile.hoseLength)
                startActivity(intent)
            }
        }

        recycler.adapter = adapter

        loadTiles()

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
                R.id.nav_home ->
                    Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()

                R.id.nav_profile ->
                    Toast.makeText(this, "Profil", Toast.LENGTH_SHORT).show()

                R.id.nav_logout ->
                    logout()
            }
            drawer.closeDrawers()
            true
        }
    }

    private fun loadTiles() {

        tiles.clear()

        val rains = RainStorage.loadRains(this)

        rains.forEach {
            tiles.add(
                RainTile(
                    title = it.name,
                    hoseLength = it.hoseLength,
                    isAddButton = false
                )
            )
        }

        // przycisk dodawania zawsze na końcu
        tiles.add(RainTile("Dodaj deszczownię", true))

        adapter.notifyDataSetChanged()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            loadTiles()
            Toast.makeText(this, "Dodano deszczownię", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logout() {
        Toast.makeText(this, "Wylogowano", Toast.LENGTH_SHORT).show()
        finish()
    }
}