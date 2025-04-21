package com.example.tlfinal

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater // Import LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat // Import ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.tlfinal.ClientProfileActivity.Companion
import com.example.tlfinal.databinding.LawyerProfileBinding // Use ViewBinding
import com.google.android.material.chip.Chip
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray // Keep if needed for hours parsing, but List<Map> is better
import java.text.NumberFormat // For currency formatting
import java.util.* // For Locale

class LawyerProfileActivity : AppCompatActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var binding: LawyerProfileBinding // Use ViewBinding
    private lateinit var auth: FirebaseAuth
    private var currentLawyerData: LawyerData? = null // Store loaded data

    // Define LawyerData locally or import if it's a shared model
    data class LawyerData(
        val firstName: String = "",
        val middleName: String? = null,
        val lastName: String = "",
        val age: Int = 0,
        val gender: String = "",
        val affiliation: String = "",
        val contactNumber: String = "",
        val emailAddress: String = "",
        val officeAddress: String = "", // Address from registration
        val rollNumber: String = "",
        val legalSpecializations: List<Map<String, Any>> = emptyList(),
        val consultationFee: Double = 0.0,
        val availabilityOption: String = "", // Store as String from Firestore
        val daysAndHours: List<Map<String, String>> = emptyList(), // Store as List<Map>
        val yearsOfExperience: Int = 0,
        val lawFirmName: String? = null, // Nullable
        val lawFirmAddress: String? = null, // Nullable
        // Add profileImageUrl: String = "" if needed
    ) {
        val fullName: String
            @JvmName("getFullNameJava")
            get() = "$firstName ${middleName?.firstOrNull()?.let { "$it." } ?: ""} $lastName".trim()
    }


    companion object {
        const val EDIT_PROFILE_REQUEST_CODE = 100
        const val TAG = "LawyerProfileActivity" // For logging
    }

    private val editProfileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(LawyerProfileActivity.TAG, "Returned from Edit Profile with RESULT_OK. Reloading data.")
            auth.currentUser?.uid?.let { loadUserData(it) }
            Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(LawyerProfileActivity.TAG, "Returned from Edit Profile without RESULT_OK (Result Code: ${result.resultCode}).")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerProfileBinding.inflate(layoutInflater) // Inflate binding
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val userId = auth.currentUser?.uid
        if (userId == null) {
            // Handle user not logged in - redirect to login
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            // Example: startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        loadUserData(userId) // Load initial data

        setupListeners() // Setup button clicks, drawer, bottom nav
    }

    private fun setupListeners() {
        // Handle Edit Profile Button
        binding.editProfileBtn.setOnClickListener {
            Log.d(LawyerProfileActivity.TAG, "Edit Profile button clicked.")
            val intent = Intent(this, LawyerEditProfileActivity::class.java)
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
                    startActivity(Intent(this, LawyerInboxActivity::class.java))
                    overridePendingTransition(0,0); finishAffinity() // Clear stack back to dashboard potentially
                    true
                }
                R.id.bottom_home -> {
                    // If profile isn't truly home, navigate back to Dashboard
                    startActivity(Intent(this, LawyerDashboardActivity::class.java))
                    overridePendingTransition(0,0); finishAffinity()
                    true
                }
                R.id.bottom_settings -> {

                    true
                }
                else -> false
            }
        }
    }


    private fun loadUserData(userId: String) {
        Log.d(TAG, "Loading user data for ID: $userId")
        firestore.collection("lawyers").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Log.d(TAG, "Lawyer document found.")
                    try {
                        // Map Firestore data to local data class
                        currentLawyerData = LawyerData(
                            firstName = document.getString("firstName") ?: "",
                            middleName = document.getString("middleName"), // Keep as nullable
                            lastName = document.getString("lastName") ?: "",
                            age = document.getLong("age")?.toInt() ?: 0,
                            gender = document.getString("gender") ?: "N/A",
                            affiliation = document.getString("affiliation") ?: "Private",
                            contactNumber = document.getString("contactNumber") ?: "N/A",
                            emailAddress = document.getString("emailAddress") ?: "N/A",
                            officeAddress = document.getString("officeAddress") ?: "N/A", // Base address
                            rollNumber = document.getString("rollNumber") ?: "N/A",
                            legalSpecializations = document.get("legalSpecializations") as? List<Map<String, Any>> ?: emptyList(),
                            consultationFee = document.getDouble("consultationFee") ?: 0.0,
                            availabilityOption = document.getString("availabilityOption") ?: "N/A",
                            // Correctly fetch daysAndHours as List<Map<String, String>>
                            daysAndHours = (document.get("daysAndHours") as? List<*>)?.mapNotNull { item ->
                                (item as? Map<*, *>)?.entries?.associate { entry ->
                                    (entry.key as? String ?: "") to (entry.value as? String ?: "")
                                }
                            } ?: emptyList(),
                            yearsOfExperience = document.getLong("yearsOfExperience")?.toInt() ?: 0,
                            lawFirmName = document.getString("lawFirmName"), // Nullable
                            lawFirmAddress = document.getString("lawFirmAddress") // Nullable
                        )

                        updateUI(currentLawyerData!!) // Update UI with mapped data

                    } catch (e: Exception) {
                        Log.e(TAG, "Error mapping Firestore data", e)
                        Toast.makeText(this, "Error loading profile data.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "Lawyer document not found for ID: $userId")
                    Toast.makeText(this, "Profile not found.", Toast.LENGTH_SHORT).show()
                    // Handle case where profile doesn't exist (e.g., redirect?)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching lawyer data", e)
                Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI(data: LawyerData) {
        Log.d(TAG, "Updating UI with data: $data")
        binding.textFullName.text = "Atty. ${data.fullName}"
        binding.textAffiliationValue.text = data.affiliation // Display affiliation

        // Personal Info Card
        binding.userGender.text = data.gender.takeIf { it.isNotBlank() } ?: "N/A"
        binding.userAge.text = if (data.age > 0) data.age.toString() else "N/A"
        binding.userAvailability.text = data.availabilityOption.takeIf { it.isNotBlank() } ?: "N/A"
        displayAvailabilityHours(data.daysAndHours) // Display formatted hours

        // Contact Info Card
        binding.userEmail.text = data.emailAddress.takeIf { it.isNotBlank() } ?: "N/A"
        binding.userContact.text = data.contactNumber.takeIf { it.isNotBlank() } ?: "N/A"

        // Set Office Address based on affiliation
        if (data.affiliation.equals("PAO", ignoreCase = true)) {
            binding.userAddressValue.text = "Baguio Public Attorney's Office"
            binding.textLawFirmAddressLabel.visibility = View.GONE // Hide Law Firm Address for PAO
            binding.textLawFirmAddressValue.visibility = View.GONE
        } else {
            binding.userAddressValue.text = data.officeAddress.takeIf { it.isNotBlank() } ?: "N/A" // Show registered address for private
            // Show Law Firm Address if Private and address exists
            if (!data.lawFirmAddress.isNullOrBlank()) {
                binding.textLawFirmAddressLabel.visibility = View.VISIBLE
                binding.textLawFirmAddressValue.visibility = View.VISIBLE
                binding.textLawFirmAddressValue.text = data.lawFirmAddress
            } else {
                binding.textLawFirmAddressLabel.visibility = View.GONE
                binding.textLawFirmAddressValue.visibility = View.GONE
            }
        }

        // Credentials Card
        binding.textRollNumberValue.text = data.rollNumber.takeIf { it.isNotBlank() } ?: "N/A" // Display Roll Number

        // Law Firm Name (Visible only if Private and exists)
        if (data.affiliation.equals("Private", ignoreCase = true) && !data.lawFirmName.isNullOrBlank()) {
            binding.textLawFirmNameLabel.visibility = View.VISIBLE
            binding.textLawFirmNameValue.visibility = View.VISIBLE
            binding.textLawFirmNameValue.text = data.lawFirmName
        } else {
            binding.textLawFirmNameLabel.visibility = View.GONE
            binding.textLawFirmNameValue.visibility = View.GONE
        }

        displayLegalSpecializations(data.legalSpecializations) // Display specializations and subcategories
        binding.userYearsofexpValue.text = if (data.yearsOfExperience >= 0) "${data.yearsOfExperience} years" else "N/A" // Show years

        // Consultation Fee Card
        val feeFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH")) // PHP currency format
        binding.userConsultationFeeValue.text = if (data.consultationFee == 0.0) "Free"
        else feeFormat.format(data.consultationFee)

        // Update Drawer Header
        val headerView = binding.navView.getHeaderView(0)
        val userNameTextView: TextView = headerView.findViewById(R.id.user_name_header)
        userNameTextView.text = data.fullName // Use non-Atty version for drawer
    }

    private fun displayAvailabilityHours(hoursList: List<Map<String, String>>) {
        binding.hoursLayout.removeAllViews() // Clear previous entries
        if (hoursList.isNotEmpty()) {
            Log.d(TAG, "Displaying ${hoursList.size} availability hour entries.")
            val inflater = LayoutInflater.from(this)
            for (hourMap in hoursList) {
                val day = hourMap["day"] ?: "N/A"
                val startTime = hourMap["startTime"] ?: "--:--"
                val endTime = hourMap["endTime"] ?: "--:--"

                // Inflate a simple text view or a custom layout row
                val textView = TextView(this).apply {
                    text = "$day: $startTime - $endTime"
                    setTextColor(ContextCompat.getColor(this@LawyerProfileActivity, R.color.black)) // Use black or appropriate color
                    textSize = 14f // Match other text sizes
                    setPadding(0, 4, 0, 4) // Add some vertical padding
                }
                binding.hoursLayout.addView(textView)
            }
            binding.hoursLayout.visibility = View.VISIBLE
        } else {
            binding.hoursLayout.visibility = View.GONE
            Log.d(TAG, "No availability hours to display.")
        }
    }

    private fun displayLegalSpecializations(specializationsList: List<Map<String, Any>>) {
        binding.userLegalServicesChips.removeAllViews() // Clear existing chips
        binding.userSubcategoriesValue.text = "" // Clear subcategory text view initially

        val allSubcategories = mutableListOf<String>()

        if (specializationsList.isNotEmpty()) {
            Log.d(TAG, "Displaying ${specializationsList.size} specializations.")
            val inflater = LayoutInflater.from(this)
            for (specMap in specializationsList) {
                val specName = specMap["specialization"] as? String
                val subCats = (specMap["subcategories"] as? List<*>)?.filterIsInstance<String>()?.filter { it.isNotBlank() } ?: emptyList()

                if (!specName.isNullOrBlank()) {
                    // Create Chip for Specialization
                    val chip = Chip(this).apply {
                        text = specName
                        isClickable = false
                        isCheckable = false
                        // Add styling if needed (e.g., chipBackgroundColor)
                    }
                    binding.userLegalServicesChips.addView(chip)
                    allSubcategories.addAll(subCats) // Collect subcategories
                }
            }
            binding.userLegalServicesChips.visibility = View.VISIBLE
            binding.textLegal.visibility = View.VISIBLE // Ensure label is visible

            // Display collected subcategories
            if(allSubcategories.isNotEmpty()) {
                binding.userSubcategoriesValue.text = allSubcategories.joinToString(", ")
                binding.textSubcategoriesLabel.visibility = View.VISIBLE
                binding.userSubcategoriesValue.visibility = View.VISIBLE
            } else {
                binding.textSubcategoriesLabel.visibility = View.GONE
                binding.userSubcategoriesValue.visibility = View.GONE
            }

        } else {
            binding.userLegalServicesChips.visibility = View.GONE
            binding.textLegal.visibility = View.GONE // Hide label if no chips
            binding.textSubcategoriesLabel.visibility = View.GONE
            binding.userSubcategoriesValue.visibility = View.GONE
            Log.d(TAG, "No legal specializations to display.")
        }
    }


    // Handle profile updates after returning from EditProfileActivity
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == EDIT_PROFILE_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d(TAG, "Received result from Edit Profile. Reloading data.")
            // Simply reload data from Firestore to ensure consistency
            auth.currentUser?.uid?.let { loadUserData(it) }
            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
        }
    }

    // Optional: Refresh data when the activity resumes
    // override fun onResume() {
    //     super.onResume()
    //     auth.currentUser?.uid?.let { loadUserData(it) }
    // }

    private fun signOutUser() {
        auth.signOut() // Sign out from Firebase Auth
        // Clear user session data (if using SharedPreferences)
        // val sharedPreferences = getSharedPreferences("user_session", MODE_PRIVATE)
        // sharedPreferences.edit().clear().apply()

        Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java) // Assuming LoginActivity exists
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}