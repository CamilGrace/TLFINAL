package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tlfinal.databinding.LawyerLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore

class LawyerLoginActivity : AppCompatActivity() {

    private lateinit var binding: LawyerLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore // Initialize Firestore instance

    companion object {
        private const val TAG = "LawyerLoginActivity" // Tag for logging
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance() // Initialize here

        binding.imageBack.setOnClickListener {
            finish()
        }

        binding.usernameEditText.addTextChangedListener(textWatcher)
        // Ensure passwordEditText and its inner editText are not null
        binding.passwordEditText.editText?.addTextChangedListener(textWatcher)

        // Initial button state
        updateButtonState()

        binding.loginButton.setOnClickListener {
            loginLawyer()
        }

        binding.signupText.setOnClickListener {
            // Consider clearing task if coming from splash/role selection
            val intent = Intent(this, RoleSelectionActivity::class.java)
            startActivity(intent)
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            updateButtonState() // Call the helper function after text changes
        }
    }

    // Helper function to check fields and update button visuals
    private fun updateButtonState() {
        val isUsernameFilled = binding.usernameEditText.text.toString().trim().isNotEmpty()
        val isPasswordFilled = binding.passwordEditText.editText?.text.toString().isNotEmpty() == true // Check inner EditText safely
        val isEnabled = isUsernameFilled && isPasswordFilled // Determine enabled state

        binding.loginButton.isEnabled = isEnabled // Set the actual enabled state

        // Set background drawable
        val buttonBackgroundRes = if (isEnabled) {
            R.drawable.button_enabled
        } else {
            R.drawable.button_disabled
        }
        binding.loginButton.setBackgroundResource(buttonBackgroundRes) // Use setBackgroundResource

        // Set text color
        val buttonTextColor = if (isEnabled) {
            ContextCompat.getColor(this, android.R.color.white) // Use 'this' context
        } else {
            ContextCompat.getColor(this, android.R.color.black) // Use 'this' context
        }
        binding.loginButton.setTextColor(buttonTextColor) // Use binding to set text color
    }


    private fun loginLawyer() {
        // **Use lowercase for BOTH input and query if you save lowercase usernames**
        // **If you save mixed case, remove .lowercase() here and ensure exact match**
        val usernameInput = binding.usernameEditText.text.toString().trim()
        val usernameQuery = usernameInput.lowercase() // Use lowercase for the query
        val password = binding.passwordEditText.editText?.text.toString().trim()

        if (usernameInput.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Attempting login for username (input): '$usernameInput', querying with: '$usernameQuery'")
        binding.progressBar.visibility = View.VISIBLE
        binding.loginButton.isEnabled = false // Disable button during login

        // Query Firestore for the lawyer document by lowercase username
        db.collection("lawyers")
            .whereEqualTo("username", usernameQuery) // Query using the consistent case (lowercase recommended)
            .limit(1) // Optimization: We only expect one user per username
            .get()
            .addOnSuccessListener { querySnapshot ->
                Log.d(TAG, "Firestore query successful. Found ${querySnapshot.size()} documents for username '$usernameQuery'")

                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    val email = document.getString("emailAddress") // Correct field name?
                    val storedUsername = document.getString("username") // Get stored username for logging

                    Log.d(TAG, "Document found. ID: ${document.id}, Stored Username: '$storedUsername', Email from DB: '$email'")


                    if (email != null && email.isNotEmpty()) {
                        // Attempt sign-in with Firebase Authentication using the fetched email
                        Log.d(TAG, "Attempting Firebase Auth sign-in with email: '$email'")
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                binding.progressBar.visibility = View.GONE // Hide progress bar regardless of outcome
                                binding.loginButton.isEnabled = true // Re-enable button

                                if (task.isSuccessful) {
                                    Log.i(TAG, "Firebase Auth sign-in successful for email: $email")
                                    Toast.makeText(this, "Lawyer Login Successful", Toast.LENGTH_SHORT).show()
                                    // Navigate to Lawyer Dashboard
                                    val intent = Intent(this, LawyerDashboardActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear back stack
                                    startActivity(intent)
                                    finish() // Finish Login activity
                                } else {
                                    // Log detailed Firebase Auth error
                                    Log.w(TAG, "Firebase Auth sign-in failed for email: $email", task.exception)
                                    val errorMessage = when (task.exception) {
                                        is FirebaseAuthInvalidUserException -> "No account found with this email. Registration might be incomplete."
                                        is FirebaseAuthInvalidCredentialsException -> "Incorrect password. Please try again."
                                        else -> "Authentication failed: ${task.exception?.message ?: "Unknown error"}"
                                    }
                                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show() // Show longer duration for errors
                                }
                            }
                    } else {
                        // Email is missing or empty in the Firestore document
                        binding.progressBar.visibility = View.GONE
                        binding.loginButton.isEnabled = true
                        Log.e(TAG, "Lawyer document found for username '$usernameQuery', but 'emailAddress' field is missing or empty.")
                        Toast.makeText(this, "Login error: User data incomplete.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // No document found for the username in Firestore
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true
                    Log.w(TAG, "No lawyer document found in Firestore for username '$usernameQuery'")
                    Toast.makeText(this, "Login Failed: Invalid username or password.", Toast.LENGTH_SHORT).show() // Generic error for security
                }
            }
            .addOnFailureListener { e ->
                // Firestore query itself failed
                binding.progressBar.visibility = View.GONE
                binding.loginButton.isEnabled = true
                Log.e(TAG, "Firestore query failed for username '$usernameQuery'", e)
                Toast.makeText(this, "Error connecting: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}