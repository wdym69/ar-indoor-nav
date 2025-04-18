//
//package com.example.indoornavdisha
//
//import android.Manifest
//import android.content.pm.PackageManager
//import android.os.Bundle
//import android.util.Log
//import android.view.MotionEvent
//import android.widget.Button
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import com.google.android.filament.gltfio.AssetLoader
//import com.google.ar.core.HitResult
//import com.google.ar.core.Plane
//import io.github.sceneview.ar.ARSceneView
//import io.github.sceneview.ar.node.AnchorNode
//import io.github.sceneview.math.Position
//import io.github.sceneview.math.Scale
//import io.github.sceneview.loaders.ModelLoader
//import com.example.indoornavdisha.WaypointStorage
//
//
//class MainActivity : AppCompatActivity() {
//
//    private val tag = "MainActivity" // Tag for logging
//    private val cameraPermissionCode = 0 // Request code for camera permission
//    private lateinit var arSceneView: ARSceneView // The AR view
//    private val placedMarkers = mutableListOf<AnchorNode>() // List to track all placed markers
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        // Set the initial layout with the "Open Camera" button
//        setContentView(R.layout.activity_main)
//
//        // Find the button and set a click listener
//        val openCameraButton = findViewById<Button>(R.id.open_camera_button)
//        openCameraButton.setOnClickListener {
//            // Check for camera permission before opening AR view
//            if (hasCameraPermission()) {
//                openArCamera()
//            } else {
//                requestCameraPermission()
//            }
//        }
//    }
//
//    // Check if we already have camera permission
//    private fun hasCameraPermission(): Boolean {
//        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
//                PackageManager.PERMISSION_GRANTED
//    }
//
//    // Request camera permission if we don't have it
//    private fun requestCameraPermission() {
//        ActivityCompat.requestPermissions(
//            this,
//            arrayOf(Manifest.permission.CAMERA),
//            cameraPermissionCode
//        )
//    }
//
//    // Handle the result of the permission request
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == cameraPermissionCode) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // If permission granted, open the AR camera
//                openArCamera()
//            } else {
//                // If permission denied, show a message
//                Toast.makeText(
//                    this,
//                    "Camera permission is required",
//                    Toast.LENGTH_LONG
//                ).show()
//            }
//        }
//    }
//
//    // Set up and open the AR camera view
//    private fun openArCamera() {
//        // Switch to AR view layout
//        setContentView(R.layout.ar_view)
//
//        // Get the AR Scene View from the layout
//        arSceneView = findViewById(R.id.arSceneView)
//
//        // Enable plane detection so user can see detected surfaces
//        arSceneView.planeRenderer.isEnabled = true
//
//        // Set up touch handling for AR interactions
//        setupTouchInteraction()
//
//        Toast.makeText(this, "AR Camera opened! Tap on a surface to place a marker", Toast.LENGTH_SHORT).show()
//    }
//
//    // Set up touch interaction for placing markers
//    private fun setupTouchInteraction() {
//        arSceneView.setOnTouchListener { view, event ->
//            // Only respond to tap down events (when finger first touches the screen)
//            if (event.action == MotionEvent.ACTION_DOWN) {
//                // Perform hit test to see what's under the user's finger
//                performHitTest(event.x, event.y)
//            }
//            // Call performClick for accessibility
//            view.performClick()
//            true
//        }
//    }
//
//    // Perform a hit test to detect what's under the user's finger
//    private fun performHitTest(x: Float, y: Float) {
//        // Get the current AR frame from SceneView
//        val arFrame = arSceneView.frame
//
//        if (arFrame != null) {
//            // Perform a hit test at the tap location
//            val hitResults = arFrame.hitTest(x, y)
//
//            // Process hit results
//            processHitResults(hitResults)
//        }
//    }
//
//    // Process the hit test results to find suitable surfaces
//    private fun processHitResults(hitResults: List<HitResult>) {
//        // Find the first hit result that is on a plane
//        val planeHitResult = hitResults.firstOrNull { hit ->
//            val trackable = hit.trackable
//            // Only select hits that are on detected planes
//            trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)
//        }
//
//        // If we found a valid hit on a plane, place a marker there
//        if (planeHitResult != null) {
//            placeMarkerAtHit(planeHitResult)
//        } else {
//            // If no plane was hit, inform the user
//            Toast.makeText(this, "Please tap on a detected surface", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    // Place a marker at the hit location
//    // Place a marker at the hit location
//    private fun placeMarkerAtHit(hitResult: HitResult) {
//        try {
//            // Create an anchor at the hit location - this keeps the marker fixed in real world space
//            val anchor = hitResult.createAnchor()
//
//            // Get the engine from ARSceneView
//            val engine = arSceneView.engine
//
//            // Create a new anchor node with the engine
//            val anchorNode = AnchorNode(engine, anchor)
//
//            // Set the anchor separately
//            anchorNode.anchor = anchor
//
//            // Add the anchor node to the scene
//            arSceneView.addChildNode(anchorNode)
//
//            // Create a visible marker as a child of the anchor
//            createVisibleMarker(anchorNode)
//
//            // Store the marker reference so we can manage it later if needed
//            placedMarkers.add(anchorNode)
//
//            // Provide feedback to the user
//            Toast.makeText(this, "Marker placed!", Toast.LENGTH_SHORT).show()
//        } catch (e: Exception) {
//            // Log and display any errors
//            Log.e(tag, "Error placing marker: ${e.message}")
//            Toast.makeText(this, "Failed to place marker: ${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun createVisibleMarker(parentNode: AnchorNode) {
//        try {
//            // Step 1: Get the engine from the AR SceneView.
//            val engine = arSceneView.engine
//
//            // Step 2: Create a marker node to serve as a fixed container.
//            val markerNode = io.github.sceneview.node.Node(engine)
//            parentNode.addChildNode(markerNode)
//
//            // Step 3: Initialize the model loader.
//            val modelLoader = ModelLoader(engine = engine, context = this)
//
//            // Step 4: Asynchronously load the 3D model.
//            modelLoader.loadModelAsync(
//                fileLocation = "test.glb",
//                onResult = { loadedModel ->
//                    if (loadedModel != null) {
//                        // Step 5: Create a model node using the loaded model.
//                        val modelInstance = modelLoader.createInstance(loadedModel)
//                        if (modelInstance == null) {
//                            Log.e(tag, "Failed to create model instance")
//                            Toast.makeText(this, "Failed to create model instance", Toast.LENGTH_SHORT).show()
//                            return@loadModelAsync
//                        }
//                        val modelNode = io.github.sceneview.node.ModelNode(
//                            modelInstance = modelInstance,  // use the loaded model here
//                            autoAnimate = true
//                        ).apply {
//                            // Set the scale to ensure proper sizing.
//                            scale = Scale(0.1f)
//                            // Position it slightly above the marker's origin for better visibility.
//                            position = Position(0f, 0.05f, 0f)
//                        }
//
//                        // Step 6: Add the model node as a child of the marker node.
//                        markerNode.addChildNode(modelNode)
//
//                        Log.d(tag, "3D model loaded successfully")
//                    } else {
//                        Log.e(tag, "Failed to load 3D model")
//                        Toast.makeText(this, "Could not load marker model", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            )
//        } catch (e: Exception) {
//            Log.e(tag, "Error in createVisibleMarker: ${e.message}")
//            Toast.makeText(this, "Failed to create marker: ${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//
//
//    // Handle the activity lifecycle - resume AR session
//    override fun onResume() {
//        super.onResume()
//        if (::arSceneView.isInitialized) {
//            arSceneView.onSessionResumed
//        }
//    }
//
//    // Handle the activity lifecycle - pause AR session
//    override fun onPause() {
//        super.onPause()
//        if (::arSceneView.isInitialized) {
//            arSceneView.onSessionPaused
//        }
//    }
//
//    // Handle the activity lifecycle - clean up AR resources
//    override fun onDestroy() {
//        super.onDestroy()
//        if (::arSceneView.isInitialized) {
//            arSceneView.destroy()
//        }
//    }
//}


package com.example.indoornavdisha

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.loaders.ModelLoader
import kotlin.math.sqrt
import com.google.ar.core.Session
import com.google.ar.core.Frame

// Simple data class for a waypoint
//data class Waypoint(
//    val waypoint_id: Int,
//    val x: Float,
//    val y: Float,
//    val z: Float
//)

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"
    private val cameraPermissionCode = 0
    private lateinit var arSceneView: ARSceneView

    // List to keep track of markers placed in AR.
    private val placedMarkers = mutableListOf<AnchorNode>()

    // Hardcoded waypoints (modify these coordinates as needed)
    private val hardcodedWaypoints = listOf(
        Waypoint(0, 1.50f, 0.5f, 6.5f),
        Waypoint(1, 1.00f, 0.0f, 7.0f),
        Waypoint(2, 0.50f, 0.0f, 7.5f),
        Waypoint(3, 0.00f, -0.5f, 8.0f),
        Waypoint(4, -0.50f, -1.0f, 8.5f),
        Waypoint(5, -0.50f, -1.0f, 9.0f),
        Waypoint(6, -1.00f, -1.5f, 9.5f),
        Waypoint(7, -1.50f, -2.0f, 10.0f),
        Waypoint(8, -1.50f, -1.5f, 10.5f),
        Waypoint(9, -1.00f, -1.5f, 11.0f),
        Waypoint(10, -0.50f, -1.0f, 11.5f),
        Waypoint(11, -1.00f, -1.0f, 12.0f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initial layout with a button to open AR camera.
        setContentView(R.layout.activity_main)

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
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openArCamera() {
        // Switch to AR view layout
        setContentView(R.layout.ar_view)
        arSceneView = findViewById(R.id.arSceneView)
        // Optionally enable plane rendering if you want to show detected planes.
        arSceneView.planeRenderer.isEnabled = true

        // Set up touch handling for placing additional markers (if needed)
        setupTouchInteraction()

        Toast.makeText(this, "AR Camera opened! Tap anywhere to place a marker", Toast.LENGTH_SHORT)
            .show()

        // Place markers for our hardcoded waypoints.
        arSceneView.onSessionUpdated = { session, frame ->
            if (frame.camera.trackingState == TrackingState.TRACKING) {
                // Check if we haven't placed markers yet
                if (placedMarkers.isEmpty()) {
                    placeMarkersFromWaypoints()
                }

                // Continue with your normal marker anchor updates
                updateMarkerAnchors(session, frame)
            }
        }
    }

    // Setup touch listener (for demonstration, left as in your original code).
    private fun setupTouchInteraction() {
        arSceneView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                placeMarkerDirectly()
            }
            view.performClick()
            true
        }
    }

    // Direct marker placement from a tap (kept from your code for testing purposes).
    private fun placeMarkerDirectly() {
        val arFrame = arSceneView.frame
        if (arFrame != null && arFrame.camera.trackingState == TrackingState.TRACKING) {
            val cameraPose = arFrame.camera.pose

            // How far in front of the camera to place the marker
            val forwardOffset = 1.0f

            // Calculate a forward offset based on the camera's z-axis.
            val dx = -cameraPose.zAxis[0] * forwardOffset
            val dz = -cameraPose.zAxis[2] * forwardOffset

            // Fixed Y-level for the floor (assuming flat ground)
            val floorY = 0f

            val newPose = Pose.makeTranslation(
                cameraPose.tx() + dx,
                floorY,
                cameraPose.tz() + dz
            )

            arSceneView.session?.let { session ->
                val anchor: Anchor = session.createAnchor(newPose)
                placeMarkerAtAnchor(anchor)
            } ?: run {
                Toast.makeText(this, "AR session is not available", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "AR Frame not available or tracking lost", Toast.LENGTH_SHORT).show()
        }
    }

    // Place a marker using an ARCore anchor.
    private fun placeMarkerAtAnchor(anchor: Anchor) {
        try {
            val engine = arSceneView.engine
            val anchorNode = AnchorNode(engine, anchor).apply {
                this.anchor = anchor
            }
            arSceneView.addChildNode(anchorNode)
            createVisibleMarker(anchorNode)
            placedMarkers.add(anchorNode)
            Toast.makeText(this, "Marker placed!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(tag, "Error placing marker: ${e.message}")
            Toast.makeText(this, "Failed to place marker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Load and attach a 3D marker model as a child of the anchor node.
    private fun createVisibleMarker(parentNode: AnchorNode) {
        try {
            val engine = arSceneView.engine
            val markerNode = io.github.sceneview.node.Node(engine)
            parentNode.addChildNode(markerNode)

            val modelLoader = ModelLoader(engine = engine, context = this)
            modelLoader.loadModelAsync(
                fileLocation = "test.glb",
                onResult = { loadedModel ->
                    if (loadedModel != null) {
                        val modelInstance = modelLoader.createInstance(loadedModel)
                        if (modelInstance == null) {
                            Log.e(tag, "Failed to create model instance")
                            Toast.makeText(this, "Failed to create model instance", Toast.LENGTH_SHORT).show()
                            return@loadModelAsync
                        }
                        val modelNode = io.github.sceneview.node.ModelNode(
                            modelInstance = modelInstance,
                            autoAnimate = true
                        ).apply {
                            // Use a slightly higher Y (or semi-transparent material) to indicate provisional placement.
                            scale = Scale(0.1f)
                            position = Position(0f, 0.05f, 0f)
                        }
                        markerNode.addChildNode(modelNode)
                        Log.d(tag, "3D model loaded successfully")
                    } else {
                        Log.e(tag, "Failed to load 3D model")
                        Toast.makeText(this, "Could not load marker model", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(tag, "Error in createVisibleMarker: ${e.message}")
            Toast.makeText(this, "Failed to create marker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Place markers from our hardcoded waypoint list.
    private fun placeMarkersFromWaypoints() {
        hardcodedWaypoints.forEach { waypoint ->
            // Create a Pose from the waypoint coordinates.
            val waypointPose = Pose.makeTranslation(waypoint.x, waypoint.y, waypoint.z)
            arSceneView.session?.let { session ->
                val anchor: Anchor = session.createAnchor(waypointPose)
                placeMarkerAtAnchor(anchor)
            }
        }
    }

    // Update marker anchors based on proximity; when the user gets close to the marker,
    // simulate a "hit-test" result by snapping the marker's Y coordinate to 0 (flat ground)
    // Update marker anchors based on proximity; when the user gets close to the marker,
// simulate a "hit-test" result by snapping the marker's Y coordinate to 0 (flat ground)
    private fun updateMarkerAnchors(session: Session, frame: Frame) {
        if (frame.camera.trackingState != com.google.ar.core.TrackingState.TRACKING) return

        val cameraPose = frame.camera.pose
        val proximityThreshold = 1.0f // meters

        // Iterate over a copy of the list as we may modify the placedMarkers
        val markersToUpdate = placedMarkers.toList()
        for (markerNode in markersToUpdate) {
            // Get the current pose of the marker's anchor.
            val markerPose = markerNode.anchor.pose

            // Calculate horizontal distance between camera and marker.
            val dx = cameraPose.tx() - markerPose.tx()
            val dz = cameraPose.tz() - markerPose.tz()
            val distance = sqrt(dx * dx + dz * dz)

            // If within threshold, simulate a hit-test by adjusting the Y coordinate.
            if (distance < proximityThreshold && markerPose.ty() != 0f) {
                Log.d(tag, "User is close to marker. Adjusting its Y position to flat ground.")
                // Create a new Pose with Y set to 0 (simulate detection of a flat surface).
                val updatedPose = Pose.makeTranslation(markerPose.tx(), 0f, markerPose.tz())
                // Create a new anchor at the updated pose.
                session.let {
                    val newAnchor = session.createAnchor(updatedPose)
                    // Remove the old marker node.
                    arSceneView.removeChildNode(markerNode)
                    placedMarkers.remove(markerNode)
                    // Place a new marker at the new anchor (which now "snaps" to the floor).
                    placeMarkerAtAnchor(newAnchor)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::arSceneView.isInitialized) {
            arSceneView.onSessionResumed
        }
    }

    override fun onPause() {
        super.onPause()
        if (::arSceneView.isInitialized) {
            arSceneView.onSessionPaused
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::arSceneView.isInitialized) {
            arSceneView.destroy()
        }
    }
}
