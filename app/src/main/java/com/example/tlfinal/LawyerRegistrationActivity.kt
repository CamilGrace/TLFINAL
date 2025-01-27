package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tlfinal.databinding.LawyerRegistrationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LawyerRegistrationActivity : AppCompatActivity() {

    private lateinit var binding: LawyerRegistrationBinding
    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Back button functionality
        binding.imageBack.setOnClickListener { finish() }

        // Initially disable the Register button
        binding.registerButton.isEnabled = false

        // Enable register button only when the checkbox is checked
        binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
            binding.registerButton.apply {
                isEnabled = isChecked
                setBackgroundResource(if (isChecked) R.drawable.button_enabled else R.drawable.button_disabled)
            }
        }

        // Add TextWatcher to confirm password EditText
        binding.confirmedPassEditText.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = binding.passEditText.editText?.text.toString()
                val confirmPassword = s.toString()
                if (password == confirmPassword) {
                    binding.confirmedPassEditText.editText?.setBackgroundResource(R.drawable.edit_text_background_green)
                } else {
                    binding.confirmedPassEditText.editText?.setBackgroundResource(R.drawable.edit_text_background_red)
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Set up TextWatchers for input validation
        val fields = arrayOf(
            binding.nameEditText,
            binding.contactEditText,
            binding.emailEditText,
            binding.unameEditText,
            binding.passEditText
        )

        // Register button click logic
        binding.registerButton.setOnClickListener {
            val fullName = binding.nameEditText.text.toString().trim()
            val contactNo = binding.contactEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val username = binding.unameEditText.text.toString().trim()
            val password = binding.passEditText.editText?.text.toString().trim()
            val confirmPassword = binding.confirmedPassEditText.editText?.text.toString().trim()

            if (fullName.isEmpty() || contactNo.isEmpty() || email.isEmpty() ||
                username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Proceed with registration
            registerLawyer(fullName, contactNo, email, username, password)
        }
    }

    private fun registerLawyer(fullName: String, contactNo: String, email: String, username: String, password: String) {
        binding.progressBar.visibility = View.VISIBLE

        // Convert username to lowercase before saving it
        val usernameLower = username.lowercase()

        // Log the username being used for registration
        Log.d("RegisterLawyer", "Registering with username: $usernameLower")

        // Create user in Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        // Prepare lawyer profile data (including lowercase username)
                        val lawyerProfile = hashMapOf(
                            "fullName" to fullName,
                            "contactNo" to contactNo,
                            "email" to email,
                            "username" to usernameLower  // Save username in lowercase
                        )

                        // Store lawyer profile in Firestore (exclude password)
                        firestore.collection("lawyers").document(userId).set(lawyerProfile)
                            .addOnSuccessListener {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                                navigateToLogin()
                            }
                            .addOnFailureListener { e ->
                                binding.progressBar.visibility = View.GONE
                                Log.e("FirestoreError", "Error saving lawyer data: ${e.message}")
                                Toast.makeText(this, "Error saving lawyer data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "User ID is null", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    binding.progressBar.visibility = View.GONE
                    Log.e("AuthError", "Registration failed: ${task.exception?.message}")
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // Check if all fields are valid
    private fun isValid(): Boolean {
        val name = binding.nameEditText.text.toString().trim()
        val contact = binding.contactEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val username = binding.unameEditText.text.toString().trim()
        val password = binding.passEditText.editText?.text.toString().trim()

        if (name.isEmpty() || contact.isEmpty() || email.isEmpty() || username.isEmpty() || password.isEmpty()) return false
        if (!binding.checkBox.isChecked) {
            Toast.makeText(this, "You must accept the terms and conditions", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}
