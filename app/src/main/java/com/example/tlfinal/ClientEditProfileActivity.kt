package com.example.tlfinal

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater // Import LayoutInflater if using dialogs later
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.tlfinal.databinding.ClientEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*



class ClientEditProfileActivity : AppCompatActivity() {
    private lateinit var binding: ClientEditProfileBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private lateinit var faceDetector: FaceDetector

    private var currentPhotoPath: String? = null
    private var selectedImageUri: Uri? = null
    private var currentProfileImageUrl: String? = null
    private var isNewImageSelected = false

    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePhotoLauncher: ActivityResultLauncher<Uri>
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestStoragePermissionLauncher: ActivityResultLauncher<String>


    companion object {
        private const val TAG = "ClientEditProfile"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ClientEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()

        setupFaceDetector()
        setupListeners()
        setupActivityResultLaunchers()
        setupPermissionLaunchers()
        loadInitialData()
    }

    private fun setupFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    private fun setupListeners() {
        binding.imageBack.setOnClickListener { finish() }
        binding.containerUploadPhoto.setOnClickListener { showImagePickDialog() }

        // Attach listener to update button state
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { checkAndUpdateSaveButtonState() } // Call check function
        }
        binding.editFullName.addTextChangedListener(textWatcher)
        binding.editEmail.addTextChangedListener(textWatcher)
        binding.editContact.addTextChangedListener(textWatcher)
        binding.editAddress.addTextChangedListener(textWatcher)

        binding.saveButton.setOnClickListener { if (validateInput()) saveProfileData() }
    }

    private fun setupPermissionLaunchers() {
        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { Log.d(TAG,"Camera permission granted"); takePhotoIntent() }
            else { Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show() }
        }
        requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { Log.d(TAG,"Storage permission granted"); selectImageIntent() }
            else { Toast.makeText(this, "Storage permission needed", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun setupActivityResultLaunchers() {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    Log.d(TAG, "Image selected: $uri")
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: SecurityException){ Log.e(TAG,"Failed to persist permission", e)}
                    processSelectedImage(uri)
                } ?: Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show()
            } else { Log.d(TAG, "Image picking cancelled") }
        }

        takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val photoUri = selectedImageUri
            if (success && photoUri != null) {
                Log.d(TAG, "Photo taken: $photoUri")
                processSelectedImage(photoUri)
            } else {
                Log.d(TAG, "Photo capture cancelled/failed. URI: $photoUri")
                currentPhotoPath?.let { try { File(it).delete() } catch (e: Exception){} }
                selectedImageUri = null
            }
        }
    }

    private fun loadInitialData() {
        val userId = auth.currentUser?.uid ?: return
        binding.progressBarUpload.visibility = View.VISIBLE

        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                binding.progressBarUpload.visibility = View.GONE
                if (document.exists()) {
                    binding.editFullName.setText(document.getString("fullName"))
                    binding.editEmail.setText(document.getString("email"))
                    binding.editContact.setText(document.getString("contactNo"))
                    binding.editAddress.setText(document.getString("address"))
                    currentProfileImageUrl = document.getString("profileImageUrl")
                    loadProfileImage(currentProfileImageUrl, binding.imgProfilePicture)
                } else { Toast.makeText(this, "Could not load profile.", Toast.LENGTH_SHORT).show() }
                checkAndUpdateSaveButtonState() // Update button after loading
            }
            .addOnFailureListener { e ->
                binding.progressBarUpload.visibility = View.GONE
                Toast.makeText(this, "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error loading profile", e)
            }
    }

    private fun showImagePickDialog() { /* ... keep existing ... */
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Change Profile Photo")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> checkCameraPermissionAndTakePhoto()
                    1 -> checkStoragePermissionAndSelectImage()
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }
    private fun checkCameraPermissionAndTakePhoto() { /* ... keep existing ... */
        when { ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> takePhotoIntent()
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> { Toast.makeText(this,"Camera permission needed.", Toast.LENGTH_LONG).show(); requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
            else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) } }
    private fun checkStoragePermissionAndSelectImage() { /* ... keep existing ... */
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        when { ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> selectImageIntent()
            ActivityCompat.shouldShowRequestPermissionRationale(this, permission) -> { Toast.makeText(this,"Storage permission needed.", Toast.LENGTH_LONG).show(); requestStoragePermissionLauncher.launch(permission) }
            else -> requestStoragePermissionLauncher.launch(permission) } }
    private fun selectImageIntent() { /* ... keep existing ... */
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI); pickImageLauncher.launch(intent) }
    private fun takePhotoIntent() {
        var photoFile: File? = null // Declare here to access in catch/finally
        var providerUri: Uri? = null // Declare here

        try {
            photoFile = createImageFile() // This sets currentPhotoPath
            val authority = "${packageName}.fileprovider"
            providerUri = FileProvider.getUriForFile(this, authority, photoFile) // Generate the output URI

            Log.d(TAG, "takePhotoIntent: Created file: ${photoFile.absolutePath}, Provider URI: $providerUri")

            if (providerUri != null) {
                // *** CRITICAL: Assign to selectedImageUri RIGHT BEFORE LAUNCH ***
                selectedImageUri = providerUri
                Log.d(TAG, "takePhotoIntent: Launching camera with output URI: $selectedImageUri")
                // The TakePicture contract expects the output URI as input
                takePhotoLauncher.launch(selectedImageUri!!)
            } else {
                // This case should ideally not happen if FileProvider is set up correctly
                Log.e(TAG, "takePhotoIntent: FileProvider returned null URI for file: ${photoFile?.absolutePath}")
                Toast.makeText(this, "Could not generate URI for camera.", Toast.LENGTH_SHORT).show()
                selectedImageUri = null // Ensure it's null if URI generation failed
                photoFile?.delete() // Clean up the unused file
            }

        } catch (ex: IOException) {
            Log.e(TAG, "takePhotoIntent: Error creating image file", ex)
            Toast.makeText(this, "Could not prepare camera (file error).", Toast.LENGTH_SHORT).show()
            selectedImageUri = null // Reset URI on error
            currentPhotoPath = null
            photoFile?.delete() // Clean up potentially created file on error
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "takePhotoIntent: Error getting FileProvider URI (likely authority mismatch or setup issue)", ex)
            Toast.makeText(this, "Could not prepare camera (URI error).", Toast.LENGTH_SHORT).show()
            selectedImageUri = null
            photoFile?.delete()
        }
    }
    @Throws(IOException::class) private fun createImageFile(): File { /* ... keep existing ... */
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if (storageDir == null) throw IOException("Cannot get external files directory.")
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply { currentPhotoPath = absolutePath; Log.d(TAG, "Temp file created: $currentPhotoPath") } }

    private fun processSelectedImage(uri: Uri) {
        // ... (Keep existing face detection logic) ...
        Log.d(TAG, "Processing selected image URI: $uri")
        binding.progressBarUpload.visibility = View.VISIBLE
        binding.tvImageError.visibility = View.GONE

        try {
            val image = InputImage.fromFilePath(this, uri)
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    binding.progressBarUpload.visibility = View.GONE
                    if (faces.isNotEmpty()) {
                        Log.d(TAG, "Face detected.")
                        selectedImageUri = uri
                        isNewImageSelected = true
                        // Load LOCAL URI preview
                        Glide.with(this).load(selectedImageUri).apply(RequestOptions.circleCropTransform()).placeholder(R.drawable.ic_person_green).error(R.drawable.ic_person_red).into(binding.imgProfilePicture)
                        checkAndUpdateSaveButtonState()
                    } else {
                        Log.w(TAG, "No face detected.")
                        Toast.makeText(this, "Please select a photo with a clear face.", Toast.LENGTH_LONG).show()
                        binding.tvImageError.text = "No face detected. Try again."
                        binding.tvImageError.visibility = View.VISIBLE
                        selectedImageUri = null
                        isNewImageSelected = false
                        loadProfileImage(currentProfileImageUrl, binding.imgProfilePicture) // Revert
                        checkAndUpdateSaveButtonState()
                    }
                }
                .addOnFailureListener { e ->
                    binding.progressBarUpload.visibility = View.GONE
                    Log.e(TAG, "Face detection failed", e)
                    Toast.makeText(this, "Could not analyze image: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Allow proceeding without detection? Let's allow for now.
                    selectedImageUri = uri
                    isNewImageSelected = true
                    Glide.with(this).load(selectedImageUri).apply(RequestOptions.circleCropTransform()).placeholder(R.drawable.ic_person_green).error(R.drawable.ic_person_red).into(binding.imgProfilePicture)
                    checkAndUpdateSaveButtonState()
                }
        } catch (e: IOException) {
            binding.progressBarUpload.visibility = View.GONE
            Log.e(TAG, "Error creating InputImage", e)
            Toast.makeText(this, "Error processing image.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInput(): Boolean {
        // ... (Keep existing validation logic) ...
        var isValid = true
        binding.editFullName.error = null
        binding.editEmail.error = null

        if (binding.editFullName.text.isNullOrBlank()) { binding.editFullName.error = "Full name required"; isValid = false }
        if (binding.editEmail.text.isNullOrBlank()) { binding.editEmail.error = "Email required"; isValid = false }
        // Add contact/address validation if made mandatory
        return isValid
    }


    // --- Renamed function ---
    private fun checkAndUpdateSaveButtonState() {
        // Determine if button should be enabled
        val isDataValid = binding.editFullName.text.isNotEmpty() &&
                binding.editEmail.text.isNotEmpty() // Add other required checks if any

        val shouldBeEnabled = isDataValid || isNewImageSelected

        // Call the helper to update visuals
        updateButtonVisuals(binding.saveButton, shouldBeEnabled)
    }

    // --- NEW HELPER FUNCTION for visual updates ---
    private fun updateButtonVisuals(button: AppCompatButton, isEnabled: Boolean) {
        button.isEnabled = isEnabled // Set the actual enabled state

        val buttonBackgroundRes = if (isEnabled) {
            R.drawable.button_enabled // Your enabled background
        } else {
            R.drawable.button_disabled // Your disabled background
        }
        button.background = ContextCompat.getDrawable(this, buttonBackgroundRes) // Use this context

        val buttonTextColorRes = if (isEnabled) {
            android.R.color.white // White text when enabled (or your custom color)
        } else {
            android.R.color.black // Black text when disabled (or your custom color)
        }
        button.setTextColor(ContextCompat.getColor(this, buttonTextColorRes)) // Use this context
    }


    private fun loadProfileImage(imageUrl: String?, imageView: ImageView) {
        // Keep existing logic
        val placeholder = R.drawable.ic_person_green
        val errorPlaceholder = R.drawable.ic_person_red
        val imageSource: Any? = if (!imageUrl.isNullOrEmpty()) imageUrl else placeholder
        Glide.with(this).load(imageSource).apply(RequestOptions.circleCropTransform()).placeholder(placeholder).error(errorPlaceholder).into(imageView)
    }

    private fun saveProfileData() {
        // ... (Keep saveProfileData logic, calling uploadProfilePicture and updateFirestore) ...
        val userId = auth.currentUser?.uid ?: return
        val updatedFullName = binding.editFullName.text.toString().trim()
        val updatedEmail = binding.editEmail.text.toString().trim()
        val updatedContact = binding.editContact.text.toString().trim()
        val updatedAddress = binding.editAddress.text.toString().trim()

        binding.progressBarUpload.visibility = View.VISIBLE
        binding.saveButton.isEnabled = false // Disable save during process

        val imageUriToUpload = selectedImageUri // Capture local val

        if (isNewImageSelected && imageUriToUpload != null) {
            uploadProfilePicture(userId, imageUriToUpload) { imageUrl ->
                if (imageUrl != null) {
                    updateFirestore(userId, updatedFullName, updatedEmail, updatedContact, updatedAddress, imageUrl)
                } else {
                    binding.progressBarUpload.visibility = View.GONE
                    binding.saveButton.isEnabled = true
                    Toast.makeText(this, "Failed to upload profile picture.", Toast.LENGTH_SHORT).show()
                    // Decide: fail completely or save text fields? Saving text fields:
                    // updateFirestore(userId, updatedFullName, updatedEmail, updatedContact, updatedAddress, currentProfileImageUrl)
                }
            }
        } else {
            updateFirestore(userId, updatedFullName, updatedEmail, updatedContact, updatedAddress, currentProfileImageUrl)
        }
    }

    private fun uploadProfilePicture(userId: String, imageUri: Uri, callback: (downloadUrl: String?) -> Unit) {
        // ... (Keep existing upload logic) ...
        Log.d(TAG, "Uploading profile picture from URI: $imageUri")
        val storageRef = storage.reference
        val profilePicRef = storageRef.child("profile_pictures/$userId/profile.jpg")
        val metadata = StorageMetadata.Builder().setContentType(contentResolver.getType(imageUri)).build()
        profilePicRef.putFile(imageUri, metadata)
            .addOnSuccessListener { task -> task.metadata?.reference?.downloadUrl?.addOnSuccessListener { url -> callback(url.toString()) }?.addOnFailureListener { e -> Log.e(TAG,"Failed get URL",e); callback(null)}}
            .addOnFailureListener { e -> Log.e(TAG, "Upload failed.", e); callback(null)}
    }


    private fun updateFirestore(userId: String, fullName: String, email: String, contact: String, address: String, newImageUrl: String?) {
        // ... (Keep existing updateFirestore logic) ...
        val updatedData = mutableMapOf<String, Any>( "fullName" to fullName, "email" to email)
        if (contact.isNotBlank()) updatedData["contactNo"] = contact
        if (address.isNotBlank()) updatedData["address"] = address
        newImageUrl?.let { updatedData["profileImageUrl"] = it }
        Log.d(TAG, "Updating Firestore: $updatedData")
        firestore.collection("users").document(userId).update(updatedData)
            .addOnSuccessListener {
                binding.progressBarUpload.visibility = View.GONE
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                isNewImageSelected = false; selectedImageUri = null // Reset flags
                if (newImageUrl != null) currentProfileImageUrl = newImageUrl // Update current URL
                setResult(Activity.RESULT_OK, Intent())
                finish()
            }
            .addOnFailureListener { e ->
                binding.progressBarUpload.visibility = View.GONE
                binding.saveButton.isEnabled = true
                Toast.makeText(this, "Error updating: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error updating Firestore", e)
            }
    }
}