package com.example.tlfinal

import android.content.Context // <-- Import Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns // Import Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tlfinal.databinding.ActivityLawyerRegistrationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


class LawyerRegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLawyerRegistrationBinding
    private val viewModel: LawyerRegistrationViewModel by viewModels()
    private var currentStep = 1
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Define SharedPreferences keys consistent with MainActivity
    private val prefsName = MainActivity.PREFS_NAME
    private val keyUserType = MainActivity.KEY_USER_TYPE


    // --- Replicate relevant validation constants here or access from fragments/common file ---
    companion object {
        private const val TAG = "LawyerRegistration"
        // Add constants used in validateAllData for clarity
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_PASSWORD_LENGTH = 100
        // ... other relevant constants if needed for validateAllData ...
    }
    // --- End Constants ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLawyerRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = Firebase.firestore
        auth = Firebase.auth

        showStep(currentStep)

        binding.btnNext.setOnClickListener { handleNextOrRegister() }
        binding.btnPrevious.setOnClickListener { handlePrevious() }
        binding.imageBack.setOnClickListener { finish() } // Or handle step back
    }

    private fun handlePrevious() {
        if (currentStep > 1) {
            currentStep--
            showStep(currentStep)
        } else {
            finish() // Go back from step 1
        }
    }


    private fun handleNextOrRegister() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        // Trigger validation in the current fragment
        val validationPassed = when (currentStep) {
            1 -> (currentFragment as? LawyerRegStep1Fragment)?.validate() ?: false
            2 -> (currentFragment as? LawyerRegStep2Fragment)?.validate() ?: false
            3 -> (currentFragment as? LawyerRegStep3Fragment)?.validate() ?: false
            else -> false
        }

        if (validationPassed) {
            if (currentStep < 3) {
                currentStep++
                showStep(currentStep)
            } else {
                // Last step, validation passed -> attempt registration
                registerLawyer()
            }
        } else {
            // Fragment validation failed, show a generic message
            Toast.makeText(this, "Please correct the errors in this step.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStep(step: Int) {
        val fragment: Fragment = when (step) {
            1 -> LawyerRegStep1Fragment()
            2 -> LawyerRegStep2Fragment()
            3 -> LawyerRegStep3Fragment()
            else -> LawyerRegStep1Fragment() // Default to step 1
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss() // Use commitAllowingStateLoss if state might be saved after onSaveInstanceState

        updateStepUI(step)
    }

    private fun updateStepUI(step: Int) {
        // Update step indicators (ensure drawables exist)
        binding.step1.setBackgroundResource(if (step >= 1) R.drawable.circle_current_step else R.drawable.circle_inactive_step)
        binding.step2.setBackgroundResource(if (step >= 2) R.drawable.circle_current_step else R.drawable.circle_inactive_step)
        binding.step3.setBackgroundResource(if (step >= 3) R.drawable.circle_current_step else R.drawable.circle_inactive_step)

        // Set line colors (ensure colors exist)
        // Using ContextCompat for safer color retrieval
        val activeColor = androidx.core.content.ContextCompat.getColor(this, R.color.your_active_color)
        val inactiveColor = androidx.core.content.ContextCompat.getColor(this, R.color.your_inactive_color)
        binding.stepIndicatorLayout.getChildAt(1)?.setBackgroundColor(if (step > 1) activeColor else inactiveColor) // Line 1-2
        binding.stepIndicatorLayout.getChildAt(3)?.setBackgroundColor(if (step > 2) activeColor else inactiveColor) // Line 2-3

        binding.btnNext.text = if (step == 3) "Register" else "Next"
        binding.btnPrevious.visibility = if (step > 1) View.VISIBLE else View.GONE // Show previous from step 2 onwards
    }

    private fun registerLawyer() {
        // Perform final validation across all data in ViewModel BEFORE hitting Firebase
        val lawyerData = viewModel.lawyerData.value
        if (lawyerData == null || !validateAllData(lawyerData)) {
            Toast.makeText(this, "Registration data is incomplete or invalid. Please review all steps.", Toast.LENGTH_LONG).show()
            return
        }

        // Safe access to potentially nullable fields after validation passes
        val email = lawyerData.emailAddress!!.trim()
        val password = lawyerData.password!! // Already checked non-blank in validateAllData
        val username = lawyerData.username!!.trim().lowercase() // Already checked non-blank

        // Show progress, disable buttons
        binding.progressBar.visibility = View.VISIBLE
        binding.btnNext.isEnabled = false
        binding.btnPrevious.isEnabled = false

        // Create user in Firebase Authentication
        Log.d(TAG, "Attempting to create Auth user with email: $email")
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val userId = auth.currentUser?.uid // Use auth.currentUser directly
                    Log.i(TAG, "Firebase Auth user created successfully. UID: $userId")

                    if (userId != null) {
                        saveLawyerDataToFirestore(userId, lawyerData, username) // Pass validated data
                    } else {
                        // Error case: Auth success but no UID
                        handleRegistrationFailure("Registration failed (User ID error).")
                        Log.e(TAG, "Auth user created but UID is null.")
                    }
                } else {
                    // Auth user creation failed
                    handleRegistrationFailure(null, authTask.exception) // Pass exception
                }
            }
    }

    private fun saveLawyerDataToFirestore(userId: String, lawyerData: LawyerRegistrationViewModel.LawyerData, usernameLowercase: String) {
        Log.d(TAG, "Attempting to save data to Firestore for UID: $userId")

        val lawyerMap: MutableMap<String, Any?> = mutableMapOf(
            // Don't store userId explicitly if using it as document ID
            "firstName" to lawyerData.firstName,
            "middleName" to lawyerData.middleName,
            "lastName" to lawyerData.lastName,
            "age" to lawyerData.age,
            "gender" to lawyerData.gender,
            "affiliation" to lawyerData.affiliation,
            "contactNumber" to lawyerData.contactNumber,
            "emailAddress" to lawyerData.emailAddress?.trim(), // Ensure trimmed
            "officeAddress" to if (lawyerData.affiliation == "PAO") "Baguio Public Attorney's Office" else lawyerData.officeAddress, // Keep logic
            "rollNumber" to lawyerData.rollNumber,
            "legalSpecializations" to lawyerData.legalSpecializations.map { (spec, subs) -> hashMapOf("specialization" to spec, "subcategories" to subs) },
            "consultationFee" to lawyerData.consultationFee,
            "availabilityOption" to lawyerData.availabilityOption?.name, // Store enum name
            "daysAndHours" to lawyerData.daysAndHours.map { dh -> hashMapOf("day" to dh.day, "startTime" to dh.startTime, "endTime" to dh.endTime) },
            "username" to usernameLowercase,
            "termsAccepted" to lawyerData.termsAccepted,
            "userType" to "lawyer" // <-- Optionally store type in Firestore too
            // DO NOT SAVE PASSWORD OR CONFIRM PASSWORD
        )
        if (lawyerData.affiliation == "Private Law Firm") {
            lawyerMap["lawFirmName"] = lawyerData.lawFirmName
            lawyerMap["lawFirmAddress"] = lawyerData.lawFirmAddress
        }

        db.collection("lawyers").document(userId) // Use specific collection 'lawyers'
            .set(lawyerMap)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Log.i(TAG, "Lawyer data saved successfully to Firestore for UID: $userId")

                // --- Save user type to SharedPreferences ---
                val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                prefs.edit().putString(keyUserType, "lawyer").apply()
                Log.d(TAG, "Saved user type: lawyer")
                // --- End SharedPreferences save ---

                Toast.makeText(this, "Lawyer registered successfully!", Toast.LENGTH_SHORT).show()

                // Navigate to Lawyer Login - MainActivity will dispatch next time
                val intent = Intent(this, LawyerLoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish() // Close registration activity
            }
            .addOnFailureListener { e ->
                // Firestore save failed, Auth user might still exist!
                handleRegistrationFailure("Registration failed (Database error).", e)
                // Consider deleting the Auth user here for consistency
                // auth.currentUser?.delete()?.addOnCompleteListener { task -> ... }
            }
    }

    // Helper to handle UI changes and logging on failure
    private fun handleRegistrationFailure(customMessage: String? = null, exception: Exception? = null) {
        binding.progressBar.visibility = View.GONE
        binding.btnNext.isEnabled = true // Re-enable buttons
        binding.btnPrevious.isEnabled = true

        val errorMessage = customMessage ?: when (exception) {
            is FirebaseAuthUserCollisionException -> "This email address is already registered."
            else -> "Registration failed: ${exception?.message ?: "Unknown error"}"
        }
        Log.w(TAG, errorMessage, exception) // Log exception details if available
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }


    // Final validation check on all ViewModel data before creating Auth user
    private fun validateAllData(data: LawyerRegistrationViewModel.LawyerData): Boolean {
        var errorMsg: String? = null // Store first validation error message

        // --- Check required fields from all steps ---
        if (data.firstName.isNullOrBlank()) errorMsg = "First Name is required (Step 1)."
        else if (data.lastName.isNullOrBlank()) errorMsg = "Last Name is required (Step 1)."
        else if (data.age == null || data.age <= 0) errorMsg = "Valid Age is required (Step 1)." // Example: Check > 0
        else if (data.gender == null) errorMsg = "Gender selection is required (Step 1)."
        else if (data.contactNumber.isNullOrBlank()) errorMsg = "Contact Number is required (Step 1)."
        // Email Format Check
        else if (data.emailAddress.isNullOrBlank() || !Patterns.EMAIL_ADDRESS.matcher(data.emailAddress.trim()).matches()) errorMsg = "Valid Email Address is required (Step 1)."

        // Step 2 Checks
        else if (data.affiliation == null) errorMsg = "Affiliation selection is required (Step 2)."
        else if (data.affiliation == "Private Law Firm" && (data.lawFirmName.isNullOrBlank() || data.lawFirmAddress.isNullOrBlank())) errorMsg = "Law Firm Name and Address required for Private (Step 2)."
        else if (data.rollNumber.isNullOrBlank()) errorMsg = "Roll Number is required (Step 2)."
        else if (data.legalSpecializations.isEmpty() || data.legalSpecializations.any { it.first.isBlank() || it.second.isEmpty() }) errorMsg = "Select Specialization and Subcategories (Step 2)."
        else if (data.consultationFee == null || data.consultationFee < 0) errorMsg = "Valid Consultation Fee is required (Step 2)."
        else if (data.availabilityOption == null) errorMsg = "Availability selection is required (Step 2)."
        else if (data.availabilityOption == LawyerRegistrationViewModel.AvailabilityOption.OPEN_SELECTED_HOURS && data.daysAndHours.isEmpty()) errorMsg = "Add specific hours for selected availability (Step 2)."

        // Step 3 Checks
        else if (data.username.isNullOrBlank()) errorMsg = "Username is required (Step 3)."
        // Add username length/format check if needed here too
        else if (data.password.isNullOrBlank()) errorMsg = "Password is required (Step 3)."
        else if (data.password.length < MIN_PASSWORD_LENGTH || data.password.length > MAX_PASSWORD_LENGTH) errorMsg = "Password length is invalid (Step 3)."
        else if (data.password != data.confirmPassword) errorMsg = "Passwords do not match (Step 3)."
        else if (!data.termsAccepted) errorMsg = "Terms and Conditions must be accepted (Step 3)."


        // If any error occurred, show it and return false
        if (errorMsg != null) {
            Log.w(TAG, "Final Validation Failed: $errorMsg")
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            return false
        }

        Log.d(TAG, "Final Validation Passed.")
        return true // All checks passed
    }
}