package com.example.tlfinal

// ... (keep existing imports) ...
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout // Keep LinearLayout import
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.tlfinal.databinding.MatchesFoundBinding // Use binding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class MatchesFoundActivity : AppCompatActivity() {

    // ... (keep existing variables: binding, firestore, lists, adapter, etc.) ...
    private lateinit var binding: MatchesFoundBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var allLawyersList: List<Lawyer>
    private lateinit var filteredLawyersList: MutableList<Lawyer>
    private lateinit var carouselAdapter: CarouselAdapter
    private lateinit var selectedLegalCategory: String
    private var selectedSubCategory: String? = null
    private lateinit var auth: FirebaseAuth

    // Filter variables
    private var selectedFilterGender: String = "Any Gender"
    private var selectedMinFee: Double = 0.0
    private var selectedMaxFee: Double = Double.MAX_VALUE

    // Filter options
    private val genderOptions = listOf("Any Gender", "Male", "Female")
    private val feeOptions = listOf("Any Fee", "Free", "100-500", "500-1000", "1000+")

    private var initialLoadComplete = false

    private lateinit var selectedAffiliationType: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MatchesFoundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()

        // ... (keep existing onCreate logic: firestore, auth, category check) ...
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // *** GET AFFILIATION FROM INTENT ***
        selectedAffiliationType = intent.getStringExtra(AffiliationSelectionActivity.EXTRA_AFFILIATION_TYPE) ?: "Private" // Default if missing
        selectedLegalCategory = intent.getStringExtra("MATCHED_CATEGORY") ?: ""
        selectedSubCategory = intent.getStringExtra("MATCHED_SUBCATEGORY")

        Log.d("MatchesFoundActivity", "Received Category: '$selectedLegalCategory', SubCat: '$selectedSubCategory', Affiliation: '$selectedAffiliationType'")
        // *** --- ***
        if (selectedLegalCategory.isBlank()) {
            Toast.makeText(this, "Error: Legal category not specified.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupFilterSpinners()
        setupRecyclerView()
        fetchAndDisplayLawyers()
    }

    private fun setupListeners() { // Changed name from setupButtonClickListeners for consistency
        binding.imageBack.setOnClickListener {
            // Navigate back to LawyerMatchingActivity explicitly
            val intent = Intent(this, LawyerMatchingActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish() // Finish this activity after navigating back
        }

        // --- ADD BOTTOM NAVIGATION LISTENER HERE ---
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
        // --- END BOTTOM NAVIGATION LISTENER ---
    }


    private fun setupRecyclerView() {
        filteredLawyersList = mutableListOf()
        carouselAdapter = CarouselAdapter(filteredLawyersList) { selectedLawyer ->
            val currentUser = auth.currentUser
            if(currentUser == null){
                Toast.makeText(this, "Error: User not signed in.", Toast.LENGTH_SHORT).show()
                return@CarouselAdapter
            }
            Log.d("SELECTED_LAWYER", "Selected Lawyer ID: ${selectedLawyer.userId}, Name: ${selectedLawyer.fullName}")
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("lawyerId", selectedLawyer.userId)
            intent.putExtra("clientUserId", currentUser.uid)
            intent.putExtra("lawyerFullName", selectedLawyer.fullName)
            startActivity(intent)
        }

        binding.carouselRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.carouselRecyclerView.adapter = carouselAdapter
        PagerSnapHelper().attachToRecyclerView(binding.carouselRecyclerView)
    }

    private fun setupFilterSpinners() {
        // Gender Spinner
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, genderOptions)
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilterGender.adapter = genderAdapter
        binding.spinnerFilterGender.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFilterGender = genderOptions[position]
                if (initialLoadComplete) { applyFiltersAndRefreshList() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Fee Spinner
        val feeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, feeOptions)
        feeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilterFee.adapter = feeAdapter
        binding.spinnerFilterFee.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> { selectedMinFee = 0.0; selectedMaxFee = Double.MAX_VALUE }
                    1 -> { selectedMinFee = 0.0; selectedMaxFee = 0.0 }
                    2 -> { selectedMinFee = 100.0; selectedMaxFee = 500.0 }
                    3 -> { selectedMinFee = 501.0; selectedMaxFee = 1000.0 }
                    4 -> { selectedMinFee = 1001.0; selectedMaxFee = Double.MAX_VALUE }
                }
                if (initialLoadComplete) { applyFiltersAndRefreshList() }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun fetchAndDisplayLawyers() {
        Log.d("MatchesFoundActivity", "Fetching all lawyers...")
        binding.textNoMatchesFound.visibility = View.GONE; binding.textResultsCount.visibility = View.GONE; binding.carouselRecyclerView.visibility = View.INVISIBLE
        firestore.collection("lawyers").get()
            .addOnSuccessListener { querySnapshot ->
                Log.d("MatchesFoundActivity", "Fetched ${querySnapshot.size()} total lawyers.")
                allLawyersList = querySnapshot.documents.mapNotNull { document ->
                    try {
                        val userId=document.id;val fn=document.getString("firstName")?:"";val ln=document.getString("lastName")?:"";if(userId.isBlank()||fn.isBlank()||ln.isBlank()){Log.w("MFActivity","Skip doc ${document.id}");return@mapNotNull null};val mn=document.getString("middleName");val em=document.getString("emailAddress")?:"";val cn=document.getString("contactNumber")?:"";val oa=document.getString("officeAddress")?:"";val af=document.getString("affiliation")?:"Private";val rn=document.getString("rollNumber")?:"";val gen=document.getString("gender")?:"";val ye=document.getLong("yearsOfExperience")?.toInt()?:0;val fee=document.getDouble("consultationFee")?:0.0;val spR=document.get("legalSpecializations");val spL:List<Map<String,Any>> =if(spR is List<*>)spR.mapNotNull{it as? Map<String,Any>} else emptyList();Lawyer(userId=userId,firstName=fn,middleName=mn,lastName=ln,email=em,contactNumber=cn,officeAddress=oa,affiliation=af,rollNumber=rn,gender=gen,yearsOfExperience=ye,consultationFee=fee,legalSpecializations=spL)}catch(e:Exception){Log.e("MFActivity","Map error ${document.id}",e);null} }
                Log.d("MatchesFoundActivity", "Mapped ${allLawyersList.size} lawyers.")
                initialLoadComplete = true
                applyFiltersAndRefreshList() // Apply initial filters including affiliation
            }
            .addOnFailureListener { e ->
                Log.e("MFActivity","Fetch error",e);Toast.makeText(this,"Fetch error:${e.message}",Toast.LENGTH_SHORT).show();binding.textNoMatchesFound.text="Error loading";binding.textNoMatchesFound.visibility=View.VISIBLE;binding.carouselRecyclerView.visibility=View.GONE;binding.textResultsCount.visibility=View.GONE;initialLoadComplete=true }
    }

    // --- *** MODIFIED applyFiltersAndRefreshList *** ---
    private fun applyFiltersAndRefreshList() {
        if (!::allLawyersList.isInitialized) {
            Log.d("MatchesFoundActivity", "Lawyer list not initialized. Skipping.")
            return
        }

        Log.d("MatchesFoundActivity", "Applying filters: Affiliation='$selectedAffiliationType', Category='$selectedLegalCategory', SubCat='$selectedSubCategory', Gender='$selectedFilterGender', Fee=$selectedMinFee-$selectedMaxFee")
        val currentlyFiltered = allLawyersList.filter { lawyer ->
            // 1. *** NEW: Check Affiliation FIRST ***
            val matchesAffiliation = lawyer.affiliation.equals(selectedAffiliationType, ignoreCase = true)
            if (!matchesAffiliation) return@filter false // Exit if affiliation doesn't match

            // 2. Check Main Category
            val matchesCategory = lawyer.matchesCategory(selectedLegalCategory)
            if (!matchesCategory) return@filter false

            // 3. Check Subcategory (if applicable)
            val matchesSubCategory = if (selectedSubCategory != null) {
                lawyer.hasSubcategoryWithinSpecialization(selectedLegalCategory, selectedSubCategory!!)
            } else {
                true
            }
            if (!matchesSubCategory) return@filter false

            // 4. Apply other filters
            val matchesGender = selectedFilterGender == "Any Gender" || lawyer.gender.equals(selectedFilterGender, ignoreCase = true)
            val matchesFee = lawyer.consultationFee >= selectedMinFee && lawyer.consultationFee <= selectedMaxFee

            // All checks must pass
            matchesGender && matchesFee
        }


        val resultsCount = currentlyFiltered.size;
        Log.d("MatchesFoundActivity", "Filtering done. Found $resultsCount.");
        filteredLawyersList.clear();
        filteredLawyersList.addAll(currentlyFiltered);
        carouselAdapter.notifyDataSetChanged();

        if(resultsCount==0){
            binding.textNoMatchesFound.text=
                if(initialLoadComplete)"No Matches For Filters"
                else "Loading...";
            binding.textNoMatchesFound.visibility=View.VISIBLE;
            binding.textResultsCount.visibility=View.GONE;
            binding.carouselRecyclerView.visibility=View.GONE
        }else{
                binding.textResultsCount.text=resources.getQuantityString(R.plurals.results_found, resultsCount, resultsCount);
            binding.textNoMatchesFound.visibility=View.GONE;
            binding.textResultsCount.visibility=View.VISIBLE;
            binding.carouselRecyclerView.visibility=View.VISIBLE;

            if(binding.carouselRecyclerView.adapter?.itemCount==resultsCount)
                binding.carouselRecyclerView.scrollToPosition(0)
        }
    }

    // --- Carousel Adapter (Keep as is) ---
    class CarouselAdapter(
        private var items: List<Lawyer>,
        private val onLawyerSelected: (Lawyer) -> Unit
    ) : RecyclerView.Adapter<CarouselAdapter.CarouselViewHolder>() {

        inner class CarouselViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            // ... (Keep all ViewHolder references) ...
            val nameTextView: TextView = itemView.findViewById(R.id.textFullname)
            val paoLayout: LinearLayout = itemView.findViewById(R.id.paoDetailsLayout)
            val rollNumberTextView: TextView = itemView.findViewById(R.id.userRollNumber)
            val specializationPAOTextView: TextView = itemView.findViewById(R.id.userSpecializationPAO)
            val subcategoryTextView: TextView = itemView.findViewById(R.id.userSubcategory)
            val addressPAOTextView: TextView = itemView.findViewById(R.id.userAddressPAO)
            val emailTextView: TextView = itemView.findViewById(R.id.userEmail)
            val contactTextView: TextView = itemView.findViewById(R.id.userContact)
            val privateLayout: LinearLayout = itemView.findViewById(R.id.privateDetailsLayout)
            val genderTextView: TextView = itemView.findViewById(R.id.userGender)
            val specializationPrivateTextView: TextView = itemView.findViewById(R.id.userSpecializationPrivate)
            val addressPrivateTextView: TextView = itemView.findViewById(R.id.userAddressPrivate)
            // Removed experienceTextView reference based on carousel_item.xml update
            // val experienceTextView: TextView = itemView.findViewById(R.id.userYearsofExp)
            val feeTextView: TextView = itemView.findViewById(R.id.userConsultationFee)

            fun bind(lawyer: Lawyer) {
                // ... (Keep existing bind logic, ensuring it matches carousel_item.xml) ...
                nameTextView.text = "Atty. ${lawyer.fullName}"
                if (lawyer.affiliation.equals("PAO", ignoreCase = true)) {
                    paoLayout.visibility = View.VISIBLE; privateLayout.visibility = View.GONE
                    rollNumberTextView.text = lawyer.rollNumber.takeIf { it.isNotBlank() } ?: "N/A"
                    specializationPAOTextView.text = lawyer.allSpecializationsString.takeIf { it.isNotBlank() } ?: "N/A"
                    subcategoryTextView.text = lawyer.primarySubcategories
                    addressPAOTextView.text = "Baguio Public Attorney's Office"
                    emailTextView.text = lawyer.email.takeIf { it.isNotBlank() } ?: "N/A"
                    contactTextView.text = lawyer.contactNumber.takeIf { it.isNotBlank() } ?: "N/A"
                } else {
                    paoLayout.visibility = View.GONE; privateLayout.visibility = View.VISIBLE
                    val displayGender = lawyer.gender.takeIf { it.isNotBlank() }?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: "N/A"
                    genderTextView.text = displayGender
                    specializationPrivateTextView.text = lawyer.allSpecializationsString.takeIf { it.isNotBlank() } ?: "N/A"
                    addressPrivateTextView.text = lawyer.officeAddress.takeIf { it.isNotBlank() } ?: "N/A"
                    // Experience removed from display
                    feeTextView.text = if (lawyer.consultationFee == 0.0) "Free" else "PHP ${"%,.0f".format(lawyer.consultationFee)}"
                }
                // --- MODIFIED/CORRECTED Click Listener ---
                itemView.setOnClickListener {
                    // Get context from the itemView
                    val context = itemView.context
                    // Get current user safely within the listener
                    val currentUser = FirebaseAuth.getInstance().currentUser

                    if (currentUser == null) {
                        Toast.makeText(context, "Error: User not signed in.", Toast.LENGTH_SHORT).show()
                        // Optionally redirect to login
                        // context.startActivity(Intent(context, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
                        return@setOnClickListener
                    }

                    Log.d("SELECTED_LAWYER", "Clicked Lawyer ID: ${lawyer.userId}, Name: ${lawyer.fullName}")

                    // Create Intent for ChatActivity
                    val intent = Intent(context, ChatActivity::class.java)

                    // Pass necessary data to ChatActivity
                    // Use generic names for receiver info
                    intent.putExtra("receiverId", lawyer.userId)
                    intent.putExtra("receiverName", lawyer.fullName) // Pass name for Chat title

                    // Optional: Pass current user ID if ChatActivity needs it explicitly
                    // intent.putExtra("senderId", currentUser.uid)

                    // Start ChatActivity
                    context.startActivity(intent)
                }
                // --- END MODIFIED Click Listener ---
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
            // ... (Keep existing logic) ...
            val view = LayoutInflater.from(parent.context).inflate(R.layout.carousel_item, parent, false)
            return CarouselViewHolder(view)
        }

        override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }
}