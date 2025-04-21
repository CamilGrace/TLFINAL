package com.example.tlfinal

import android.os.Build // Import Build
import android.os.Bundle
import android.text.Editable
import android.text.Html // Import Html
import android.text.TextWatcher
import android.util.Log // Import Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog // Import AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.tlfinal.databinding.FragmentLawyerRegStep3Binding
// Import R class
import com.example.tlfinal.R

class LawyerRegStep3Fragment : Fragment() {

    private var _binding: FragmentLawyerRegStep3Binding? = null
    private val binding get() = _binding!!
    private val viewModel: LawyerRegistrationViewModel by activityViewModels()

    companion object {
        const val MIN_USERNAME_LENGTH = 6
        const val MAX_USERNAME_LENGTH = 50
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_PASSWORD_LENGTH = 100
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLawyerRegStep3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners() // Consolidate listener setup
        restoreFromViewModel()
    }

    private fun setupListeners() {
        // Text Watchers
        binding.usernameEditText.addTextChangedListener(textWatcher)
        binding.passwordEditText.addTextChangedListener(textWatcher)
        binding.confirmPasswordEditText.addTextChangedListener(textWatcher)

        // Checkbox listener updates ViewModel
        binding.termsCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val data = viewModel.lawyerData.value ?: LawyerRegistrationViewModel.LawyerData()
            viewModel.lawyerData.value = data.copy(termsAccepted = isChecked)
            // Optionally trigger validation or button update here if needed immediately
            validate() // Example: Re-validate when checkbox changes
        }

        // --- Add Click Listeners for Links ---
        binding.termsAndConditionsLink.setOnClickListener {
            Log.d("LawyerRegStep3", "Terms link clicked")
            showTermsDialog()
        }
        binding.privacyPolicyLink.setOnClickListener {
            Log.d("LawyerRegStep3", "Policy link clicked")
            showPrivacyPolicyDialog()
        }
        // --- ---
    }


    private fun restoreFromViewModel() {
        viewModel.lawyerData.value?.let { data ->
            binding.usernameEditText.setText(data.username)
            binding.passwordEditText.setText(data.password)
            binding.confirmPasswordEditText.setText(data.confirmPassword)
            binding.termsCheckBox.isChecked = data.termsAccepted
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { updateViewModelAndValidate() } // Call combined function
    }

    private fun updateViewModelAndValidate() {
        updateViewModel() // Update ViewModel first
        validate()      // Then run validation (which shows errors)
    }


    private fun updateViewModel() {
        val data = viewModel.lawyerData.value ?: LawyerRegistrationViewModel.LawyerData()
        viewModel.lawyerData.value = data.copy(
            username = binding.usernameEditText.text.toString().trim(),
            password = binding.passwordEditText.text.toString(),
            confirmPassword = binding.confirmPasswordEditText.text.toString(),
            termsAccepted = binding.termsCheckBox.isChecked // Also update terms from checkbox state
        )
        Log.d("LawyerRegStep3", "ViewModel updated: ${viewModel.lawyerData.value}") // Log updated data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- Validation Function (Keep existing logic, maybe add logging) ---
    fun validate(): Boolean {
        var isValid = true
        Log.d("LawyerRegStep3", "Running validation...") // Add log

        // Username
        val username = binding.usernameEditText.text.toString().trim()
        when {
            username.isBlank() -> { binding.usernameEditText.error = "Username is Required"; isValid = false } // Use TIL error
            username.length < MIN_USERNAME_LENGTH -> { binding.usernameEditText.error = "Minimum ${MIN_USERNAME_LENGTH} characters"; isValid = false }
            username.length > MAX_USERNAME_LENGTH -> { binding.usernameEditText.error = "Maximum ${MAX_USERNAME_LENGTH} characters"; isValid = false }
            else -> binding.usernameEditText.error = null
        }

        // Password
        val password = binding.passwordEditText.text.toString()
        when {
            password.isEmpty() -> { binding.passwordTextInputLayout.error = "Password is Required"; isValid = false }
            password.length < MIN_PASSWORD_LENGTH -> { binding.passwordTextInputLayout.error = "Minimum ${MIN_PASSWORD_LENGTH} characters"; isValid = false }
            password.length > MAX_PASSWORD_LENGTH -> { binding.passwordTextInputLayout.error = "Maximum ${MAX_PASSWORD_LENGTH} characters"; isValid = false }
            else -> binding.passwordTextInputLayout.error = null
        }

        // Confirm Password
        val confirmPassword = binding.confirmPasswordEditText.text.toString()
        when {
            confirmPassword.isEmpty() -> { binding.confirmPasswordTextInputLayout.error = "Confirm Password is Required"; isValid = false }
            password.isNotEmpty() && password != confirmPassword -> { binding.confirmPasswordTextInputLayout.error = "Passwords do not match"; isValid = false }
            else -> binding.confirmPasswordTextInputLayout.error = null
        }

        // Terms
        if (!binding.termsCheckBox.isChecked) {
            // No specific error field, rely on Toast or Activity-level handling
            if(isValid) { // Only show toast if other fields were okay
                Toast.makeText(requireContext(), "Please accept the Terms and Privacy Policy", Toast.LENGTH_SHORT).show()
            }
            isValid = false
        }

        Log.d("LawyerRegStep3", "Validation result: $isValid")
        return isValid
    }

    // --- Add Dialog Functions ---
    private fun showTermsDialog() {
        val termsText = getString(R.string.terms_content_placeholder) // Use placeholder from strings.xml
        val formattedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(termsText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION") Html.fromHtml(termsText)
        }

        AlertDialog.Builder(requireContext()) // Use requireContext() in Fragment
            .setTitle("Terms and Conditions")
            .setMessage(formattedText)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        val policyText = getString(R.string.privacy_policy_content_placeholder) // Use placeholder from strings.xml
        val formattedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(policyText, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION") Html.fromHtml(policyText)
        }

        AlertDialog.Builder(requireContext()) // Use requireContext() in Fragment
            .setTitle("Privacy Policy")
            .setMessage(formattedText)
            .setPositiveButton("Close", null)
            .show()
    }
    // --- ---
}