package com.example.tlfinal

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
// Removed TextWatcher imports as we use core version
// import android.text.Editable
// import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton // Keep this
import androidx.core.content.ContextCompat
// Import specific TextWatcher
import android.text.TextWatcher
import android.text.Editable
import android.text.Html
import com.example.tlfinal.databinding.ClientRegistrationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.tlfinal.R // Ensure R is imported

class ClientRegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ClientRegistrationBinding
    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    private val prefsName = MainActivity.PREFS_NAME
    private val keyUserType = MainActivity.KEY_USER_TYPE

    private companion object {
        const val MAX_FULL_NAME_LENGTH = 100; const val MAX_CONTACT_NO_LENGTH = 30
        const val MAX_EMAIL_LENGTH = 254; const val MAX_USERNAME_LENGTH = 50
        const val MIN_PASSWORD_LENGTH = 6; const val MAX_PASSWORD_LENGTH = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.imageBack.setOnClickListener { finish() }

        // --- Setup Link Click Listeners ---
        binding.termsAndConditions.setOnClickListener { // Use ID from XML
            Log.d("ClientRegistration", "Terms clicked")
            showTermsDialog()
        }
        binding.privacyAndPolicy.setOnClickListener { // Use ID from XML
            Log.d("ClientRegistration", "Policy clicked")
            showPrivacyPolicyDialog()
        }
        // --- ---

        // Enable register button only when the checkbox is checked
        binding.checkBox.setOnCheckedChangeListener { _, isChecked -> // Use correct ID 'checkBox'
            // Call check function which calls visual update
            checkAndUpdateSaveButtonState()
        }

        // Add TextWatcher to confirm password EditText
        binding.confirmedPassEditText.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { validateConfirmPassword() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.passEditText.editText?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { validateConfirmPassword() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Add watchers to other fields to update button state
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { checkAndUpdateSaveButtonState() }
        }
        binding.nameEditText.addTextChangedListener(watcher)
        binding.contactEditText.addTextChangedListener(watcher)
        binding.emailEditText.addTextChangedListener(watcher)
        binding.unameEditText.addTextChangedListener(watcher)
        // Password fields already trigger validation which can call button update indirectly,
        // but adding explicitly is safer if validation logic changes.
        binding.passEditText.editText?.addTextChangedListener(watcher)
        binding.confirmedPassEditText.editText?.addTextChangedListener(watcher)


        binding.registerButton.setOnClickListener {
            if (validateInput()) { // Validate all inputs first
                // Check if checkbox is checked before proceeding
                if (!binding.checkBox.isChecked) {
                    Toast.makeText(this, "Please accept the Terms and Privacy Policy", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val fullName = binding.nameEditText.text.toString().trim()
                val contactNo = binding.contactEditText.text.toString().trim()
                val email = binding.emailEditText.text.toString().trim()
                val username = binding.unameEditText.text.toString().trim()
                val password = binding.passEditText.editText!!.text.toString() // Trimmed in validation, not needed here

                registerUser(fullName, contactNo, email, username, password)
            }
        }
        // Set initial button state (disabled)
        updateButtonVisuals(binding.registerButton, false)
    }

    // Removed setupTermsAndPolicyText() function as spans are no longer needed

    private fun showTermsDialog() {
        val termsText = getString(R.string.terms_content_placeholder) // Get the string with HTML
        val formattedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(termsText, Html.FROM_HTML_MODE_LEGACY) // Use newer method for API 24+
        } else {
            @Suppress("DEPRECATION") // Suppress warning for older method
            Html.fromHtml(termsText) // Use deprecated method for older APIs
        }

        AlertDialog.Builder(this)
            .setTitle("Terms and Conditions")
            .setMessage(formattedText) // <<< SET FORMATTED TEXT
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        val policyText = getString(R.string.privacy_policy_content_placeholder) // Get the string with HTML
        val formattedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(policyText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(policyText)
        }

        AlertDialog.Builder(this)
            .setTitle("Privacy Policy")
            .setMessage(formattedText) // <<< SET FORMATTED TEXT
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // --- Keep Button Update Helper ---
    private fun updateButtonVisuals(button: androidx.appcompat.widget.AppCompatButton, isEnabled: Boolean) {
        button.isEnabled = isEnabled
        val buttonBackgroundRes = if (isEnabled) R.drawable.button_enabled else R.drawable.button_disabled
        button.setBackgroundResource(buttonBackgroundRes)
        val buttonTextColor = ContextCompat.getColor(this, if (isEnabled) android.R.color.white else android.R.color.black)
        button.setTextColor(buttonTextColor)
    }

    // --- NEW: Combined Check and Update Function ---
    private fun checkAndUpdateSaveButtonState() {
        // Check if ALL required fields are filled AND checkbox is checked
        val allFieldsFilled = binding.nameEditText.text.toString().trim().isNotEmpty() &&
                binding.contactEditText.text.toString().trim().isNotEmpty() &&
                binding.emailEditText.text.toString().trim().isNotEmpty() &&
                binding.unameEditText.text.toString().trim().isNotEmpty() &&
                binding.passEditText.editText?.text.toString().isNotEmpty() == true &&
                binding.confirmedPassEditText.editText?.text.toString().isNotEmpty() == true &&
                (binding.passEditText.editText?.text.toString() == binding.confirmedPassEditText.editText?.text.toString()) // Optional: include password match here

        val isChecked = binding.checkBox.isChecked

        updateButtonVisuals(binding.registerButton, allFieldsFilled && isChecked) // Update visuals based on combined state
    }


    // ... (Keep validateConfirmPassword, validateInput, registerUser, navigateToLogin) ...
    private fun validateConfirmPassword() { /* ... keep existing ... */
        val password = binding.passEditText.editText?.text.toString()
        val confirmPassword = binding.confirmedPassEditText.editText?.text.toString()
        if (confirmPassword.isNotEmpty() && password.isNotEmpty()) {
            if (password == confirmPassword) { binding.confirmedPassEditText.editText?.setBackgroundResource(R.drawable.edit_text_background_green); binding.confirmedPassEditText.error = null }
            else { binding.confirmedPassEditText.editText?.setBackgroundResource(R.drawable.edit_text_background_red); binding.confirmedPassEditText.error = "Passwords do not match" }
        } else { binding.confirmedPassEditText.editText?.setBackgroundResource(R.drawable.edit_text_background_default); binding.confirmedPassEditText.error = null }
        checkAndUpdateSaveButtonState() // Also update main button state when confirm changes
    }
    private fun validateInput(): Boolean { /* ... keep existing validation logic ... */
        binding.nameEditText.error = null; binding.contactEditText.error = null; binding.emailEditText.error = null; binding.unameEditText.error = null; binding.passEditText.editText?.error = null; binding.confirmedPassEditText.editText?.error = null
        val fullName = binding.nameEditText.text.toString().trim(); val contactNo = binding.contactEditText.text.toString().trim(); val email = binding.emailEditText.text.toString().trim(); val username = binding.unameEditText.text.toString().trim(); val password = binding.passEditText.editText?.text.toString() ?: ""; val confirmPassword = binding.confirmedPassEditText.editText?.text.toString() ?: ""
        var isValid = true
        if (fullName.isEmpty()) { binding.nameEditText.error = "Full name required"; isValid = false }
        if (contactNo.isEmpty()) { binding.contactEditText.error = "Contact number required"; isValid = false }
        if (email.isEmpty()) { binding.emailEditText.error = "Email required"; isValid = false }
        if (username.isEmpty()) { binding.unameEditText.error = "Username required"; isValid = false }
        if (password.isEmpty()) { binding.passEditText.editText?.error = "Password required"; isValid = false }
        if (confirmPassword.isEmpty()) { binding.confirmedPassEditText.editText?.error = "Confirm password required"; isValid = false }
        if (!isValid) return false
        if (fullName.length > MAX_FULL_NAME_LENGTH) { binding.nameEditText.error = "Max ${MAX_FULL_NAME_LENGTH} chars"; isValid = false }
        if (contactNo.length > MAX_CONTACT_NO_LENGTH) { binding.contactEditText.error = "Max ${MAX_CONTACT_NO_LENGTH} chars"; isValid = false }
        if (email.length > MAX_EMAIL_LENGTH) { binding.emailEditText.error = "Max ${MAX_EMAIL_LENGTH} chars"; isValid = false }
        if (username.length > MAX_USERNAME_LENGTH) { binding.unameEditText.error = "Max ${MAX_USERNAME_LENGTH} chars"; isValid = false }
        if (password.length < MIN_PASSWORD_LENGTH) { binding.passEditText.editText?.error = "Min ${MIN_PASSWORD_LENGTH} chars"; isValid = false } else if (password.length > MAX_PASSWORD_LENGTH) { binding.passEditText.editText?.error = "Max ${MAX_PASSWORD_LENGTH} chars"; isValid = false }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { binding.emailEditText.error = "Invalid email format"; isValid = false }
        if (password != confirmPassword) { binding.confirmedPassEditText.editText?.error = "Passwords do not match"; isValid = false }
        return isValid
    }
    private fun registerUser(fullName: String, contactNo: String, email: String, username: String, password: String) { /* ... keep existing ... */
        binding.progressBar.visibility = View.VISIBLE; binding.registerButton.isEnabled = false
        val usernameLower = username.lowercase(); Log.d("RegisterUser", "Reg user: $usernameLower")
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        val userProfile = hashMapOf("fullName" to fullName, "contactNo" to contactNo, "email" to email, "username" to usernameLower, "userType" to "client")
                        firestore.collection("users").document(userId).set(userProfile)
                            .addOnSuccessListener { binding.progressBar.visibility = View.GONE; val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE); prefs.edit().putString(keyUserType, "client").apply(); Log.d("RegisterUser", "Saved type: client"); Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show(); navigateToLogin() }
                            .addOnFailureListener { e -> binding.progressBar.visibility = View.GONE; binding.registerButton.isEnabled = true; Log.e("FirestoreError", "Save fail: ${e.message}", e); Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show() }
                    } else { binding.progressBar.visibility = View.GONE; binding.registerButton.isEnabled = true; Log.e("RegisterUser", "Auth ok, UID null."); Toast.makeText(this, "Reg fail (UID error).", Toast.LENGTH_SHORT).show() }
                } else { binding.progressBar.visibility = View.GONE; binding.registerButton.isEnabled = true; Log.e("AuthError", "Reg fail: ${task.exception?.message}", task.exception); Toast.makeText(this, "Reg fail: ${task.exception?.message}", Toast.LENGTH_LONG).show() }
            }
    }
    private fun navigateToLogin() { /* ... keep existing ... */ val i = Intent(this, LoginActivity::class.java); i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK; startActivity(i); finish() }

}