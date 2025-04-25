package com.example.indoornavdisha

import android.util.Log
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.indoornavdisha.WaypointStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
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

// Data model for a waypoint

data class Waypoint(
    val waypoint_id: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

// Data model for the API response, including transformation matrix

data class WaypointsResponse(
    val message: String,
    val waypoints: List<Waypoint>,
    val transformation_matrix: List<List<Double>>
)

// Retrofit interface for API communication
interface ApiService {
    @Multipart
    @POST("upload")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part,
        @Part("dest_x") destX: okhttp3.RequestBody,
        @Part("dest_y") destY: okhttp3.RequestBody,
        @Part("dest_z") destZ: okhttp3.RequestBody
    ): Response<WaypointsResponse>

    companion object {
        fun create(): ApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(500, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("http://192.168.136.161:5000/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}

class SecondActivity : AppCompatActivity() {

    private lateinit var uploadButton: Button
    private lateinit var imagePreview: ImageView
    private lateinit var resultText: TextView
    private lateinit var destinationSpinner: Spinner
    private var selectedImageUri: Uri? = null
    private var selectedDestCoords: Triple<Float,Float,Float>? = null
    private val apiService by lazy { ApiService.create() }

    // Hardcoded map of destination names to COLMAP coordinates
    private val destMap = mapOf(
        "Men's Washroom" to Triple(3.13874f, -0.296136f, -0.948969f),
        "Women's Washroom" to Triple(1.22196f, 0.444023f, 15.2605f),
        "Common Lift" to Triple(4.67341f, 0.68111f, 12.5769f),
        "Staff Lift" to Triple(-1.41325f, 2.11509f, 4.8258f),
        "MCA Section" to Triple(-0.678117f, -0.840322f, 17.2862f),
        "Library" to Triple(-2.99935f, 0.156417f, -26.8863f)
    )

    // Register activity result for selecting an image
    private val getImageContent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedImageUri = uri
                    imagePreview.setImageURI(uri)
                    Toast.makeText(this, "Image selected. Ready to upload.", Toast.LENGTH_SHORT).show()
                    uploadButton.text = "Upload Selected Image"
                    uploadButton.setOnClickListener { uploadSelectedImage() }
                    destinationSpinner.isEnabled = true
                }
            }
        }

    // Register permission request for storage access
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) openImagePicker()
            else Toast.makeText(this, "Storage permission is required", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.image_view)

        uploadButton = findViewById(R.id.uploadButton)
        imagePreview = findViewById(R.id.imagePreview)
        resultText = findViewById(R.id.resultText)
        destinationSpinner = findViewById(R.id.destinationSpinner)

        // Populate spinner
        val destinations = destMap.keys.toList()
        destinationSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, destinations)
        destinationSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val name = parent.getItemAtPosition(position) as String
                selectedDestCoords = destMap[name]
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
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

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        getImageContent.launch(intent)
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    if (idx != -1) result = cursor.getString(idx)
                }
            }
        }
        if (result == null) {
            uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                result = if (cut != -1) path.substring(cut + 1) else path
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
        val coords = selectedDestCoords
        if (coords == null) {
            Toast.makeText(this, "Please select a destination before uploading.", Toast.LENGTH_SHORT).show()
            return
        }

        uploadButton.isEnabled = false
        uploadButton.text = "Uploading..."
        resultText.text = "Processing image..."

        lifecycleScope.launch {
            try {
                // Prepare file
                val file = withContext(Dispatchers.IO) {
                    val pfd = contentResolver.openFileDescriptor(uri, "r")
                    val input = FileInputStream(pfd!!.fileDescriptor)
                    val name = getFileName(uri)
                    val tmp = File(cacheDir, name)
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    tmp
                }

                val imagePart = MultipartBody.Part.createFormData(
                    "image", file.name, file.asRequestBody("image/*".toMediaTypeOrNull())
                )
                val (dx, dy, dz) = coords
                val destX = dx.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val destY = dy.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                val destZ = dz.toString().toRequestBody("text/plain".toMediaTypeOrNull())

                val response = apiService.uploadImage(imagePart, destX, destY, destZ)
                if (response.isSuccessful) {
                    response.body()?.let { resp ->
                        WaypointStorage.waypoints = resp.waypoints
                        WaypointStorage.transformationMatrix = resp.transformation_matrix
                    }
                    // Display feedback
                    val wpFb = WaypointStorage.waypoints.joinToString("\n") { wp ->
                        "Waypoint ${'$'}{wp.waypoint_id}: (${ '$'}{wp.x}, ${ '$'}{wp.y}, ${ '$'}{wp.z})"
                    }.ifEmpty { "No waypoints returned." }
                    val tmFb = WaypointStorage.transformationMatrix.joinToString("\n") { row ->
                        row.joinToString(", ", prefix = "[", postfix = "]")
                    }
                    resultText.text = "Upload successful!\n\nWaypoints:\n${'$'}wpFb\n\nTransformation Matrix:\n${'$'}tmFb"

                    startActivity(Intent(this@SecondActivity, MainActivity::class.java))
                    finish()
                } else {
                    resultText.text = "Error: ${'$'}{response.code()} - ${'$'}{response.message()}"
                    uploadButton.text = "Retry Upload"
                    uploadButton.isEnabled = true
                }
            } catch (e: Exception) {
                resultText.text = "Upload failed: ${'$'}{e.message}"
                uploadButton.text = "Retry Upload"
                uploadButton.isEnabled = true
            }
        }
    }
}


package com.example.indoornavdisha

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
import okhttp3.RequestBody.Companion.asRequestBody
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

// Data model for a waypoint

data class Waypoint(
    val waypoint_id: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

// Data model for the API response, including transformation matrix

data class WaypointsResponse(
    val message: String,
    val waypoints: List<Waypoint>,
    val transformation_matrix: List<List<Double>>
)

// Retrofit interface for API communication
interface ApiService {
    @Multipart
    @POST("upload")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part
    ): Response<WaypointsResponse>

    companion object {
        fun create(): ApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(500, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("http://172.20.10.7:5000/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}

class SecondActivity : AppCompatActivity() {

    private lateinit var uploadButton: Button
    private lateinit var imagePreview: ImageView
    private lateinit var resultText: TextView
    private var selectedImageUri: Uri? = null
    private val apiService by lazy { ApiService.create() }

    // Register activity result for selecting an image
    private val getImageContent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    selectedImageUri = uri
                    imagePreview.setImageURI(uri)
                    Toast.makeText(this, "Image selected. Ready to upload.", Toast.LENGTH_SHORT).show()
                    uploadButton.text = "Upload Selected Image"
                    uploadButton.setOnClickListener { uploadSelectedImage() }
                }
            }
        }

    // Register permission request for storage access
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) openImagePicker()
            else Toast.makeText(this, "Storage permission is required", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.image_view)

        uploadButton = findViewById(R.id.uploadButton)
        imagePreview = findViewById(R.id.imagePreview)
        resultText = findViewById(R.id.resultText)

        uploadButton.setOnClickListener { checkPermissionAndPickImage() }
    }

    private fun checkPermissionAndPickImage() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        getImageContent.launch(intent)
    }

    private fun getFileName(uri: Uri): String {
        // Attempt to fetch display name for content URIs
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
        // Fallback to file path if necessary
        if (result == null) {
            uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                result = if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result ?: "image_upload.jpg"
    }

    private fun uploadSelectedImage() {
        val imageUri = selectedImageUri
        if (imageUri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show()
            return
        }

        uploadButton.isEnabled = false
        uploadButton.text = "Uploading..."
        resultText.text = "Processing image..."

        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val pfd = contentResolver.openFileDescriptor(imageUri, "r")
                    val input = FileInputStream(pfd?.fileDescriptor)
                    val original = getFileName(imageUri)
                    val temp = File(cacheDir, original)
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                    temp
                }

                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

                val response = apiService.uploadImage(body)
                if (response.isSuccessful) {
                    response.body()?.let { resp ->
                        // Store fetched data
                        WaypointStorage.waypoints = resp.waypoints
                        WaypointStorage.transformationMatrix = resp.transformation_matrix
                    }

                    // Display feedback
                    val feedback = WaypointStorage.waypoints.joinToString("\n") { wp ->
                        "Waypoint ${wp.waypoint_id}: (${wp.x}, ${wp.y}, ${wp.z})"
                    }.ifEmpty { "No waypoints returned." }
                    resultText.text = "Upload successful!\n\nWaypoints:\n$feedback"

                    // Proceed to MainActivity
                    startActivity(Intent(this@SecondActivity, MainActivity::class.java))
                    finish()
                } else {
                    resultText.text = "Error: ${response.code()} - ${response.message()}"
                    uploadButton.text = "Retry Upload"
                    uploadButton.isEnabled = true
                }
            } catch (e: Exception) {
                resultText.text = "Upload failed: ${e.message}"
                uploadButton.text = "Retry Upload"
                uploadButton.isEnabled = true
            }
        }
    }
}