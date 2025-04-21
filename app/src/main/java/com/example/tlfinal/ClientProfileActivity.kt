package com.example.tlfinal

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
// Removed DrawerLayout and NavigationView imports
// import androidx.core.view.GravityCompat
// import androidx.drawerlayout.widget.DrawerLayout
// import com.google.android.material.navigation.NavigationView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.tlfinal.databinding.ClientProfileBinding // Use binding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
// Import R class if needed

class ClientProfileActivity : AppCompatActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var binding: ClientProfileBinding
    private lateinit var auth: FirebaseAuth

    companion object {
        const val TAG = "ClientProfileActivity"
    }

    // Keep the launcher for Edit Profile
    private val editProfileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Returned from Edit Profile with RESULT_OK. Reloading data.")
            auth.currentUser?.uid?.let { loadUserData(it) }
            Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG, "Returned from Edit Profile without RESULT_OK (Result Code: ${result.resultCode}).")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val userId = auth.currentUser?.uid
        if (userId == null) {
            handleNotLoggedIn()
            return
        }

        setupListeners()
        loadUserData(userId)
    }

    private fun setupListeners() {
        // Handle Edit Profile Button
        binding.editProfileBtn.setOnClickListener {
            Log.d(TAG, "Edit Profile button clicked.")
            val intent = Intent(this, ClientEditProfileActivity::class.java)
            editProfileLauncher.launch(intent)
        }

        // Handle Back Button click
        binding.imageBack.setOnClickListener {
            finish() // Go back
        }

        binding.bottomNavigation.selectedItemId = R.id.bottom_home // Assuming Profile IS home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId == binding.bottomNavigation.selectedItemId) {
                return@setOnItemSelectedListener false // Don't navigate to self
            }
            when (item.itemId) {
                R.id.bottom_message -> {
                    startActivity(Intent(this, InboxActivity::class.java))
                    overridePendingTransition(0,0); finishAffinity() // Clear stack back to dashboard potentially
                    true
                }
                R.id.bottom_home -> {
                    // If profile isn't truly home, navigate back to Dashboard
                    startActivity(Intent(this, ClientDashboardActivity::class.java))
                    overridePendingTransition(0,0); finishAffinity()
                    true
                }
                R.id.bottom_settings -> {
                    startActivity(Intent(this, ClientSettingsActivity::class.java))
                    overridePendingTransition(0,0); finishAffinity()
                    true
                }
                else -> false
            }
        }
    }


    private fun loadUserData(userId: String) {
        Log.d(TAG, "Loading user data for ID: $userId")
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Log.d(TAG, "User document found.")
                    val fullName = document.getString("fullName") ?: "N/A"
                    val email = document.getString("email") ?: "N/A"
                    val contactNo = document.getString("contactNo") ?: "N/A"
                    val address = document.getString("address") ?: "N/A"
                    val profileImageUrl = document.getString("profileImageUrl")

                    // Update Main UI
                    binding.textFullName.text = fullName
                    binding.userEmail.text = email
                    binding.userContact.text = contactNo
                    binding.userAddress.text = address.takeIf { it.isNotBlank() } ?: "Address not set"

                    loadProfileImage(profileImageUrl, binding.imgProfilePicture)

                } else {
                    Log.w(TAG, "User document not found for ID: $userId")
                    Toast.makeText(this, "Profile not found.", Toast.LENGTH_SHORT).show()
                    clearProfileData()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching user data", e)
                Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
                clearProfileData()
            }
    }

    // Function to load image using Glide
    private fun loadProfileImage(imageUrl: String?, imageView: ImageView) {
        // Keep this function as is
        val placeholder = R.drawable.ic_person_green
        val errorPlaceholder = R.drawable.ic_person_red

        val imageSource: Any? = if (!imageUrl.isNullOrEmpty()) imageUrl else placeholder

        Glide.with(this)
            .load(imageSource)
            .apply(RequestOptions.circleCropTransform())
            .placeholder(placeholder)
            .error(errorPlaceholder)
            .into(imageView)
    }

    // Function to clear profile fields
    private fun clearProfileData() {
        binding.textFullName.text = "N/A"
        binding.userEmail.text = "N/A"
        binding.userContact.text = "N/A"
        binding.userAddress.text = "N/A"
        binding.imgProfilePicture.setImageResource(R.drawable.ic_person_green)
    }

    private fun handleNotLoggedIn() {
        // Keep this function as is
        Toast.makeText(this, "Please log in.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

}