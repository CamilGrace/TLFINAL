package com.example.tlfinal

data class Lawyer(
    // Common fields fetched for all
    val userId: String = "", // Firebase Auth UID stored during lawyer registration
    val firstName: String = "",
    val middleName: String? = null, // Optional
    val lastName: String = "",
    val email: String = "",         // Added - Assuming collected during registration
    val contactNumber: String = "",  // Added - Assuming collected during registration
    val affiliation: String = "Private", // Added: e.g., "PAO" or "Private" - Needs to be set during registration

    // Expertise (Crucial - matches registration structure)
    // List of maps like { "specialization": "Family Law", "subcategories": ["Divorce", "Annulment"] }
    val legalSpecializations: List<Map<String, Any>> = emptyList(),

    // PAO Specific (Might be blank/default for private)
    val rollNumber: String = "",     // Added - Assuming collected during PAO registration

    // Private Specific (Or fields used for filtering all types)
    val gender: String = "",        // Needed for filtering
    val yearsOfExperience: Int = 0, // Needed for filtering (Assuming registration collected this)
    val consultationFee: Double = 0.0, // Needed for filtering (Assuming registration collected this)
    val officeAddress: String = ""  // Used for Private lawyer display

    // Add profileImageUrl: String = "" if you have profile pictures
) {
    // Helper property for full name display
    val fullName: String
        @JvmName("getFullNameJava") // Add this if you encounter platform declaration clash
        get() = "$firstName ${middleName?.firstOrNull()?.let { "$it." } ?: ""} $lastName".trim()

    // Helper to get primary specialization string for display
    val primarySpecialization: String
        get() = (legalSpecializations.firstOrNull()?.get("specialization") as? String)?.takeIf { it.isNotBlank() } ?: "N/A"

    // Helper to get subcategories string for display (e.g., for the primary specialization)
    val primarySubcategories: String
        get() = (legalSpecializations.firstOrNull()?.get("subcategories") as? List<*>)
            ?.filterIsInstance<String>() // Ensure they are strings
            ?.filter { it.isNotBlank() }
            ?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: "N/A"

    // Helper to get ALL specializations combined string
    val allSpecializationsString: String
        get() = legalSpecializations.mapNotNull { it["specialization"] as? String }.filter { it.isNotBlank() }.joinToString(", ")

    // Helper to check if a lawyer matches the selected category (more robust)
    fun matchesCategory(category: String): Boolean {
        if (category.isBlank()) return true // If no category selected, maybe match all? Or handle appropriately.
        // Check if the category matches any specialization OR any subcategory of any specialization
        return legalSpecializations.any { specMap ->
            val specName = specMap["specialization"] as? String
            val subCats = (specMap["subcategories"] as? List<*>)?.filterIsInstance<String>()

            specName?.equals(category, ignoreCase = true) == true ||
                    subCats?.any { it.equals(category, ignoreCase = true) } == true
        }
    }

    fun hasSubcategoryWithinSpecialization(mainCategory: String, subCategory: String): Boolean {
        if (mainCategory.isBlank() || subCategory.isBlank()) return false
        return legalSpecializations.any { specMap ->
            val specName = specMap["specialization"] as? String
            // Check if this map is for the correct main category
            if (specName?.equals(mainCategory, ignoreCase = true) == true) {
                val subCats = (specMap["subcategories"] as? List<*>)?.filterIsInstance<String>()
                // Check if this specialization's subcategories contain the required one
                subCats?.any { it.equals(subCategory, ignoreCase = true) } == true
            } else {
                false // Not the right main category for this map entry
            }
        }
    }
}