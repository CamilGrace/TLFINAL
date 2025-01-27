package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tlfinal.databinding.ClientSettingsBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class ClientSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ClientSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding
        binding = ClientSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle Back Button click
        binding.imageBack.setOnClickListener {
            finish() // Close the activity and go back to the previous screen
        }

        // Handle Bottom Navigation selection
        val bottomNavigationView: BottomNavigationView = binding.bottomNavigation
        bottomNavigationView.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_message -> {
                    // Navigate to Profile Activity
                    startActivity(Intent(this, ClientInboxActivity::class.java))
                    true
                }
                R.id.bottom_home -> {
                    // Navigate to Dashboard Activity
                    startActivity(Intent(this, ClientDashboardActivity::class.java))
                    true
                }
                R.id.bottom_settings -> {
                    // Stay on Settings Activity; no action needed
                    true
                }
                else -> false
            }
        }

        // Set the default selected item in Bottom Navigation to Settings
        bottomNavigationView.selectedItemId = R.id.bottom_settings
    }

    override fun onResume() {
        super.onResume()

        // Ensure the Bottom Navigation highlights the Settings icon
        binding.bottomNavigation.selectedItemId = R.id.bottom_settings
    }
}
