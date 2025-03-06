package com.example.indoornavdisha

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.sceneview.ar.ARSceneView

class MainActivity : AppCompatActivity() {

    private val cameraPermissionCode = 0
    private lateinit var arSceneView: ARSceneView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find the button and set click listener
        val openCameraButton = findViewById<Button>(R.id.open_camera_button)
        openCameraButton.setOnClickListener {
            if (hasCameraPermission()) {
                openArCamera()
            } else {
                requestCameraPermission()
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            cameraPermissionCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openArCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openArCamera() {
        // Switch to AR view layout
        setContentView(R.layout.ar_view)

        // Get the AR Scene View
        arSceneView = findViewById(R.id.arSceneView)

        // ArSceneView automatically handles the AR session and camera rendering

        Toast.makeText(this, "AR Camera opened!", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()

        // ArSceneView handles the session lifecycle automatically if initialized
        if (::arSceneView.isInitialized) {
            arSceneView.onSessionResumed
        }
    }

    override fun onPause() {
        super.onPause()

        // ArSceneView handles the session lifecycle automatically if initialized
        if (::arSceneView.isInitialized) {
//            arSceneView.pause()
            arSceneView.onSessionPaused
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // ArSceneView handles cleanup automatically if initialized
        if (::arSceneView.isInitialized) {
            arSceneView.destroy()
        }
    }
}