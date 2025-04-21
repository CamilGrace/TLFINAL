package com.example.tlfinal

import android.content.Context // <-- Import Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tlfinal.databinding.UserLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: UserLoginBinding
    private lateinit var auth: FirebaseAuth
    // Define SharedPreferences keys consistent with MainActivity
    private val prefsName = MainActivity.PREFS_NAME
    private val keyUserType = MainActivity.KEY_USER_TYPE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Check if user is already logged in (optional redundancy, MainActivity should handle)
        // if (auth.currentUser != null) {
        //     // User already logged in, potentially navigate based on stored type
        //     // MainActivity should ideally handle this before LoginActivity is even shown
        // }


        // Enable login button only when both fields are filled
        binding.usernameEditText.addTextChangedListener(textWatcher)
        binding.passwordEditText.editText?.addTextChangedListener(textWatcher) // Ensure it's the inner EditText

        // Login button logic
        binding.loginButton.setOnClickListener { loginUser() }

        // Navigate to Lawyer Login
        binding.lawyerLogin.setOnClickListener {
            startActivity(Intent(this, LawyerLoginActivity::class.java))
            // Consider finishing LoginActivity if navigating away
            // finish()
        }

        // Navigate to Registration Role Selection
        binding.signupText.setOnClickListener {
            startActivity(Intent(this, RoleSelectionActivity::class.java))
            // Consider finishing LoginActivity if navigating away
            // finish()
        }
    }

    // Define the TextWatcher as a member variable
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

    private fun loginUser() {
        val usernameOrEmail = binding.usernameEditText.text.toString().trim() // User might enter email or username
        val password = binding.passwordEditText.editText?.text.toString().trim()

        if (usernameOrEmail.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username/email and password", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.loginButton.isEnabled = false // Disable button during login attempt

        // Try direct email login first (more common)
        if (usernameOrEmail.contains("@")) {
            Log.d("LoginUser", "Attempting direct email login: $usernameOrEmail")
            auth.signInWithEmailAndPassword(usernameOrEmail, password)
                .addOnCompleteListener { task ->
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true // Re-enable button

                    if (task.isSuccessful) {
                        Log.d("LoginUser", "Direct email login successful.")
                        // Assume email login is for clients in this activity
                        saveUserTypeAndNavigate("client")
                    } else {
                        Log.w("LoginUser", "Direct email login failed.", task.exception)
                        // If direct email fails, maybe try fetching username (if structure allows)
                        // For now, just show the error. Could add username fetch as fallback.
                        Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            // Treat as username, convert to lowercase for query
            val usernameLower = usernameOrEmail.lowercase(Locale.ROOT)
            Log.d("LoginUser", "Attempting username lookup: $usernameLower")
            val db = FirebaseFirestore.getInstance()

            // Query Firestore for the username (assuming 'users' collection for clients)
            db.collection("users") // Query the CLIENT collection
                .whereEqualTo("username", usernameLower) // Query with lowercase username
                .limit(1) // Only need one match
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        val email = querySnapshot.documents[0].getString("email")
                        if (email != null) {
                            Log.d("LoginUser", "Found email via username: $email")
                            // Now attempt Firebase Auth login with the fetched email
                            auth.signInWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    binding.progressBar.visibility = View.GONE
                                    binding.loginButton.isEnabled = true // Re-enable button

                                    if (task.isSuccessful) {
                                        Log.d("LoginUser", "Login successful via username lookup.")
                                        // Successfully logged in via username lookup, save type
                                        saveUserTypeAndNavigate("client")
                                    } else {
                                        Log.w("LoginUser", "Auth failed after username lookup.", task.exception)
                                        Toast.makeText(this, "Login Failed: Incorrect password or ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } else {
                            // Username found, but no email associated? Data issue.
                            binding.progressBar.visibility = View.GONE
                            binding.loginButton.isEnabled = true
                            Log.e("LoginUser", "Username found but email is null in Firestore.")
                            Toast.makeText(this, "Login Failed: User data error.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // No user found with this username in the 'users' collection
                        binding.progressBar.visibility = View.GONE
                        binding.loginButton.isEnabled = true
                        Log.d("LoginUser", "No user found with this username in 'users' collection")
                        Toast.makeText(this, "Login Failed: Invalid username or password.", Toast.LENGTH_SHORT).show() // Generic error
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE
                    binding.loginButton.isEnabled = true
                    Log.e("LoginUser", "Error fetching user data by username: ${e.message}", e)
                    Toast.makeText(this, "Login Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Helper function to save user type and navigate
    private fun saveUserTypeAndNavigate(userType: String) {
        // Save to SharedPreferences
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString(keyUserType, userType).apply()
        Log.d("LoginUser", "Saved user type: $userType")

        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()

        // Navigate to the appropriate dashboard (ClientDashboard for this LoginActivity)
        val intent = Intent(this, ClientDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear back stack
        startActivity(intent)
        finish() // Close LoginActivity
    }
}