package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.example.tlfinal.databinding.ClientEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ClientEditProfileActivity : AppCompatActivity() {
    private lateinit var binding: ClientEditProfileBinding
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle Back Button click
        binding.imageBack.setOnClickListener {
            finish() // Close the activity and go back to the previous screen
        }

        firestore = FirebaseFirestore.getInstance()

        // Enable save button only when the required fields are filled
        binding.userFullName.addTextChangedListener { updateSaveButtonState() }
        binding.userEmail.addTextChangedListener { updateSaveButtonState() }

        // Save Button Click Listener
        binding.saveButton.setOnClickListener {
            val updatedFullName = binding.userFullName.text.toString()
            val updatedEmail = binding.userEmail.text.toString()
            val updatedContact = binding.userContact.text.toString()
            val updatedAddress = binding.userAddress.text.toString()

            if (updatedFullName.isNotBlank() && updatedEmail.isNotBlank()) {
                // Update Firestore
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                userId?.let {
                    val updatedData = mapOf(
                        "fullName" to updatedFullName,
                        "email" to updatedEmail,
                        "contactNo" to updatedContact,
                        "address" to updatedAddress
                    )
                    firestore.collection("users").document(it)
                        .update(updatedData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()

                            // Pass updated data back to the profile screen
                            val resultIntent = Intent()
                            resultIntent.putExtra("fullName", updatedFullName)
                            resultIntent.putExtra("email", updatedEmail)
                            resultIntent.putExtra("contactNo", updatedContact)
                            resultIntent.putExtra("address", updatedAddress)
                            setResult(RESULT_OK, resultIntent)

                            finish() // Close the activity
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(this, "Full Name and Email cannot be empty.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSaveButtonState() {
        val isFullNameFilled = binding.userFullName.text.isNotEmpty()
        val isEmailFilled = binding.userEmail.text.isNotEmpty()

        // Enable button if both Full Name and Email are filled
        binding.saveButton.isEnabled = isFullNameFilled && isEmailFilled
        binding.saveButton.setBackgroundResource(
            if (binding.saveButton.isEnabled) R.drawable.button_enabled else R.drawable.button_disabled
        )
    }
}

