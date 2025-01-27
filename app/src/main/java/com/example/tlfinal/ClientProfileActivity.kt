package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.tlfinal.databinding.ClientProfileBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ClientProfileActivity : AppCompatActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var binding: ClientProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        // Fetch and display initial user data
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let { loadUserData(it) }

        // Handle Edit Profile Button
        binding.editProfileBtn.setOnClickListener {
            val intent = Intent(this, ClientEditProfileActivity::class.java)
            startActivity(intent)
        }

        // Handle Drawer Layout and Navigation View
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navigationView: NavigationView = binding.navView

        // Initialize BottomNavigationView
        val bottomNavigation: BottomNavigationView = binding.bottomNavigation

        // Handle Hamburger Menu icon click
        binding.imgMenu.setOnClickListener {
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
                    startActivity(Intent(this, ClientInboxActivity::class.java))
                    true
                }
                R.id.bottom_home -> {
                    // Stay on Home, no action needed
                    true
                }
                R.id.bottom_settings -> {
                    startActivity(Intent(this, ClientSettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadUserData(userId: String) {
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val fullName = document.getString("fullName") ?: "N/A"
                    val email = document.getString("email") ?: "N/A"
                    val contactNo = document.getString("contactNo") ?: "N/A"
                    val address = document.getString("address") ?: "N/A"

                    // Update UI with user data
                    binding.textFullName.text = fullName
                    binding.userEmail.text = email
                    binding.userContact.text = contactNo
                    binding.userAddress.text = address

                    // Update the drawer header with the user's name
                    val headerView = binding.navView.getHeaderView(0)
                    val userNameTextView: TextView = headerView.findViewById(R.id.user_name)
                    userNameTextView.text = fullName
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.bottom_home
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let { loadUserData(it) }
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