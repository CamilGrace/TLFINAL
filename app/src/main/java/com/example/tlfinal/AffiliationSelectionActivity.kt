package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tlfinal.databinding.ActivityAffiliationSelectionBinding

class AffiliationSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAffiliationSelectionBinding

    companion object {
        const val EXTRA_AFFILIATION_TYPE = "com.example.tlfinal.AFFILIATION_TYPE" // Key for intent extra
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAffiliationSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Set Home as selected initially ---
        binding.bottomNavigation.selectedItemId = R.id.bottom_home
        // --- ---

        binding.imageBack.setOnClickListener {
            finish() // Close the activity and go back to the previous screen
        }

        // Handle Bottom Navigation selection
        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            // Prevent navigating to the current screen again
            if (item.itemId == binding.bottomNavigation.selectedItemId && item.itemId != R.id.bottom_home) { // Allow re-selecting home without navigating away
                return@setOnNavigationItemSelectedListener false
            } else if (item.itemId == R.id.bottom_home){
                // If user explicitly clicks home, maybe finish this activity to go back?
                // Or just do nothing if you want them to use the back button.
                // finish() // Option 1: Go back to Dashboard
                return@setOnNavigationItemSelectedListener true // Option 2: Do nothing, stay here
            }


            when (item.itemId) {
                R.id.bottom_message -> {
                    startActivity(Intent(this, InboxActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish() // Finish this activity when navigating away
                    true
                }
                // R.id.bottom_home handled above
                R.id.bottom_settings -> {
                    startActivity(Intent(this, ClientSettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish() // Finish this activity when navigating away
                    true
                }
                else -> false
            }
        }

        binding.btnPAO.setOnClickListener { // Use correct ID
            val intent = Intent(this, PAORequirementsActivity::class.java)
            // *** ADD EXTRA ***
            intent.putExtra(EXTRA_AFFILIATION_TYPE, "PAO")
            startActivity(intent)
        }

        binding.btnPrivate.setOnClickListener { // Use correct ID
            val intent = Intent(this, PrivateLawyerRequirementsActivity::class.java)
            // *** ADD EXTRA ***
            intent.putExtra(EXTRA_AFFILIATION_TYPE, "Private") // Use "Private" or "Private Law Firm" consistently
            startActivity(intent)
        }
    }

    // Optional: Override onResume if you want Home always selected when returning
    override fun onResume() {
        super.onResume()
        binding.bottomNavigation.selectedItemId = R.id.bottom_home
    }
}