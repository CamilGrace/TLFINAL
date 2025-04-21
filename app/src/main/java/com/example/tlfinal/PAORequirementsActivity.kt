package com.example.tlfinal

// Keep existing imports...
import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.tlfinal.databinding.ActivityPaoRequirementsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import android.webkit.MimeTypeMap

class PAORequirementsActivity : AppCompatActivity() {

    // ... (Keep other variables: binding, storage, fileUri, launchers, etc.) ...
    private lateinit var binding: ActivityPaoRequirementsBinding
    private lateinit var storage: FirebaseStorage
    private var fileUri: Uri? = null
    private lateinit var pickFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestStoragePermissionLauncher: ActivityResultLauncher<String>

    private lateinit var currentPhotoPath: String
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var textRecognizer: TextRecognizer

    private val idTypeList = listOf(
        "Passport", "Driver's License", "UMID",
        "PRC ID", "Postal ID", "National ID", "Voter's ID", "OWWA ID"
    )
    private lateinit var idTypeAdapter: ArrayAdapter<String>
    private var isImageLikelyValidId = false
    private var selectedAffiliation: String? = null

    companion object {
        private const val TAG = "PAORequirementsActivity"
        private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB Example
        private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/jpg")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaoRequirementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedAffiliation = intent.getStringExtra(AffiliationSelectionActivity.EXTRA_AFFILIATION_TYPE)
        Log.d(TAG, "Received affiliation: $selectedAffiliation")
        if (selectedAffiliation == null) {
            // Handle error - affiliation type is essential for saving correctly
            Toast.makeText(this, "Error: Affiliation type missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }


        storage = FirebaseStorage.getInstance()
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        setupIdTypeDropdown()
        setupButtonClickListeners()
        setupActivityResultLaunchers() // <<< Modified
        setupPermissionLaunchers()
        setupFormTextWatchers()

        updateButtonState()
        binding.tvFileRequirementsInfo.visibility = View.VISIBLE
    }

    // --- Setup Functions ---
    // ... (Keep setupIdTypeDropdown, setupButtonClickListeners, setupFormTextWatchers, setupPermissionLaunchers) ...
    private fun setupIdTypeDropdown() {
        idTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, idTypeList)
        // Correct access using binding for AutoCompleteTextView inside TextInputLayout
        binding.idTypeDropdown.setAdapter(idTypeAdapter)
        binding.idTypeDropdown.setOnItemClickListener { _, _, position, _ ->
            updateIdFormatExample(idTypeList[position])
            isImageLikelyValidId = false
            binding.tvImageValidationError.visibility = View.GONE
            updateButtonState()
        }
    }
    private fun setupButtonClickListeners() {
        binding.imageBack.setOnClickListener { finish() }
        binding.btnUploadRequirements.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                selectFile()
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        binding.btnTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhoto() // This now calls takePhotoIntent internally
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        binding.btnProceedMatching.setOnClickListener { uploadFileAndProceed() }
    }

    private fun setupFormTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateButtonState() }
        }
        // Correct access using binding
        binding.idNumberEditText.addTextChangedListener(textWatcher)
        binding.lastNameEditText.addTextChangedListener(textWatcher)
        binding.firstNameEditText.addTextChangedListener(textWatcher)
        binding.middleNameEditText.addTextChangedListener(textWatcher)
        binding.dobEditText.addTextChangedListener(textWatcher)
    }

    private fun setupPermissionLaunchers() {
        requestCameraPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) { Log.d(TAG, "Camera permission granted"); takePhoto() } // Call takePhoto which calls takePhotoIntent
                else { Log.d(TAG, "Camera permission denied"); Toast.makeText(this,"Camera permission is required.", Toast.LENGTH_SHORT).show() }
            }

        requestStoragePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) { Log.d(TAG, "Storage permission granted"); selectFile() }
                else { Log.d(TAG, "Storage permission denied"); Toast.makeText(this,"Storage permission might be needed.", Toast.LENGTH_SHORT).show() }
            }
    }

    // --- *** MODIFIED setupActivityResultLaunchers *** ---
    private fun setupActivityResultLaunchers() {
        pickFileLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        if (validateSelectedFile(uri)) {
                            try {
                                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                contentResolver.takePersistableUriPermission(uri, takeFlags)
                                Log.d(TAG, "Persisted read permission for URI: $uri")
                            } catch (e: SecurityException) {
                                Log.e(TAG, "Could not persist permission: ${e.message}")
                            }
                            fileUri = uri
                            handleImageSelectionSuccess() // Proceed with valid image
                        } else {
                            resetState() // Reset the UI/selection state
                        }
                    } ?: run {
                        Log.w(TAG, "File picker returned OK but data or URI was null.")
                        handleImageSelectionFailure("Failed to retrieve file URI from picker.")
                    }
                } else {
                    handleImageSelectionFailure("File picking cancelled or failed (Result Code: ${result.resultCode}).")
                }
            }

        // *** CHANGE: Use StartActivityForResult for Camera Intent ***
        takePhotoLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                Log.d(TAG,"takePhotoLauncher result: ${result.resultCode}, expected fileUri: $fileUri")
                if (result.resultCode == Activity.RESULT_OK) {
                    if (fileUri != null) {
                        if (validateSelectedFile(fileUri!!)) {
                            try {
                                contentResolver.openInputStream(fileUri!!)?.use { stream ->
                                    if (stream.available() > 0) {
                                        Log.d(TAG,"Photo confirmed via stream: $fileUri")
                                        handleImageSelectionSuccess(isPhotoTaken = true)
                                    } else { handleImageSelectionFailure("Error saving photo (empty file).") }
                                } ?: throw FileNotFoundException("Could not open stream for $fileUri")
                            } catch (e: Exception) { Log.e(TAG, "Error verifying photo URI $fileUri", e); handleImageSelectionFailure("Error verifying photo.") }
                        } else {
                            resetState() // <<< Reset if invalid
                            if (::currentPhotoPath.isInitialized && currentPhotoPath.isNotEmpty()) { try { File(currentPhotoPath).delete() } catch (e: Exception) {} }
                        }
                        // <<< END VALIDATION CALL >>>
                    } else { handleImageSelectionFailure("Error processing photo result (null URI).") }
                } else {
                    // ... (keep cancellation logic) ...
                    if (::currentPhotoPath.isInitialized && currentPhotoPath.isNotEmpty()) { try { File(currentPhotoPath).delete() } catch (e: Exception) { }}
                    handleImageSelectionFailure("Photo capture cancelled.")
                }
            }
    }


    // --- Image Handling & OCR ---
    // ... (Keep handleImageSelectionSuccess, handleImageSelectionFailure, selectFile, displayImage) ...
    private fun handleImageSelectionSuccess(isPhotoTaken: Boolean = false) {
        binding.idDetailsFormLayout.visibility = View.GONE
        binding.tvImageValidationError.visibility = View.GONE
        binding.uploadStatusTextView.text = "Processing image..."
        isImageLikelyValidId = false

        displayImage(fileUri!!)
        if (fileUri != null) {
            runTextRecognitionAndValidation(fileUri!!)
        } else {
            handleImageSelectionFailure("Image URI is invalid.")
        }
        // updateButtonState() is called after validation
    }

    private fun handleImageSelectionFailure(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        resetState()
    }

    private fun selectFile() {
        Log.d(TAG, "Launching file picker (ACTION_GET_CONTENT)")
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        pickFileLauncher.launch(intent)
    }


    // --- *** MODIFIED takePhoto - Calls takePhotoIntent *** ---
    private fun takePhoto() {
        // This function now just calls the intent setup and launch function
        takePhotoIntent()
    }

    // --- *** NEW takePhotoIntent function (renamed from takePhoto) *** ---
    private fun takePhotoIntent() {
        Log.d(TAG, "Attempting to launch camera intent")
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            var photoFile: File? = null
            try { photoFile = createImageFile() } catch (ex: IOException) { Log.e(TAG, "Error creating image file", ex); Toast.makeText(this, "Could not prepare camera (file error).", Toast.LENGTH_SHORT).show(); return }
            if (photoFile != null) {
                val authority = "${packageName}.fileprovider"
                fileUri = FileProvider.getUriForFile(this, authority, photoFile)
                Log.d(TAG, "takePhotoIntent: Created file: ${photoFile.absolutePath}, Provider URI: $fileUri")
                if (fileUri != null) {
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri)
                    grantUriPermissionToCamera(takePictureIntent, fileUri)
                    Log.d(TAG, "takePhotoIntent: Launching camera intent...")
                    takePhotoLauncher.launch(takePictureIntent) // <<< Passing Intent is correct for StartActivityForResult
                } else {
                    Log.e(TAG, "takePhotoIntent: FileProvider null URI.");
                    Toast.makeText(this, "Could not generate URI for camera.",
                        Toast.LENGTH_SHORT).show(); photoFile.delete()
                }
            }
        } else {
            Toast.makeText(this, "No camera app found.", Toast.LENGTH_SHORT).show()
        }

    }


    // ... (Keep grantUriPermissionToCamera, createImageFile, displayImage, runTextRecognitionAndValidation, checkImageType, parseOcrResult, extractValueAfterKeyword, capitalizeWords) ...
    // Helper to grant permissions to camera apps
    private fun grantUriPermissionToCamera(intent: Intent, uri: Uri?) {
        uri ?: return
        val resolvedIntentActivities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolvedIntentInfo in resolvedIntentActivities) {
            val packageName = resolvedIntentInfo.activityInfo.packageName
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Log.d(TAG, "Granted write permission to $packageName for $uri")
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir == null) throw IOException("Cannot get external files directory")
        if (!storageDir.exists() && !storageDir.mkdirs()) throw IOException("Failed to create directory")
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
            Log.d(TAG, "Image file created at: $currentPhotoPath")
        }
    }

    private fun displayImage(uri: Uri) {
        try {
            Log.d(TAG, "Attempting to display image from URI: $uri")
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if(bitmap != null) {
                    binding.capturedImageView.setImageBitmap(bitmap)
                    Log.d(TAG, "Image displayed successfully")
                } else throw IOException("Failed to decode bitmap")
            } ?: throw IOException("ContentResolver failed to open InputStream for $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading/displaying image", e)
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            resetState()
        }
    }
    private fun runTextRecognitionAndValidation(uri: Uri) {
        binding.uploadStatusTextView.text = "Analyzing ID..."
        binding.idDetailsFormLayout.visibility = View.GONE
        binding.tvImageValidationError.visibility = View.GONE
        isImageLikelyValidId = false

        try {
            val image = InputImage.fromFilePath(this, uri)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d("OCR_RESULT", "Full Text:\n${visionText.text}")
                    val selectedIdType = binding.idTypeDropdown.text.toString()
                    isImageLikelyValidId = checkImageType(visionText.text, selectedIdType)

                    if (isImageLikelyValidId) {
                        binding.uploadStatusTextView.text = "Analysis complete. Please verify fields."
                        binding.tvImageValidationError.visibility = View.GONE
                        binding.idDetailsFormLayout.visibility = View.VISIBLE
                        parseOcrResult(visionText)
                    } else {
                        binding.uploadStatusTextView.text = "Validation Failed"
                        binding.tvImageValidationError.text = "Image does not appear to be a valid '$selectedIdType'. Please upload a clear picture of the correct ID."
                        binding.tvImageValidationError.visibility = View.VISIBLE
                        binding.idDetailsFormLayout.visibility = View.GONE
                        fileUri = null
                        binding.capturedImageView.setImageResource(R.drawable.ic_image_placeholder)
                    }
                    updateButtonState()
                }
                .addOnFailureListener { e ->
                    binding.uploadStatusTextView.text = "Text analysis failed. Please fill details manually."
                    binding.idDetailsFormLayout.visibility = View.VISIBLE
                    Log.e("OCR_ERROR", "Text recognition failed", e)
                    Toast.makeText(baseContext, "Could not analyze text: ${e.message}", Toast.LENGTH_SHORT).show()
                    updateButtonState()
                }
        } catch (e: Exception) {
            binding.uploadStatusTextView.text = "Failed to load image for analysis. Please fill details manually."
            binding.idDetailsFormLayout.visibility = View.VISIBLE
            Log.e("OCR_ERROR", "Failed to load image for InputImage", e)
            Toast.makeText(baseContext, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            updateButtonState()
        }
    }
    private fun checkImageType(ocrText: String, idType: String): Boolean { /* ... keep existing ... */
        val textLower = ocrText.lowercase()
        Log.d(TAG, "Checking if text contains hints for ID type: '$idType'")
        return when (idType) {
            "Passport" -> textLower.contains("passport") || textLower.contains("republic of the philippines") && textLower.contains("department of foreign affairs")
            "Driver's License" -> textLower.contains("driver's license") || textLower.contains("land transportation office") || textLower.contains("professional") || textLower.contains("non-professional")
            "SSS Card", "UMID" -> textLower.contains("social security system") || textLower.contains("unified multi-purpose id") || textLower.contains("crn")
            "GSIS Card" -> textLower.contains("government service insurance system") || textLower.contains("gsis")
            "PRC ID" -> textLower.contains("professional regulation commission") || textLower.contains("prc")
            "Postal ID" -> textLower.contains("postal") || textLower.contains("phlpost")
            "National ID" -> textLower.contains("philsys") || textLower.contains("republika ng pilipinas") && textLower.contains("psn")
            "Voter's ID" -> textLower.contains("voter") || textLower.contains("comelec") || textLower.contains("commission on elections")
            "OWWA ID" -> textLower.contains("owwa") || textLower.contains("overseas workers welfare")
            else -> false
        }
    }
    private fun parseOcrResult(texts: Text) { /* ... keep existing ... */
        val fullText = texts.text.lowercase()
        val idNumberPattern = Pattern.compile("\\b(\\d{1,4}[- ]?\\d{3,7}[- ]?\\d{1,4})\\b|\\b(\\d{10,14})\\b")
        val dobPattern = Pattern.compile("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b|\\b(\\d{4}-\\d{2}-\\d{2})\\b")
        var extractedIdNumber: String? = null; var extractedDob: String? = null
        var extractedLastName: String? = null; var extractedFirstName: String? = null; var extractedMiddleName: String? = null
        for (block in texts.textBlocks) { for (line in block.lines) { val lineTextLower = line.text.lowercase(); Log.d("OCR_LINE", line.text)
            if (extractedIdNumber == null) { if (lineTextLower.contains("id no") || lineTextLower.contains("id number") || lineTextLower.contains("no.")) { val p = line.text.replace(Regex("[^0-9-]"), ""); if (p.matches(Regex("\\d{2}-?\\d{7}-?\\d"))) { extractedIdNumber = p.replace("-", ""); Log.d("OCR_PARSE", "Found ID (SSS/UMID) near keyword: $extractedIdNumber")} else if (p.length in 9..14 && !p.contains(Regex("\\d{5,}"))) { extractedIdNumber = p.replace("-", ""); Log.d("OCR_PARSE", "Found ID (Generic) near keyword: $extractedIdNumber")}}
                if (extractedIdNumber == null) { val m = idNumberPattern.matcher(line.text); if (m.find()) { val g = m.group(0)?.replace(Regex("[ -]"), ""); if (g != null && g.length > 5 && g.length < 15 && !g.matches(Regex("\\d{4}"))) { extractedIdNumber = g; Log.d("OCR_PARSE", "Found ID (Pattern): $extractedIdNumber")}}}}
            if (extractedDob == null) { val m = dobPattern.matcher(line.text); if (m.find()) { extractedDob = m.group(0); Log.d("OCR_PARSE", "Found DOB: $extractedDob")} else if (lineTextLower.contains("birth") || lineTextLower.contains("dob")) { val mk = dobPattern.matcher(line.text); if (mk.find()) { extractedDob = mk.group(0); Log.d("OCR_PARSE", "Found DOB near keyword: $extractedDob")}}}
            if (lineTextLower.contains("last name") || lineTextLower.contains("surname")) { extractedLastName = extractValueAfterKeyword(line.text,"last name|surname")?.let { capitalizeWords(it) }; Log.d("OCR_PARSE", "Found Last Name near keyword: $extractedLastName")} else if (lineTextLower.contains("first name") || lineTextLower.contains("given name")) { extractedFirstName = extractValueAfterKeyword(line.text,"first name|given name")?.let { capitalizeWords(it) }; Log.d("OCR_PARSE", "Found First Name near keyword: $extractedFirstName")} else if (lineTextLower.contains("middle name") || lineTextLower.contains("middle initial")) { extractedMiddleName = extractValueAfterKeyword(line.text,"middle name|middle initial")?.let { capitalizeWords(it) }; Log.d("OCR_PARSE", "Found Middle Name near keyword: $extractedMiddleName")} else if (extractedLastName == null && lineTextLower.contains(",") && lineTextLower.length < 50) { val p = line.text.split(","); if (p.size > 1) { val l = p[0].trim(); val fm = p[1].trim().split(Regex("\\s+")); val f = fm.firstOrNull()?.trim(); if (l.matches(Regex("[a-zA-Z'-.\\s]+")) && f?.matches(Regex("[a-zA-Z'-.\\s]+")) == true) { extractedLastName = capitalizeWords(l); extractedFirstName = capitalizeWords(f); if (fm.size > 1) extractedMiddleName = fm.drop(1).joinToString(" ").trim().let { capitalizeWords(it) }; Log.d("OCR_PARSE", "Found Name (Comma): L=$extractedLastName, F=$extractedFirstName, M=$extractedMiddleName")}}} }}
        binding.idNumberEditText.setText(extractedIdNumber ?: ""); binding.lastNameEditText.setText(extractedLastName ?: ""); binding.firstNameEditText.setText(extractedFirstName ?: ""); binding.middleNameEditText.setText(extractedMiddleName ?: ""); binding.dobEditText.setText(extractedDob ?: "")
    }
    private fun extractValueAfterKeyword(line: String, keywordsRegex: String): String? { /* ... keep existing ... */ val p = Pattern.compile("(?:^|\\s)(?i)($keywordsRegex)\\s*[:]?\\s*(.+)"); val m = p.matcher(line); return if (m.find() && m.groupCount() >= 2) m.group(2)?.trim() else { val kp = Pattern.compile("(?i)($keywordsRegex)"); val km = kp.matcher(line); var lke = -1; while(km.find()){ lke = km.end() }; if(lke != -1 && lke < line.length) line.substring(lke).trim().ifEmpty { null } else null } }
    private fun capitalizeWords(str: String): String { /* ... keep existing ... */ return str.split(Regex("\\s+")).joinToString(" ") { w -> w.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } } }


    // --- Upload and State Management ---
    private fun updateButtonState() { /* ... keep existing logic ... */
        val isIdTypeSelected = binding.idTypeDropdown.text.isNotEmpty()
        val isImageSelected = fileUri != null
        val isFormVisible = binding.idDetailsFormLayout.visibility == View.VISIBLE
        val areFormDetailsFilled = !isFormVisible || ( binding.idNumberEditText.text?.isNotEmpty() == true && binding.lastNameEditText.text?.isNotEmpty() == true && binding.firstNameEditText.text?.isNotEmpty() == true )
        binding.btnProceedMatching.isEnabled = isIdTypeSelected && isImageSelected && isImageLikelyValidId && isFormVisible && areFormDetailsFilled
    }

    private fun uploadFileAndProceed() { /* ... keep existing logic using putFile ... */
        if (!binding.btnProceedMatching.isEnabled || !isImageLikelyValidId) {
            if(binding.idTypeDropdown.text.isBlank()){ Toast.makeText(this, "Please select ID type.", Toast.LENGTH_SHORT).show() }
            else if (fileUri == null){ Toast.makeText(this, "Please provide an image.", Toast.LENGTH_SHORT).show() }
            else if (!isImageLikelyValidId) { Toast.makeText(this, "Uploaded image doesn't match selected ID type.", Toast.LENGTH_LONG).show(); binding.tvImageValidationError.visibility = View.VISIBLE }
            else { if (binding.idNumberEditText.text.isNullOrBlank()) binding.idNumberInputLayout.error = "ID Number required" else binding.idNumberInputLayout.error = null; if (binding.lastNameEditText.text.isNullOrBlank()) binding.lastNameInputLayout.error = "Last Name required" else binding.lastNameInputLayout.error = null; if (binding.firstNameEditText.text.isNullOrBlank()) binding.firstNameInputLayout.error = "First Name required" else binding.firstNameInputLayout.error = null; Toast.makeText(this, "Please verify/fill required ID details.", Toast.LENGTH_SHORT).show() }
            return
        }
        binding.idNumberInputLayout.error = null; binding.lastNameInputLayout.error = null; binding.firstNameInputLayout.error = null

        binding.btnProceedMatching.isEnabled = false; binding.uploadStatusTextView.text = "Uploading..."
        val localFileUri = fileUri!!; val storageRef: StorageReference = storage.reference; val fileName = getFileName(localFileUri); val fileRef: StorageReference = storageRef.child("pao_requirements/${UUID.randomUUID()}-$fileName")
        Log.d(TAG, "Attempting upload: $fileName from URI: $localFileUri to path: ${fileRef.path}")
        val metadata = StorageMetadata.Builder().setContentType(contentResolver.getType(localFileUri)).setCustomMetadata("original_filename", fileName).build()
        Log.d(TAG, "Starting putFile with metadata: $metadata")
        val uploadTask = fileRef.putFile(localFileUri, metadata)
        uploadTask.addOnSuccessListener { taskSnapshot -> Log.d(TAG, "putFile success: ${taskSnapshot.bytesTransferred} bytes"); taskSnapshot.metadata?.reference?.downloadUrl?.addOnSuccessListener { downloadUri -> Log.d(TAG, "Got download URL: $downloadUri"); saveDataToFirestore(downloadUri, fileName) }?.addOnFailureListener { e -> handleUploadFailure("Upload ok but failed get URL: ${e.message}", e) } }
            .addOnFailureListener { e -> val se = e as? StorageException; val c = se?.errorCode ?: "N/A"; val h = se?.httpResultCode ?: -1; Log.e(TAG,"Upload failed. Code: $c, HttpCode: $h, Msg: ${e.message}", e); val um = when (c) { StorageException.ERROR_OBJECT_NOT_FOUND -> "File source not found."; StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "Upload failed. Check network."; StorageException.ERROR_CANCELED -> "Upload cancelled."; StorageException.ERROR_UNKNOWN -> "Unknown upload error."; else -> "Upload failed: ${e.localizedMessage}" }; handleUploadFailure(um, e) }
            .addOnProgressListener { taskSnapshot -> val p = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt(); binding.uploadStatusTextView.text = "Uploading... $p%" }
    }

    private fun saveDataToFirestore(downloadUri: Uri, fileName: String) {
        val userId = auth.currentUser?.uid
        if (userId == null || selectedAffiliation == null) { // Also check selectedAffiliation
            handleUploadFailure("User not signed in or affiliation missing")
            return
        }
        val selectedIdType = binding.idTypeDropdown.text.toString(); val idNumber = binding.idNumberEditText.text.toString(); val lastName = binding.lastNameEditText.text.toString(); val firstName = binding.firstNameEditText.text.toString(); val middleName = binding.middleNameEditText.text.toString().takeIf { it.isNotBlank() }; val dob = binding.dobEditText.text.toString()
        val requirementData: MutableMap<String, Any?> = mutableMapOf("userId" to userId, "fileUrl" to downloadUri.toString(), "fileName" to fileName, "timestamp" to com.google.firebase.Timestamp.now(), "idType" to selectedIdType, "idNumber" to idNumber, "lastName" to lastName, "firstName" to firstName, "dateOfBirth" to dob, "affiliationType" to selectedAffiliation); middleName?.let { requirementData["middleName"] = it }
        Log.d(TAG, "Saving PAO data to Firestore: $requirementData")
        db.collection("pao_requirements") // Save to PAO collection
            .add(requirementData)
            .addOnSuccessListener {
                Log.d(TAG, "Firestore save successful, ID: ${it.id}")
                Toast.makeText(this, "ID uploaded successfully!", Toast.LENGTH_SHORT).show()

                // *** PASS AFFILIATION TO LawyerMatchingActivity ***
                val intent = Intent(this, LawyerMatchingActivity::class.java)
                intent.putExtra(AffiliationSelectionActivity.EXTRA_AFFILIATION_TYPE, selectedAffiliation) // Pass it along
                // intent.putExtra("VERIFICATION_COMPLETE", true) // Optional
                startActivity(intent)
                finish()
                // *** --- ***
            }
            .addOnFailureListener { e -> handleUploadFailure("Error saving file info: ${e.message}", e) }
    }
    private fun handleUploadFailure(message: String, exception: Exception? = null) { /* ... keep existing ... */ binding.btnProceedMatching.isEnabled = true; binding.uploadStatusTextView.text = "Upload Failed"; Toast.makeText(this, message, Toast.LENGTH_LONG).show(); if (exception != null) Log.e("PAOReqError", message, exception) else Log.e("PAOReqError", message) }
    private fun resetState() {
        // ... (keep existing reset logic) ...
        fileUri = null; isImageLikelyValidId = false
        binding.uploadStatusTextView.text = ""
        binding.capturedImageView.setImageResource(R.drawable.ic_image_placeholder)
        binding.idDetailsFormLayout.visibility = View.GONE
        binding.idNumberEditText.text = null; binding.lastNameEditText.text = null; binding.firstNameEditText.text = null; binding.middleNameEditText.text = null; binding.dobEditText.text = null
        binding.idTypeDropdown.text = null; binding.idTypeDropdown.clearFocus()
        binding.imgIdFormatExample.visibility = View.GONE
        binding.tvImageValidationError.visibility = View.GONE
        binding.tvFileRequirementsInfo.visibility = View.VISIBLE // <<< Keep requirements visible
        binding.idNumberInputLayout.error = null; binding.lastNameInputLayout.error = null; binding.firstNameInputLayout.error = null
        updateButtonState()
    }
    private fun getFileName(uri: Uri): String { /* ... keep existing ... */ var name = uri.lastPathSegment ?: "image_${UUID.randomUUID()}"; val ls = name.lastIndexOf('/'); if (ls != -1) name = name.substring(ls + 1); if(name.startsWith("image_") || name.contains("%") || !name.contains(".")){ try { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))?.let { name = it } } } catch (e: Exception) { Log.w(TAG, "Could not query display name: $uri", e) } }; if (!name.contains('.')) { val mime = contentResolver.getType(uri); val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { ".$it"} ?: ".img"; name += ext }; return name.replace(Regex("[^a-zA-Z0-9._-]"), "_") }

    // --- NEW FUNCTION: Validate File ---
    private fun validateSelectedFile(uri: Uri): Boolean {
        var isValid = true
        var errorMessage: String? = null
        binding.tvImageValidationError.visibility = View.GONE // Hide previous error

        // 1. Check MIME Type
        val mimeType = contentResolver.getType(uri)
        Log.d(TAG, "Validating file. MIME Type: $mimeType")
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.lowercase(Locale.ROOT))) {
            errorMessage = "Invalid format. Use JPG, JPEG, or PNG."
            isValid = false
        }

        // 2. Check File Size (Only if MIME Type was valid)
        if (isValid) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            val fileSize = cursor.getLong(sizeIndex)
                            Log.d(TAG, "File size: $fileSize bytes")
                            // --- CORRECTED SIZE CHECK ---
                            if (fileSize > MAX_FILE_SIZE_BYTES) {
                                errorMessage = "File too large (Max ${MAX_FILE_SIZE_BYTES / 1024 / 1024}MB)."
                                isValid = false
                            } else {

                            }
                        } else {
                            Log.w(TAG, "Could not determine file size (Index -1 or value is null).")

                        }
                    } else {
                        Log.w(TAG, "Could not move cursor to first row for size check.")
                    }
                } ?: run { // Handle null cursor case
                    Log.w(TAG, "ContentResolver query for size returned null cursor for URI: $uri")
                                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking file size for URI: $uri", e)
                errorMessage = "Could not check file size."
                isValid = false // Treat error during check as invalid
            }
        }

        // Show error message if invalid
        if (!isValid && errorMessage != null) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            binding.tvImageValidationError.text = errorMessage
            binding.tvImageValidationError.visibility = View.VISIBLE
        }
        // No 'else' needed here to hide the error view, it was hidden at the start
        return isValid
    }

    // Function to update ID format example
    private fun updateIdFormatExample(idType: String) {
        binding.tvFileRequirementsInfo.visibility = View.VISIBLE
        val exampleDrawableId = when (idType) {
            "Passport" -> R.drawable.ic_id_placeholder_passport
            "Driver's License" -> R.drawable.ic_id_placeholder_drivers // <<< CHECK THIS DRAWABLE NAME
            "UMID" -> R.drawable.ic_id_placeholder_umid
            "National ID" -> R.drawable.ic_id_placeholder_national
            "PRC ID" -> R.drawable.ic_id_placeholder_prc
            "Postal ID" -> R.drawable.ic_id_placeholder_postal
            "Voter's ID" -> R.drawable.ic_id_placeholder_voters
            "OWWA ID" -> R.drawable.ic_id_placeholder_owwa
            else -> R.drawable.ic_image_placeholder // Default fallback
        }
        binding.imgIdFormatExample.setImageResource(exampleDrawableId)
        binding.imgIdFormatExample.visibility = View.VISIBLE
    }
}