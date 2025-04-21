package com.example.tlfinal

import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tlfinal.databinding.ActivityDocumentDetailBinding
import android.Manifest // Import Manifest
import androidx.appcompat.app.AlertDialog

class DocumentDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDocumentDetailBinding
    private var documentToDownload: LegalDocument? = null

    // Permission Launcher
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    companion object {
        private const val TAG = "DocDetailActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize permission launcher
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission granted.")
                startDownload() // Retry download after permission grant
            } else {
                Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission denied.")
                Toast.makeText(this, "Storage permission is required to download documents.", Toast.LENGTH_LONG).show()
            }
        }

        // Get document data from Intent
        documentToDownload = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("DOCUMENT_DATA", LegalDocument::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("DOCUMENT_DATA")
        }

        if (documentToDownload == null) {
            Log.e(TAG, "No document data received.")
            Toast.makeText(this, "Error: Document details not found.", Toast.LENGTH_SHORT).show()
            finish() // Close activity if no data
            return
        }

        // Populate UI
        binding.tvDocumentTitleDetail.text = documentToDownload!!.title
        binding.tvDocumentDescriptionDetail.text = documentToDownload!!.description
        // TODO: Load preview image into binding.ivDocumentPreview using Glide/Coil/Picasso if URL is provided
        // Example: Glide.with(this).load(documentToDownload!!.previewImageUrl).placeholder(R.drawable.ic_image_placeholder).into(binding.ivDocumentPreview)


        // Setup Listeners
        binding.backButton.setOnClickListener { finish() }
        binding.backToListText.setOnClickListener { finish() } // Make text clickable too

        binding.cbTerms.setOnCheckedChangeListener { _, isChecked ->
            binding.btnDownloadDocument.isEnabled = isChecked // Enable button only if checked
        }

        binding.btnDownloadDocument.setOnClickListener {
            checkPermissionAndDownload()
        }
    }

    private fun checkPermissionAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No specific permission needed for downloads to app-specific directory or MediaStore on Q+
            startDownload()
        } else {
            // Need WRITE_EXTERNAL_STORAGE for older versions
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Storage permission already granted.")
                    startDownload()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    // Explain why permission is needed (optional)
                    AlertDialog.Builder(this)
                        .setTitle("Storage Permission Needed")
                        .setMessage("This app needs permission to save the document to your device's Downloads folder.")
                        .setPositiveButton("OK") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                else -> {
                    // Directly request the permission
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }


    private fun startDownload() {
        val doc = documentToDownload ?: return // Should not be null here, but check again
        if (doc.downloadUrl.isBlank()) {
            Toast.makeText(this, "Download link is missing.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadUri = Uri.parse(doc.downloadUrl)

            val request = DownloadManager.Request(downloadUri).apply {
                // Extract filename, default if needed
                val fileName = doc.downloadUrl.substring(doc.downloadUrl.lastIndexOf('/') + 1)
                    .takeIf { it.isNotBlank() } ?: "${doc.title.replace(" ", "_")}.pdf" // Default extension

                setTitle(doc.title) // Title shown in notification
                setDescription("Downloading ${doc.title}...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // Save to public Downloads directory
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true) // Allow download over mobile data
                setAllowedOverRoaming(true)
            }

            val downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download enqueued with ID: $downloadId. URL: ${doc.downloadUrl}")
            Toast.makeText(this, "Starting download: ${doc.title}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting download", e)
            Toast.makeText(this, "Error starting download: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}