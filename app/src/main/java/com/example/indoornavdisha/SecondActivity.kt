// in this code only api call is made and waypoints are printed, the secondactivity and mainactivity are not connected

//package com.example.indoornavdisha
//
//import android.Manifest
//import android.app.Activity
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.net.Uri
//import android.os.Bundle
//import android.provider.MediaStore
//import android.util.Log
//import android.widget.Button
//import android.widget.ImageView
//import android.widget.TextView
//import android.widget.Toast
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.lifecycleScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import okhttp3.MediaType.Companion.toMediaTypeOrNull
//import okhttp3.MultipartBody
//import okhttp3.OkHttpClient
//import okhttp3.RequestBody.Companion.asRequestBody
//import retrofit2.Response
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import retrofit2.http.Multipart
//import retrofit2.http.POST
//import retrofit2.http.Part
//import java.io.File
//import java.io.FileInputStream
//import java.io.FileOutputStream
//import java.util.concurrent.TimeUnit
//import com.example.indoornavdisha.WaypointStorage
//
//// Data model for a waypoint response
//// Each waypoint has an ID and x, y, z coordinates
//data class Waypoint(
//    val waypoint_id: Int,
//    val x: Float,
//    val y: Float,
//    val z: Float
//)
//
//// Data model for the API response
//data class WaypointsResponse(
//    val message: String, // Status message from server
//    val waypoints: List<Waypoint> // List of waypoints received from the server
//)
//
//// Retrofit interface for API communication
//interface ApiService {
//    @Multipart
//    @POST("upload") // Define the API endpoint for image upload
//    suspend fun uploadImage(
//        @Part image: MultipartBody.Part // Image file to be uploaded
//    ): Response<WaypointsResponse> // API response containing waypoints
//
//    companion object {
//        fun create(): ApiService {
//            val client = OkHttpClient.Builder()
//                .connectTimeout(500, TimeUnit.SECONDS) // Timeout settings
//                .readTimeout(120, TimeUnit.SECONDS)
//                .writeTimeout(120, TimeUnit.SECONDS)
//                .build()
//
//            return Retrofit.Builder()
//                .baseUrl("http://192.168.0.102:5000/") // Use this for Android emulator (localhost equivalent)
//                .client(client)
//                .addConverterFactory(GsonConverterFactory.create()) // Convert JSON response to data classes
//                .build()
//                .create(ApiService::class.java)
//        }
//    }
//}
//
//class SecondActivity : AppCompatActivity() {
//
//    private val TAG = "SecondActivity" // Log tag for debugging
//
//    private lateinit var uploadButton: Button // Button to upload image
//    private lateinit var imagePreview: ImageView // ImageView to show selected image
//    private lateinit var resultText: TextView // TextView to display server response
//
//    private var selectedImageUri: Uri? = null // Stores the selected image's URI
//
//    private val apiService by lazy { ApiService.create() } // Create API service instance
//
//    // Register activity result for selecting an image
//    private val getImageContent =
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == Activity.RESULT_OK) {
//                result.data?.data?.let { uri ->
//                    selectedImageUri = uri // Store selected image URI
//                    imagePreview.setImageURI(uri) // Display image preview
//                    Toast.makeText(this, "Image selected. Ready to upload.", Toast.LENGTH_SHORT).show()
//                    uploadButton.text = "Upload Selected Image"
//                    uploadButton.setOnClickListener { uploadSelectedImage() } // Enable upload after selection
//                }
//            }
//        }
//
//    // Register permission request for storage access
//    private val requestPermissionLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
//            if (isGranted) {
//                openImagePicker()
//            } else {
//                Toast.makeText(this, "Storage permission is required", Toast.LENGTH_LONG).show()
//            }
//        }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.image_view) // Set UI layout
//
//        // Initialize UI elements
//        uploadButton = findViewById(R.id.uploadButton)
//        imagePreview = findViewById(R.id.imagePreview)
//        resultText = findViewById(R.id.resultText)
//
//        uploadButton.setOnClickListener { checkPermissionAndPickImage() } // Request permission & select image
//    }
//
//    private fun checkPermissionAndPickImage() {
//        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
//            Manifest.permission.READ_MEDIA_IMAGES // For Android 13+
//        } else {
//            Manifest.permission.READ_EXTERNAL_STORAGE // For Android 12 and below
//        }
//
//        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
//            openImagePicker()
//        } else {
//            requestPermissionLauncher.launch(permission)
//        }
//    }
//
//    private fun openImagePicker() {
//        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//        getImageContent.launch(intent)
//    }
//
//    private fun getFileName(uri: Uri): String {
//        var result: String? = null
//        if (uri.scheme == "content") {
//            val cursor = contentResolver.query(uri, null, null, null, null)
//            cursor?.use {
//                if (it.moveToFirst()) {
//                    val index = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
//                    if (index != -1) {
//                        result = it.getString(index)
//                    }
//                }
//            }
//        }
//        if (result == null) {
//            result = uri.path
//            val cut = result?.lastIndexOf('/')
//            if (cut != null && cut != -1) {
//                result = result?.substring(cut + 1)
//            }
//        }
//        return result ?: "image_upload.jpg"
//    }
//
//
//    private fun uploadSelectedImage() {
//        val imageUri = selectedImageUri
//        if (imageUri == null) {
//            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        uploadButton.isEnabled = false // Disable button during upload
//        uploadButton.text = "Uploading..."
//        resultText.text = "Processing image..."
//
//        lifecycleScope.launch {
//            try {
//                val file = withContext(Dispatchers.IO) {
//                    val parcelFileDescriptor = contentResolver.openFileDescriptor(imageUri, "r")
//                    val inputStream = FileInputStream(parcelFileDescriptor?.fileDescriptor)
//
//                    // Get the original file name
//                    val originalFileName = getFileName(imageUri)
//                    val tempFile = File(cacheDir, originalFileName) // Use original file name (with extension)
//
//                    val outputStream = FileOutputStream(tempFile)
//                    inputStream.copyTo(outputStream)
//                    outputStream.close()
//                    tempFile
//                }
//
//
//                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
//                val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
//
//                val response = apiService.uploadImage(body)
//                if (response.isSuccessful) {
//                    val waypointsResponse = response.body()
//
//                    // Store waypoints in the global object
//                    if (waypointsResponse != null) {
//                        WaypointStorage.waypoints = waypointsResponse.waypoints
//                    }
//
////                    val formattedWaypoints = waypointsResponse?.waypoints?.joinToString("\n") { waypoint ->
////                        "Waypoint ${waypoint.waypoint_id}: (${waypoint.x}, ${waypoint.y}, ${waypoint.z})"
////                    } ?: "No waypoints returned."
//
//                    val formattedWaypoints = if (WaypointStorage.waypoints.isNotEmpty()) {
//                        WaypointStorage.waypoints.joinToString("\n") { waypoint ->
//                            "Waypoint ${waypoint.waypoint_id}: (${waypoint.x}, ${waypoint.y}, ${waypoint.z})"
//                        }
//                    } else {
//                        "No waypoints returned."
//                    }
//
//                    resultText.text = "Upload successful!\n\nWaypoints:\n$formattedWaypoints"
//                    uploadButton.text = "Select Another Image"
//                    uploadButton.isEnabled = true
//                    uploadButton.setOnClickListener { checkPermissionAndPickImage() }
//
//                } else {
//                    resultText.text = "Error: ${response.code()} - ${response.message()}"
//                    uploadButton.text = "Retry Upload"
//                    uploadButton.isEnabled = true
//                }
//            } catch (e: Exception) {
//                resultText.text = "Upload failed: ${e.message}"
//                uploadButton.text = "Retry Upload"
//                uploadButton.isEnabled = true
//            }
//        }
//    }
//}


// in this code the api call is made and the secondactivity and mainactivity both are connected
//package com.example.indoornavdisha
//
//import android.Manifest
//import android.app.Activity
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.net.Uri
//import android.os.Bundle
//import android.provider.MediaStore
//import android.util.Log
//import android.widget.Button
//import android.widget.ImageView
//import android.widget.TextView
//import android.widget.Toast
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.lifecycleScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import okhttp3.MediaType.Companion.toMediaTypeOrNull
//import okhttp3.MultipartBody
//import okhttp3.OkHttpClient
//import okhttp3.RequestBody.Companion.asRequestBody
//import retrofit2.Response
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import retrofit2.http.Multipart
//import retrofit2.http.POST
//import retrofit2.http.Part
//import java.io.File
//import java.io.FileInputStream
//import java.io.FileOutputStream
//import java.util.concurrent.TimeUnit
//import com.example.indoornavdisha.WaypointStorage
//
//// Data model for a waypoint response
//data class Waypoint(
//    val waypoint_id: Int,
//    val x: Float,
//    val y: Float,
//    val z: Float
//)
//
//// Data model for the API response
//data class WaypointsResponse(
//    val message: String,
//    val waypoints: List<Waypoint>
//)
//
//// Retrofit interface for API communication
//interface ApiService {
//    @Multipart
//    @POST("upload")
//    suspend fun uploadImage(
//        @Part image: MultipartBody.Part
//    ): Response<WaypointsResponse>
//
//    companion object {
//        fun create(): ApiService {
//            val client = OkHttpClient.Builder()
//                .connectTimeout(500, TimeUnit.SECONDS)
//                .readTimeout(120, TimeUnit.SECONDS)
//                .writeTimeout(120, TimeUnit.SECONDS)
//                .build()
//
//            return Retrofit.Builder()
//                .baseUrl("http://192.168.0.103:5000/")
//                .client(client)
//                .addConverterFactory(GsonConverterFactory.create())
//                .build()
//                .create(ApiService::class.java)
//        }
//    }
//}
//
//class SecondActivity : AppCompatActivity() {
//
//    private val TAG = "SecondActivity"
//
//    private lateinit var uploadButton: Button
//    private lateinit var imagePreview: ImageView
//    private lateinit var resultText: TextView
//
//    private var selectedImageUri: Uri? = null
//
//    private val apiService by lazy { ApiService.create() }
//
//    // Register activity result for selecting an image
//    private val getImageContent =
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == Activity.RESULT_OK) {
//                result.data?.data?.let { uri ->
//                    selectedImageUri = uri
//                    imagePreview.setImageURI(uri)
//                    Toast.makeText(this, "Image selected. Ready to upload.", Toast.LENGTH_SHORT).show()
//                    uploadButton.text = "Upload Selected Image"
//                    uploadButton.setOnClickListener { uploadSelectedImage() }
//                }
//            }
//        }
//
//    // Register permission request for storage access
//    private val requestPermissionLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
//            if (isGranted) {
//                openImagePicker()
//            } else {
//                Toast.makeText(this, "Storage permission is required", Toast.LENGTH_LONG).show()
//            }
//        }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.image_view)
//
//        uploadButton = findViewById(R.id.uploadButton)
//        imagePreview = findViewById(R.id.imagePreview)
//        resultText = findViewById(R.id.resultText)
//
//        uploadButton.setOnClickListener { checkPermissionAndPickImage() }
//    }
//
//    private fun checkPermissionAndPickImage() {
//        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
//            Manifest.permission.READ_MEDIA_IMAGES
//        } else {
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        }
//
//        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
//            openImagePicker()
//        } else {
//            requestPermissionLauncher.launch(permission)
//        }
//    }
//
//    private fun openImagePicker() {
//        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//        getImageContent.launch(intent)
//    }
//
//    private fun getFileName(uri: Uri): String {
//        var result: String? = null
//        if (uri.scheme == "content") {
//            val cursor = contentResolver.query(uri, null, null, null, null)
//            cursor?.use {
//                if (it.moveToFirst()) {
//                    val index = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
//                    if (index != -1) {
//                        result = it.getString(index)
//                    }
//                }
//            }
//        }
//        if (result == null) {
//            result = uri.path
//            val cut = result?.lastIndexOf('/')
//            if (cut != null && cut != -1) {
//                result = result?.substring(cut + 1)
//            }
//        }
//        return result ?: "image_upload.jpg"
//    }
//
//    private fun uploadSelectedImage() {
//        val imageUri = selectedImageUri
//        if (imageUri == null) {
//            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        uploadButton.isEnabled = false
//        uploadButton.text = "Uploading..."
//        resultText.text = "Processing image..."
//
//        lifecycleScope.launch {
//            try {
//                val file = withContext(Dispatchers.IO) {
//                    val parcelFileDescriptor = contentResolver.openFileDescriptor(imageUri, "r")
//                    val inputStream = FileInputStream(parcelFileDescriptor?.fileDescriptor)
//                    val originalFileName = getFileName(imageUri)
//                    val tempFile = File(cacheDir, originalFileName)
//                    val outputStream = FileOutputStream(tempFile)
//                    inputStream.copyTo(outputStream)
//                    outputStream.close()
//                    tempFile
//                }
//
//                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
//                val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
//
//                val response = apiService.uploadImage(body)
//                if (response.isSuccessful) {
//                    val waypointsResponse = response.body()
//
//                    // Store waypoints in the global object
//                    if (waypointsResponse != null) {
//                        WaypointStorage.waypoints = waypointsResponse.waypoints
//                    }
//
//                    // Optionally format and display waypoints here
//                    val formattedWaypoints = if (WaypointStorage.waypoints.isNotEmpty()) {
//                        WaypointStorage.waypoints.joinToString("\n") { waypoint ->
//                            "Waypoint ${waypoint.waypoint_id}: (${waypoint.x}, ${waypoint.y}, ${waypoint.z})"
//                        }
//                    } else {
//                        "No waypoints returned."
//                    }
//                    resultText.text = "Upload successful!\n\nWaypoints:\n$formattedWaypoints"
//
//                    // Now call MainActivity after storing waypoints
//                    val intent = Intent(this@SecondActivity, MainActivity::class.java)
//                    startActivity(intent)
//                    finish() // Close SecondActivity if desired
//
//                } else {
//                    resultText.text = "Error: ${response.code()} - ${response.message()}"
//                    uploadButton.text = "Retry Upload"
//                    uploadButton.isEnabled = true
//                }
//            } catch (e: Exception) {
//                resultText.text = "Upload failed: ${e.message}"
//                uploadButton.text = "Retry Upload"
//                uploadButton.isEnabled = true
//            }
//        }
//    }
//}

package com.example.indoornavdisha

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import com.example.indoornavdisha.WaypointStorage


// Data model for a waypoint response
data class Waypoint(
    val waypoint_id: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

// Data model for the API response
data class WaypointsResponse(
    val message: String,
    val waypoints: List<Waypoint>
)

interface ApiService {
    @Multipart
    @POST("upload")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part,
        @Part("dest_x") destX: RequestBody,
        @Part("dest_y") destY: RequestBody,
        @Part("dest_z") destZ: RequestBody
    ): Response<WaypointsResponse>

    companion object {
        fun create(): ApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(500, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("http://192.168.0.103:5000/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}

class SecondActivity : AppCompatActivity() {
    private val TAG = "SecondActivity"
    private lateinit var uploadButton: Button
    private lateinit var imagePreview: ImageView
    private lateinit var resultText: TextView
    private lateinit var destinationSpinner: Spinner

    private var selectedImageUri: Uri? = null
    private var selectedDestCoords: Triple<Float,Float,Float>? = null

    // Hardcoded map of destination names to COLMAP coordinates
    private val destMap = mapOf(
        "Men's Washroom" to Triple(3.13874f, -0.296136f, -0.948969f),
        "Women's Washroom" to Triple(1.22196f, 0.444023f, 15.2605f ),
        "Common Lift" to Triple(4.67341f, 0.68111f, 12.5769f),
        "Staff Lift" to Triple(-1.41325f, 2.11509f, 4.8258f),
        "MCA Section" to Triple(-0.678117f, -0.840322f, 17.2862f),
        "Library" to Triple(-2.99935f, 0.156417f, -26.8863f)
    )

    private val apiService by lazy { ApiService.create() }

    private val getImageContent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedImageUri = uri
                    imagePreview.setImageURI(uri)
                    Toast.makeText(this, "Image selected. Ready to upload.", Toast.LENGTH_SHORT).show()
                    uploadButton.text = "Upload Selected Image"
                    uploadButton.setOnClickListener { uploadSelectedImage() }
                    // Enable destination selection
                    destinationSpinner.isEnabled = true
                }
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) openImagePicker() else
                Toast.makeText(this, "Storage permission is required", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.image_view)

        uploadButton       = findViewById(R.id.uploadButton)
        imagePreview       = findViewById(R.id.imagePreview)
        resultText         = findViewById(R.id.resultText)
        destinationSpinner = findViewById(R.id.destinationSpinner)

        // Populate spinner with destination names
        val destinations = destMap.keys.toList()
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, destinations)
        destinationSpinner.adapter = spinnerAdapter
        destinationSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val name = parent.getItemAtPosition(position) as String
                selectedDestCoords = destMap[name]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
                selectedDestCoords = null
            }
        }

        uploadButton.setOnClickListener { checkPermissionAndPickImage() }
    }

    private fun checkPermissionAndPickImage() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
            openImagePicker()
        else
            requestPermissionLauncher.launch(permission)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        getImageContent.launch(intent)
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "image_upload.jpg"
    }

    private fun uploadSelectedImage() {
        val uri = selectedImageUri
        if (uri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            return
        }
        // Ensure user picked a destination
        val coords = selectedDestCoords
        if (coords == null) {
            Toast.makeText(this, "Please select a destination before uploading.", Toast.LENGTH_SHORT).show()
            uploadButton.isEnabled = true
            uploadButton.text = "Retry Upload"
            return
        }

        uploadButton.isEnabled = false
        uploadButton.text      = "Uploading..."
        resultText.text        = "Processing image..."

        lifecycleScope.launch {
            try {
                // Prepare image file
                val file = withContext(Dispatchers.IO) {
                    val pfd = contentResolver.openFileDescriptor(uri, "r")
                    val input  = FileInputStream(pfd?.fileDescriptor)
                    val name   = getFileName(uri)
                    val temp   = File(cacheDir, name)
                    input.copyTo(FileOutputStream(temp))
                    temp
                }
                val imageBody = file.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", file.name, imageBody)

                // Prepare destination coords as text parts
                val (dx, dy, dz) = coords
                val destX = dx.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val destY = dy.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val destZ = dz.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                // Call API
                val response = apiService.uploadImage(imagePart, destX, destY, destZ)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) WaypointStorage.waypoints = body.waypoints

                    val display = WaypointStorage.waypoints.joinToString("\n") {
                        "Waypoint ${it.waypoint_id}: (${it.x}, ${it.y}, ${it.z})"
                    }
                    resultText.text = "Upload successful!\n\nWaypoints:\n$display"

                    startActivity(Intent(this@SecondActivity, MainActivity::class.java))
                    finish()
                } else {
                    resultText.text = "Error: ${response.code()} - ${response.message()}"
                    uploadButton.text      = "Retry Upload"
                    uploadButton.isEnabled = true
                }
            } catch (e: Exception) {
                resultText.text = "Upload failed: ${e.message}"
                uploadButton.text      = "Retry Upload"
                uploadButton.isEnabled = true
            }
        }
    }
}

