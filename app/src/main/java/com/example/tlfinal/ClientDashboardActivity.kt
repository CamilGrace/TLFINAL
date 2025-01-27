package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import android.widget.ImageView
import com.example.tlfinal.databinding.ClientDashboardBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ClientDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ClientDashboardBinding
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var menuIcon: ImageView
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        // Initialize DrawerLayout and NavigationView
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)

        // Initialize BottomNavigationView
        bottomNavigation = findViewById(R.id.bottomNavigation)

        // Initialize the hamburger menu icon
        menuIcon = findViewById(R.id.img_menu)

        // Set an OnClickListener on the hamburger menu to open/close the drawer
        menuIcon.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Handle item clicks in the navigation drawer
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> startActivity(Intent(this, ClientProfileActivity::class.java))
                R.id.nav_inbox -> startActivity(Intent(this, ClientInboxActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, ClientSettingsActivity::class.java))
                R.id.nav_logout -> signOutUser()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Handle Bottom Navigation selection
        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_message -> {
                    // Navigate to Profile Activity
                    startActivity(Intent(this, ClientInboxActivity::class.java))
                    true
                }
                R.id.bottom_home -> {
                    // Stay on Home, no action needed
                    true
                }
                R.id.bottom_settings -> {
                    // Navigate to Settings Activity
                    startActivity(Intent(this, ClientSettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // OnClickListener for container_matching
        binding.containerMatching.setOnClickListener {
            // Navigate to LawyerMatchingActivity
            val intent = Intent(this, LawyerMatchingActivity::class.java)
            startActivity(intent)
        }

        // Load the user's name from Firestore
        loadUserName()
    }

    // Function to load the user's name and display it in the drawer header
    private fun loadUserName() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let {
            firestore.collection("users").document(it)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val userName = document.getString("fullName") // Assuming the field is named 'fullName'
                        userName?.let { name ->
                            // Update the header view in the navigation drawer with the user's name
                            val headerView = navigationView.getHeaderView(0)
                            val userNameTextView: TextView = headerView.findViewById(R.id.user_name)
                            userNameTextView.text = name
                        }
                    }
                }
                .addOnFailureListener { e ->
                    // Handle error if any
                    e.printStackTrace()
                }
        }
    }

    // Ensure "Home" is selected on start or whenever you navigate to the Client Dashboard
    override fun onResume() {
        super.onResume()
        // Programmatically select the Home icon
        bottomNavigation.selectedItemId = R.id.bottom_home
    }

    private fun signOutUser() {
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Close the current activity
    }
}
