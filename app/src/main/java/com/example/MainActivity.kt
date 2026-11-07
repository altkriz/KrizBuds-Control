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
import com.krizbuds.control.BluetoothEarbudsManager
import com.example.ui.KrizBudsApp
import com.example.ui.theme.KrizBudsTheme

class MainActivity : ComponentActivity() {
    private lateinit var earbudsManager: BluetoothEarbudsManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (allGranted) {
            earbudsManager.checkPairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth permissions are required to detect earbuds", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            earbudsManager = BluetoothEarbudsManager(applicationContext)
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
        }

        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            val state by earbudsManager.state.collectAsState()

            KrizBudsTheme(darkTheme = isDarkTheme) {
                KrizBudsApp(
                    state = state,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    onModeSelected = { earbudsManager.setMode(it) },
                    onAncLevelSelected = { earbudsManager.setAncLevel(it) },
                    onBeastModeToggled = { earbudsManager.toggleBeastMode(it) },
                    onScanClicked = {
                        requestRequiredPermissions()
                        earbudsManager.startScan()
                    },
                    onDeviceSelected = { dev ->
                        earbudsManager.connectToDevice(dev.device)
                    },
                    onDisconnect = { earbudsManager.disconnect() },
                    onEqPresetSelected = { earbudsManager.setEqPreset(it) }
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
            earbudsManager.checkPairedDevices()
        }
    }
}
