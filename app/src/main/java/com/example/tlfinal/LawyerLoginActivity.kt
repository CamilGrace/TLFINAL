package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tlfinal.databinding.LawyerLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LawyerLoginActivity : AppCompatActivity() {

    private lateinit var binding: LawyerLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.imageBack.setOnClickListener {
            finish()
        }

        // Enable login button only when both fields are filled
        binding.usernameEditText.addTextChangedListener(textWatcher)
        binding.passwordEditText.editText?.addTextChangedListener(textWatcher)

        binding.loginButton.setOnClickListener {
            loginLawyer()
        }

        binding.signupText.setOnClickListener {
            val intent = Intent(this, RoleSelectionActivity::class.java)
            startActivity(intent)
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            binding.loginButton.isEnabled = binding.usernameEditText.text.toString().isNotEmpty() &&
                    binding.passwordEditText.editText?.text.toString().isNotEmpty()
            binding.loginButton.setBackgroundResource(
                if (binding.loginButton.isEnabled) R.drawable.button_enabled else R.drawable.button_disabled
            )
        }

        override fun afterTextChanged(s: Editable?) {}
    }

    private fun loginLawyer() {
        val username = binding.usernameEditText.text.toString().trim().lowercase() // Convert to lowercase
        val password = binding.passwordEditText.editText?.text.toString().trim()

        // Log the username being used for login
        Log.d("LoginLawyer", "Attempting to login with username: $username")

        binding.progressBar.visibility = View.VISIBLE

        val db = FirebaseFirestore.getInstance()
        db.collection("lawyers").whereEqualTo("username", username).get()  // Query with lowercase username
            .addOnSuccessListener { querySnapshot ->
                binding.progressBar.visibility = View.GONE

                // Log the result of the query
                Log.d("LoginLawyer", "Query returned ${querySnapshot.size()} document(s)")

                if (!querySnapshot.isEmpty) {
                    val email = querySnapshot.documents[0].getString("email")
                    email?.let {
                        // Log the email being used to sign in
                        Log.d("LoginLawyer", "Found email: $email")

                        auth.signInWithEmailAndPassword(it, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(this, "Lawyer Login Successful", Toast.LENGTH_SHORT).show()
                                    // Navigate to the next screen (e.g., Lawyer Dashboard)
                                    startActivity(Intent(this, LawyerDashboardActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                } else {
                    // Log if no lawyer was found
                    Log.d("LoginLawyer", "No lawyer found with this username")
                    Toast.makeText(this, "No lawyer found with this username", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                // Log the error if the query fails
                Log.e("LoginLawyer", "Error fetching lawyer data: ${e.message}")
                Toast.makeText(this, "Error fetching lawyer data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
