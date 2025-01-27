package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.tlfinal.databinding.LawyerProfileBinding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import org.json.JSONObject

class LawyerProfileActivity : AppCompatActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var binding: LawyerProfileBinding
    private lateinit var auth: FirebaseAuth

    companion object {
        const val EDIT_PROFILE_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Fetch and display initial user data
        val userId = auth.currentUser?.uid
        userId?.let { loadUserData(it) }


        // Handle Edit Profile Button
        binding.editProfileBtn.setOnClickListener {
            val intent = Intent(this, LawyerEditProfileActivity::class.java)
            startActivityForResult(intent, EDIT_PROFILE_REQUEST_CODE)
        }

        // Get the DrawerLayout and NavigationView from the layout
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navigationView: NavigationView = binding.navView

        binding.imgMenu.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }


        // Handle item clicks in the navigation drawer
        navigationView.setNavigationItemSelectedListener { item: MenuItem ->
            when (item.itemId) {
                R.id.nav_profile -> {
                    // Handle Home (Profile) click
                }
                R.id.nav_inbox -> {
                    // Handle Settings click
                }
                R.id.nav_settings -> {
                    // Handle Share click
                }
                R.id.nav_logout -> {
                    signOutUser()
                }
            }

            // Close the drawer after item click
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Bottom Navigation setup
        binding.bottomNavigation.selectedItemId = R.id.bottom_home
        binding.bottomNavigation.setOnItemSelectedListener { item ->
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
    }

    private fun loadUserData(userId: String) {
        firestore.collection("lawyers").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val fullName = document.getString("fullName") ?: "N/A"
                    val email = document.getString("email") ?: "N/A"
                    val contactNo = document.getString("contactNo") ?: "N/A"
                    val address = document.getString("address") ?: "N/A"
                    val gender = document.getString("gender") ?: "N/A"
                    val age = document.getLong("age")?.toInt() ?: 0
                    val availability = document.getString("availability") ?: "N/A"
                    val legalServices = document.getString("legalservices") ?: ""
                    val preLawDegree = document.getString("prelawdegree")?: "N/A"
                    val lawSchool = document.getString("lawschool") ?: "N/A"
                    val yearsOfExp = document.getLong("yearsofexp")?.toInt() ?: 0
                    val consultationFee = document.getLong("consultationfee")?.toInt() ?: 0
                    val hours = document.get("hours")
                    // Update UI
                    binding.textFullName.text = fullName
                    binding.userGender.text = gender
                    binding.userAge.text = if (age > 0) age.toString() else "N/A"
                    binding.userAvailability.text = availability
                    binding.userEmail.text = email
                    binding.userContact.text = contactNo
                    binding.userAddress.text = address
                    binding.userPrelaw.text = preLawDegree
                    binding.userLawschool.text = lawSchool
                    binding.userYearsofexp.text = if(yearsOfExp > 0) yearsOfExp.toString() else "N/A"
                    binding.userConsultationFee.text = if(consultationFee > 0) consultationFee.toString() else "N/A"

                    // Update legal services chips
                    displayLegalServices(legalServices)
                    // Update Availability and Time Pickers
                    displayAvailabilityHours(hours.toString())
                    // Update the navigation header with the user's full name
                    val headerView = binding.navView.getHeaderView(0)
                    val userNameTextView: TextView = headerView.findViewById(R.id.user_name)
                    userNameTextView.text = fullName
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun displayAvailabilityHours(hours: String) {
        val container = binding.hoursLayout
        container.removeAllViews()
        if(hours.isNotBlank() && hours != "null")
        {
            try {
                val jsonArray = JSONArray(hours)
                for (i in 0 until jsonArray.length())
                {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val day = jsonObject.getString("day")
                    val openingTime = jsonObject.getString("openingTime")
                    val closingTime = jsonObject.getString("closingTime")
                    val textView = TextView(this)
                    textView.text = "$day: $openingTime - $closingTime"
                    container.addView(textView)
                }
            }
            catch(e:Exception)
            {
                Log.e("LAWYER_PROFILE", "Error parsing hours: ${e.message}", e)
            }
        }

    }

    private fun displayLegalServices(legalServices: String) {
        val chipGroup = binding.userLegalServicesChips
        chipGroup.removeAllViews() // Clear existing chips

        if (legalServices.isNotBlank()) {
            val servicesList = legalServices.split(",").map { it.trim() }
            for(service in servicesList){
                val chip = Chip(this)
                chip.text = service
                chip.isClickable = false
                chip.isCheckable = false
                chipGroup.addView(chip)
            }
        }

    }

    // Handle profile updates after returning from EditProfileActivity
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_PROFILE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.let {
                val fullName = it.getStringExtra("fullName") ?: "N/A"
                val email = it.getStringExtra("email") ?: "N/A"
                val contactNo = it.getStringExtra("contactNo") ?: "N/A"
                val address = it.getStringExtra("address") ?: "N/A"
                val gender = it.getStringExtra("gender") ?: "N/A"
                val age = it.getIntExtra("age",0)
                val availability = it.getStringExtra("availability") ?: "N/A"
                val legalServices = it.getStringExtra("legalservices") ?: ""
                val preLawDegree = it.getStringExtra("prelawdegree")?: "N/A"
                val lawSchool = it.getStringExtra("lawschool") ?: "N/A"
                val yearsOfExp = it.getIntExtra("yearsofexp", 0)
                val consultationFee = it.getIntExtra("consultationfee", 0)
                val hours = it.getStringExtra("hours")

                // Update UI elements
                binding.textFullName.text = fullName
                binding.userGender.text = gender
                binding.userAge.text = if (age > 0) age.toString() else "N/A"
                binding.userAvailability.text = availability
                binding.userEmail.text = email
                binding.userContact.text = contactNo
                binding.userAddress.text = address
                binding.userPrelaw.text = preLawDegree
                binding.userLawschool.text = lawSchool
                binding.userYearsofexp.text = if(yearsOfExp > 0) yearsOfExp.toString() else "N/A"
                binding.userConsultationFee.text = if(consultationFee > 0) consultationFee.toString() else "N/A"


                // Update legal services chips
                displayLegalServices(legalServices)
                // Update Availability and Time Pickers
                displayAvailabilityHours(hours ?: "")

                // Update the navigation header with the updated user's full name
                val headerView = binding.navView.getHeaderView(0)
                val userNameTextView: TextView = headerView.findViewById(R.id.user_name)
                userNameTextView.text = fullName

                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
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