package com.example.tlfinal

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.widget.addTextChangedListener
import com.example.tlfinal.R
import com.example.tlfinal.databinding.LawyerEditProfileBinding
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

class LawyerEditProfileActivity : AppCompatActivity() {
    private lateinit var binding: LawyerEditProfileBinding
    private lateinit var firestore: FirebaseFirestore
    private var selectedAvailability = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle Back Button click
        binding.imageBack.setOnClickListener {
            finish()
        }

        firestore = FirebaseFirestore.getInstance()

        // Initialize MaterialAutoCompleteTextView and TextInputLayout
        val legalServicesTextView = findViewById<MaterialAutoCompleteTextView>(R.id.user_legal_services)
        val legalServicesTextInputLayout = findViewById<TextInputLayout>(R.id.legal_services_layout)
        //Get the days hours container
        val daysHoursContainer = findViewById<LinearLayout>(R.id.days_hours_container)
        //Get the add day button
        val addDayButton = findViewById<Button>(R.id.add_day_button)
        // Get the radio group for availability options
        val availabilityRadioGroup = findViewById<RadioGroup>(R.id.availabilityRadioGroup)
        val hoursLayout = findViewById<LinearLayout>(R.id.hours_layout)
        val genderRadioGroup = findViewById<RadioGroup>(R.id.genderRadioGroup)

        // Create list of available services
        val legalServices = arrayOf(
            "Civil Litigation",
            "Contract Drafting",
            "Corporate Law",
            "Criminal Litigation",
            "Family Law",
            "Immigration Law",
            "Intellectual Property Law",
            "Labor Law",
            "Notarial Services",
            "Legal Consulting",
            "Real Estate Law",
            "Tax Law"
        )
        // Create an adapter
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, legalServices)
        //Set the adapter to the MaterialAutoCompleteTextView
        legalServicesTextView.setAdapter(adapter)
        // Set onClick listener to display the dropdown options
        legalServicesTextView.setOnClickListener {
            legalServicesTextView.showDropDown()
        }
        // Set on item selected listener for multiselect
        legalServicesTextView.setOnItemClickListener{
                parent, view, position, id->
            val selectedItem = parent.getItemAtPosition(position) as String
            // Retrieve the text value from the MaterialAutoCompleteTextView
            val currentText = legalServicesTextView.text.toString()
            //Check if item has already been selected
            if(!currentText.contains(selectedItem)){
                //Append the current text with the selected item
                val newText = if(currentText.isNotEmpty()) "$currentText, $selectedItem" else selectedItem
                //Set the value of the MaterialAutoCompleteTextView
                legalServicesTextView.setText(newText)
            }
            legalServicesTextView.dismissDropDown()
        }

        // Set initial visibility of hours_layout based on the availabilityRadioGroup selection
        availabilityRadioGroup.setOnCheckedChangeListener{_, checkedId ->
            hoursLayout.visibility = if(checkedId == R.id.radio_open_selected_hours) View.VISIBLE else View.GONE
            when(checkedId){
                R.id.radio_no_hours -> selectedAvailability = "No Hours Available"
                R.id.radio_always_open -> selectedAvailability = "Always Open"
                R.id.radio_permanently_closed -> selectedAvailability = "Permanently Closed"
                R.id.radio_temporarily_closed -> selectedAvailability = "Temporarily Closed"
                R.id.radio_open_selected_hours -> selectedAvailability = "Open on selected hours"
            }
            updateSaveButtonState()
        }

        // Add first initial time pickers.
        addTimePickers(daysHoursContainer)

        //Handle the add button
        addDayButton.setOnClickListener {
            addTimePickers(daysHoursContainer)
        }

        // set onchecked change listener for the gender radio group
        genderRadioGroup.setOnCheckedChangeListener{ _, checkedId ->
            updateSaveButtonState()
        }
        // Enable save button only when the required fields are filled
        binding.userFullName.addTextChangedListener { updateSaveButtonState() }
        binding.userEmail.addTextChangedListener { updateSaveButtonState() }
        binding.userContact.addTextChangedListener { updateSaveButtonState() }
        binding.userAddress.addTextChangedListener { updateSaveButtonState() }
        binding.userAge.addTextChangedListener { updateSaveButtonState() }
        // Removed the addTextChangedListener for binding.userAvailability
        // Removed the addTextChangedListener for binding.userGender
        binding.userPrelaw.addTextChangedListener { updateSaveButtonState() }
        binding.userLawschool.addTextChangedListener { updateSaveButtonState() }
        binding.userYearsofexp.addTextChangedListener { updateSaveButtonState() }
        binding.userConsultationFee.addTextChangedListener { updateSaveButtonState() }

        // Save Button Click Listener
        binding.saveButton.setOnClickListener {
            val updatedFullName = binding.userFullName.text.toString()
            val updatedEmail = binding.userEmail.text.toString()
            val updatedContact = binding.userContact.text.toString()
            val updatedAddress = binding.userAddress.text.toString()
            val updatedGender = getSelectedGender()
            val updatedAge = binding.userAge.text.toString().toIntOrNull() ?: 0
            val updatedLegalServices = legalServicesTextView.text.toString() // Get Selected Legal Services
            val updatedPrelawDegree = binding.userPrelaw.text.toString()
            val updatedLawSchool = binding.userLawschool.text.toString()
            val updatedYearsOfExperience = binding.userYearsofexp.text.toString().toIntOrNull() ?: 0
            val updatedConsultationFee = binding.userConsultationFee.text.toString().toIntOrNull() ?: 0
            val updatedAvailability = selectedAvailability
            val updatedHours = getAvailabilityHours(daysHoursContainer)
            val hoursJson = convertHoursToJsonString(updatedHours) // Convert hours to JSON String

            if (updatedFullName.isNotBlank() && updatedEmail.isNotBlank()) {
                // Update Firestore
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                userId?.let {
                    val updatedData = mapOf(
                        "fullName" to updatedFullName,
                        "email" to updatedEmail,
                        "contactNo" to updatedContact,
                        "address" to updatedAddress,
                        "gender" to updatedGender,
                        "age" to updatedAge,
                        "availability" to updatedAvailability,
                        "legalservices" to updatedLegalServices,
                        "prelawdegree" to updatedPrelawDegree,
                        "lawschool" to updatedLawSchool,
                        "yearsofexp" to updatedYearsOfExperience,
                        "consultationfee" to updatedConsultationFee,
                        "hours" to updatedHours
                    )
                    firestore.collection("lawyers").document(it)
                        .update(updatedData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()

                            // Pass updated data back to the profile screen
                            val resultIntent = Intent()
                            resultIntent.putExtra("fullName", updatedFullName)
                            resultIntent.putExtra("email", updatedEmail)
                            resultIntent.putExtra("contactNo", updatedContact)
                            resultIntent.putExtra("address", updatedAddress)
                            resultIntent.putExtra("gender", updatedGender)
                            resultIntent.putExtra("age", updatedAge)
                            resultIntent.putExtra("availability", updatedAvailability)
                            resultIntent.putExtra("legalservices", updatedLegalServices)
                            resultIntent.putExtra("prelawdegree", updatedPrelawDegree)
                            resultIntent.putExtra("lawschool", updatedLawSchool)
                            resultIntent.putExtra("yearsofexp", updatedYearsOfExperience)
                            resultIntent.putExtra("consultationfee", updatedConsultationFee)
                            resultIntent.putExtra("hours", hoursJson) //Pass Json string
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
    private fun addTimePickers(container: LinearLayout) {
        val inflater = layoutInflater
        val rowView = inflater.inflate(R.layout.time_picker_row, container, false)
        // Get views from the row
        val dayTextView = rowView.findViewById<TextView>(R.id.dayTextView)
        val openingTextView = rowView.findViewById<TextView>(R.id.openingTextView)
        val closingTextView = rowView.findViewById<TextView>(R.id.closingTextView)
        val deleteButton = rowView.findViewById<Button>(R.id.deleteButton)

        // Create a new calendar for getting the current time
        val calendar = Calendar.getInstance()

        // Display a time picker when the opening time TextView is clicked
        openingTextView.setOnClickListener{
            val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                updateTimeTextView(openingTextView, calendar)

            }
            TimePickerDialog(this, timeSetListener, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }

        // Display a time picker when the closing time TextView is clicked
        closingTextView.setOnClickListener{
            val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                updateTimeTextView(closingTextView, calendar)

            }
            TimePickerDialog(this, timeSetListener, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }

        // Handle delete button
        deleteButton.setOnClickListener {
            container.removeView(rowView)
            updateSaveButtonState()

        }
        // set the day text view to the day of the week.
        val daysOfWeek = resources.getStringArray(R.array.days_of_week)
        val dayPosition = container.childCount % daysOfWeek.size
        dayTextView.text = daysOfWeek[dayPosition]
        container.addView(rowView)
    }
    private fun updateTimeTextView(textView: TextView, calendar: Calendar){
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        textView.text = format.format(calendar.time)

        updateSaveButtonState()
    }
    private fun getAvailabilityHours(container: LinearLayout): MutableList<Map<String,String>> {
        val availabilityHours = mutableListOf<Map<String,String>>()
        for(i in 0 until container.childCount)
        {
            val rowView = container.getChildAt(i)
            val dayTextView = rowView.findViewById<TextView>(R.id.dayTextView)
            val openingTextView = rowView.findViewById<TextView>(R.id.openingTextView)
            val closingTextView = rowView.findViewById<TextView>(R.id.closingTextView)
            val day = dayTextView.text.toString()
            val openingTime = openingTextView.text.toString()
            val closingTime = closingTextView.text.toString()
            val hour = mapOf(
                "day" to day,
                "openingTime" to openingTime,
                "closingTime" to closingTime
            )
            availabilityHours.add(hour)
        }
        return availabilityHours
    }
    private fun convertHoursToJsonString(hours:  MutableList<Map<String,String>>): String {
        val jsonArray = JSONArray()
        for(hour in hours){
            val jsonObject = JSONObject()
            jsonObject.put("day", hour["day"])
            jsonObject.put("openingTime", hour["openingTime"])
            jsonObject.put("closingTime", hour["closingTime"])
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }
    private fun getSelectedGender(): String {
        val selectedId = binding.genderRadioGroup.checkedRadioButtonId
        return when (selectedId) {
            R.id.radio_female -> "Female"
            R.id.radio_male -> "Male"
            else -> "" // Default, should not happen
        }
    }
    private fun updateSaveButtonState() {
        val isFullNameFilled = binding.userFullName.text.isNotEmpty()
        val isEmailFilled = binding.userEmail.text.isNotEmpty()
        val isContactFilled = binding.userContact.text.isNotEmpty()
        val isAddressFilled = binding.userAddress.text.isNotEmpty()
        val isGenderSelected = binding.genderRadioGroup.checkedRadioButtonId != -1
        val isAgeFilled = binding.userAge.text.isNotEmpty()
        val isSpecializationFilled = binding.userLegalServices.text.isNotEmpty()
        val isPrelawFilled = binding.userPrelaw.text.isNotEmpty()
        val isLawSchoolFilled = binding.userLawschool.text.isNotEmpty()
        val isYearOfExpFilled = binding.userYearsofexp.text.isNotEmpty()
        val isConsultationFilled = binding.userConsultationFee.text.isNotEmpty()
        val isAvailabilitySelected = selectedAvailability.isNotEmpty() || binding.availabilityRadioGroup.checkedRadioButtonId != -1


        // Enable button if all required fields are filled
        binding.saveButton.isEnabled = isFullNameFilled && isEmailFilled && isContactFilled && isAddressFilled
                && isAgeFilled && isSpecializationFilled
                && isPrelawFilled && isLawSchoolFilled && isYearOfExpFilled && isConsultationFilled
                && isAvailabilitySelected && isGenderSelected
        binding.saveButton.setBackgroundResource(
            if (binding.saveButton.isEnabled) R.drawable.button_enabled else R.drawable.button_disabled
        )
    }
}