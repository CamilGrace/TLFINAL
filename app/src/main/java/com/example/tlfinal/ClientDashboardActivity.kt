package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions // Import RequestOptions
import com.example.tlfinal.databinding.ClientDashboardBinding // Use ViewBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
// Import R class if not automatically resolved in this file

class ClientDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ClientDashboardBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "ClientDashboard" // Tag for logging
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupListeners() // Consolidate listener setup

        // Load user data initially (will also be called in onResume)
        // loadUserDataForDrawer() // Let onResume handle the first load too for simplicity
    }

    private fun setupListeners() {
        // Set an OnClickListener on the hamburger menu to open/close the drawer
        binding.imgMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // Handle item clicks in the navigation drawer
        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START) // Close drawer first
            when (item.itemId) {
                R.id.nav_profile -> startActivity(Intent(this, ClientProfileActivity::class.java))
                R.id.nav_inbox -> startActivity(Intent(this, InboxActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, ClientSettingsActivity::class.java))
                R.id.nav_logout -> signOutUser()
                else -> return@setNavigationItemSelectedListener false
            }
            true
        }

        // Handle Bottom Navigation selection
        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            // Prevent re-selecting the current item causing unnecessary actions
            if (item.itemId == binding.bottomNavigation.selectedItemId) {
                return@setOnNavigationItemSelectedListener false
            }
            when (item.itemId) {
                R.id.bottom_message -> {
                    startActivity(Intent(this, InboxActivity::class.java))
                    overridePendingTransition(0,0) // Prevent animation flicker
                    true
                }
                R.id.bottom_home -> {
                    // Already on Home, do nothing
                    true
                }
                R.id.bottom_settings -> {
                    startActivity(Intent(this, ClientSettingsActivity::class.java))
                    overridePendingTransition(0,0)
                    true
                }
                else -> false
            }
        }

        // OnClickListener for container_matching
        binding.containerMatching.setOnClickListener {
            val intent = Intent(this, AffiliationSelectionActivity::class.java)
            startActivity(intent)
        }

        // OnClickListener for Browse Library
        binding.containerFrameLibrary.setOnClickListener {
            Log.d(TAG, "Browse Library clicked")
            val intent = Intent(this, DocumentLibraryActivity::class.java)
            startActivity(intent)
        }
    }


    // Function to load user data and display it in the drawer header
    private fun loadUserDataForDrawer() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.w(TAG, "User ID is null, cannot load drawer data.")
            clearDrawerHeader() // Clear header if user somehow logs out
            return
        }
        Log.d(TAG, "Loading drawer data for user ID: $userId")

        // --- Ensure nav_view has a header ---
        if (binding.navView.headerCount == 0) {
            Log.e(TAG, "NavigationView has no header layout set!")
            return
        }
        val headerView = binding.navView.getHeaderView(0)
        val userNameTextView: TextView? = headerView.findViewById(R.id.user_name_header) // Use correct ID
        val headerProfileImageView: ImageView? = headerView.findViewById(R.id.img_profile_picture_header) // Use correct ID
        // --- ---

        if (userNameTextView == null || headerProfileImageView == null) {
            Log.e(TAG, "Could not find user_name_header or img_profile_picture_header in nav_header.xml")
            return // Exit if header views aren't found
        }

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Log.d(TAG, "User document found for drawer.")
                    val userName = document.getString("fullName")
                    val profileImageUrl = document.getString("profileImageUrl")

                    userNameTextView.text = userName ?: "User Name" // Set name

                    // Load profile picture into drawer header ImageView using Glide
                    Log.d(TAG, "Drawer Profile Image URL: $profileImageUrl")
                    loadProfileImageIntoHeader(profileImageUrl, headerProfileImageView) // Use helper

                } else {
                    Log.w(TAG, "User document does not exist for drawer (UID: $userId).")
                    clearDrawerHeader() // Set defaults if no data
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading drawer data", e)
                Toast.makeText(this, "Error loading user info", Toast.LENGTH_SHORT).show()
                clearDrawerHeader() // Set defaults on failure
            }
    }

    // Helper to load image into header, separating Glide logic
    private fun loadProfileImageIntoHeader(imageUrl: String?, imageView: ImageView) {
        val placeholder = R.drawable.ic_person_green // Default placeholder for drawer
        val errorPlaceholder = R.drawable.ic_person_red // Error placeholder

        val imageSource: Any? = if (!imageUrl.isNullOrEmpty()) imageUrl else placeholder

        Glide.with(this) // Use activity context directly
            .load(imageSource)
            .apply(RequestOptions.circleCropTransform()) // Apply circle crop
            .placeholder(placeholder)
            .error(errorPlaceholder)
            .into(imageView)
    }


    private fun clearDrawerHeader() {
        try {
            if (binding.navView.headerCount > 0) {
                val headerView = binding.navView.getHeaderView(0)
                val userNameTextView: TextView? = headerView.findViewById(R.id.user_name_header)
                val headerProfileImageView: ImageView? = headerView.findViewById(R.id.img_profile_picture_header)
                userNameTextView?.text = "User Name" // Default text
                headerProfileImageView?.setImageResource(R.drawable.ic_person_green) // Default image
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing drawer header", e)
        }
    }


    override fun onResume() {
        super.onResume()
        // Ensure "Home" is selected on the bottom nav when returning
        binding.bottomNavigation.selectedItemId = R.id.bottom_home
        // Reload user data for the drawer every time the activity resumes
        // This ensures the header is updated if the profile was changed elsewhere
        loadUserDataForDrawer()
    }

    private fun signOutUser() {
        auth.signOut()
        // Optional: Clear SharedPreferences if you use them
        // val sharedPreferences = getSharedPreferences("user_session", MODE_PRIVATE)
        // sharedPreferences.edit().clear().apply()

        Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}