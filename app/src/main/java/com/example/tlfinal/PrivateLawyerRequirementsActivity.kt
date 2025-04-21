package com.example.tlfinal

// Keep existing imports...
import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import com.example.tlfinal.databinding.ActivityPrivateLawyerRequirementsBinding // Use correct binding
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
import java.io.FileNotFoundException // Import FNFE
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import android.webkit.MimeTypeMap
import com.example.tlfinal.R // Import R

class PrivateLawyerRequirementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateLawyerRequirementsBinding
    private lateinit var storage: FirebaseStorage
    private var fileUri: Uri? = null
    private lateinit var pickFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Intent> // Use Intent type
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestStoragePermissionLauncher: ActivityResultLauncher<String>
    private lateinit var currentPhotoPath: String
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var textRecognizer: TextRecognizer

    private val idTypeList = listOf(
        "Passport", "Driver's License", "UMID", "SSS Card", "GSIS Card", // Added SSS/GSIS
        "PRC ID", "Postal ID", "National ID", "Voter's ID", "OWWA ID"
    )
    private lateinit var idTypeAdapter: ArrayAdapter<String>
    private var isImageLikelyValidId = false

    private var selectedAffiliation: String? = null

    companion object {
        private const val TAG = "PrivateReqActivity" // Corrected TAG
        private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024
        private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/jpg")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivateLawyerRequirementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // *** GET AFFILIATION FROM INTENT ***
        selectedAffiliation = intent.getStringExtra(AffiliationSelectionActivity.EXTRA_AFFILIATION_TYPE)
        Log.d(TAG, "Received affiliation: $selectedAffiliation")
        if (selectedAffiliation == null) {
            Toast.makeText(this, "Error: Affiliation type missing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // *** --- ***

        storage = FirebaseStorage.getInstance()
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        setupIdTypeDropdown()
        setupButtonClickListeners()
        setupActivityResultLaunchers() // Correct initialization needed
        setupPermissionLaunchers()
        setupFormTextWatchers()

        updateButtonState()
        binding.tvFileRequirementsInfo.visibility = View.VISIBLE
    }

    // --- Setup Functions ---
    private fun setupIdTypeDropdown() {
        idTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, idTypeList)
        binding.idTypeDropdown.setAdapter(idTypeAdapter) // Use binding from correct layout
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                selectFile()
            } else { requestStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE) }
        }
        binding.btnTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhoto()
            } else { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
        }
        binding.btnProceedMatchingPrivate.setOnClickListener { uploadFileAndProceed() } // Use correct ID
    }
    private fun setupFormTextWatchers() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateButtonState() }
        }
        binding.idNumberEditText.addTextChangedListener(textWatcher)
        binding.lastNameEditText.addTextChangedListener(textWatcher)
        binding.firstNameEditText.addTextChangedListener(textWatcher)
        binding.middleNameEditText.addTextChangedListener(textWatcher)
        binding.dobEditText.addTextChangedListener(textWatcher)
    }
    private fun setupPermissionLaunchers() { /* ... Keep existing ... */
        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) takePhoto() else Toast.makeText(this,"Camera needed", Toast.LENGTH_SHORT).show()}
        requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) selectFile() else Toast.makeText(this,"Storage needed", Toast.LENGTH_SHORT).show()}
    }

    // --- *** CORRECTED setupActivityResultLaunchers *** ---
    private fun setupActivityResultLaunchers() {
        pickFileLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        if (validateSelectedFile(uri)) { // Validate first
                            try {
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                Log.d(TAG, "Persisted read permission for URI: $uri")
                            } catch (e: SecurityException) { Log.e(TAG, "Could not persist permission: ${e.message}") }
                            fileUri = uri
                            handleImageSelectionSuccess() // Then handle success
                        } else {
                            resetState() // Reset if validation fails
                        }
                    } ?: handleImageSelectionFailure("Failed to retrieve file URI from picker.")
                } else {
                    handleImageSelectionFailure("File picking cancelled.")
                }
            }

        // Correct initialization using StartActivityForResult
        takePhotoLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                Log.d(TAG,"takePhotoLauncher result: ${result.resultCode}, expected fileUri: $fileUri")
                if (result.resultCode == Activity.RESULT_OK) {
                    val photoUriToCheck = fileUri // Capture uri set *before* camera launch
                    if (photoUriToCheck != null) { // Use the captured uri
                        if (validateSelectedFile(photoUriToCheck)) { // Validate first
                            try {
                                contentResolver.openInputStream(photoUriToCheck)?.use { stream ->
                                    if (stream.available() > 0) {
                                        Log.d(TAG,"Photo confirmed via stream: $photoUriToCheck")
                                        handleImageSelectionSuccess(isPhotoTaken = true)
                                    } else { handleImageSelectionFailure("Error saving photo (empty file).") }
                                } ?: throw FileNotFoundException("Could not open stream for $photoUriToCheck")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error verifying photo URI $photoUriToCheck", e)
                                handleImageSelectionFailure("Error verifying saved photo.")
                            }
                        } else {
                            resetState() // Reset if validation fails
                            cleanupTempPhotoFile() // Clean up file if invalid
                        }
                    } else { handleImageSelectionFailure("Error processing photo result (null URI).") }
                } else {
                    cleanupTempPhotoFile() // Clean up file if cancelled
                    handleImageSelectionFailure("Photo capture cancelled.")
                }
            }
    }
    // Helper to clean up temporary photo file
    private fun cleanupTempPhotoFile(){
        if (::currentPhotoPath.isInitialized && currentPhotoPath.isNotEmpty()) {
            try {
                val tempFile = File(currentPhotoPath)
                if (tempFile.exists()) {
                    tempFile.delete()
                    Log.d(TAG, "Deleted temporary photo file: $currentPhotoPath")
                }
            } catch (e: Exception) { Log.e(TAG, "Error deleting temporary photo file", e)}
            currentPhotoPath = "" // Reset path
        }
    }

    private fun validateSelectedFile(uri: Uri): Boolean {
        var isValid = true
        var errorMessage: String? = null
        binding.tvImageValidationError.visibility = View.GONE

        // 1. Check MIME Type
        val mimeType = contentResolver.getType(uri)
        Log.d(TAG, "Validating file. MIME Type: $mimeType")
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.lowercase(Locale.ROOT))) {
            errorMessage = "Invalid format. Use JPG, JPEG, or PNG."
            isValid = false
        }

        // 2. Check File Size
        if (isValid) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            val fileSize = cursor.getLong(sizeIndex)
                            Log.d(TAG, "File size: $fileSize bytes")
                            if (fileSize > MAX_FILE_SIZE_BYTES) {
                                errorMessage = "File too large (Max ${MAX_FILE_SIZE_BYTES / 1024 / 1024}MB)."
                                isValid = false
                            } else {

                            }
                        } else { Log.w(TAG, "Could not determine file size (Index -1 or null).") }
                    } else { Log.w(TAG, "Could not move cursor for size check.") }
                } ?: Log.w(TAG, "Size query returned null cursor for URI: $uri")
            } catch (e: Exception) { Log.e(TAG, "Error checking file size for URI: $uri", e); errorMessage = "Could not check file size."; isValid = false }
        }

        if (!isValid && errorMessage != null) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            binding.tvImageValidationError.text = errorMessage
            binding.tvImageValidationError.visibility = View.VISIBLE
        }

        return isValid
    }

    // --- Image Handling & OCR ---
    // ... (Keep handleImageSelectionSuccess, handleImageSelectionFailure, selectFile, displayImage) ...
    private fun handleImageSelectionSuccess(isPhotoTaken: Boolean = false) { /* ... keep existing ... */
        binding.idDetailsFormLayout.visibility = View.GONE; binding.tvImageValidationError.visibility = View.GONE; binding.uploadStatusTextView.text = "Processing image..."; isImageLikelyValidId = false; displayImage(fileUri!!); if (fileUri != null) runTextRecognitionAndValidation(fileUri!!) else handleImageSelectionFailure("Invalid URI") }
    private fun handleImageSelectionFailure(message: String) { /* ... keep existing ... */ Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); resetState() }
    private fun selectFile() { /* ... keep existing ... */ Log.d(TAG, "Select file"); val i = Intent(Intent.ACTION_GET_CONTENT).apply{type="image/*";addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}; pickFileLauncher.launch(i) }
    private fun takePhoto() { /* ... Keep existing ... */ Log.d(TAG, "Take Photo"); takePhotoIntent() } // Calls helper
    private fun takePhotoIntent() { /* ... Keep existing logic to launch camera intent with StartActivityForResult ... */
        Log.d(TAG, "Attempting launch camera intent"); val i=Intent(MediaStore.ACTION_IMAGE_CAPTURE); if(i.resolveActivity(packageManager)!=null){ var pf:File?=null; try{pf=createImageFile()}catch(e:IOException){Log.e(TAG,"File create error",e);Toast.makeText(this,"Camera prep error",Toast.LENGTH_SHORT).show();return}; pf?.also{f->val a="${packageName}.fileprovider";fileUri=FileProvider.getUriForFile(this,a,f);Log.d(TAG,"Created ${f.absolutePath}, URI $fileUri");if(fileUri!=null){i.putExtra(MediaStore.EXTRA_OUTPUT,fileUri);grantUriPermissionToCamera(i,fileUri);Log.d(TAG,"Launching cam intent");takePhotoLauncher.launch(i)}else{Log.e(TAG,"Provider URI null");Toast.makeText(this,"URI error",Toast.LENGTH_SHORT).show();f.delete()}}}else Toast.makeText(this,"No camera app",Toast.LENGTH_SHORT).show() }
    private fun grantUriPermissionToCamera(intent: Intent, uri: Uri?) { /* ... Keep existing ... */ uri?:return; val r=packageManager.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY); for(ri in r){val pn=ri.activityInfo.packageName; grantUriPermission(pn,uri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION); Log.d(TAG,"Granted perm to $pn for $uri")} }
    @Throws(IOException::class) private fun createImageFile(): File { /* ... Keep existing ... */ val tS=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date()); val sD=getExternalFilesDir(Environment.DIRECTORY_PICTURES)?:throw IOException("No external dir"); if(!sD.exists()&& !sD.mkdirs()) throw IOException("Failed dir create"); return File.createTempFile("JPEG_${tS}_",".jpg",sD).apply{currentPhotoPath=absolutePath;Log.d(TAG,"File created: $currentPhotoPath")} }
    private fun displayImage(uri: Uri) { /* ... Keep existing ... */ try {Log.d(TAG,"Display $uri"); contentResolver.openInputStream(uri)?.use{val b=BitmapFactory.decodeStream(it); if(b!=null)binding.capturedImageView.setImageBitmap(b) else throw IOException("Decode fail")}?:throw IOException("Stream fail $uri")}catch(e:Exception){Log.e(TAG,"Display error",e);Toast.makeText(this,"Image load error",Toast.LENGTH_SHORT).show();resetState()} }

    // --- *** RESTORED runTextRecognitionAndValidation *** ---
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
                    val selectedIdType = binding.idTypeDropdown.text.toString() // Use binding
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

    // --- *** RESTORED checkImageType *** ---
    private fun checkImageType(ocrText: String, idType: String): Boolean {
        val textLower = ocrText.lowercase()
        Log.d(TAG, "Checking if text contains hints for ID type: '$idType'")
        // Modify this check if needed for private flow (e.g., return true)
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

    // --- *** RESTORED parseOcrResult *** ---
    private fun parseOcrResult(texts: Text) {
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

    // --- *** RESTORED extractValueAfterKeyword *** ---
    private fun extractValueAfterKeyword(line: String, keywordsRegex: String): String? {
        val pattern = Pattern.compile("(?:^|\\s)(?i)($keywordsRegex)\\s*[:]?\\s*(.+)")
        val matcher = pattern.matcher(line)
        return if (matcher.find() && matcher.groupCount() >= 2) {
            matcher.group(2)?.trim()
        } else {
            val keywordPattern = Pattern.compile("(?i)($keywordsRegex)")
            val keywordMatcher = keywordPattern.matcher(line)
            var lastKeywordEnd = -1
            while(keywordMatcher.find()){ lastKeywordEnd = keywordMatcher.end() }
            if(lastKeywordEnd != -1 && lastKeywordEnd < line.length) line.substring(lastKeywordEnd).trim().ifEmpty { null } else null
        }
    }

    // --- *** RESTORED capitalizeWords *** ---
    private fun capitalizeWords(str: String): String {
        return str.split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }



    // --- Upload and State Management ---
    private fun updateButtonState() {
        // Use binding
        val isIdTypeSelected = binding.idTypeDropdown.text.isNotEmpty()
        val isImageSelected = fileUri != null
        val isFormVisible = binding.idDetailsFormLayout.visibility == View.VISIBLE
        val areFormDetailsFilled = !isFormVisible || (
                binding.idNumberEditText.text?.isNotEmpty() == true &&
                        binding.lastNameEditText.text?.isNotEmpty() == true &&
                        binding.firstNameEditText.text?.isNotEmpty() == true
                )
        // Adjust ID check logic if needed for private flow
        binding.btnProceedMatchingPrivate.isEnabled = isIdTypeSelected && isImageSelected && isImageLikelyValidId && isFormVisible && areFormDetailsFilled // Use correct button ID
    }

    private fun uploadFileAndProceed() {
        // Use binding
        if (!binding.btnProceedMatchingPrivate.isEnabled /*|| !isImageLikelyValidId*/) { // Check correct button ID
            // ... (keep validation messages, use binding for IDs) ...
            if(binding.idTypeDropdown.text.isBlank()){ Toast.makeText(this, "Select ID type", Toast.LENGTH_SHORT).show() } else if (fileUri == null){ Toast.makeText(this, "Provide image", Toast.LENGTH_SHORT).show() } else if (!isImageLikelyValidId && checkImageType(binding.idNumberEditText.text.toString(), binding.idTypeDropdown.text.toString())) { /* Recheck image validity if needed or remove */ Toast.makeText(this, "Invalid ID image?", Toast.LENGTH_LONG).show(); binding.tvImageValidationError.visibility = View.VISIBLE } else { if (binding.idNumberEditText.text.isNullOrBlank()) binding.idNumberInputLayout.error = "ID Required" else binding.idNumberInputLayout.error=null; if (binding.lastNameEditText.text.isNullOrBlank()) binding.lastNameInputLayout.error = "Last Name required" else binding.lastNameInputLayout.error=null; if (binding.firstNameEditText.text.isNullOrBlank()) binding.firstNameInputLayout.error = "First Name required" else binding.firstNameInputLayout.error=null; Toast.makeText(this, "Verify/fill details", Toast.LENGTH_SHORT).show() }
            return
        }
        // ... (keep error clearing, progress setup, use binding) ...
        binding.idNumberInputLayout.error=null; binding.lastNameInputLayout.error=null; binding.firstNameInputLayout.error=null
        binding.btnProceedMatchingPrivate.isEnabled = false; binding.uploadStatusTextView.text = "Uploading..."

        val localFileUri = fileUri!!
        val storageRef: StorageReference = storage.reference
        val fileName = getFileName(localFileUri)
        val fileRef: StorageReference = storageRef.child("private_id_verifications/${UUID.randomUUID()}-$fileName") // Use specific path

        Log.d(TAG, "Uploading private ID: $fileName from URI: $localFileUri to path: ${fileRef.path}")
        val metadata = StorageMetadata.Builder().setContentType(contentResolver.getType(localFileUri)).setCustomMetadata("original_filename", fileName).build()
        val uploadTask = fileRef.putFile(localFileUri, metadata)

        uploadTask.addOnSuccessListener { taskSnapshot ->
            Log.d(TAG, "Upload OK: ${taskSnapshot.bytesTransferred} bytes")
            taskSnapshot.metadata?.reference?.downloadUrl?.addOnSuccessListener { downloadUri ->
                Log.d(TAG, "Got URL: $downloadUri")
                saveDataToFirestore(downloadUri, fileName) // Save to specific collection
            }?.addOnFailureListener { e -> handleUploadFailure("Failed get URL: ${e.message}", e) }
        }.addOnFailureListener { e -> handleUploadFailure("Upload failed: ${e.message}", e)
        }.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
            binding.uploadStatusTextView.text = "Uploading... $progress%"
        }
    }


    // --- Modified saveDataToFirestore ---
    private fun saveDataToFirestore(downloadUri: Uri, fileName: String) {
        val userId = auth.currentUser?.uid
        if (userId == null || selectedAffiliation == null) {
            handleUploadFailure("User not signed in or affiliation missing")
            return
        }
        val selectedIdType = binding.idTypeDropdown.text.toString()
        val idNumber = binding.idNumberEditText.text.toString()
        val lastName = binding.lastNameEditText.text.toString()
        val firstName = binding.firstNameEditText.text.toString()
        val middleName = binding.middleNameEditText.text.toString().takeIf { it.isNotBlank() }
        val dob = binding.dobEditText.text.toString()

        val requirementData: MutableMap<String, Any?> = mutableMapOf(
            "userId" to userId, "fileUrl" to downloadUri.toString(), "fileName" to fileName,
            "timestamp" to com.google.firebase.Timestamp.now(), "idType" to selectedIdType,
            "idNumber" to idNumber, "lastName" to lastName, "firstName" to firstName,
            "dateOfBirth" to dob, "affiliationType" to selectedAffiliation )
        middleName?.let { requirementData["middleName"] = it }

        Log.d(TAG, "Saving Private verification data: $requirementData")
        db.collection("private_id_verifications") // Save to private collection
            .add(requirementData)
            .addOnSuccessListener {
                Log.d(TAG, "Private ID data saved, ID: ${it.id}")
                Toast.makeText(this, "ID uploaded successfully!", Toast.LENGTH_SHORT).show()

                // *** PASS AFFILIATION TO LawyerMatchingActivity ***
                val intent = Intent(this, LawyerMatchingActivity::class.java)
                intent.putExtra(AffiliationSelectionActivity.EXTRA_AFFILIATION_TYPE, selectedAffiliation) // Pass it along
                // intent.putExtra("VERIFICATION_COMPLETE", true) // Optional
                startActivity(intent)
                finish()
                // *** --- ***
            }
            .addOnFailureListener { e -> handleUploadFailure("Error saving ID info: ${e.message}", e) }
    }

    private fun handleUploadFailure(message: String, exception: Exception? = null) {
        // Use binding
        binding.btnProceedMatchingPrivate.isEnabled = true // Use correct ID
        binding.uploadStatusTextView.text = "Upload Failed"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (exception != null) Log.e(TAG, message, exception) else Log.e(TAG, message) // Use consistent TAG
    }

    private fun resetState() {
        // Use binding
        fileUri = null; isImageLikelyValidId = false
        binding.uploadStatusTextView.text = ""
        binding.capturedImageView.setImageResource(R.drawable.ic_image_placeholder)
        binding.idDetailsFormLayout.visibility = View.GONE
        binding.idNumberEditText.text = null; binding.lastNameEditText.text = null; binding.firstNameEditText.text = null; binding.middleNameEditText.text = null; binding.dobEditText.text = null
        binding.idTypeDropdown.text = null; binding.idTypeDropdown.clearFocus() // Use correct ID
        binding.imgIdFormatExample.visibility = View.GONE
        binding.tvImageValidationError.visibility = View.GONE
        binding.tvFileRequirementsInfo.visibility = View.VISIBLE
        binding.idNumberInputLayout.error = null; binding.lastNameInputLayout.error = null; binding.firstNameInputLayout.error = null
        updateButtonState()
    }

    private fun getFileName(uri: Uri): String { /* ... keep existing ... */
        var n=uri.lastPathSegment?:"img_${UUID.randomUUID()}";val s=n.lastIndexOf('/');if(s!=-1)n=n.substring(s+1);if(n.startsWith("img_")||n.contains("%")||!n.contains(".")){try{contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))?.let{n=it}}}catch(e:Exception){Log.w(TAG,"Query name fail:$uri",e)}};if(!n.contains('.')){val m=contentResolver.getType(uri);val x=MimeTypeMap.getSingleton().getExtensionFromMimeType(m)?.let{".$it"}?:".img";n+=x};return n.replace(Regex("[^a-zA-Z0-9._-]"),"_")}

    // --- Function to update ID format example ---
    private fun updateIdFormatExample(idType: String) {
        binding.tvFileRequirementsInfo.visibility = View.VISIBLE
        val exampleDrawableId = when (idType) {
            "Passport" -> R.drawable.ic_id_placeholder_passport
            "Driver's License" -> R.drawable.ic_id_placeholder_drivers
            "UMID" -> R.drawable.ic_id_placeholder_umid
            "National ID" -> R.drawable.ic_id_placeholder_national
            "PRC ID" -> R.drawable.ic_id_placeholder_prc
            "Postal ID" -> R.drawable.ic_id_placeholder_postal
            "Voter's ID" -> R.drawable.ic_id_placeholder_voters
            "OWWA ID" -> R.drawable.ic_id_placeholder_owwa
            else -> R.drawable.ic_image_placeholder
        }
        binding.imgIdFormatExample.setImageResource(exampleDrawableId)
        binding.imgIdFormatExample.visibility = View.VISIBLE
    }
}