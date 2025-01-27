package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.tlfinal.databinding.LawyerMatchingBinding
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson

class LawyerMatchingActivity : AppCompatActivity() {

    private lateinit var binding: LawyerMatchingBinding
    private lateinit var huggingFaceApi: HuggingFaceApi
    private lateinit var apiKey: String
    private lateinit var alertDialog: AlertDialog
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var availableLegalServices = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lawyer_matching)
        binding = LawyerMatchingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Get API key from local.properties
        apiKey = getApiKeyFromProperties()

        // Initialize Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .client(
                OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        huggingFaceApi = retrofit.create(HuggingFaceApi::class.java)


        binding.imageBack.setOnClickListener { finish() }

        val helpButton = findViewById<ImageView>(R.id.help_button)
        val legalCategorySpinner = findViewById<Spinner>(R.id.spinnerLegalCategory)
        val additionalDetailsEditText = findViewById<EditText>(R.id.editTextAdditionalDetails)
        val findMyLawyerButton = findViewById<Button>(R.id.btnFindMyLawyer)

        helpButton.setOnClickListener {
            showHelpDialog()
        }

        val categories = listOf(
            "Personal Issues",
            "Business",
            "Family",
            "Property",
            "Contractual",
            "Employment/Work",
            "Legal Documents",
            "Special Projects/Contracts",
            "General Legal Assistance"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        legalCategorySpinner.adapter = adapter

        legalCategorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                updateButtonState(legalCategorySpinner, additionalDetailsEditText, findMyLawyerButton)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                updateButtonState(legalCategorySpinner, additionalDetailsEditText, findMyLawyerButton)
            }
        }
        // Fetch legal services and update availableLegalServices
        fetchAvailableLegalServices { services ->
            availableLegalServices = services
            Log.d("AVAILABLE_LEGAL_SERVICES", "Available Legal Services: $availableLegalServices")
            updateButtonState(legalCategorySpinner, additionalDetailsEditText, findMyLawyerButton)
        }

        additionalDetailsEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateButtonState(legalCategorySpinner, additionalDetailsEditText, findMyLawyerButton)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        updateButtonState(legalCategorySpinner, additionalDetailsEditText, findMyLawyerButton)

        findMyLawyerButton.setOnClickListener {
            val selectedCategory = legalCategorySpinner.selectedItem.toString()
            val additionalDetails = additionalDetailsEditText.text.toString()

            showSummaryDialog(selectedCategory, additionalDetails)
        }
    }

    private fun fetchAvailableLegalServices(callback: (String) -> Unit) {
        val userId = auth.currentUser?.uid
        userId?.let {
            firestore.collection("lawyers")
                .get()
                .addOnSuccessListener { result ->
                    var availableServices = "Available Legal Services:"
                    for (document in result) {
                        val legalServices = document.getString("legalservices") ?: ""
                        if (legalServices.isNotBlank()) {
                            availableServices += " " + legalServices + " by " + (document.getString("fullName")
                                ?: "Unknown")
                        }

                    }
                    val servicesWithoutNames = availableServices.replace(Regex(" by [^,]+"), "").replace("Available Legal Services: ","").trim()

                    callback(servicesWithoutNames)
                }
                .addOnFailureListener { e ->
                    handleError("Error fetching legal services ${e.message}")
                    callback("")
                }
        }
    }


    private fun getApiKeyFromProperties(): String {
        return BuildConfig.GEMINI_API_KEY
    }

    private fun updateButtonState(spinner: Spinner, detailsEditText: EditText, button: Button) {
        val isLegalCategorySelected = spinner.selectedItemPosition != 0
        val areDetailsFilled = detailsEditText.text.toString().isNotEmpty()
        button.isEnabled = isLegalCategorySelected && areDetailsFilled && availableLegalServices.isNotEmpty()
        button.setBackgroundResource(
            if (button.isEnabled) R.drawable.button_enabled else R.drawable.button_disabled
        )
    }

    private fun showSummaryDialog(selectedCategory: String, additionalDetails: String) {
        val message = """
            <b>Selected Legal Issue:</b> $selectedCategory<br><br>
            <b>Additional Details:</b> $additionalDetails<br><br>
            Do you want to proceed with this information for lawyer matching?
        """.trimIndent()

        showAlertDialog(
            title = "Summary",
            message = Html.fromHtml(message),
            positiveButtonText = "Proceed",
            onPositiveButtonClick = {
                callHuggingFaceApi("$selectedCategory\n$additionalDetails")
            },
            negativeButtonText = "Edit",
            onNegativeButtonClick = null
        )
    }
    private fun callHuggingFaceApi(userDetails: String) {
        showLoadingDialog("Analyzing your legal concern...")
        val prompt = "Given the following legal services: $availableLegalServices, what is the best legal service that applies to the following user's issue: $userDetails?"
        Log.d("HUGGINGFACE_PROMPT", "Prompt used: $prompt")
        val legalServicesList = availableLegalServices.split(",").map { it.trim() }

        val request = HuggingFaceRequest(
            inputs = prompt,
            parameters = Parameters(candidate_labels = legalServicesList)
        )

        val apiUrl = "https://api-inference.huggingface.co/models/facebook/bart-large-mnli" // Use your chosen model ID
        huggingFaceApi.classifyText(apiUrl, "Bearer $apiKey", request).enqueue(object : Callback<HuggingFaceResponse> {
            override fun onResponse(call: Call<HuggingFaceResponse>, response: Response<HuggingFaceResponse>) {
                dismissLoadingDialog()
                if (response.isSuccessful) {
                    val generatedContent = response.body()?.labels?.firstOrNull()?.trim()
                    Log.d("HUGGINGFACE_RESPONSE", "Hugging Face Raw Response: $generatedContent")
                    Log.d("HUGGINGFACE_RESPONSE_FULL", "Hugging Face Raw Response (FULL): $response")
                    val matchedCategory = findMatchingCategory(generatedContent ?: "", userDetails)
                    showClassificationAndNavigate(matchedCategory ?: "No result")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("API_ERROR", "API Error: ${response.code()}, ${errorBody}")
                    handleError("API Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<HuggingFaceResponse>, t: Throwable) {
                dismissLoadingDialog()
                Log.e("API_ERROR", "API Call Failed: ${t.message}")
                handleError("Failed: ${t.message}")
            }
        })
    }
    private fun findMatchingCategory(response: String, userDetails: String): String? {
        val legalServices = availableLegalServices.split(",").map { it.trim() }
        Log.d("SERVICES_LIST", "List of service $legalServices")
        Log.d("HUGGINGFACE_RESPONSE_TEXT", "Response from API: $response")
        for (service in legalServices) {
            val serviceName = service.replace(Regex("by.*"),"").trim()
            Log.d("MATCHING_SERVICE_ITEM", "Checking against service $serviceName")
            if (response.contains(serviceName, ignoreCase = true)) {
                Log.d("MATCHING_SERVICE", "Matching service $serviceName")
                return serviceName
            }
        }

        if (userDetails.contains("annulment", ignoreCase = true) || userDetails.contains("marriage", ignoreCase = true) ||  userDetails.contains("divorce", ignoreCase = true) || userDetails.contains("custody", ignoreCase = true) || userDetails.contains("adoption", ignoreCase = true)) {
            return "Family Law"
        }
        if (userDetails.contains("contract", ignoreCase = true) || userDetails.contains("agreement", ignoreCase = true) || userDetails.contains("lease", ignoreCase = true)){
            return "Contract Drafting"
        }
        if (userDetails.contains("company", ignoreCase = true) || userDetails.contains("shareholder", ignoreCase = true) || userDetails.contains("corporate", ignoreCase = true))
        {
            return "Corporate Law"
        }
        if (userDetails.contains("dismissed", ignoreCase = true) || userDetails.contains("job", ignoreCase = true) || userDetails.contains("employment", ignoreCase = true))
        {
            return "Labor Law"
        }
        if (userDetails.contains("tax", ignoreCase = true) || userDetails.contains("tax return", ignoreCase = true))
        {
            return "Tax Law"
        }
        if (userDetails.contains("property", ignoreCase = true) || userDetails.contains("neighbor", ignoreCase = true) || userDetails.contains("land", ignoreCase = true))
        {
            return "Real Estate Law"
        }
        if (userDetails.contains("arrested", ignoreCase = true) || userDetails.contains("driving under the influence", ignoreCase = true))
        {
            return "Criminal Litigation"
        }
        if (userDetails.contains("copyright", ignoreCase = true) || userDetails.contains("website", ignoreCase = true))
        {
            return "Intellectual Property Law"
        }
        if (userDetails.contains("visa", ignoreCase = true) || userDetails.contains("immigration", ignoreCase = true))
        {
            return "Immigration Law"
        }
        if (userDetails.contains("notarize", ignoreCase = true) || userDetails.contains("notary", ignoreCase = true))
        {
            return "Notarial Services"
        }
        if (userDetails.contains("sue", ignoreCase = true) || userDetails.contains("injured", ignoreCase = true))
        {
            return "Civil Litigation"
        }
        if (userDetails.contains("legal implications", ignoreCase = true) || userDetails.contains("legal advice", ignoreCase = true) || userDetails.contains("legal issues", ignoreCase = true))
        {
            return "Legal Consulting"
        }


        return null
    }
    private fun showClassificationAndNavigate(category: String) {
        val message = "<b>Legal service matched:</b><br><br>$category"
        showAlertDialog(
            title = "Classification Result",
            message = Html.fromHtml(message),
            positiveButtonText = "Continue",
            onPositiveButtonClick = {
                navigateToMatchesFound(category)
            },
            negativeButtonText = null,
            onNegativeButtonClick = null
        )

    }

    private fun navigateToMatchesFound(category: String) {
        showLoadingDialog("Navigating to matched lawyers...")
        val intent = Intent(this, MatchesFoundActivity::class.java)
        intent.putExtra("LEGAL_CATEGORY", category)
        startActivity(intent)
        dismissLoadingDialog()
    }
    private fun showHelpDialog() {
        val guideContent = """
             1. <b>Personal Issues</b>: Legal problems related to individual rights or personal matters (e.g., personal injury, criminal defense).<br><br>
            2. <b>Business</b>: Legal matters related to business operations, including contracts, corporate governance, and disputes.<br><br>
            3. <b>Family</b>: Legal issues related to family matters like divorce, child custody, spousal support, or adoption.<br><br>
            4. <b>Property</b>: Issues involving real estate, land disputes, landlord-tenant conflicts, or property transactions.<br><br>
            5. <b>Contractual</b>: Legal problems related to contracts, including breaches, enforcement, or disputes.<br><br>
            6. <b>Employment/Work</b>: Legal issues related to employment law, such as wrongful termination, workplace discrimination, wage disputes, etc.<br><br>
            7. <b>Legal Documents</b>: Assistance with preparing or reviewing legal documents like wills, contracts, leases, etc.<br><br>
            8. <b>Special Projects/Contracts</b>: Legal services related to unique or specialized projects, such as joint ventures, large contracts, or specific legal agreements.<br><br>
            9. <b>General Legal Assistance</b>: A broad category that allows users to ask about anything that doesn’t fit the other categories but still needs legal expertise.<br><br>
        """.trimIndent()

        showAlertDialog(
            title = "Guide to Legal Concerns",
            message = Html.fromHtml(guideContent),
            positiveButtonText = "Got it",
            onPositiveButtonClick = null,
            negativeButtonText = null,
            onNegativeButtonClick = null
        )
    }


    private fun showAlertDialog(
        title: String,
        message: CharSequence?,
        positiveButtonText: String?,
        onPositiveButtonClick: (() -> Unit)?,
        negativeButtonText: String?,
        onNegativeButtonClick: (() -> Unit)?
    ) {
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)

        if (positiveButtonText != null) {
            builder.setPositiveButton(positiveButtonText) { dialog, _ ->
                onPositiveButtonClick?.invoke()
                dialog.dismiss()
            }
        }

        if (negativeButtonText != null) {
            builder.setNegativeButton(negativeButtonText) { dialog, _ ->
                onNegativeButtonClick?.invoke()
                dialog.dismiss()
            }
        }
        builder.show()
    }


    private fun showLoadingDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        val loadingView = layoutInflater.inflate(R.layout.dialog_loading, null)
        val loadingTextView = loadingView.findViewById<TextView>(R.id.loadingText)
        loadingTextView.text = message
        builder.setView(loadingView)
        builder.setCancelable(false)

        alertDialog = builder.create()
        alertDialog.show()
    }

    private fun dismissLoadingDialog() {
        if (::alertDialog.isInitialized && alertDialog.isShowing) {
            alertDialog.dismiss()
        }
    }

    private fun handleError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}