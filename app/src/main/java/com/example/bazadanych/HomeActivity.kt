package com.example.bazadanych

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.appbar.MaterialToolbar

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navigationView =
            findViewById<NavigationView>(R.id.navigationView)

        setSupportActionBar(toolbar)

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
                    Toast.makeText(this,"Home",Toast.LENGTH_SHORT).show()

                R.id.nav_profile ->
                    Toast.makeText(this,"Profil",Toast.LENGTH_SHORT).show()

                R.id.nav_logout ->
                    logout()
            }

            drawer.closeDrawers()
            true
        }
    }

    private fun logout() {
        Toast.makeText(this,"Wylogowano",Toast.LENGTH_SHORT).show()
        finish()
    }
}