package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tlfinal.databinding.UserLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: UserLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = UserLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Enable login button only when both fields are filled
        binding.usernameEditText.addTextChangedListener(textWatcher)

        // Ensure password edit text is accessed correctly
        binding.passwordEditText.editText?.addTextChangedListener(textWatcher)

        // Login button logic
        binding.loginButton.setOnClickListener { loginUser() }

        // Navigate to Lawyer Login
        binding.lawyerLogin.setOnClickListener {
            startActivity(Intent(this, LawyerLoginActivity::class.java))
        }

        // Navigate to Registration
        binding.signupText.setOnClickListener {
            startActivity(Intent(this, RoleSelectionActivity::class.java))
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val isButtonEnabled = binding.usernameEditText.text.toString().isNotEmpty() &&
                    binding.passwordEditText.editText?.text.toString().isNotEmpty()
            binding.loginButton.isEnabled = isButtonEnabled
            binding.loginButton.setBackgroundResource(
                if (isButtonEnabled) R.drawable.button_enabled else R.drawable.button_disabled
            )
        }

        override fun afterTextChanged(s: Editable?) {}
    }

    private fun loginUser() {
        val username = binding.usernameEditText.text.toString().trim().toLowerCase(Locale.ROOT) // Convert to lowercase
        val password = binding.passwordEditText.editText?.text.toString().trim()

        // Log the username being used for login
        Log.d("LoginUser", "Attempting to login with username: $username")

        // Make sure the progress bar is visible while the request is processing
        binding.progressBar.visibility = View.VISIBLE

        val db = FirebaseFirestore.getInstance()

        // Ensure user is querying the Firestore database correctly
        db.collection("users").whereEqualTo("username", username).get()  // Query with lowercase username
            .addOnSuccessListener { querySnapshot ->
                binding.progressBar.visibility = View.GONE

                // Log the result of the query
                Log.d("LoginUser", "Query returned ${querySnapshot.size()} document(s)")

                if (!querySnapshot.isEmpty) {
                    val email = querySnapshot.documents[0].getString("email")
                    email?.let {
                        // Log the email being used to sign in
                        Log.d("LoginUser", "Found email: $email")

                        auth.signInWithEmailAndPassword(it, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, ClientDashboardActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                } else {
                    // Log if no user was found
                    Log.d("LoginUser", "No user found with this username")
                    Toast.makeText(this, "No user found with this username", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                // Log the error if the query fails
                Log.e("LoginUser", "Error fetching user data: ${e.message}")
                Toast.makeText(this, "Error fetching user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
