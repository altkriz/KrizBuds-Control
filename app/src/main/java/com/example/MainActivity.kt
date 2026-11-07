package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.boat.control.BluetoothBoatManager
import com.example.ui.BoatControlApp
import com.example.ui.theme.BoatControlTheme

class MainActivity : ComponentActivity() {
    private lateinit var boatManager: BluetoothBoatManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            boatManager.checkPairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required to detect earbuds", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            boatManager = BluetoothBoatManager(applicationContext)
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
        }

        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            val state by boatManager.state.collectAsState()

            BoatControlTheme(darkTheme = isDarkTheme) {
                BoatControlApp(
                    state = state,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onModeSelected = { boatManager.setMode(it) },
                    onAncLevelSelected = { boatManager.setAncLevel(it) },
                    onBeastModeToggled = { boatManager.toggleBeastMode(it) },
                    onScanClicked = {
                        requestRequiredPermissions()
                        boatManager.startScan()
                    },
                    onDeviceSelected = { dev ->
                        boatManager.connectToDevice(dev.device)
                    },
                    onDisconnect = { boatManager.disconnect() },
                    onEqPresetSelected = { boatManager.setEqPreset(it) }
                )
            }
        }

        // Request permissions after UI composition
        window.decorView.post {
            requestRequiredPermissions()
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            boatManager.checkPairedDevices()
        }
    }
}
