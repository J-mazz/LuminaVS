package com.lumina.engine

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumina.engine.ui.theme.LuminaVSTheme
import com.lumina.engine.ui.LuminaApp
import com.lumina.engine.LuminaViewModel

/**
 * MainActivity - Entry point for Lumina Virtual Studio
 * Initializes JNI bridge and Chaquopy runtime
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var nativeEngine: NativeEngine
    private lateinit var pythonBridge: PythonBridge
    private lateinit var cameraController: CameraController

    private val requiredPermissions: Array<String> by lazy {
        val mediaPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        mediaPerms
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val allGranted = requiredPermissions.all { perm ->
                result[perm] == true ||
                    ContextCompat.checkSelfPermission(this, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                Log.i(TAG, "All permissions granted")
                onPermissionsGranted()
            } else {
                handlePermissionDenied(result)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.i(TAG, "Lumina Virtual Studio starting...")

        ensurePermissions()

        cameraController = CameraController(this)

        // Initialize native engine
        nativeEngine = NativeEngine()
        val nativeInitialized = nativeEngine.initialize()
        Log.i(TAG, "Native engine initialized: $nativeInitialized")

        // Initialize Python orchestrator
        pythonBridge = PythonBridge()
        val pythonInitialized = pythonBridge.initialize(filesDir.absolutePath)
        Log.i(TAG, "Python orchestrator initialized: $pythonInitialized")

        setContent {
            val luminaViewModel: LuminaViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val dynamicTheme by luminaViewModel.dynamicTheme.collectAsState()

            LuminaVSTheme(dynamicColor = dynamicTheme) {
                LuminaApp(
                    nativeBridge = nativeEngine,
                    pythonOrchestrator = pythonBridge,
                    cameraController = cameraController,
                    luminaViewModel = luminaViewModel
                )
            }
        }
    }

    private fun ensurePermissions() {
        if (hasAllPermissions()) {
            onPermissionsGranted()
            return
        }
        permissionLauncher.launch(requiredPermissions)
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all { perm ->
        ContextCompat.checkSelfPermission(this, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun handlePermissionDenied(result: Map<String, Boolean>) {
        val shouldShowRationale = requiredPermissions.any { perm ->
            !result.getOrElse(perm) { false } && shouldShowRequestPermissionRationale(perm)
        }

        if (shouldShowRationale) {
            showRationaleDialog()
        } else {
            showSettingsDialog()
        }
    }

    private fun showRationaleDialog() {
        androidx.compose.ui.platform.ComposeView(this).post {
            android.app.AlertDialog.Builder(this)
                .setTitle("Permissions required")
                .setMessage("Camera, microphone, and media access are required for Lumina to capture and render content.")
                .setPositiveButton("Continue") { _, _ -> permissionLauncher.launch(requiredPermissions) }
                .setNegativeButton("Exit") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun showSettingsDialog() {
        androidx.compose.ui.platform.ComposeView(this).post {
            android.app.AlertDialog.Builder(this)
                .setTitle("Permissions denied")
                .setMessage("Please enable camera and microphone permissions in Settings to continue using Lumina.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Exit") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun onPermissionsGranted() {
        Log.i(TAG, "Permissions granted; proceeding with full functionality")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Shutting down Lumina Virtual Studio")
        
        pythonBridge.shutdown()
        nativeEngine.shutdown()
    }
}
