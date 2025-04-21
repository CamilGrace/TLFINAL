package com.example.tlfinal

import android.app.Activity // Keep Activity import
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable // <<< ADD IMPORT
import android.text.TextWatcher // <<< ADD IMPORT
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog // <<< Use androidx AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener // Keep this convenient listener
import com.example.tlfinal.databinding.LawyerEditProfileBinding // Use ViewBinding
import com.google.android.material.chip.Chip // Import Chip
// Removed unused import: import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

// Import your project's R class <<< ADD IMPORT (Adjust package name if needed)
import com.example.tlfinal.R

class LawyerEditProfileActivity : AppCompatActivity() {
    private lateinit var binding: LawyerEditProfileBinding // Use ViewBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var alertDialog: AlertDialog // Keep lateinit declaration
    private var selectedAvailabilityOption: String = ""
    private var currentLegalSpecializations = mutableListOf<Map<String, Any>>()
    private val allSpecializations = listOf(
        "Family Law", "Criminal Law", "Civil Law", "Labor Law", "Immigration Law",
        "Consumer Protection Law", "Real Estate & Property Law", "Business & Corporate Law",
        "Tax Law", "Health Law", "Environmental Law", "Public Assistance & Welfare Law",
        "Traffic and Transportation Law"
    )

    private val allSubcategories = mapOf(
        "Family Law" to listOf("Marriage & Divorce", "Child Custody & Support", "Adoption", "Property Division", "Domestic Violence"),
        "Criminal Law" to listOf("Criminal Defense", "Victim's Advocacy", "Traffic Violations", "Special Criminal Laws"),
        "Civil Law" to listOf("Personal Injury", "Property Disputes", "Contracts & Obligations", "Torts"),
        "Labor Law" to listOf("Employee Rights", "Workplace Disputes", "Social Security Benefits", "Union and Collective Bargaining"),
        "Immigration Law" to listOf("Visa & Work Permits", "Permanent Residency & Citizenship", "Deportation Issues", "Family Immigration"),
        "Consumer Protection Law" to listOf("Product Liability", "Consumer Rights Violations", "Financial Protection", "Complaints Against Service Providers"),
        "Real Estate & Property Law" to listOf("Property Transactions", "Landlord/Tenant Disputes", "Land Use & Zoning", "Construction Issues"),
        "Business & Corporate Law" to listOf("Business Formation", "Intellectual Property", "Contracts and Commercial Transactions", "Taxation"),
        "Tax Law" to listOf("Personal Taxes", "Corporate Taxes", "Estate Taxes", "Tax Disputes"),
        "Health Law" to listOf("Medical Malpractice", "Health Insurance Issues", "Health Rights & Protection"),
        "Environmental Law" to listOf("Pollution Complaints", "Environmental Permits", "Conservation and Protection"),
        "Public Assistance & Welfare Law" to listOf("Government Assistance Programs", "Social Welfare Rights"),
        "Traffic and Transportation Law" to listOf("Traffic Violations", "Accidents & Liability", "Public Transportation Issues")
    )

    companion object {
        const val TAG = "LawyerEditProfile"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupUI()
        loadInitialData()
        setupListeners()
    }

    private fun setupUI() {
        // Setup adapter for Legal Services MultiAutoCompleteTextView using binding
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, allSpecializations)
        binding.editLegalServices.setAdapter(adapter) // <<< Use binding
        binding.editLegalServices.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer()) // <<< Use binding
        binding.editLegalServices.threshold = 1 // <<< Use binding

        // Setup adapter for Affiliation Spinner using binding
        val affiliationOptions = listOf("Select Affiliation", "PAO", "Private Law Firm")
        val affiliationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, affiliationOptions)
        affiliationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAffiliation.adapter = affiliationAdapter // <<< Use binding
    }

    private fun loadInitialData() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Error: Not logged in.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firestore.collection("lawyers").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Personal Info - Use binding
                    binding.editFirstName.setText(document.getString("firstName"))
                    binding.editMiddleName.setText(document.getString("middleName"))
                    binding.editLastName.setText(document.getString("lastName"))
                    val gender = document.getString("gender")
                    if (gender == "Female") binding.radioFemale.isChecked = true // Use binding
                    else if (gender == "Male") binding.radioMale.isChecked = true // Use binding
                    binding.editAge.setText(document.getLong("age")?.toString() ?: "") // Use binding

                    // Contact Info - Use binding
                    binding.editEmail.setText(document.getString("emailAddress"))
                    binding.editContact.setText(document.getString("contactNumber"))
                    binding.editAddress.setText(document.getString("officeAddress"))

                    // Credentials & Expertise - Use binding
                    val affiliation = document.getString("affiliation") ?: "Private Law Firm"
                    selectSpinnerItemByValue(binding.spinnerAffiliation, affiliation) // Use binding
                    binding.editRollNumber.setText(document.getString("rollNumber")) // Use binding

                    toggleAffiliationFields(affiliation) // Set initial visibility

                    binding.editLawFirmName.setText(document.getString("lawFirmName")) // Use binding
                    binding.editLawFirmAddress.setText(document.getString("lawFirmAddress")) // Use binding

                    // Load Legal Specializations
                    currentLegalSpecializations = (document.get("legalSpecializations") as? List<Map<String, Any>>)?.toMutableList() ?: mutableListOf()
                    displaySpecializationChips() // Update chips

                    binding.editYearsofexp.setText(document.getLong("yearsOfExperience")?.toString() ?: "") // Use binding

                    // Availability - Use binding
                    selectedAvailabilityOption = document.getString("availabilityOption") ?: ""
                    when (selectedAvailabilityOption) {
                        "No Hours Available" -> binding.radioNoHours.isChecked = true
                        "Always open" -> binding.radioAlwaysOpen.isChecked = true
                        "Permanently closed" -> binding.radioPermanentlyClosed.isChecked = true
                        "Temporarily Closed" -> binding.radioTemporarilyClosed.isChecked = true
                        "Open on selected hours" -> binding.radioOpenSelectedHours.isChecked = true
                        else -> binding.availabilityRadioGroup.clearCheck()
                    }
                    binding.hoursLayout.visibility = if (selectedAvailabilityOption == "Open on selected hours") View.VISIBLE else View.GONE

                    val hoursList = (document.get("daysAndHours") as? List<*>)?.mapNotNull { item ->
                        (item as? Map<*, *>)?.entries?.associate { entry ->
                            (entry.key as? String ?: "") to (entry.value as? String ?: "")
                        }
                    } ?: emptyList()
                    populateAvailabilityHours(hoursList)


                    // Consultation Fee - Use binding
                    binding.editConsultationFee.setText(document.getDouble("consultationFee")?.toString() ?: "")

                    updateSaveButtonState()
                } else {
                    Toast.makeText(this, "Could not load profile data.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error loading lawyer data", e)
            }
    }

    private fun setupListeners() {
        binding.imageBack.setOnClickListener { finish() }

        // Affiliation Spinner Listener - Use binding
        binding.spinnerAffiliation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedAffiliation = parent?.getItemAtPosition(position).toString()
                if (position > 0) {
                    toggleAffiliationFields(selectedAffiliation)
                    updateSaveButtonState()
                } else {
                    toggleAffiliationFields(null)
                    updateSaveButtonState() // Also update state when "Select" is chosen
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                toggleAffiliationFields(null)
                updateSaveButtonState() // Update state if nothing selected
            }
        }

        // Availability RadioGroup Listener - Use binding
        binding.availabilityRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val needsHoursLayout = (checkedId == R.id.radio_open_selected_hours) // R is resolved now
            binding.hoursLayout.visibility = if (needsHoursLayout) View.VISIBLE else View.GONE
            selectedAvailabilityOption = when (checkedId) {
                R.id.radio_no_hours -> "No Hours Available" // R is resolved now
                R.id.radio_always_open -> "Always open" // R is resolved now
                R.id.radio_permanently_closed -> "Permanently closed" // R is resolved now
                R.id.radio_temporarily_closed -> "Temporarily Closed" // R is resolved now
                R.id.radio_open_selected_hours -> "Open on selected hours" // R is resolved now
                else -> ""
            }
            if (needsHoursLayout && binding.daysHoursContainer.childCount == 0) {
                addTimePickers(binding.daysHoursContainer)
            }
            updateSaveButtonState()
        }

        // Add Day Button Listener - Use binding
        binding.addDayButton.setOnClickListener {
            addTimePickers(binding.daysHoursContainer) // Use binding
        }

        // MultiAutoCompleteTextView Listener (for Chip display) - Use binding
        binding.editLegalServices.addTextChangedListener { updateSpecializationChipsFromText() }

        // Add TextChangedListeners using the shared textWatcher - Use binding
        val textWatcher = object : TextWatcher { // Define textWatcher here
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateSaveButtonState() }
        }
        binding.editFirstName.addTextChangedListener(textWatcher)
        binding.editMiddleName.addTextChangedListener(textWatcher)
        binding.editLastName.addTextChangedListener(textWatcher)
        binding.editAge.addTextChangedListener(textWatcher)
        binding.editEmail.addTextChangedListener(textWatcher)
        binding.editContact.addTextChangedListener(textWatcher)
        binding.editAddress.addTextChangedListener(textWatcher)
        binding.editRollNumber.addTextChangedListener(textWatcher)
        binding.editLawFirmName.addTextChangedListener(textWatcher)
        binding.editLawFirmAddress.addTextChangedListener(textWatcher)
        binding.editLegalServices.addTextChangedListener(textWatcher) // Already has chip update too
        binding.editYearsofexp.addTextChangedListener(textWatcher)
        binding.editConsultationFee.addTextChangedListener(textWatcher)
        binding.genderRadioGroup.setOnCheckedChangeListener { _, _ -> updateSaveButtonState() } // Use binding

        // Save Button Click Listener - Use binding
        binding.saveButton.setOnClickListener {
            if (validateInput()) {
                saveProfileData()
            }
        }
    }

    // --- Helper Functions ---

    private fun toggleAffiliationFields(affiliation: String?) {
        // Use binding
        when (affiliation) {
            "PAO" -> {
                binding.layoutRollNumber.visibility = View.VISIBLE
                binding.layoutLawFirmName.visibility = View.GONE
                binding.layoutLawFirmAddress.visibility = View.GONE
                binding.editAddress.setText("Baguio Public Attorney's Office")
                binding.editAddress.isEnabled = false
            }
            "Private Law Firm" -> {
                binding.layoutRollNumber.visibility = View.VISIBLE
                binding.layoutLawFirmName.visibility = View.VISIBLE
                binding.layoutLawFirmAddress.visibility = View.VISIBLE
                binding.editAddress.isEnabled = true
                if(binding.editAddress.text.toString() == "Baguio Public Attorney's Office"){
                    binding.editAddress.setText("")
                }
            }
            else -> {
                binding.layoutRollNumber.visibility = View.GONE
                binding.layoutLawFirmName.visibility = View.GONE
                binding.layoutLawFirmAddress.visibility = View.GONE
                binding.editAddress.isEnabled = true
            }
        }
    }

    private fun updateSpecializationChipsFromText() {
        // Use binding
        val text = binding.editLegalServices.text.toString()
        val selectedSpecs = text.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() && allSpecializations.contains(it) }
            .distinct()

        binding.editSpecializationChips.removeAllViews()
        selectedSpecs.forEach { spec ->
            val chip = Chip(this).apply {
                this.text = spec
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    removeSpecialization(spec)
                }
            }
            binding.editSpecializationChips.addView(chip)
        }
        updateSaveButtonState()
    }

    private fun removeSpecialization(specToRemove: String) {
        // Use binding
        val currentText = binding.editLegalServices.text.toString()
        val currentSpecs = currentText.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()

        if (currentSpecs.remove(specToRemove)) {
            val newText = currentSpecs.joinToString(", ")
            binding.editLegalServices.setText(newText)
            // TextWatcher will automatically call updateSpecializationChipsFromText
        }
    }
    private fun displaySpecializationChips() {
        // Use binding
        binding.editSpecializationChips.removeAllViews()
        val specNames = currentLegalSpecializations.mapNotNull { it["specialization"] as? String }

        specNames.forEach { spec ->
            val chip = Chip(this).apply {
                text = spec
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    removeSpecialization(spec)
                }
            }
            binding.editSpecializationChips.addView(chip)
        }
        binding.editLegalServices.setText(specNames.joinToString(", "))
    }

    private fun populateAvailabilityHours(hoursList: List<Map<String, String>>) {
        // Use binding
        binding.daysHoursContainer.removeAllViews()
        if (hoursList.isEmpty() && binding.radioOpenSelectedHours.isChecked) {
            addTimePickers(binding.daysHoursContainer)
        } else {
            hoursList.forEach { hourMap ->
                addTimePickers(binding.daysHoursContainer, hourMap)
            }
        }
    }

    private fun addTimePickers(container: LinearLayout, initialData: Map<String, String>? = null) {
        // Use binding for inflater if needed, but LayoutInflater.from(this) is fine
        val inflater = LayoutInflater.from(this)
        val rowView = inflater.inflate(R.layout.time_picker_row, container, false) // Use R here
        val dayTextView = rowView.findViewById<TextView>(R.id.dayTextView) // Use R here
        val openingTextView = rowView.findViewById<TextView>(R.id.openingTextView) // Use R here
        val closingTextView = rowView.findViewById<TextView>(R.id.closingTextView) // Use R here
        val deleteButton = rowView.findViewById<Button>(R.id.deleteButton) // Use R here

        // Use R here for string array
        dayTextView.text = initialData?.get("day") ?: getDefaultDay(container.childCount)
        openingTextView.text = initialData?.get("startTime") ?: "Select Time"
        closingTextView.text = initialData?.get("endTime") ?: "Select Time"

        openingTextView.setOnClickListener { showTimePicker(openingTextView) }
        closingTextView.setOnClickListener { showTimePicker(closingTextView) }
        deleteButton.setOnClickListener {
            container.removeView(rowView)
            updateSaveButtonState()
        }

        container.addView(rowView)
    }

    private fun getDefaultDay(position: Int): String {
        val daysOfWeek = resources.getStringArray(R.array.days_of_week) // Use R here
        return daysOfWeek[position % daysOfWeek.size]
    }

    private fun showTimePicker(textViewToUpdate: TextView) {
        // ... (logic remains the same) ...
        val calendar = Calendar.getInstance()
        val existingTime = textViewToUpdate.text.toString()
        if (existingTime != "Select Time") {
            try {
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                calendar.time = sdf.parse(existingTime) ?: Date()
            } catch (e: Exception) { /* Use current time */ }
        }
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            updateTimeTextView(textViewToUpdate, calendar)
        }
        TimePickerDialog(this, timeSetListener, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
    }


    private fun updateTimeTextView(textView: TextView, calendar: Calendar) {
        // ... (logic remains the same) ...
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        textView.text = format.format(calendar.time)
        updateSaveButtonState()
    }

    private fun getAvailabilityHoursData(): List<Map<String, String>> {
        // Use binding
        val availabilityHours = mutableListOf<Map<String, String>>()
        for (i in 0 until binding.daysHoursContainer.childCount) {
            val rowView = binding.daysHoursContainer.getChildAt(i)
            // Use R here when finding views within the inflated row
            val dayTextView = rowView.findViewById<TextView>(R.id.dayTextView)
            val openingTextView = rowView.findViewById<TextView>(R.id.openingTextView)
            val closingTextView = rowView.findViewById<TextView>(R.id.closingTextView)

            val day = dayTextView.text.toString()
            val openingTime = openingTextView.text.toString()
            val closingTime = closingTextView.text.toString()

            if (openingTime != "Select Time" && closingTime != "Select Time") {
                val hourMap = mapOf(
                    "day" to day, "startTime" to openingTime, "endTime" to closingTime
                )
                availabilityHours.add(hourMap)
            }
        }
        return availabilityHours
    }

    private fun getSelectedGender(): String {
        // Use binding and R
        return when (binding.genderRadioGroup.checkedRadioButtonId) {
            R.id.radio_female -> "Female"
            R.id.radio_male -> "Male"
            else -> ""
        }
    }

    private fun validateInput(): Boolean {
        // Use binding
        var isValid = true
        binding.legalServicesLayout.error = null
        binding.spinnerAffiliation.selectedView?.let { (it as? TextView)?.error = null }
        binding.editFirstName.error = null
        binding.editLastName.error = null
        binding.editEmail.error = null
        binding.layoutLawFirmName.error = null
        binding.layoutLawFirmAddress.error = null

        if (binding.editFirstName.text.isNullOrBlank()) { binding.editFirstName.error = "First name required"; isValid = false }
        if (binding.editLastName.text.isNullOrBlank()) { binding.editLastName.error = "Last name required"; isValid = false }
        if (binding.editEmail.text.isNullOrBlank()) { binding.editEmail.error = "Email required"; isValid = false }

        if (binding.spinnerAffiliation.selectedItemPosition == 0) {
            Toast.makeText(this, "Please select an affiliation", Toast.LENGTH_SHORT).show()
            isValid = false
        }
        if (binding.editLegalServices.text.isNullOrBlank()) {
            binding.legalServicesLayout.error = "At least one specialization required"; isValid = false
        }

        val selectedAffiliation = binding.spinnerAffiliation.selectedItem.toString()
        if (selectedAffiliation == "Private Law Firm") {
            if (binding.editLawFirmName.text.isNullOrBlank()) { binding.layoutLawFirmName.error = "Law Firm name required"; isValid = false; }
            if (binding.editLawFirmAddress.text.isNullOrBlank()) { binding.layoutLawFirmAddress.error = "Law Firm address required"; isValid = false; }
        }
        // Add other essential validation checks

        return isValid
    }

    private fun updateSaveButtonState() {
        // Use binding
        val isPotentiallyValid = binding.editFirstName.text.isNotEmpty() &&
                binding.editLastName.text.isNotEmpty() &&
                binding.editEmail.text.isNotEmpty() &&
                binding.spinnerAffiliation.selectedItemPosition > 0 &&
                binding.editLegalServices.text.isNotEmpty() &&
                binding.genderRadioGroup.checkedRadioButtonId != -1 &&
                binding.availabilityRadioGroup.checkedRadioButtonId != -1

        binding.saveButton.isEnabled = isPotentiallyValid
        binding.saveButton.setBackgroundResource(
            if (isPotentiallyValid) R.drawable.button_enabled else R.drawable.button_disabled // Use R
        )
    }

    private fun selectSpinnerItemByValue(spinner: Spinner, value: String?) {
        // ... (logic remains the same) ...
        val adapter = spinner.adapter as? ArrayAdapter<String> ?: return
        if (value != null) {
            val position = adapter.getPosition(value)
            if (position >= 0) {
                spinner.setSelection(position)
            } else {
                Log.w(TAG, "Value '$value' not found in spinner ${spinner.id}")
                spinner.setSelection(0)
            }
        } else {
            spinner.setSelection(0)
        }
    }

    private fun saveProfileData() {
        // Use binding
        val userId = auth.currentUser?.uid ?: return

        showLoadingDialog("Saving Profile...")

        val updatedFirstName = binding.editFirstName.text.toString()
        val updatedMiddleName = binding.editMiddleName.text.toString().takeIf { it.isNotBlank() }
        val updatedLastName = binding.editLastName.text.toString()
        val updatedGender = getSelectedGender()
        val updatedAge = binding.editAge.text.toString().toIntOrNull() ?: 0
        val updatedEmail = binding.editEmail.text.toString()
        val updatedContact = binding.editContact.text.toString()
        val updatedAffiliation = binding.spinnerAffiliation.selectedItem.toString()
        val updatedRollNumber = binding.editRollNumber.text.toString()
        val updatedYearsExperience = binding.editYearsofexp.text.toString().toIntOrNull() ?: 0
        val updatedConsultationFee = binding.editConsultationFee.text.toString().toDoubleOrNull() ?: 0.0
        val updatedAvailabilityOption = selectedAvailabilityOption
        val updatedOfficeAddress = if (updatedAffiliation == "PAO") "Baguio Public Attorney's Office" else binding.editAddress.text.toString()
        val updatedLawFirmName = if(updatedAffiliation == "Private Law Firm") binding.editLawFirmName.text.toString().takeIf { it.isNotBlank() } else null
        val updatedLawFirmAddress = if(updatedAffiliation == "Private Law Firm") binding.editLawFirmAddress.text.toString().takeIf { it.isNotBlank() } else null
        val updatedSpecializationsData = parseAndStructureSpecializations(binding.editLegalServices.text.toString())
        val updatedHoursData = if (updatedAvailabilityOption == "Open on selected hours") getAvailabilityHoursData() else emptyList()

        val updatedDataMap: MutableMap<String, Any?> = mutableMapOf(
            "firstName" to updatedFirstName, "lastName" to updatedLastName, "age" to updatedAge,
            "gender" to updatedGender, "affiliation" to updatedAffiliation, "contactNumber" to updatedContact,
            "emailAddress" to updatedEmail, "officeAddress" to updatedOfficeAddress, "rollNumber" to updatedRollNumber,
            "legalSpecializations" to updatedSpecializationsData, "consultationFee" to updatedConsultationFee,
            "availabilityOption" to updatedAvailabilityOption, "daysAndHours" to updatedHoursData,
            "yearsOfExperience" to updatedYearsExperience
        )
        updatedMiddleName?.let { updatedDataMap["middleName"] = it }
        updatedLawFirmName?.let { updatedDataMap["lawFirmName"] = it }
        updatedLawFirmAddress?.let { updatedDataMap["lawFirmAddress"] = it }

        Log.d(TAG, "Updating Firestore with data: $updatedDataMap")

        firestore.collection("lawyers").document(userId)
            .update(updatedDataMap.filterValues { it != null })
            .addOnSuccessListener {
                dismissLoadingDialog()
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK, Intent()) // Send simple OK result
                finish()
            }
            .addOnFailureListener { e ->
                dismissLoadingDialog()
                Toast.makeText(this, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error updating Firestore", e)
            }
    }

    private fun parseAndStructureSpecializations(text: String): List<Map<String, Any>> {
        // ... (logic remains the same) ...
        val selectedSpecs = text.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() && allSpecializations.contains(it) }
            .distinct()

        return selectedSpecs.map { specName ->
            mapOf(
                "specialization" to specName,
                "subcategories" to (allSubcategories[specName] ?: emptyList())
            )
        }
    }

    // --- Loading Dialog Helpers ---
    private fun showLoadingDialog(message: String) {
        // Use binding if R cannot be resolved otherwise
        if (!::alertDialog.isInitialized || !alertDialog.isShowing) {
            val builder = AlertDialog.Builder(this)
            // Use binding to inflate if needed, otherwise LayoutInflater.from(this)
            val loadingView = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null)
            val loadingTextView = loadingView.findViewById<TextView>(R.id.loadingText) // Use R here
            loadingTextView.text = message
            builder.setView(loadingView)
            builder.setCancelable(false)
            alertDialog = builder.create()
            alertDialog.show()
        }
    }

    private fun dismissLoadingDialog() {
        if (::alertDialog.isInitialized && alertDialog.isShowing) {
            alertDialog.dismiss()
        }
    }
}