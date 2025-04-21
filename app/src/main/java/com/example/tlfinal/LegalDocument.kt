package com.example.tlfinal

import android.os.Parcelable
import kotlinx.parcelize.Parcelize // Import Parcelize
import java.util.UUID

@Parcelize // Make it Parcelable to pass between activities easily
data class LegalDocument(
    val id: String = UUID.randomUUID().toString(), // Unique ID, useful if fetching from DB
    val title: String = "",
    val description: String = "",
    val category: String = "", // e.g., "Contracts", "Legal Notices"
    val previewImageUrl: String? = null, // URL or local resource name for preview
    val downloadUrl: String = "" // URL for the actual document download
) : Parcelable