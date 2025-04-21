package com.example.tlfinal

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter // <-- Import InputFilter
import android.text.TextWatcher
import android.util.Patterns // Import Patterns for email validation
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.tlfinal.databinding.FragmentLawyerRegStep1Binding

class LawyerRegStep1Fragment : Fragment() {

    private var _binding: FragmentLawyerRegStep1Binding? = null
    private val binding get() = _binding!!
    private val viewModel: LawyerRegistrationViewModel by activityViewModels()

    // --- Define Max Length Constants for Step 1 ---
    companion object {
        const val MAX_NAME_LENGTH = 50 // Use this for the filter
        const val MIN_AGE = 18
        const val MAX_AGE = 100
        const val MAX_CONTACT_LENGTH = 30
        const val MAX_EMAIL_LENGTH = 254
    }
    // --- End Constants ---

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLawyerRegStep1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Apply InputFilters for Length ---
        val nameLengthFilter = InputFilter.LengthFilter(MAX_NAME_LENGTH)
        binding.firstNameEditText.filters = arrayOf(nameLengthFilter)
        binding.middleNameEditText.filters = arrayOf(nameLengthFilter)
        binding.lastNameEditText.filters = arrayOf(nameLengthFilter)
        // You could add length filters to other EditTexts here too if needed
        // binding.contactNumberEditText.filters = arrayOf(InputFilter.LengthFilter(MAX_CONTACT_LENGTH))
        // binding.emailAddressEditText.filters = arrayOf(InputFilter.LengthFilter(MAX_EMAIL_LENGTH))
        // --- End Apply InputFilters ---


        // Add TextWatchers to update ViewModel
        binding.firstNameEditText.addTextChangedListener(textWatcher)
        binding.middleNameEditText.addTextChangedListener(textWatcher)
        binding.lastNameEditText.addTextChangedListener(textWatcher)
        binding.ageEditText.addTextChangedListener(textWatcher)
        binding.contactNumberEditText.addTextChangedListener(textWatcher)
        binding.emailAddressEditText.addTextChangedListener(textWatcher)


        // Set initial values from ViewModel (if any)
        viewModel.lawyerData.value?.let { data ->
            binding.firstNameEditText.setText(data.firstName)
            binding.middleNameEditText.setText(data.middleName)
            binding.lastNameEditText.setText(data.lastName)
            binding.ageEditText.setText(data.age?.toString() ?: "")
            binding.contactNumberEditText.setText(data.contactNumber)
            binding.emailAddressEditText.setText(data.emailAddress)
            //restore radio button
            when (data.gender) {
                "Male" -> binding.radioMale.isChecked = true
                "Female" -> binding.radioFemale.isChecked = true
                // Add other genders if applicable
            }
        }
        binding.genderRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            updateViewModel()
        }
    }
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            updateViewModel()
        }
    }

    private fun updateViewModel() {
        val selectedGender = when (binding.genderRadioGroup.checkedRadioButtonId) {
            R.id.radioMale -> "Male"
            R.id.radioFemale -> "Female"
            else -> null // Or "" if you prefer an empty string for no selection
        }
        val data = viewModel.lawyerData.value ?: LawyerRegistrationViewModel.LawyerData()
        // Use individual update methods if defined in ViewModel, otherwise copy works
        viewModel.lawyerData.value = data.copy(
            firstName = binding.firstNameEditText.text.toString().trim(), // Trim input
            middleName = binding.middleNameEditText.text.toString().trim(), // Trim input
            lastName = binding.lastNameEditText.text.toString().trim(), // Trim input
            age = binding.ageEditText.text.toString().toIntOrNull(),
            contactNumber = binding.contactNumberEditText.text.toString().trim(), // Trim input
            emailAddress = binding.emailAddressEditText.text.toString().trim(), // Trim input
            gender = selectedGender
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // The validate() function remains the same - it's still a good final check
    fun validate(): Boolean {
        var isValid = true

        // --- First Name ---
        val firstName = binding.firstNameEditText.text.toString().trim()
        if (firstName.isBlank()) {
            binding.firstNameEditText.error = "First name is required"
            isValid = false
        } else if (firstName.length > MAX_NAME_LENGTH) { // This check might seem redundant with the filter, but good practice
            binding.firstNameEditText.error = "Maximum ${MAX_NAME_LENGTH} characters"
            isValid = false
        } else {
            binding.firstNameEditText.error = null
        }

        // --- Middle Name (Optional Length Check) ---
        val middleName = binding.middleNameEditText.text.toString().trim()
        // Middle name might be optional, so don't check isBlank(), just length if not empty
        if (middleName.isNotEmpty() && middleName.length > MAX_NAME_LENGTH) {
            binding.middleNameEditText.error = "Maximum ${MAX_NAME_LENGTH} characters"
            isValid = false
        } else {
            binding.middleNameEditText.error = null // Clear error if empty or valid length
        }

        // --- Last Name ---
        val lastName = binding.lastNameEditText.text.toString().trim()
        if (lastName.isBlank()) {
            binding.lastNameEditText.error = "Last Name is Required"
            isValid = false
        } else if (lastName.length > MAX_NAME_LENGTH) { // Redundant check, but safe
            binding.lastNameEditText.error = "Maximum ${MAX_NAME_LENGTH} characters"
            isValid = false
        } else {
            binding.lastNameEditText.error = null
        }

        // --- Age ---
        val ageStr = binding.ageEditText.text.toString()
        val age = ageStr.toIntOrNull()
        if (ageStr.isBlank()) {
            binding.ageEditText.error = "Age is required"
            isValid = false
        } else if (age == null || age < MIN_AGE || age > MAX_AGE) {
            binding.ageEditText.error = "Age must be between ${MIN_AGE} and ${MAX_AGE}"
            isValid = false
        } else {
            binding.ageEditText.error = null
        }

        // --- Contact Number ---
        val contactNumber = binding.contactNumberEditText.text.toString().trim()
        if (contactNumber.isBlank()){
            binding.contactNumberEditText.error = "Contact Number is required"
            isValid = false
        } else if (contactNumber.length > MAX_CONTACT_LENGTH) {
            binding.contactNumberEditText.error = "Maximum ${MAX_CONTACT_LENGTH} characters"
            isValid = false
            // Consider adding phone number format validation here too
        } else {
            binding.contactNumberEditText.error = null
        }

        // --- Email Address ---
        val email = binding.emailAddressEditText.text.toString().trim()
        if (email.isBlank()) {
            binding.emailAddressEditText.error = "Email is Required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailAddressEditText.error = "Invalid email format"
            isValid = false
        } else if (email.length > MAX_EMAIL_LENGTH) {
            binding.emailAddressEditText.error = "Maximum ${MAX_EMAIL_LENGTH} characters"
            isValid = false
        } else {
            binding.emailAddressEditText.error = null
        }

        // --- Gender ---
        if (binding.genderRadioGroup.checkedRadioButtonId == -1) {
            Toast.makeText(requireContext(), "Please select gender", Toast.LENGTH_SHORT).show()
            isValid = false;
        }

        return isValid
    }
}