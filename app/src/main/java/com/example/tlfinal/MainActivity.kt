package com.example.tlfinal

import android.content.Context // Import Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth // Import FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // Define keys for SharedPreferences
    companion object {
        const val PREFS_NAME = "AppPrefs" // Choose a name for your preferences file
        const val KEY_USER_TYPE = "USER_TYPE" // Key to store user type ("client" or "lawyer")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // It's generally better practice to initialize Firebase in a custom Application class,
        // but doing it here works for simpler apps.
        FirebaseApp.initializeApp(this)
        FirebaseFirestore.setLoggingEnabled(true) // Keep for debugging if needed

        auth = FirebaseAuth.getInstance()

        // Check if a user is currently signed in
        if (auth.currentUser != null) {
            // User is signed in, now determine their type
            val userType = getUserTypeFromPreferences()

            if (userType != null) {
                // Navigate based on stored user type
                when (userType) {
                    "client" -> navigateToClientDashboard()
                    "lawyer" -> navigateToLawyerDashboard()
                    else -> {
                        // Unknown user type stored, default to LoginActivity
                        navigateToLogin()
                    }
                }
            } else {
                // User is logged in with Firebase Auth, but we couldn't find their type
                // This might happen if login/registration didn't save the type correctly.
                // Defaulting to LoginActivity is safest here.
                // Alternatively, you could try fetching from Firestore here, but it adds delay.
                navigateToLogin()
            }

        } else {
            // No user is signed in, go to LoginActivity
            navigateToLogin()
        }

        // Finish MainActivity so the user cannot navigate back to it using the back button
        // This happens AFTER the appropriate next activity has been started.
        finish()
    }

    private fun getUserTypeFromPreferences(): String? {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_USER_TYPE, null) // Return null if not found
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        // Add flags to clear the back stack if needed, especially if coming from an error state
        // intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun navigateToClientDashboard() {
        // Replace ClientDashboardActivity::class.java with your actual client dashboard activity
        val intent = Intent(this, ClientDashboardActivity::class.java)
        // Add flags to clear the back stack is usually good practice when entering the main part of the app
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun navigateToLawyerDashboard() {
        // Replace LawyerDashboardActivity::class.java with your actual lawyer dashboard activity
        val intent = Intent(this, LawyerDashboardActivity::class.java)
        // Add flags to clear the back stack
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}