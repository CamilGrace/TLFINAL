package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.tlfinal.databinding.MatchesFoundBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MatchesFoundActivity : AppCompatActivity() {

    private lateinit var binding: MatchesFoundBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var lawyerList: List<Lawyer> // To hold the fetched lawyers data
    private lateinit var selectedLegalCategory: String // Category passed from LawyerMatchingActivity
    private var selectedGender: String? = null // Gender preference
    private var maxYearsOfExperience: Int = Int.MAX_VALUE // Maximum years of experience
    private var maxConsultationFee: Double = Double.MAX_VALUE // Maximum consultation fee

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.matches_found)
        binding = MatchesFoundBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle Back Button click
        binding.imageBack.setOnClickListener {
            finish() // Close the activity and go back to the previous screen
        }

        // Get preferences and legal category from the Intent
        selectedLegalCategory = intent.getStringExtra("LEGAL_CATEGORY") ?: ""
        selectedGender = intent.getStringExtra("selectedGender") // e.g., "Male" or "Female"
        maxYearsOfExperience = intent.getIntExtra("maxYearsOfExperience", Int.MAX_VALUE)
        maxConsultationFee = intent.getDoubleExtra("maxConsultationFee", Double.MAX_VALUE)

        firestore = FirebaseFirestore.getInstance()

        // Initialize RecyclerView for the carousel
        val recyclerView = findViewById<RecyclerView>(R.id.carouselRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // Attach PagerSnapHelper for centering the cards
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        // Fetch the lawyers data from Firestore
        fetchLawyersData(recyclerView)
    }

    private fun fetchLawyersData(recyclerView: RecyclerView) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let {
            firestore.collection("lawyers")
                .get()
                .addOnSuccessListener { querySnapshot ->
                    lawyerList = querySnapshot.documents.mapNotNull { document ->
                        document.toObject(Lawyer::class.java)
                    }

                    val filteredLawyers = lawyerList.filter { lawyer ->
                        val legalServicesList = lawyer.legalservices.split(",").map { it.trim() }
                        val matchesCategory = legalServicesList.any { it.equals(selectedLegalCategory, ignoreCase = true) }
                        (selectedGender == null || lawyer.gender.equals(selectedGender, ignoreCase = true)) &&
                                matchesCategory &&
                                lawyer.yearsofexp <= maxYearsOfExperience &&
                                lawyer.consultationfee <= maxConsultationFee
                    }

                    val noMatchesTextView = findViewById<TextView>(R.id.textNoMatchesFound)
                    if (filteredLawyers.isEmpty()) {
                        noMatchesTextView.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        noMatchesTextView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        recyclerView.adapter = CarouselAdapter(filteredLawyers) { selectedLawyer ->
                            // Navigate to ClientMessagingActivity
                            val intent = Intent(this, ClientMessagingActivity::class.java)
                            intent.putExtra("lawyerFullName", selectedLawyer.fullName)
                            startActivity(intent)
                        }
                    }

                    recyclerView.post {
                        recyclerView.scrollToPosition(0)
                    }

                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error fetching data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Data class for Lawyer
    data class Lawyer(
        val fullName: String = "",
        val gender: String = "",
        val legalservices: String = "",
        val yearsofexp: Int = 0,
        val consultationfee: Double = 0.0,
        val address: String = ""
    )


    // Carousel Adapter
    class CarouselAdapter(
        private val items: List<Lawyer>,
        private val onLawyerSelected: (Lawyer) -> Unit // Callback for item click
    ) : RecyclerView.Adapter<CarouselAdapter.CarouselViewHolder>() {

        class CarouselViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: TextView = itemView.findViewById(R.id.textFullname)
            val genderTextView: TextView = itemView.findViewById(R.id.textGender)
            val lawCategoryTextView: TextView = itemView.findViewById(R.id.userLegal)
            val experienceTextView: TextView = itemView.findViewById(R.id.userYearsofExp)
            val feeTextView: TextView = itemView.findViewById(R.id.userConsultationFee)
            val addressTextView: TextView = itemView.findViewById(R.id.userAddress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarouselViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.carousel_item, parent, false)
            return CarouselViewHolder(view)
        }

        override fun onBindViewHolder(holder: CarouselViewHolder, position: Int) {
            val lawyer = items[position]
            holder.nameTextView.text = "Atty. ${lawyer.fullName}"
            holder.genderTextView.text = lawyer.gender
            val legalServicesList = lawyer.legalservices.split(",").map { it.trim() }
            holder.lawCategoryTextView.text = legalServicesList.joinToString(", ")
            holder.experienceTextView.text = "${lawyer.yearsofexp} years of experience"
            holder.feeTextView.text = "${lawyer.consultationfee} pesos"
            holder.addressTextView.text = lawyer.address
            // Set click listener for the card
            holder.itemView.setOnClickListener {
                onLawyerSelected(lawyer) // Trigger callback
            }
        }


        override fun getItemCount(): Int = items.size
    }

}