package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tlfinal.databinding.RoleRegistrationBinding

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: RoleRegistrationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding
        binding = RoleRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the "Back" button functionality
        binding.imageBack.setOnClickListener {
            finish() // Close this activity and return to the previous one
        }

        // Set click listeners for the buttons
        binding.clientButton.setOnClickListener {
            // Enable client button
            updateButtonState(binding.clientButton, true)
            // Disable lawyer button
            updateButtonState(binding.lawyerButton, false)
            // Show continue text
            binding.lawyerLogin.visibility = View.VISIBLE

            // Set click listener for the continue button
            binding.lawyerLogin.setOnClickListener {
                // Navigate to ClientRegistrationActivity
                val intent = Intent(this@RoleSelectionActivity, ClientRegistrationActivity::class.java)
                startActivity(intent)
            }
        }

        binding.lawyerButton.setOnClickListener {
            // Enable lawyer button
            updateButtonState(binding.lawyerButton, true)
            // Disable client button
            updateButtonState(binding.clientButton, false)
            // Show continue text
            binding.lawyerLogin.visibility = View.VISIBLE

            // Set click listener for the continue button
            binding.lawyerLogin.setOnClickListener {
                // Navigate to LawyerRegistrationActivity
                val intent = Intent(this@RoleSelectionActivity, LawyerRegistrationActivity::class.java)
                startActivity(intent)
            }
        }
    }

    // Helper function to update button state
    private fun updateButtonState(button: androidx.appcompat.widget.AppCompatButton, isEnabled: Boolean) {
        val buttonBackground = if (isEnabled) {
            R.drawable.button_enabled // Enabled button background
        } else {
            R.drawable.button_disabled // Disabled button background
        }
        button.background = ContextCompat.getDrawable(this@RoleSelectionActivity, buttonBackground)

        val buttonTextColor = if (isEnabled) {
            ContextCompat.getColor(this@RoleSelectionActivity, android.R.color.white) // White text when enabled
        } else {
            ContextCompat.getColor(this@RoleSelectionActivity, android.R.color.black) // Black text when disabled
        }
        button.setTextColor(buttonTextColor)
    }
}