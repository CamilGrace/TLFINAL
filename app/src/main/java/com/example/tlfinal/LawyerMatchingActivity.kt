package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView // Keep for type casting if needed
import android.widget.MultiAutoCompleteTextView // Keep for tokenizer
import android.widget.TextView // Keep for dialog
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tlfinal.databinding.LawyerMatchingBinding // Use View Binding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class LawyerMatchingActivity : AppCompatActivity() {

    private lateinit var binding: LawyerMatchingBinding // Use View Binding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var alertDialog: AlertDialog
    private lateinit var huggingFaceApi: HuggingFaceApi
    private lateinit var apiKey: String
    private var availableLegalServices = ""

    // Data for dropdowns
    private val specializations = listOf(
        "Family Law", "Criminal Law", "Civil Law", "Labor Law", "Immigration Law",
        "Consumer Protection Law", "Real Estate & Property Law", "Business & Corporate Law",
        "Tax Law", "Health Law", "Environmental Law", "Public Assistance & Welfare Law",
        "Traffic and Transportation Law"
    )
    private val subcategoriesMap = mapOf(
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

    private var currentMatchedCategory: String? = null // To store result of first API call
    private var currentUserInputDetails: String = "" // To store combined user input for second API call

    private lateinit var specializationAdapter: ArrayAdapter<String>
    private lateinit var subcategoryAdapter: ArrayAdapter<String> // Adapter for subcategories

    companion object { // <<< ENSURE THIS BLOCK EXISTS
        private const val TAG = "LawyerMatchingActivity" // Define TAG here
    }

    private var affiliationType: String = "Private"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LawyerMatchingBinding.inflate(layoutInflater) // Inflate binding
        setContentView(binding.root) // Set content view from binding

        affiliationType = intent.getStringExtra(AffiliationSelectionActivity.EXTRA_AFFILIATION_TYPE) ?: "Private" // Default if missing
        Log.d(TAG, "Received affiliation type: $affiliationType")

        setupListeners()

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        apiKey = getApiKeyFromProperties()
        setupRetrofit()

        setupSpecializationDropdown()
        setupSubcategoryDropdown()

        fetchAvailableLegalServices { services ->
            availableLegalServices = services
            Log.d("LawyerMatchingActivity", "Available Services: $availableLegalServices")
            updateButtonState() // Update button state after fetching services
        }

        // Add TextWatchers using binding
        binding.editTextAdditionalDetails.addTextChangedListener(textWatcher)
        binding.subcategoriesDropdown.addTextChangedListener(textWatcher) // Correct access

        updateButtonState() // Set initial button state
    }

    private fun setupListeners() {
        binding.imageBack.setOnClickListener { finish() }
        binding.helpButton.setOnClickListener { showHelpDialog() }
        binding.btnFindMyLawyer.setOnClickListener { findLawyer() }

        binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            if (item.itemId == binding.bottomNavigation.selectedItemId) {
                 return@setOnNavigationItemSelectedListener false
            }
                when (item.itemId) {
                    R.id.bottom_message -> {
                        startActivity(Intent(this, InboxActivity::class.java)) // Or LawyerInboxActivity?
                        overridePendingTransition(0, 0)
                        finishAffinity() // Finish this and potentially others to go "home"
                        true
                    }
                    R.id.bottom_home -> {
                        // Navigate back to the main Dashboard Activity
                        startActivity(Intent(this, ClientDashboardActivity::class.java)) // Or LawyerDashboardActivity?
                        overridePendingTransition(0, 0)
                        finishAffinity() // Clear stack back to dashboard
                        true
                    }
                    R.id.bottom_settings -> {
                        startActivity(Intent(this, ClientSettingsActivity::class.java)) // Or LawyerSettingsActivity?
                        overridePendingTransition(0, 0)
                        finishAffinity() // Finish this and potentially others
                        true
                    }
                    else -> false
                }
            }
    }

    // --- Setup Functions ---
    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .client(
                OkHttpClient.Builder()
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        huggingFaceApi = retrofit.create(HuggingFaceApi::class.java)
    }

    private fun setupSpecializationDropdown() {
        specializationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, specializations)
        binding.specializationDropdown.setAdapter(specializationAdapter) // Use binding
        binding.specializationDropdown.setOnItemClickListener { _, _, position, _ -> // Use binding
            val selectedSpecialization = specializationAdapter.getItem(position) ?: ""
            showSubcategories(selectedSpecialization)
            // updateButtonState() is called within showSubcategories
        }
    }

    private fun setupSubcategoryDropdown() {
        subcategoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, mutableListOf<String>())
        binding.subcategoriesDropdown.setAdapter(subcategoryAdapter) // Use binding
        binding.subcategoriesDropdown.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer()) // Use binding
        binding.subcategoriesDropdown.threshold = 1 // Use binding
        // TextWatcher is added in onCreate
    }

    // Shared TextWatcher
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { updateButtonState() }
    }

    private fun showSubcategories(specialization: String) {
        val subs = subcategoriesMap[specialization] ?: emptyList()

        subcategoryAdapter.clear()
        subcategoryAdapter.addAll(subs)
        subcategoryAdapter.notifyDataSetChanged()

        binding.subcategoriesLayout.visibility = if (subs.isNotEmpty()) View.VISIBLE else View.GONE // Use binding
        binding.subcategoriesDropdown.setText("") // Use binding to clear

        updateButtonState() // Update button state whenever subcategories are shown/hidden/cleared
    }

    // --- State and Validation ---
    private fun updateButtonState() {
        val isCategorySelected = binding.specializationDropdown.text.toString().isNotEmpty()
        val selectedSpecialization = binding.specializationDropdown.text.toString()
        val subcategoriesNeeded = !subcategoriesMap[selectedSpecialization].isNullOrEmpty()

        // Check subcategory text only if the layout is actually visible and subcategories are needed
        val isSubcategorySelectedOrNotNeeded = !subcategoriesNeeded ||
                (binding.subcategoriesLayout.visibility == View.VISIBLE && binding.subcategoriesDropdown.text.toString().trim().isNotEmpty())

        val areDetailsFilled = binding.editTextAdditionalDetails.text.toString().isNotEmpty()
        val servicesLoaded = availableLegalServices.isNotEmpty() // Check if services have loaded

        // Determine final enabled state
        val isEnabled = isCategorySelected && isSubcategorySelectedOrNotNeeded && areDetailsFilled && servicesLoaded

        // --- Update Button Visuals ---
        binding.btnFindMyLawyer.isEnabled = isEnabled // Set enabled state

        // Set background drawable
        val buttonBackgroundRes = if (isEnabled) {
            R.drawable.button_enabled
        } else {
            R.drawable.button_disabled
        }
        binding.btnFindMyLawyer.setBackgroundResource(buttonBackgroundRes)

        // Set text color
        val buttonTextColor = if (isEnabled) {
            ContextCompat.getColor(this, android.R.color.white) // Use 'this' context
        } else {
            ContextCompat.getColor(this, android.R.color.black) // Use 'this' context
        }
        binding.btnFindMyLawyer.setTextColor(buttonTextColor)
        // --- End Update Button Visuals ---

        Log.d("UpdateButtonState", "Enabled: $isEnabled (Cat: $isCategorySelected, SubOK: $isSubcategorySelectedOrNotNeeded, Details: $areDetailsFilled, Services Loaded: $servicesLoaded)")
    }


    private fun findLawyer() {
        val specialization = binding.specializationDropdown.text.toString()
        val subcategoriesText = binding.subcategoriesDropdown.text.toString()
        val additionalDetails = binding.editTextAdditionalDetails.text.toString() // Store user input globally
        val subcategoriesList = subcategoriesText.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        // Re-validate using current state before showing summary
        val subcategoriesNeeded = !subcategoriesMap[specialization].isNullOrEmpty()
        val isSubcategorySelectedOrNotNeeded = !subcategoriesNeeded || subcategoriesList.isNotEmpty()

        if (specialization.isBlank()) {
            Toast.makeText(this, "Please select a legal specialization.", Toast.LENGTH_SHORT).show()
            return
        }
        if (subcategoriesNeeded && subcategoriesList.isEmpty()) {
            // Check subcategory dropdown text directly as well, in case parsing failed somehow
            if (binding.subcategoriesDropdown.text.toString().trim().isEmpty()){
                Toast.makeText(this, "Please select at least one subcategory.", Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (additionalDetails.isBlank()) {
            Toast.makeText(this, "Please provide additional details.", Toast.LENGTH_SHORT).show()
            return
        }

        currentUserInputDetails = "Initial Choice: $specialization. Subcategories: ${subcategoriesList.joinToString()}. Details: $additionalDetails" // <<< STORED DETAILS

        // Show summary and trigger FIRST API call via its positive button
        showSummaryDialog(specialization, subcategoriesList, additionalDetails)
    }

    // --- Network & API Calls ---
    private fun fetchAvailableLegalServices(callback: (String) -> Unit) {
        Log.d("FETCH_SERVICES", "Starting to fetch lawyer specializations...")
        firestore.collection("lawyers")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val uniqueSpecializations = mutableSetOf<String>() // Store only unique specialization names
                Log.d("FETCH_SERVICES", "Found ${querySnapshot.size()} lawyer documents.")

                for (document in querySnapshot.documents) {
                    Log.d("FETCH_SERVICES", "Processing lawyer: ${document.id}")
                    val specializationsData = document.get("legalSpecializations")
                    // ... (Type checks remain the same) ...
                    if (specializationsData !is List<*>) continue
                    val specializationsList = specializationsData as List<HashMap<String, Any>>

                    for (specializationDataMap in specializationsList) {
                        if (specializationDataMap !is Map<*,*>) continue
                        val specializationName = specializationDataMap["specialization"] as? String
                        Log.d("FETCH_SERVICES", "  Found specialization map: $specializationDataMap -> Name: $specializationName")
                        if (!specializationName.isNullOrBlank()) {
                            uniqueSpecializations.add(specializationName) // <<< ONLY add specialization name
                            Log.d("FETCH_SERVICES", "  Added '$specializationName' to set.")
                        } // ... (Removed adding subcategories here) ...
                    }
                }
                val servicesString = uniqueSpecializations.joinToString(", ") // Join only unique specializations
                Log.d("FETCH_SERVICES", "Finished fetching. Unique Specializations String: '$servicesString'")
                callback(servicesString)
            }
            .addOnFailureListener { e ->
                Log.e("FETCH_SERVICES", "Error fetching lawyer specializations", e)
                handleError("Could not fetch available specializations.")
                callback("")
            }
    }

    private fun getApiKeyFromProperties(): String {
        // ... (Keep existing getApiKeyFromProperties logic) ...
        return try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) {
            Log.e("API_KEY_ERROR", "Could not retrieve API key.", e)
            Toast.makeText(this, "API Key configuration error.", Toast.LENGTH_LONG).show()
            ""
        }
    }

    private fun showSummaryDialog(specialization: String, subcategories: List<String>, additionalDetails: String) {
        val subcategoriesString = if (subcategories.isNotEmpty()) subcategories.joinToString(", ") else "None"
        val message = """
            <b>Selected Legal Concern:</b> $specialization<br><br>
            <b>Subcategories:</b> $subcategoriesString<br><br>
            <b>Additional Details:</b> $additionalDetails<br><br>
            Do you want to proceed with classifying this information?
        """.trimIndent() // Modified text

        showAlertDialog(
            title = "Summary", message = Html.fromHtml(message),
            positiveButtonText = "Classify Concern", // <<< CHANGED TEXT
            onPositiveButtonClick = {
                // --- TRIGGER FIRST API CALL ---
                callCategoryClassificationApi(specialization, subcategories, additionalDetails) // <<< CHANGED ACTION
            },
            negativeButtonText = "Edit", onNegativeButtonClick = null
        )
    }

    private fun callCategoryClassificationApi(userInputSpecialization: String, userInputSubcategories: List<String>, details: String) {
        showLoadingDialog("Analyzing main legal area...")

        // The prompt still includes all details for context
        val subcatString = if (userInputSubcategories.isNotEmpty()) " Subcategories selected: ${userInputSubcategories.joinToString(", ")}." else ""
        val prompt = "Given these legal specializations: $availableLegalServices. " + // Focus on specializations
                "Which specialization best fits this user's issue? User's initial choice: $userInputSpecialization.$subcatString Details: $details"

        Log.d("HUGGINGFACE_PROMPT_CAT", prompt)

        val candidateLabels = availableLegalServices.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        if (candidateLabels.isEmpty()) {
            dismissLoadingDialog()
            handleError("No available legal specializations found to classify against.") // Adjusted error message
            return
        }
        Log.d("HUGGINGFACE_CANDIDATES_CAT", "Candidate Labels: $candidateLabels")


        val request = HuggingFaceRequest(inputs = prompt, parameters = Parameters(candidate_labels = candidateLabels))

        huggingFaceApi.classifyText(
            "https://api-inference.huggingface.co/models/facebook/bart-large-mnli",
            "Bearer $apiKey",
            request
        ).enqueue(object : Callback<HuggingFaceResponse> {
            override fun onResponse(call: Call<HuggingFaceResponse>, response: Response<HuggingFaceResponse>) {
                dismissLoadingDialog()
                if (response.isSuccessful) {
                    val huggingFaceResult = response.body()
                    if (huggingFaceResult != null) {
                        val bestCategory = huggingFaceResult.labels.firstOrNull() // Highest scoring label
                        val score = huggingFaceResult.scores.firstOrNull()

                        Log.d("HUGGINGFACE_RESPONSE", "Full Body: $huggingFaceResult")

                        if (bestCategory != null && score != null && candidateLabels.contains(bestCategory)) {
                            currentMatchedCategory = bestCategory // <<< STORE result
                            showConfirmCategoryDialog(bestCategory, score)
                        } else {
                            Log.w("HUGGINGFACE_MATCH", "API result '$bestCategory' not in candidate list or invalid. Response: $huggingFaceResult")
                            // Fallback: Could use the user's original specialization choice?
                            // Or show a more generic error.
                            // For now, use the original specialization as a fallback IF a category was initially selected
                            if (userInputSpecialization.isNotBlank() && candidateLabels.contains(userInputSpecialization)) {
                                currentMatchedCategory = userInputSpecialization
                                showConfirmCategoryDialog(userInputSpecialization, 0.0) // Show with 0 confidence score
                            } else {
                                handleError("Could not reliably classify the legal concern.")
                            }
                        }
                    } else {
                        handleError("Empty response body from Hugging Face API.")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("API_ERROR", "API Error: ${response.code()}, Body: $errorBody")
                    handleError("API Classification Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<HuggingFaceResponse>, t: Throwable) {
                dismissLoadingDialog()
                Log.e("API_ERROR", "API Call Failed", t)
                handleError("Network Error: ${t.localizedMessage}")
            }
        })
    }

    private fun showConfirmCategoryDialog(matchedCategory: String, score: Double) {
        val formattedScore = String.format("%.1f", score * 100)
        val message = "We classified your primary legal area as:\n\n<b>$matchedCategory</b>\n(Confidence: $formattedScore%)\n\nDo you want to proceed to find the specific subcategory?"

        showAlertDialog(
            title = "Confirm Legal Area",
            message = Html.fromHtml(message),
            positiveButtonText = "Yes, find subcategory",
            onPositiveButtonClick = {
                // --- TRIGGER SECOND API CALL ---
                callSubcategoryClassificationApi(matchedCategory, currentUserInputDetails) // Use stored details
            },
            negativeButtonText = "No, use this category",
            onNegativeButtonClick = {
                saveRequestAndProceed(matchedCategory, null) // Save only main category
            }
        )
    }

    private fun callSubcategoryClassificationApi(confirmedCategory: String, originalUserDetails: String) {
        val relevantSubcategories = subcategoriesMap[confirmedCategory] ?: emptyList()

        if (relevantSubcategories.isEmpty()) {
            Log.i(TAG, "No subcategories for '$confirmedCategory'. Saving main category.")
            saveRequestAndProceed(confirmedCategory, null) // Proceed with just main category
            return
        }

        showLoadingDialog("Analyzing specific issue...")

        // Prompt focuses on subcategories
        val prompt = "Within the legal area of '$confirmedCategory', which of the following specific subcategories best fits the user's issue described below?\n\nSubcategories: ${relevantSubcategories.joinToString(", ")}\n\nUser's issue: $originalUserDetails"
        Log.d("HUGGINGFACE_PROMPT_SUB", prompt)

        val candidateLabels = relevantSubcategories // Use subcategories as candidates
        Log.d("HUGGINGFACE_CANDIDATES_SUB", "Candidate Labels: $candidateLabels")

        val request = HuggingFaceRequest(inputs = prompt, parameters = Parameters(candidate_labels = candidateLabels))

        huggingFaceApi.classifyText("https://api-inference.huggingface.co/models/facebook/bart-large-mnli", "Bearer $apiKey", request)
            .enqueue(object : Callback<HuggingFaceResponse> {
                override fun onResponse(call: Call<HuggingFaceResponse>, response: Response<HuggingFaceResponse>) {
                    dismissLoadingDialog()
                    if (response.isSuccessful) {
                        val result = response.body()
                        if (result != null) {
                            val bestSubcategory = result.labels.firstOrNull()
                            val score = result.scores.firstOrNull()
                            Log.d("HUGGINGFACE_SUB_RESP", "Result: $result")

                            if (bestSubcategory != null && score != null && candidateLabels.contains(bestSubcategory)) {
                                Log.i("HUGGINGFACE_SUB_MATCH", "Best Subcategory: $bestSubcategory (Score: $score)")
                                // Save BOTH category and subcategory
                                saveRequestAndProceed(confirmedCategory, bestSubcategory) // <<< Pass both results
                            } else {
                                Log.w("HUGGINGFACE_SUB_MATCH", "API subcategory '$bestSubcategory' not valid. Using main category.")
                                handleError("Could not pinpoint subcategory.")
                                saveRequestAndProceed(confirmedCategory, null) // <<< Save only main category
                            }
                        } else { handleError("Empty response from Subcategory API."); saveRequestAndProceed(confirmedCategory, null) }
                    } else {
                        val errorBody = response.errorBody()?.string(); Log.e("API_ERROR_SUB", "API Error: ${response.code()}, Body: $errorBody"); handleError("Subcategory Classification Error: ${response.code()}"); saveRequestAndProceed(confirmedCategory, null)
                    }
                }
                override fun onFailure(call: Call<HuggingFaceResponse>, t: Throwable) {
                    dismissLoadingDialog(); Log.e("API_ERROR_SUB", "API Call Failed", t); handleError("Network Error: ${t.localizedMessage}"); saveRequestAndProceed(confirmedCategory, null)
                }
            })
    }

    private fun saveRequestAndProceed(matchedCategory: String, matchedSubcategory: String?) {
        showLoadingDialog("Saving request...")
        val specialization = binding.specializationDropdown.text.toString();
        val subcategoriesText = binding.subcategoriesDropdown.text.toString();
        val subcategoriesList = subcategoriesText.split(',').map{it.trim()}.filter{it.isNotEmpty()};
        val additionalDetails = binding.editTextAdditionalDetails.text.toString()
        val requestData = hashMapOf<String, Any?>("userId" to auth.currentUser?.uid, "userInputSpecialization" to specialization, "userInputSubcategories" to subcategoriesList, "additionalDetails" to additionalDetails, "matchedCategory" to matchedCategory, "matchedSubcategory" to matchedSubcategory, "timestamp" to com.google.firebase.Timestamp.now())


        firestore.collection("client_requests")
            .add(requestData)
            .addOnSuccessListener {
                dismissLoadingDialog()
                Toast.makeText(this, "Request submitted successfully!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MatchesFoundActivity::class.java)
                intent.putExtra("MATCHED_CATEGORY", matchedCategory)
                matchedSubcategory?.let { subcat -> intent.putExtra("MATCHED_SUBCATEGORY", subcat) }
                // *** PASS AFFILIATION TYPE TO MatchesFoundActivity ***
                intent.putExtra(AffiliationSelectionActivity.EXTRA_AFFILIATION_TYPE, affiliationType) // Pass it along
                // *** --- ***
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e -> /* ... error handling ... */ dismissLoadingDialog(); handleError("Error saving request: ${e.message}"); Log.e("FirestoreError","Error add req",e) }
    }

    // --- Helper Functions ---
    private fun showHelpDialog() {
        // Use StringBuilder for efficient string concatenation
        val guideContentBuilder = StringBuilder()

        // Iterate through your specializations and subcategories maps
        var index = 1
        for (specialization in specializations) { // Use the 'specializations' list
            guideContentBuilder.append("<b>${index}. ${specialization}</b>:<br>") // Bold category name

            // Get subcategories, join them, and add description based on category
            val subcats = subcategoriesMap[specialization] ?: emptyList() // Get from subcategoriesMap
            val subcatString = if (subcats.isNotEmpty()) {
                subcats.joinToString(", ")
            } else {
                "General assistance within this area." // Default if no specific subcats listed
            }

            // Add a generic description or specific ones if you have them
            val description = when (specialization) {
                "Family Law" -> "Covers issues like marriage, divorce, child matters, property division, etc."
                "Criminal Law" -> "Handles defense against criminal charges, victim support, and traffic violations."
                "Civil Law" -> "Deals with disputes between individuals/organizations, such as personal injury or property conflicts."
                "Labor Law" -> "Focuses on employment rights, workplace issues, benefits, and union matters."
                "Immigration Law" -> "Assists with visas, residency, citizenship, and deportation issues."
                "Consumer Protection Law" -> "Protects rights against unfair business practices, faulty products, etc."
                "Real Estate & Property Law" -> "Involves buying/selling property, leases, land disputes, and construction."
                "Business & Corporate Law" -> "Covers business setup, contracts, intellectual property, and corporate governance."
                "Tax Law" -> "Addresses personal, corporate, and estate taxes, including disputes."
                "Health Law" -> "Relates to medical malpractice, insurance issues, and patient rights."
                "Environmental Law" -> "Deals with pollution, permits, and conservation efforts."
                "Public Assistance & Welfare Law" -> "Concerns government benefits and social welfare rights."
                "Traffic and Transportation Law" -> "Handles traffic tickets, accident liability, and public transport issues."
                else -> "General legal assistance in this area." // Default description
            }

            guideContentBuilder.append("$description<br>")
            if (subcats.isNotEmpty()) { // Only show subcategory list if they exist
                guideContentBuilder.append("<i>Subcategories: $subcatString</i><br>") // Italicize subcategories
            }
            guideContentBuilder.append("<br>") // Add space between categories
            index++
        }

        // Build the final string
        val guideContent = guideContentBuilder.toString()

        // Show the AlertDialog
        showAlertDialog(
            title = "Guide to Legal Concerns",
            message = Html.fromHtml(guideContent), // Use Html.fromHtml for formatting
            positiveButtonText = "Got it",
            onPositiveButtonClick = null,
            negativeButtonText = null,
            onNegativeButtonClick = null
        )
    }

    // Keep the existing showAlertDialog function
    private fun showAlertDialog(
        title: String, message: CharSequence?, positiveButtonText: String?,
        onPositiveButtonClick: (() -> Unit)?, negativeButtonText: String?,
        onNegativeButtonClick: (() -> Unit)?
    ) {
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message) // Message is already CharSequence (Html.fromHtml result)
            .setCancelable(true) // Allow dismissing by tapping outside

        if (positiveButtonText != null) {
            builder.setPositiveButton(positiveButtonText) { dialog, _ ->
                onPositiveButtonClick?.invoke()
                dialog.dismiss()
            }
        }
        // Removed negative button logic as it was null in the call
        builder.show()
    }


    private fun showLoadingDialog(message: String) {
        // ... (Keep existing loading dialog code) ...
        if (!::alertDialog.isInitialized || !alertDialog.isShowing) {
            val builder = AlertDialog.Builder(this)
            val loadingView = layoutInflater.inflate(R.layout.dialog_loading, null)
            val loadingTextView = loadingView.findViewById<TextView>(R.id.loadingText)
            loadingTextView.text = message
            builder.setView(loadingView)
            builder.setCancelable(false)
            alertDialog = builder.create()
            alertDialog.show()
        }
    }

    private fun dismissLoadingDialog() {
        // ... (Keep existing dismiss dialog code) ...
        if (::alertDialog.isInitialized && alertDialog.isShowing) {
            alertDialog.dismiss()
        }
    }

    private fun handleError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

}