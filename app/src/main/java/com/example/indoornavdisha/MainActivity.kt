package com.example.indoornavdisha

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.core.Session
import com.google.ar.core.Frame
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.loaders.ModelLoader
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"
    private val cameraPermissionCode = 0
    private lateinit var arSceneView: ARSceneView

    // Keep track of placed markers so we only place once
    private val placedMarkers = mutableListOf<AnchorNode>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            cameraPermissionCode
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
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
        setContentView(R.layout.ar_view)
        arSceneView = findViewById(R.id.arSceneView)
        arSceneView.planeRenderer.isEnabled = true

        Toast.makeText(this, "AR Camera opened!", Toast.LENGTH_SHORT).show()

        arSceneView.onSessionUpdated = { session, frame ->
            if (frame.camera.trackingState == TrackingState.TRACKING) {
                if (placedMarkers.isEmpty()) {
                    placeMarkersFromStorage(session)
                }
                updateMarkerAnchors(session, frame)
            }
        }
    }

    /*
    // TOUCH-TO-PLACE DISABLED
    // If you need to re-enable tap-to-place, uncomment these:

    private fun setupTouchInteraction() {
        arSceneView.setOnTouchListener { view, event ->
            // no-op: tap handling disabled
            view.performClick()
            true
        }
    }

    private fun placeMarkerDirectly() {
        // no-op
    }
    */

    private fun placeMarkerAtAnchor(anchor: Anchor) {
        try {
            val engine = arSceneView.engine
            val anchorNode = AnchorNode(engine, anchor).apply { this.anchor = anchor }
            arSceneView.addChildNode(anchorNode)
            createVisibleMarker(anchorNode)
            placedMarkers.add(anchorNode)
        } catch (e: Exception) {
            Log.e(tag, "Error placing marker: ${e.message}")
            Toast.makeText(this, "Failed to place marker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createVisibleMarker(parentNode: AnchorNode) {
        try {
            val engine = arSceneView.engine
            val markerNode = io.github.sceneview.node.Node(engine)
            parentNode.addChildNode(markerNode)

            val modelLoader = ModelLoader(engine = engine, context = this)
            modelLoader.loadModelAsync(
                fileLocation = "test.glb",
                onResult = { loadedModel ->
                    loadedModel?.let {
                        modelLoader.createInstance(it)?.let { instance ->
                            val modelNode = io.github.sceneview.node.ModelNode(
                                modelInstance = instance,
                                autoAnimate = true
                            ).apply {
                                scale = Scale(0.1f)
                                position = Position(0f, 0.05f, 0f)
                            }
                            markerNode.addChildNode(modelNode)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(tag, "Error in createVisibleMarker: ${e.message}")
        }
    }

    /**
     * Pulls your COLMAP camera‐to‐world matrix and waypoints from storage,
     * inverts the transform (world→camera), rebases each waypoint into the
     * AR device’s local frame, and places them.
     */
    private fun placeMarkersFromStorage(session: Session) {
        val tm = WaypointStorage.transformationMatrix
        val wps = WaypointStorage.waypoints
        if (tm.size != 4 || tm.any { it.size != 4 } || wps.isEmpty()) return

        // Extract R (3×3) and t (3×1)
        val R = Array(3) { i -> DoubleArray(3) { j -> tm[i][j] } }
        val t = DoubleArray(3) { i -> tm[i][3] }

        // Inverse of [R t; 0 1] is [R^T  -R^T t; 0 1]
        val Rt = Array(3) { i -> DoubleArray(3) { j -> R[j][i] } }
        val invT = DoubleArray(3).also { inv ->
            for (i in 0 until 3) {
                inv[i] = -(Rt[i][0]*t[0] + Rt[i][1]*t[1] + Rt[i][2]*t[2])
            }
        }

        // Place each waypoint
        wps.forEach { wp ->
            val xw = wp.x.toDouble()
            val yw = wp.y.toDouble()
            val zw = wp.z.toDouble()

            val xc = (Rt[0][0]*xw + Rt[0][1]*yw + Rt[0][2]*zw + invT[0]).toFloat()
            val yc = (Rt[1][0]*xw + Rt[1][1]*yw + Rt[1][2]*zw + invT[1]).toFloat()
            val zc = (Rt[2][0]*xw + Rt[2][1]*yw + Rt[2][2]*zw + invT[2]).toFloat()

            val pose = Pose.makeTranslation(xc, yc, zc)
            session.createAnchor(pose)?.let { placeMarkerAtAnchor(it) }
        }
    }

    private fun updateMarkerAnchors(session: Session, frame: Frame) {
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val camPose = frame.camera.pose
        val threshold = 1.0f
        placedMarkers.toList().forEach { node ->
            val mPose = node.anchor.pose
            val dx = camPose.tx() - mPose.tx()
            val dz = camPose.tz() - mPose.tz()
            if (sqrt(dx*dx + dz*dz) < threshold && mPose.ty() != 0f) {
                val newPose = Pose.makeTranslation(mPose.tx(), 0f, mPose.tz())
                session.createAnchor(newPose)?.let {
                    arSceneView.removeChildNode(node)
                    placedMarkers.remove(node)
                    placeMarkerAtAnchor(it)
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