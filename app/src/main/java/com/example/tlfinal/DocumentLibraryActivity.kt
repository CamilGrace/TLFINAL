package com.example.tlfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager // Import LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tlfinal.databinding.ActivityDocumentLibraryBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap // Use LinkedHashMap to preserve order

class DocumentLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentLibraryBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var documentAdapter: DocumentCategoryAdapter // Renamed adapter
    // Store data grouped by category
    private val documentsByCategory = LinkedHashMap<String, MutableList<LegalDocument>>() // Preserve category order
    private val displayList = mutableListOf<Any>() // Holds headers, items, view more buttons for adapter
    private val categoryExpandedState = mutableMapOf<String, Boolean>() // Track expanded categories
    private var currentSearchQuery: String? = null // Store current search query

    companion object {
        private const val TAG = "DocLibraryActivity"
        private const val INITIAL_ITEMS_PER_CATEGORY = 3 // Number of items to show initially
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = Firebase.firestore

        setupToolbar()
        setupRecyclerView()
        setupSearchView()

        fetchDocuments()
    }

    private fun setupToolbar() {
        binding.imgMenuLibrary.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        // The adapter now handles different types (Header, Document, ViewMore)
        documentAdapter = DocumentCategoryAdapter(
            displayList,
            onItemClicked = { document ->
                Log.d(TAG, "Document clicked: ${document.title}")
                val intent = Intent(this, DocumentDetailActivity::class.java)
                intent.putExtra("DOCUMENT_DATA", document)
                startActivity(intent)
            },
            onViewMoreClicked = { category ->
                Log.d(TAG, "View More clicked for category: $category")
                toggleCategoryExpansion(category)
            }
        )
        binding.documentRecyclerView.layoutManager = LinearLayoutManager(this) // Ensure LayoutManager
        binding.documentRecyclerView.adapter = documentAdapter
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query
                filterAndPrepareDisplayList() // Refilter the full data
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText
                filterAndPrepareDisplayList() // Refilter as user types
                return true
            }
        })
        binding.searchView.setOnCloseListener {
            currentSearchQuery = null
            filterAndPrepareDisplayList() // Show full list when search is closed
            false
        }
    }

    private fun fetchDocuments() {
        binding.progressBarLibrary.visibility = View.VISIBLE
        binding.documentRecyclerView.visibility = View.GONE
        binding.tvNoResults.visibility = View.GONE
        Log.d(TAG, "Fetching documents from Firestore...")

        firestore.collection("legal_documents")
            .orderBy("category", Query.Direction.ASCENDING)
            .orderBy("title", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                documentsByCategory.clear() // Clear previous data
                categoryExpandedState.clear() // Reset expanded state
                if (querySnapshot.isEmpty) {
                    Log.w(TAG, "No documents found in Firestore.")
                    // Handled later by filterAndPrepareDisplayList
                } else {
                    Log.d(TAG, "Fetched ${querySnapshot.size()} documents.")
                    querySnapshot.documents.forEach { doc ->
                        try {
                            val document = doc.toObject(LegalDocument::class.java)?.copy(id = doc.id)
                            if (document != null && document.title.isNotBlank() && document.downloadUrl.isNotBlank()) {
                                // Group by category
                                documentsByCategory.getOrPut(document.category) { mutableListOf() }.add(document)
                            } else {
                                Log.w(TAG, "Skipping document ${doc.id}: Missing essential data or failed mapping.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error mapping document ${doc.id}", e)
                        }
                    }
                    Log.d(TAG, "Grouped documents into ${documentsByCategory.size} categories.")
                }
                binding.progressBarLibrary.visibility = View.GONE
                filterAndPrepareDisplayList() // Prepare the initial display list
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching documents", e)
                binding.progressBarLibrary.visibility = View.GONE
                binding.tvNoResults.text = "Error loading documents."
                binding.tvNoResults.visibility = View.VISIBLE
                binding.documentRecyclerView.visibility = View.GONE
                Toast.makeText(this, "Error loading documents: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Function to prepare the list shown by the RecyclerView based on filters and expansion state
    private fun filterAndPrepareDisplayList() {
        displayList.clear()
        val query = currentSearchQuery?.lowercase(Locale.ROOT)?.trim()
        var resultsFound = false

        // Iterate through categories in the order they were fetched (thanks to LinkedHashMap)
        for ((category, documents) in documentsByCategory) {
            // Filter documents within this category based on search query
            val filteredDocs = if (query.isNullOrEmpty()) {
                documents // No search, use all docs for this category
            } else {
                documents.filter {
                    it.title.lowercase(Locale.ROOT).contains(query) ||
                            it.description.lowercase(Locale.ROOT).contains(query) ||
                            it.category.lowercase(Locale.ROOT).contains(query)
                }
            }

            if (filteredDocs.isNotEmpty()) {
                resultsFound = true
                displayList.add(category) // Add category header

                val isExpanded = categoryExpandedState[category] ?: false // Default to collapsed
                val countToShow = if (isExpanded) filteredDocs.size else INITIAL_ITEMS_PER_CATEGORY

                // Add document items up to the limit (or all if expanded)
                displayList.addAll(filteredDocs.take(countToShow))

                // Add "View More" button if there are more items than initially shown and not expanded
                if (!isExpanded && filteredDocs.size > INITIAL_ITEMS_PER_CATEGORY) {
                    displayList.add(DocumentCategoryAdapter.ViewMoreItem(category)) // Add special item type
                }
            }
        }

        documentAdapter.notifyDataSetChanged() // Update RecyclerView
        updateNoResultsView(!resultsFound) // Update "No Results" based on whether *any* category had results
    }

    // Toggles the expansion state for a category and rebuilds the display list
    private fun toggleCategoryExpansion(category: String) {
        val currentState = categoryExpandedState[category] ?: false
        categoryExpandedState[category] = !currentState // Toggle state
        filterAndPrepareDisplayList() // Rebuild and update the list
    }

    // Updated function to show appropriate "No Results" message
    fun updateNoResultsView(isEmpty: Boolean) {
        if (isEmpty && !currentSearchQuery.isNullOrEmpty()) {
            binding.tvNoResults.text = "No documents match your search."
            binding.tvNoResults.visibility = View.VISIBLE
            binding.documentRecyclerView.visibility = View.GONE
        } else if (isEmpty && documentsByCategory.isEmpty() && !binding.progressBarLibrary.isShown) {
            binding.tvNoResults.text = "No documents available."
            binding.tvNoResults.visibility = View.VISIBLE
            binding.documentRecyclerView.visibility = View.GONE
        } else if (isEmpty && currentSearchQuery.isNullOrEmpty() && documentsByCategory.isNotEmpty()) {
            // This case shouldn't logically happen if filtering is correct, but as a fallback:
            binding.tvNoResults.text = "No documents match current filters (Internal check)."
            binding.tvNoResults.visibility = View.VISIBLE
            binding.documentRecyclerView.visibility = View.GONE
        }
        else { // Not empty or progress bar shown
            binding.tvNoResults.visibility = View.GONE
            if (!binding.progressBarLibrary.isShown) {
                binding.documentRecyclerView.visibility = View.VISIBLE
            }
        }
    }
}

// --- Adapter Class (Moved outside Activity or make static nested) ---
class DocumentCategoryAdapter(
    private val displayItems: List<Any>, // Can contain String (header), LegalDocument, or ViewMoreItem
    private val onItemClicked: (LegalDocument) -> Unit,
    private val onViewMoreClicked: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Define View Type constants
    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_DOCUMENT = 1
        private const val VIEW_TYPE_VIEW_MORE = 2
    }

    // Special data class for the "View More" item
    data class ViewMoreItem(val category: String)

    // --- ViewHolders ---
    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view.findViewById(R.id.tvCategoryHeader)
    }

    inner class DocumentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvDocumentTitle)
        val description: TextView = view.findViewById(R.id.tvDocumentDescription)
        val subText: TextView? = view.findViewById(R.id.tvEventSubText) // Optional
        val viewDetailsButton: FrameLayout = view.findViewById(R.id.btnViewDetails)
    }

    inner class ViewMoreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewMoreButton: TextView = view.findViewById(R.id.btnViewMore)
    }

    // --- Adapter Methods ---
    override fun getItemViewType(position: Int): Int {
        return when (displayItems[position]) {
            is String -> VIEW_TYPE_HEADER
            is LegalDocument -> VIEW_TYPE_DOCUMENT
            is ViewMoreItem -> VIEW_TYPE_VIEW_MORE
            else -> throw IllegalArgumentException("Invalid type of data $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_document_category_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_DOCUMENT -> {
                val view = inflater.inflate(R.layout.item_document, parent, false)
                DocumentViewHolder(view)
            }
            VIEW_TYPE_VIEW_MORE -> {
                val view = inflater.inflate(R.layout.item_document_view_more, parent, false)
                ViewMoreViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                val categoryName = displayItems[position] as String
                holder.headerText.text = categoryName
            }
            is DocumentViewHolder -> {
                val document = displayItems[position] as LegalDocument
                holder.title.text = document.title
                holder.description.text = document.description
                // holder.subText?.text = "'${document.category}'" // Example for subtext

                holder.viewDetailsButton.setOnClickListener { // Or holder.itemView.setOnClickListener
                    onItemClicked(document)
                }
            }
            is ViewMoreViewHolder -> {
                val viewMoreItem = displayItems[position] as ViewMoreItem
                holder.viewMoreButton.setOnClickListener {
                    onViewMoreClicked(viewMoreItem.category)
                }
            }
        }
    }

    override fun getItemCount(): Int = displayItems.size

    // No updateList needed here, Activity manages the displayList and notifies adapter
    // The adapter only works with the list it's given in the constructor (or updated externally)
}