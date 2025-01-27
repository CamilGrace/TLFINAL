package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.tlfinal.databinding.LawyerDashboardBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.prolificinteractive.materialcalendarview.MaterialCalendarView

class LawyerDashboardActivity : AppCompatActivity() {
    private lateinit var binding: LawyerDashboardBinding
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        // Get the DrawerLayout and NavigationView from the layout
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navigationView: NavigationView = binding.navView
        val bottomNavigationView: BottomNavigationView = binding.bottomNavigation

        // Open the Drawer when the menu icon is clicked
        binding.imgMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Handle item clicks in the navigation drawer
        navigationView.setNavigationItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_profile -> {
                    startActivity(Intent(this, LawyerProfileActivity::class.java))
                }
                R.id.nav_inbox -> {
                    // Handle Settings click
                }
                R.id.nav_settings -> {
                    // Handle Share click
                }
                R.id.nav_logout -> {
                    // Handle Logout click
                }
            }

            // Close the drawer after an item is selected
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }


        // Load user data and update navigation header
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let { loadUserData(it) }

        // Set background color for MaterialCalendarView
        val calendarView = findViewById<MaterialCalendarView>(R.id.calendarView)
        calendarView.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
        // If you need to set a transparent background
        //calendarView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    private fun loadUserData(userId: String) {
        firestore.collection("lawyers").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject(LawyerProfile::class.java)
                    user?.let {
                        // Update the navigation header with the user's full name
                        val headerView = binding.navView.getHeaderView(0)
                        val userNameTextView: TextView = headerView.findViewById(R.id.user_name)
                        userNameTextView.text = it.fullName
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun signOutUser() {
        // Clear user session data (if using SharedPreferences or other storage)
        val sharedPreferences = getSharedPreferences("user_session", MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // Redirect to LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear activity stack
        startActivity(intent)
        finish() // Close the current activity
    }
}
