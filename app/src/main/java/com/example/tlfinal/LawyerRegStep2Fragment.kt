package com.example.tlfinal

import android.os.Bundle
import android.text.Editable
import android.text.InputType // Import InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button // Import Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.MultiAutoCompleteTextView // Ensure this import!
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.tlfinal.databinding.FragmentLawyerRegStep2Binding


class LawyerRegStep2Fragment : Fragment() {

    private var _binding: FragmentLawyerRegStep2Binding? = null
    private val binding get() = _binding!!
    private val viewModel: LawyerRegistrationViewModel by activityViewModels()

    // --- Define Max Length Constants for Step 2 ---
    companion object {
        const val MAX_ROLL_NUMBER_LENGTH = 20
        const val MAX_FIRM_NAME_LENGTH = 100
        const val MAX_FIRM_ADDRESS_LENGTH = 250
        const val MAX_CONSULTATION_FEE = 10000.00
        const val MIN_CONSULTATION_FEE = 0.00
    }
    // --- End Constants ---

    private val specializations = listOf(
        "Family Law", "Criminal Law", "Civil Law", "Labor Law", "Immigration Law",
        "Consumer Protection Law", "Real Estate & Property Law", "Business & Corporate Law",
        "Tax Law", "Health Law", "Environmental Law", "Public Assistance & Welfare Law",
        "Traffic and Transportation Law"
    )

    private val subcategories = mapOf(
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

    private lateinit var specializationAdapter: ArrayAdapter<String>
    // Removed selectedSpecializations list - derive directly from VM or views when needed

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLawyerRegStep2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- Setup Adapters and Listeners ---
        specializationAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, specializations)
        // Static Specialization Dropdown
        binding.specializationDropdown.setAdapter(specializationAdapter)
        binding.specializationDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedSpecialization = specializationAdapter.getItem(position) ?: ""
            showSubcategories(selectedSpecialization, binding.subcategoriesDropdown) // Target static subcat dropdown
            // binding.subcategoriesDropdown.setText("", false) // Don't clear automatically
            updateViewModelSpecializations()
        }
        binding.specializationDropdown.addTextChangedListener { updateViewModelSpecializations() } // Update on text change too


        // Static Subcategories Dropdown
        (binding.subcategoriesDropdown as? MultiAutoCompleteTextView)?.apply {
            setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
            addTextChangedListener { updateViewModelSpecializations() } // Update on text change
        }


        // Button to Add More Specializations
        binding.addCategoryButton.setOnClickListener { addSpecializationCategory() }

        // Add TextWatchers for simple fields
        binding.rollNumberEditText.addTextChangedListener(textWatcher)
        binding.consultationFeeEditText.addTextChangedListener(textWatcher)
        binding.lawFirmAddressEditText.addTextChangedListener(textWatcher)
        binding.lawFirmNameEditText.addTextChangedListener(textWatcher)

        // Availability Setup
        setupAvailabilityRadioGroup()
        // Button to Add Day/Hour Row - Use the correct ID from XML
        binding.addDayButton.setOnClickListener { addDay() }

        // Affiliation Change Listener
        binding.affiliationRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val isPrivate = (checkedId == R.id.radioPrivate)
            binding.privateLawFirmDetails.visibility = if (isPrivate) View.VISIBLE else View.GONE
            if (!isPrivate) {
                binding.lawFirmNameEditText.text = null
                binding.lawFirmAddressEditText.text = null
                binding.lawFirmNameEditText.error = null
                binding.lawFirmAddressEditText.error = null
            }
            updateViewModel()
        }

        // Restore data AFTER setting up listeners
        restoreFromViewModel()
    }

    private fun restoreFromViewModel() {
        viewModel.lawyerData.value?.let { data ->
            binding.rollNumberEditText.setText(data.rollNumber)

            // Restore specializations
            rebuildSpecializationViews() // Rebuild dynamic views based on VM data

            binding.consultationFeeEditText.setText(data.consultationFee?.toString() ?: "")

            // Restore Availability
            data.availabilityOption?.let { option ->
                val isSelectedHours = (option == LawyerRegistrationViewModel.AvailabilityOption.OPEN_SELECTED_HOURS)
                binding.hoursLayout.visibility = if (isSelectedHours) View.VISIBLE else View.GONE
                when (option) {
                    LawyerRegistrationViewModel.AvailabilityOption.NO_HOURS -> binding.radioNoHours.isChecked = true
                    LawyerRegistrationViewModel.AvailabilityOption.ALWAYS_OPEN -> binding.radioAlwaysOpen.isChecked = true
                    LawyerRegistrationViewModel.AvailabilityOption.PERMANENTLY_CLOSED -> binding.radioPermanentlyClosed.isChecked = true
                    LawyerRegistrationViewModel.AvailabilityOption.TEMPORARILY_CLOSED -> binding.radioTemporarilyClosed.isChecked = true
                    LawyerRegistrationViewModel.AvailabilityOption.OPEN_SELECTED_HOURS -> {
                        binding.radioOpenSelectedHours.isChecked = true
                        rebuildAvailability() // Rebuild the day/hour rows
                    }
                }
            } ?: binding.availabilityRadioGroup.clearCheck()

            // Restore Affiliation
            val isPrivate = (data.affiliation == "Private Law Firm")
            binding.privateLawFirmDetails.isVisible = isPrivate
            when (data.affiliation) {
                "PAO" -> binding.radioPAO.isChecked = true
                "Private Law Firm" -> {
                    binding.radioPrivate.isChecked = true
                    binding.lawFirmNameEditText.setText(data.lawFirmName)
                    binding.lawFirmAddressEditText.setText(data.lawFirmAddress)
                }
                else -> binding.affiliationRadioGroup.clearCheck()
            }
        }
    }

    private fun rebuildAvailability() {
        binding.daysHoursContainer.removeAllViews() // Clear existing rows
        viewModel.lawyerData.value?.daysAndHours?.forEach { dayHour ->
            addDay(dayHour.day, dayHour.startTime, dayHour.endTime) // Add row with data
        }
    }

    // Adds a *new*, empty specialization row dynamically
    private fun addSpecializationCategory() {
        // Inflate the layout for a dynamic specialization entry
        val newSpecializationLayout = LayoutInflater.from(requireContext()).inflate(
            R.layout.item_specialization_category, // Ensure this layout exists
            binding.root.findViewById(android.R.id.content), // Use root view group temporarily for inflation params
            false // Don't attach to root yet
        ) as LinearLayout

        // Find views within the inflated layout (IDs must match item_specialization_category.xml)
        val specializationDropdown = newSpecializationLayout.findViewById<AutoCompleteTextView>(R.id.specializationDropdown)
        val subcategoriesDropdown = newSpecializationLayout.findViewById<MultiAutoCompleteTextView>(R.id.subcategoriesDropdown)
        val removeButton = newSpecializationLayout.findViewById<View>(R.id.removeCategoryButton)

        // Setup specialization dropdown
        specializationDropdown.setAdapter(specializationAdapter)
        specializationDropdown.setOnItemClickListener { _, _, position, _ ->
            val selected = specializationAdapter.getItem(position) ?: ""
            showSubcategories(selected, subcategoriesDropdown) // Target this row's subcat dropdown
            updateViewModelSpecializations()
        }
        specializationDropdown.addTextChangedListener { updateViewModelSpecializations() }


        // Setup subcategories dropdown
        (subcategoriesDropdown as? MultiAutoCompleteTextView)?.apply {
            setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
            addTextChangedListener { updateViewModelSpecializations() }
        }


        // Setup remove button
        removeButton.setOnClickListener {
            // Get the parent of the button -> parent of that -> the LinearLayout row
            val parentLayout = it.parent?.parent as? LinearLayout
            (parentLayout?.parent as? ViewGroup)?.removeView(parentLayout) // Remove the whole row from its container
            updateViewModelSpecializations() // Recalculate and update VM
        }

        // Add the new layout to the main LinearLayout, BEFORE the addCategoryButton
        val mainLinearLayout = binding.root.children.firstOrNull() as? LinearLayout // Get the main container
        mainLinearLayout?.let { container ->
            val buttonIndex = container.indexOfChild(binding.addCategoryButton)
            if (buttonIndex != -1) {
                container.addView(newSpecializationLayout, buttonIndex)
            } else {
                container.addView(newSpecializationLayout) // Add at end if button not found (fallback)
            }
        }

        updateViewModelSpecializations() // Update VM after adding the row structure
    }

    // Rebuilds all dynamic specialization rows based on ViewModel data
    private fun rebuildSpecializationViews() {
        val mainLinearLayout = binding.root.children.firstOrNull() as? LinearLayout ?: return // Get main container

        // --- Clear existing *dynamic* specialization rows first ---
        // Iterate backwards to avoid index issues while removing
        for (i in mainLinearLayout.childCount - 1 downTo 0) {
            val child = mainLinearLayout.getChildAt(i)
            // Identify dynamic rows (e.g., by checking if they contain the remove button)
            if (child is LinearLayout && child.findViewById<View>(R.id.removeCategoryButton) != null) {
                mainLinearLayout.removeViewAt(i)
            }
        }
        // --- End clearing ---


        val specList = viewModel.lawyerData.value?.legalSpecializations ?: listOf()

        if (specList.isNotEmpty()) {
            // Handle the *first* specialization using the static fields in the XML
            val firstSpec = specList[0]
            binding.specializationDropdown.setText(firstSpec.first, false)
            showSubcategories(firstSpec.first, binding.subcategoriesDropdown) // Target static subcat
            (binding.subcategoriesDropdown as? MultiAutoCompleteTextView)?.setText(firstSpec.second.joinToString(", "), false)

            // Add the rest dynamically
            specList.drop(1).forEach { (specialization, subcategoriesList) ->
                addDynamicSpecializationRow(specialization, subcategoriesList)
            }
        } else {
            // If list is empty, clear the static fields
            binding.specializationDropdown.setText("", false)
            binding.subcategoriesDropdown.setText("", false)
            showSubcategories("", binding.subcategoriesDropdown) // Clear/hide static subcat
        }
    }

    // Adds one dynamic specialization row *with pre-filled data*
    private fun addDynamicSpecializationRow(specialization: String, subcategoriesList: List<String>) {
        // Inflate the layout
        val newSpecializationLayout = LayoutInflater.from(requireContext()).inflate(
            R.layout.item_specialization_category,
            binding.root.findViewById(android.R.id.content),
            false
        ) as LinearLayout

        // Find views
        val specializationDropdown = newSpecializationLayout.findViewById<AutoCompleteTextView>(R.id.specializationDropdown)
        val subcategoriesDropdown = newSpecializationLayout.findViewById<MultiAutoCompleteTextView>(R.id.subcategoriesDropdown)
        val removeButton = newSpecializationLayout.findViewById<View>(R.id.removeCategoryButton)

        // Setup specialization dropdown
        specializationDropdown.setAdapter(specializationAdapter)
        specializationDropdown.setText(specialization, false) // Set initial text
        specializationDropdown.setOnItemClickListener { _, _, position, _ ->
            val selected = specializationAdapter.getItem(position) ?: ""
            showSubcategories(selected, subcategoriesDropdown)
            updateViewModelSpecializations()
        }
        specializationDropdown.addTextChangedListener { updateViewModelSpecializations() }


        // Setup subcategories dropdown
        (subcategoriesDropdown as? MultiAutoCompleteTextView)?.apply {
            setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
            // Show subcategories relevant to the initial specialization
            showSubcategories(specialization, this) // Pass 'this' (the dropdown itself)
            setText(subcategoriesList.joinToString(", "), false) // Set initial text
            addTextChangedListener { updateViewModelSpecializations() }
        }


        // Setup remove button
        removeButton.setOnClickListener {
            val parentLayout = it.parent?.parent as? LinearLayout
            (parentLayout?.parent as? ViewGroup)?.removeView(parentLayout)
            updateViewModelSpecializations()
        }

        // Add the new layout to the main LinearLayout, BEFORE the addCategoryButton
        val mainLinearLayout = binding.root.children.firstOrNull() as? LinearLayout
        mainLinearLayout?.let { container ->
            val buttonIndex = container.indexOfChild(binding.addCategoryButton)
            if (buttonIndex != -1) {
                container.addView(newSpecializationLayout, buttonIndex)
            } else {
                container.addView(newSpecializationLayout)
            }
        }
    }


    // Shows relevant subcategories in the target dropdown
    private fun showSubcategories(specialization: String, targetDropdown: MultiAutoCompleteTextView) { // Always require target
        val subs = subcategories[specialization] ?: emptyList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subs)

        targetDropdown.setAdapter(adapter)
        // Set tokenizer just in case
        targetDropdown.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())

        // Control visibility of the *static* subcategory layout based on the *static* specialization
        if (targetDropdown == binding.subcategoriesDropdown) {
            binding.subcategoriesLayout.visibility = if (subs.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }


    private fun setupAvailabilityRadioGroup() {
        binding.availabilityRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val showHours = (checkedId == R.id.radio_open_selected_hours)
            binding.hoursLayout.visibility = if (showHours) View.VISIBLE else View.GONE
            if (!showHours) {
                binding.daysHoursContainer.removeAllViews() // Clear dynamic day/hour rows
            } else if (binding.daysHoursContainer.childCount == 0 && showHours) {
                // If switching to selected hours and it's empty, add one default row
                addDay()
            }
            updateViewModel()
        }
    }

    // Adds one row for Day/Time selection programmatically
    private fun addDay(day: String? = null, startTime: String? = null, endTime: String? = null) {
        val dayLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 8, 0, 8) // Add some vertical padding between rows
        }

        // --- Create Views Programmatically ---
        val dayDropdown = AutoCompleteTextView(requireContext()).apply {
            // Unique ID is not strictly necessary if accessed by index, but can help debugging
            // id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8 // Add spacing
            }
            hint = "Day"
            val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, daysOfWeek))
            if (day != null) setText(day, false)
            addTextChangedListener(textWatcher) // Add listener to update VM
        }

        val startTimeEditText = EditText(requireContext()).apply {
            // id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            hint = "Start (HH:MM)" // Hint for format
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            if (startTime != null) setText(startTime)
            addTextChangedListener(textWatcher)
        }

        val endTimeEditText = EditText(requireContext()).apply {
            // id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            hint = "End (HH:MM)"
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
            if (endTime != null) setText(endTime)
            addTextChangedListener(textWatcher)
        }

        // Simple Remove Button (using Text for now)
        val removeDayButton = Button(requireContext(), null, android.R.attr.buttonStyleSmall).apply {
            // id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "X" // Simple remove indicator
            minWidth = 0 // Override default min width
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(16,0,16,0) // Adjust padding
            // Add background tint or style if needed
            setOnClickListener {
                binding.daysHoursContainer.removeView(dayLayout) // Remove the specific row layout
                updateViewModel() // Update VM after removing
            }
        }
        // --- End Create Views ---


        // --- Add Views to Layout ---
        dayLayout.addView(dayDropdown)
        dayLayout.addView(startTimeEditText)
        dayLayout.addView(endTimeEditText)
        dayLayout.addView(removeDayButton) // Add remove button to the row
        // --- End Add Views ---

        binding.daysHoursContainer.addView(dayLayout) // Add the row to the container

        // updateViewModel() // Called by textWatcher now
    }

    // Generic TextWatcher - updates the whole VM state
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { updateViewModel() }
    }

    // Gathers all specialization data from static and dynamic views
    private fun updateViewModelSpecializations() {
        val collectedSpecializations = mutableListOf<Pair<String, List<String>>>()
        val mainLinearLayout = binding.root.children.firstOrNull() as? LinearLayout ?: return

        // 1. Get data from the static/first row
        val mainSpec = binding.specializationDropdown.text.toString().trim()
        if (mainSpec.isNotEmpty()) {
            val mainSubs = (binding.subcategoriesDropdown as? MultiAutoCompleteTextView)?.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            collectedSpecializations.add(mainSpec to mainSubs)
        }

        // 2. Get data from dynamically added rows
        mainLinearLayout.children.forEach { view ->
            // Check if this child is a dynamic specialization row (contains the remove button)
            if (view is LinearLayout && view.findViewById<View>(R.id.removeCategoryButton) != null) {
                val specDropdown = view.findViewById<AutoCompleteTextView>(R.id.specializationDropdown)
                val subDropdown = view.findViewById<MultiAutoCompleteTextView>(R.id.subcategoriesDropdown)

                val spec = specDropdown?.text.toString().trim()
                if (spec.isNotEmpty()) {
                    val subs = subDropdown?.text.toString()
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    // Avoid adding duplicates just in case (though UI should prevent this)
                    if (!collectedSpecializations.any { it.first == spec }) {
                        collectedSpecializations.add(spec to subs)
                    }
                }
            }
        }

        // Update the ViewModel
        val data = viewModel.lawyerData.value ?: LawyerRegistrationViewModel.LawyerData()
        // Only update if the list actually changed to prevent observer loops
        if (data.legalSpecializations != collectedSpecializations) {
            viewModel.lawyerData.value = data.copy(legalSpecializations = collectedSpecializations)
        }
    }

    // Updates the entire ViewModel state based on current view values
    private fun updateViewModel() {
        // Ensure specializations are collected first (might be triggered by other listeners)
        updateViewModelSpecializations()

        val selectedAvailabilityOption = when (binding.availabilityRadioGroup.checkedRadioButtonId) {
            R.id.radio_no_hours -> LawyerRegistrationViewModel.AvailabilityOption.NO_HOURS
            R.id.radio_always_open -> LawyerRegistrationViewModel.AvailabilityOption.ALWAYS_OPEN
            R.id.radio_permanently_closed -> LawyerRegistrationViewModel.AvailabilityOption.PERMANENTLY_CLOSED
            R.id.radio_temporarily_closed -> LawyerRegistrationViewModel.AvailabilityOption.TEMPORARILY_CLOSED
            R.id.radio_open_selected_hours -> LawyerRegistrationViewModel.AvailabilityOption.OPEN_SELECTED_HOURS
            else -> null
        }

        // Collect Day/Hours Data only if the correct option is selected
        val collectedDaysAndHours = mutableListOf<LawyerRegistrationViewModel.DayHours>()
        if (selectedAvailabilityOption == LawyerRegistrationViewModel.AvailabilityOption.OPEN_SELECTED_HOURS) {
            binding.daysHoursContainer.children.forEach { view ->
                if (view is LinearLayout) {
                    // Access views within the programmatically created LinearLayout row
                    val dayDropdown = view.getChildAt(0) as? AutoCompleteTextView
                    val startTimeEditText = view.getChildAt(1) as? EditText
                    val endTimeEditText = view.getChildAt(2) as? EditText

                    val day = dayDropdown?.text.toString().trim()
                    val startTime = startTimeEditText?.text.toString().trim()
                    val endTime = endTimeEditText?.text.toString().trim()

                    if (day.isNotEmpty() && startTime.isNotEmpty() && endTime.isNotEmpty()) {
                        collectedDaysAndHours.add(LawyerRegistrationViewModel.DayHours(day, startTime, endTime))
                    }
                }
            }
        }

        val selectedAffiliation = when (binding.affiliationRadioGroup.checkedRadioButtonId) {
            R.id.radioPAO -> "PAO"
            R.id.radioPrivate -> "Private Law Firm"
            else -> null
        }

        val currentData = viewModel.lawyerData.value ?: LawyerRegistrationViewModel.LawyerData()
        // Create the new state
        val newState = currentData.copy(
            rollNumber = binding.rollNumberEditText.text.toString().trim(),
            consultationFee = binding.consultationFeeEditText.text.toString().toDoubleOrNull(),
            availabilityOption = selectedAvailabilityOption,
            daysAndHours = collectedDaysAndHours,
            legalSpecializations = viewModel.lawyerData.value?.legalSpecializations ?: emptyList(), // Use already updated list
            affiliation = selectedAffiliation,
            lawFirmName = if (selectedAffiliation == "Private Law Firm") binding.lawFirmNameEditText.text.toString().trim() else null,
            lawFirmAddress = if (selectedAffiliation == "Private Law Firm") binding.lawFirmAddressEditText.text.toString().trim() else null
        )
        // Only update ViewModel if state actually changed
        if (newState != currentData) {
            viewModel.lawyerData.value = newState
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Validation function remains largely the same, but references updated constants/logic
    fun validate(): Boolean {
        var isValid = true
        updateViewModel() // Ensure VM has latest data before validating
        val currentData = viewModel.lawyerData.value ?: LawyerRegistrationViewModel.LawyerData()


        // --- Roll Number ---
        val rollNumber = binding.rollNumberEditText.text.toString().trim() // Use direct text for immediate error
        if (rollNumber.isBlank()) {
            binding.rollNumberEditText.error = "Roll Number is Required"
            isValid = false
        } else if (rollNumber.length > MAX_ROLL_NUMBER_LENGTH) {
            binding.rollNumberEditText.error = "Maximum ${MAX_ROLL_NUMBER_LENGTH} characters"
            isValid = false
        } else {
            binding.rollNumberEditText.error = null
        }

        // --- Legal Specializations (Validate based on VM data) ---
        val currentSpecializations = currentData.legalSpecializations
        if (currentSpecializations.isEmpty() || currentSpecializations.all { it.first.isBlank() }) {
            binding.specializationDropdown.error = "Select at least one specialization"
            // Toast.makeText(requireContext(), "Please select at least one legal specialization", Toast.LENGTH_SHORT).show() // Toast optional if error shows
            isValid = false
        } else {
            binding.specializationDropdown.error = null // Clear error on static dropdown
        }
        // Check subcategories
        if (currentSpecializations.any { it.first.isNotBlank() && it.second.isEmpty() }) {
            (binding.subcategoriesDropdown as? MultiAutoCompleteTextView)?.error = "Select subcategories"
            // Toast.makeText(requireContext(), "Please select subcategories for all chosen specializations", Toast.LENGTH_SHORT).show() // Optional Toast
            isValid = false;
        } else {
            (binding.subcategoriesDropdown as? MultiAutoCompleteTextView)?.error = null // Clear error on static dropdown
            // You might want to loop through dynamic rows and clear their errors too if setting them
        }


        // --- Consultation Fee ---
        val feeStr = binding.consultationFeeEditText.text.toString() // Use direct text
        val fee = feeStr.toDoubleOrNull()
        if (feeStr.isBlank()) {
            binding.consultationFeeEditText.error = "Consultation Fee is Required"
            isValid = false
        } else if (fee == null || fee < MIN_CONSULTATION_FEE || fee > MAX_CONSULTATION_FEE ) {
            binding.consultationFeeEditText.error = "Enter a valid fee (${MIN_CONSULTATION_FEE}-${MAX_CONSULTATION_FEE})"
            isValid = false
        } else {
            binding.consultationFeeEditText.error = null
        }

        // --- Availability (Validate based on VM data) ---
        val selectedAvailabilityOption = currentData.availabilityOption
        if (selectedAvailabilityOption == null) {
            Toast.makeText(requireContext(), "Please select availability", Toast.LENGTH_SHORT).show()
            isValid = false;
        } else if (selectedAvailabilityOption == LawyerRegistrationViewModel.AvailabilityOption.OPEN_SELECTED_HOURS) {
            val daysHoursList = currentData.daysAndHours
            // Check if list is empty OR if any entry has blank fields
            if (daysHoursList.isEmpty() || daysHoursList.any { it.day.isBlank() || it.startTime.isBlank() || it.endTime.isBlank()}) {
                isValid = false
                Toast.makeText(requireContext(), "Please add valid days and times for selected hours", Toast.LENGTH_LONG).show()
                // Highlight the container or first invalid row (more complex UI)
            }
        }

        // --- Affiliation (Validate based on VM data) ---
        val selectedAffiliation = currentData.affiliation
        if (selectedAffiliation == null) {
            Toast.makeText(requireContext(), "Please select Affiliation", Toast.LENGTH_SHORT).show()
            isValid = false
        } else if (selectedAffiliation == "Private Law Firm") {
            // --- Law Firm Name (if private) ---
            val firmName = binding.lawFirmNameEditText.text.toString().trim() // Use direct text
            if (firmName.isBlank()) {
                binding.lawFirmNameEditText.error = "Law Firm Name is required"
                isValid = false
            } else if (firmName.length > MAX_FIRM_NAME_LENGTH) {
                binding.lawFirmNameEditText.error = "Maximum ${MAX_FIRM_NAME_LENGTH} characters"
                isValid = false
            } else {
                binding.lawFirmNameEditText.error = null
            }

            // --- Law Firm Address (if private) ---
            val firmAddress = binding.lawFirmAddressEditText.text.toString().trim() // Use direct text
            if (firmAddress.isBlank()) {
                binding.lawFirmAddressEditText.error = "Law Firm Address is required"
                isValid = false
            } else if (firmAddress.length > MAX_FIRM_ADDRESS_LENGTH) {
                binding.lawFirmAddressEditText.error = "Maximum ${MAX_FIRM_ADDRESS_LENGTH} characters"
                isValid = false
            } else {
                binding.lawFirmAddressEditText.error = null
            }
        } else {
            // Clear errors if not private
            binding.lawFirmNameEditText.error = null
            binding.lawFirmAddressEditText.error = null
        }

        return isValid
    }
}
