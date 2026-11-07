package com.krizbuds.control

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.UUID

enum class AncMode(val title: String) {
    NORMAL("Normal / Off"),
    ANC("ANC (Noise Cancellation)"),
    SPEAKTHROUGH("Ambient / SpeakThrough")
}

enum class AncLevel(val title: String, val subtitle: String, val filterId: Byte, val gainHex: Short) {
    DEFAULT("Level 1 (Mild)", "Default office / fan noise", 0x01.toByte(), 0x0000.toShort()),
    BALANCED("Level 2 (Balanced)", "Enhanced commute / chatter", 0x02.toByte(), 0x0064.toShort()), // +1.0 dB
    MAX_DEEP("Level 3 (Max Deep)", "Maximum deep cancellation", 0x03.toByte(), 0x012C.toShort()) // +3.0 dB
}

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String
)

data class EarbudsState(
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val deviceName: String = "No Device Connected",
    val macAddress: String = "",
    val leftBattery: Int = -1,
    val rightBattery: Int = -1,
    val caseBattery: Int = -1,
    val isLeftCharging: Boolean = false,
    val isRightCharging: Boolean = false,
    val isCaseCharging: Boolean = false,
    val currentMode: AncMode = AncMode.NORMAL,
    val ancLevel: AncLevel = AncLevel.DEFAULT,
    val isBeastMode: Boolean = false,
    val firmwareVersion: String = "Reading...",
    val statusLog: String = "Ready to scan for audio devices",
    val discoveredDevices: List<DiscoveredDevice> = emptyList()
)

class BluetoothEarbudsManager(private val context: Context) {
    private val TAG = "KrizBudsControl"
    private var bluetoothManager: BluetoothManager? = null
    private var adapter: BluetoothAdapter? = null
    private var gatt: BluetoothGatt? = null
    private var sppSocket: BluetoothSocket? = null
    private var sppOutputStream: OutputStream? = null
    private var sppInputStream: InputStream? = null
    private var sppJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(EarbudsState())
    val state: StateFlow<EarbudsState> = _state.asStateFlow()

    companion object {
        // Airoha AB1562 / AB155x SPP UUID
        val AIROHA_SPP_UUID: UUID = UUID.fromString("00000000-0000-0000-0099-aabbccddeeff")
        // Standard Bluetooth Serial Port Profile (SPP)
        val STANDARD_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        // BES / Bestechnic SPP UUID
        val BES_SPP_UUID: UUID = UUID.fromString("66666666-6666-6666-6666-666666666666")
        
        // BLE Services for Bluetrum & Standard BLE devices
        val BLUETRUM_SERVICE: UUID = UUID.fromString("0000ae00-0000-1000-8000-00805f9b34fb")
        val BLUETRUM_WRITE_CHAR: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
        val BLUETRUM_NOTIFY_CHAR: UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val STANDARD_BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val STANDARD_BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val DEVICE_INFO_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val FIRMWARE_REVISION_CHAR: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED") {
                val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                if (dev != null && dev.address == _state.value.macAddress && level >= 0) {
                    Log.d(TAG, "Battery broadcast received: $level%")
                    updateBatteryIfBetter(level)
                }
            }
        }
    }

    init {
        try {
            bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            adapter = bluetoothManager?.adapter
            
            val filter = IntentFilter().apply {
                addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            context.registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth init error: ${e.message}")
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun canAccessBluetooth(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT) &&
            hasPermission(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH) &&
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun readDeviceBatteryReflectively(device: BluetoothDevice) {
        try {
            val method: Method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int ?: -1
            if (level >= 0) {
                Log.d(TAG, "Read battery level via reflection: $level%")
                updateBatteryIfBetter(level)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Reflection getBatteryLevel: ${e.message}")
        }
    }

    private fun updateBatteryIfBetter(level: Int) {
        val curr = _state.value
        val left = if (curr.leftBattery < 0) level else curr.leftBattery
        val right = if (curr.rightBattery < 0) level else curr.rightBattery
        _state.value = curr.copy(
            leftBattery = left,
            rightBattery = right,
            statusLog = "Battery: $level%"
        )
    }

    fun checkPairedDevices() {
        if (!canAccessBluetooth()) {
            _state.value = _state.value.copy(statusLog = "Bluetooth permissions required")
            return
        }

        try {
            val bonded = adapter?.bondedDevices ?: return
            val pairedDevices = bonded.filter { dev ->
                val name = dev.name ?: ""
                EarbudModels.isSupportedDevice(name)
            }.map { DiscoveredDevice(it, it.name ?: "Unknown Earbuds", it.address) }

            if (pairedDevices.isNotEmpty()) {
                _state.value = _state.value.copy(
                    discoveredDevices = pairedDevices,
                    statusLog = "Found ${pairedDevices.size} paired device(s)"
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException checking bonded devices: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking bonded devices: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            try {
                val dev = result?.device ?: return
                val name = dev.name ?: result.scanRecord?.deviceName ?: return

                if (EarbudModels.isSupportedDevice(name)) {
                    val currentList = _state.value.discoveredDevices.toMutableList()
                    if (currentList.none { it.address == dev.address }) {
                        currentList.add(DiscoveredDevice(dev, name, dev.address))
                        _state.value = _state.value.copy(
                            discoveredDevices = currentList,
                            statusLog = "Discovered: $name"
                        )
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "ScanResult security exception: ${e.message}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = _state.value.copy(
                isScanning = false,
                statusLog = "Scan failed with error: $errorCode"
            )
        }
    }

    fun startScan() {
        if (!canAccessBluetooth()) {
            _state.value = _state.value.copy(statusLog = "Permissions needed to scan")
            return
        }

        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = _state.value.copy(statusLog = "Bluetooth adapter not ready")
            return
        }

        checkPairedDevices()
        _state.value = _state.value.copy(isScanning = true, statusLog = "Scanning for Bluetooth earbuds...")

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(null, settings, scanCallback)

            handler.postDelayed({
                stopScan()
            }, 10000)
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(isScanning = false, statusLog = "Bluetooth permission denied")
        } catch (e: Exception) {
            _state.value = _state.value.copy(isScanning = false, statusLog = "Scan failed: ${e.message}")
        }
    }

    fun stopScan() {
        if (_state.value.isScanning) {
            try {
                adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            } catch (e: SecurityException) {
                // Ignore
            }
            _state.value = _state.value.copy(isScanning = false, statusLog = "Scan completed.")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (!canAccessBluetooth()) {
            _state.value = _state.value.copy(statusLog = "Permissions needed to connect")
            return
        }

        stopScan()
        disconnect()

        val devName = try { device.name ?: "Wireless Earbuds" } catch (e: SecurityException) { "Wireless Earbuds" }
        _state.value = _state.value.copy(
            isConnecting = true,
            deviceName = devName,
            macAddress = device.address,
            statusLog = "Connecting to $devName (${device.address})..."
        )

        readDeviceBatteryReflectively(device)

        // Launch SPP connection in background for Airoha / Classic devices
        connectSppChannel(device)

        // Connect GATT with TRANSPORT_AUTO (works with dual-mode / BR-EDR devices)
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_AUTO)
        } catch (e: SecurityException) {
            Log.e(TAG, "Gatt connect permission error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Gatt connect failed: ${e.message}")
        }
    }

    private fun connectSppChannel(device: BluetoothDevice) {
        sppJob?.cancel()
        sppJob = scope.launch {
            val uuids = listOf(AIROHA_SPP_UUID, BES_SPP_UUID, STANDARD_SPP_UUID)
            var socket: BluetoothSocket? = null

            for (uuid in uuids) {
                try {
                    Log.d(TAG, "Attempting SPP connection with UUID $uuid...")
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket.connect()
                    Log.d(TAG, "SPP Socket Connected successfully with UUID $uuid!")
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "SPP connection failed with $uuid: ${e.message}")
                    try { socket?.close() } catch (ex: Exception) {}
                    socket = null
                }
            }

            // Fallback: Reflection method createRfcommSocket(1)
            if (socket == null) {
                try {
                    val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    socket = m.invoke(device, 1) as? BluetoothSocket
                    socket?.connect()
                    Log.d(TAG, "Connected via fallback reflection RFCOMM port 1!")
                } catch (e: Exception) {
                    Log.d(TAG, "Fallback RFCOMM failed: ${e.message}")
                    socket = null
                }
            }

            if (socket != null && socket.isConnected) {
                sppSocket = socket
                sppOutputStream = socket.outputStream
                sppInputStream = socket.inputStream

                _state.value = _state.value.copy(
                    isConnected = true,
                    isConnecting = false,
                    statusLog = "Connected to ${device.name ?: "Earbuds"} via Audio Control Channel!"
                )

                // Send Airoha RACE Battery query packet
                sendAirohaBatteryQuery()

                // Start listening for responses
                listenSppStream(socket.inputStream)
            } else {
                Log.d(TAG, "SPP could not be established; relying on GATT & System Audio Profile")
            }
        }
    }

    private var isPartnerExisting = false

    private fun sendAirohaBatteryQuery() {
        scope.launch {
            // Airoha RACE packet for Query Battery:
            // Header: 0x05, 0x5A
            // Length: 0x03, 0x00 (3 bytes)
            // CmdId:  0xD6, 0x0C (3286 = 0x0CD6 little endian)
            // Payload: Role (0x00 for AGENT/Master, 0x01 for PARTNER/Slave)
            val queryMaster = byteArrayOf(0x05, 0x5A, 0x03, 0x00, 0xD6.toByte(), 0x0C, 0x00)
            val queryPartnerRelay = byteArrayOf(0x05, 0x5A, 0x03, 0x00, 0xD6.toByte(), 0x0C, 0x01)
            // Query TWS Partner Presence check: CmdId 0x0D00 (3328 = 0x0D00)
            val queryPartnerPresence = byteArrayOf(0x05, 0x5A, 0x02, 0x00, 0x00, 0x0D)

            try {
                // First, check TWS partner presence and channel
                sppOutputStream?.write(queryPartnerPresence)
                sppOutputStream?.flush()
                delay(200)

                // Query Master (Agent)
                sppOutputStream?.write(queryMaster)
                sppOutputStream?.flush()
                Log.d(TAG, "Sent Airoha RACE battery query for Master")
                
                delay(500)
                // Query Partner
                sppOutputStream?.write(queryPartnerRelay)
                sppOutputStream?.flush()
                Log.d(TAG, "Sent Airoha RACE battery query for Partner")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending Airoha battery query: ${e.message}")
            }
        }
    }

    private fun listenSppStream(inputStream: InputStream) {
        val buffer = ByteArray(1024)
        try {
            while (sppSocket?.isConnected == true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    val data = buffer.copyOf(bytesRead)
                    parseSppIncomingData(data)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "SPP stream closed: ${e.message}")
        }
    }

    private fun parseSppIncomingData(data: ByteArray) {
        if (data.size < 4) return
        val hex = data.joinToString(" ") { "%02X".format(it) }
        Log.d(TAG, "SPP Received [${data.size} bytes]: $hex")

        // Check for Airoha RACE packet (Response: 0x5B or Notification: 0x5D)
        if (data[0] == 0x05.toByte() && (data[1] == 0x5B.toByte() || data[1] == 0x5D.toByte())) {
            // Check Command ID at index 4, 5:
            val cmdId = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
            Log.d(TAG, "Airoha RACE response CmdId: 0x%04X, type: 0x%02X".format(cmdId, data[1]))

            // 0x0CD6 (3286): Battery Status response
            if (cmdId == 0x0CD6) {
                if (data[1] == 0x5D.toByte() && data.size >= 9) {
                    // Notification: 05 5D 05 00 D6 0C [status] [role] [level]
                    val status = data[6].toInt() and 0xFF
                    val role = data[7].toInt() and 0xFF
                    val level = data[8].toInt() and 0xFF
                    Log.d(TAG, "Airoha RACE Battery Notification -> Role: $role, Level: $level%")
                    if (role == 0) {
                        _state.value = _state.value.copy(
                            leftBattery = level,
                            // If right bud is paired and active in TWS, mirror or keep updated
                            rightBattery = if (_state.value.rightBattery <= 0) level else _state.value.rightBattery,
                            statusLog = "Left Bud: $level%"
                        )
                    } else if (role == 1) {
                        _state.value = _state.value.copy(
                            rightBattery = level,
                            statusLog = "Right Bud: $level%"
                        )
                    }
                } else if (data[1] == 0x5B.toByte() && data.size >= 9) {
                    val status = data[6].toInt() and 0xFF
                    if (status == 0) {
                        val role = data[7].toInt() and 0xFF
                        val level = data[8].toInt() and 0xFF
                        Log.d(TAG, "Airoha RACE Battery Response -> Role: $role, Level: $level%")
                        if (role == 0) {
                            _state.value = _state.value.copy(
                                leftBattery = level,
                                rightBattery = if (_state.value.rightBattery <= 0) level else _state.value.rightBattery,
                                statusLog = "Left: $level%"
                            )
                        } else if (role == 1) {
                            _state.value = _state.value.copy(rightBattery = level, statusLog = "Right: $level%")
                        }
                    }
                }
            }
            // 0x0D00 (3328): TWS Partner Presence and Role list
            else if (cmdId == 0x0D00 && data.size >= 8) {
                // Parse ae4 records from index 6
                var partnerFound = false
                var i3 = 6
                while (i3 < data.size - 1) {
                    val type = data[i3].toInt() and 0xFF
                    val ch = data[i3 + 1].toInt() and 0xFF
                    if (type == 5) {
                        partnerFound = true
                        break
                    }
                    i3 += 2
                }
                isPartnerExisting = partnerFound
                Log.d(TAG, "Airoha TWS Partner Presence: $partnerFound")
                if (partnerFound && _state.value.rightBattery <= 0 && _state.value.leftBattery > 0) {
                    _state.value = _state.value.copy(rightBattery = _state.value.leftBattery)
                }
            }
            // 0x0901 (2305): Device status notifications (including Box / Case battery)
            else if (cmdId == 2305 && data.size >= 10) {
                val moduleId = (data[7].toInt() and 0xFF shl 8) or (data[6].toInt() and 0xFF)
                val value = data[9].toInt() and 0xFF
                Log.d(TAG, "Airoha Device Status Notify -> Module: $moduleId, Val: $value")
                when (moduleId) {
                    101 -> _state.value = _state.value.copy(leftBattery = value)
                    102 -> _state.value = _state.value.copy(rightBattery = value)
                    103 -> _state.value = _state.value.copy(caseBattery = value)
                }
            }
            // 0x0E06 (3590): ANC Status response
            else if (cmdId == 0x0E06 && data.size >= 8) {
                val status = data[6].toInt() and 0xFF
                val modeByte = data[7].toInt() and 0xFF
                Log.d(TAG, "Airoha ANC Response -> Status: $status, Mode: $modeByte")
                if (status == 0 && (modeByte == 10 || modeByte == 11)) {
                    val mode = when (modeByte) {
                        11 -> AncMode.NORMAL
                        10 -> {
                            // In Airoha protocol: Filter 1..3 = ANC, Filter 4..6 = PassThrough/SpeakThrough
                            if (data.size > 8 && data[8].toInt() >= 4) AncMode.SPEAKTHROUGH else AncMode.ANC
                        }
                        else -> _state.value.currentMode
                    }
                    _state.value = _state.value.copy(currentMode = mode, statusLog = "Mode: ${mode.title}")
                }
            }
        } else {
            // Fallback Bluetrum vendor parser
            parseVendorNotification(data)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val name = try { g?.device?.name ?: _state.value.deviceName } catch (e: SecurityException) { _state.value.deviceName }
                _state.value = _state.value.copy(
                    isConnected = true,
                    isConnecting = false,
                    deviceName = name,
                    statusLog = "Connected to $name! Discovering audio controls..."
                )
                try {
                    g?.discoverServices()
                } catch (e: SecurityException) {
                    Log.e(TAG, "discoverServices SecurityException")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // If Airoha SPP is connected, do NOT mark disconnected or reset batteries!
                // Airoha TWS handles all commands and notifications over SPP RFCOMM.
                if (sppSocket?.isConnected == true) {
                    Log.d(TAG, "BLE GATT disconnected (expected for Airoha), but SPP is active; staying connected.")
                    return
                }
                _state.value = _state.value.copy(
                    isConnected = false,
                    isConnecting = false,
                    leftBattery = -1,
                    rightBattery = -1,
                    caseBattery = -1,
                    statusLog = "Disconnected from device."
                )
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || g == null) return

            try {
                var configured = false
                val bleService = g.getService(BLUETRUM_SERVICE)
                if (bleService != null) {
                    val notifyChar = bleService.getCharacteristic(BLUETRUM_NOTIFY_CHAR)
                    if (notifyChar != null) {
                        g.setCharacteristicNotification(notifyChar, true)
                        val cccd = notifyChar.getDescriptor(CCCD_UUID)
                        if (cccd != null) {
                            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            g.writeDescriptor(cccd)
                            configured = true
                        }
                    }
                }

                val battService = g.getService(STANDARD_BATTERY_SERVICE)
                if (battService != null) {
                    val battChar = battService.getCharacteristic(STANDARD_BATTERY_LEVEL)
                    if (battChar != null) {
                        g.readCharacteristic(battChar)
                    }
                }

                val infoService = g.getService(DEVICE_INFO_SERVICE)
                if (infoService != null) {
                    val fwChar = infoService.getCharacteristic(FIRMWARE_REVISION_CHAR)
                    if (fwChar != null) {
                        g.readCharacteristic(fwChar)
                    }
                }

                _state.value = _state.value.copy(
                    statusLog = if (configured) "Ready! Battery & Noise controls active" else "Connected (standard profile)"
                )

                requestStatusUpdate()
            } catch (e: SecurityException) {
                Log.e(TAG, "onServicesDiscovered security error: ${e.message}")
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == STANDARD_BATTERY_LEVEL && value.isNotEmpty()) {
                    val batt = value[0].toInt() and 0xFF
                    updateBatteryIfBetter(batt)
                } else if (characteristic.uuid == FIRMWARE_REVISION_CHAR) {
                    val fw = String(value)
                    _state.value = _state.value.copy(firmwareVersion = fw)
                }
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            parseVendorNotification(value)
        }
    }

    fun requestStatusUpdate() {
        val g = gatt ?: return
        try {
            val service = g.getService(BLUETRUM_SERVICE) ?: return
            val writeChar = service.getCharacteristic(BLUETRUM_WRITE_CHAR) ?: return
            val queryCmd = byteArrayOf(0xAA.toByte(), 0x01, 0x05)
            writeChar.value = queryCmd
            writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            g.writeCharacteristic(writeChar)
        } catch (e: SecurityException) {
            Log.e(TAG, "requestStatusUpdate SecurityException")
        }
    }

    private fun parseVendorNotification(data: ByteArray) {
        if (data.size < 2) return

        when (data[0].toInt() and 0xFF) {
            0x01, 0x11 -> {
                val level = data[1].toInt() and 0xFF
                val isCharging = if (data.size > 2) data[2].toInt() == 1 else false
                _state.value = _state.value.copy(
                    leftBattery = level.coerceIn(0, 100),
                    isLeftCharging = isCharging,
                    statusLog = "Updated Left Bud: $level%"
                )
            }
            0x02, 0x12 -> {
                val level = data[1].toInt() and 0xFF
                val isCharging = if (data.size > 2) data[2].toInt() == 1 else false
                _state.value = _state.value.copy(
                    rightBattery = level.coerceIn(0, 100),
                    isRightCharging = isCharging,
                    statusLog = "Updated Right Bud: $level%"
                )
            }
            0x03, 0x13 -> {
                val level = data[1].toInt() and 0xFF
                val isCharging = if (data.size > 2) data[2].toInt() == 1 else false
                _state.value = _state.value.copy(
                    caseBattery = level.coerceIn(0, 100),
                    isCaseCharging = isCharging,
                    statusLog = "Updated Case: $level%"
                )
            }
            0x14 -> {
                val mode = when (data[1].toInt()) {
                    0x01 -> AncMode.ANC
                    0x02 -> AncMode.SPEAKTHROUGH
                    else -> AncMode.NORMAL
                }
                _state.value = _state.value.copy(currentMode = mode)
            }
        }
    }

    fun setAncLevel(level: AncLevel) {
        _state.value = _state.value.copy(ancLevel = level)
        if (_state.value.currentMode == AncMode.ANC) {
            setMode(AncMode.ANC, level)
        }
    }

    fun toggleBeastMode(enabled: Boolean) {
        _state.value = _state.value.copy(isBeastMode = enabled)
        scope.launch {
            try {
                if (sppOutputStream != null) {
                    // 1. Airoha RACE Beast Mode (CmdId 0x1101 / 4353):
                    // ON  = [0x05, 0x5A, 0x04, 0x00, 0x01, 0x11, 0xA4, 0x00]
                    // OFF = [0x05, 0x5A, 0x04, 0x00, 0x01, 0x11, 0xA5, 0x00]
                    val airohaActionByte = if (enabled) 0xA4.toByte() else 0xA5.toByte()
                    val airohaPacket = byteArrayOf(0x05, 0x5A, 0x04, 0x00, 0x01, 0x11, airohaActionByte, 0x00)
                    sppOutputStream?.write(airohaPacket)
                    sppOutputStream?.flush()
                    Log.d(TAG, "Sent Airoha Beast Mode packet (Enabled: $enabled)")

                    // 2. BES Beast Mode (CmdId 25352 / 0x6308, Tag 0x81):
                    // ON  = [0x08, 0x63, 0x02, 0x00, 0x81, 0x01]
                    // OFF = [0x08, 0x63, 0x02, 0x00, 0x81, 0x00]
                    delay(50)
                    val besVal = if (enabled) 0x01.toByte() else 0x00.toByte()
                    val besPacket = byteArrayOf(0x08, 0x63, 0x02, 0x00, 0x81.toByte(), besVal)
                    sppOutputStream?.write(besPacket)
                    sppOutputStream?.flush()
                    Log.d(TAG, "Sent BES Beast Mode packet (Enabled: $enabled)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending Beast Mode command: ${e.message}")
            }
        }
    }

    fun setEqPreset(preset: EqPreset) {
        scope.launch {
            try {
                if (sppOutputStream != null) {
                    // Airoha RACE EQ Command (CmdId 0x0E04, SubCmd 0x01)
                    val eqPacket = byteArrayOf(0x05, 0x5A, 0x04, 0x00, 0x04, 0x0E, 0x01, preset.id)
                    sppOutputStream?.write(eqPacket)
                    sppOutputStream?.flush()
                    Log.d(TAG, "Sent Airoha EQ Preset: ${preset.title} (${preset.id})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending EQ Preset: ${e.message}")
            }
        }
    }


    fun setMode(mode: AncMode, requestedLevel: AncLevel? = null) {
        val level = requestedLevel ?: _state.value.ancLevel
        _state.value = _state.value.copy(
            currentMode = mode,
            ancLevel = level,
            statusLog = if (mode == AncMode.ANC) "Switched to ANC (${level.title})" else "Switched mode to ${mode.title}"
        )
        scope.launch {
            // Send via Airoha RACE Protocol over SPP
            try {
                if (sppOutputStream != null) {
                    when (mode) {
                        AncMode.NORMAL -> {
                            // Turn ANC OFF: CmdId 0x0E06 (3590), SubCmd 11 (OFF)
                            // Length = 4 bytes: 0x04, 0x00
                            val offPacket = byteArrayOf(0x05, 0x5A, 0x04, 0x00, 0x06, 0x0E, 0x00, 0x0B)
                            sppOutputStream?.write(offPacket)
                            sppOutputStream?.flush()
                            Log.d(TAG, "Sent Airoha RACE ANC OFF packet")
                        }
                        AncMode.ANC -> {
                            // Turn ANC ON with chosen filter:
                            // Filter 1 = ANC1 (Mild), Filter 2 = ANC2 (Balanced), Filter 3 = ANC3 (Deep)
                            val filterByte = level.filterId
                            val ancPacket = byteArrayOf(0x05, 0x5A, 0x06, 0x00, 0x06, 0x0E, 0x00, 0x0A, filterByte, 0x01)
                            sppOutputStream?.write(ancPacket)
                            sppOutputStream?.flush()
                            Log.d(TAG, "Sent Airoha RACE ANC ON packet for ${level.title} (Filter: $filterByte)")

                            // Send calibrated Gain packet (SubCmd 0x0F)
                            delay(100)
                            val gainLow = (level.gainHex.toInt() and 0xFF).toByte()
                            val gainHigh = ((level.gainHex.toInt() shr 8) and 0xFF).toByte()
                            val gainPacket = byteArrayOf(0x05, 0x5A, 0x06, 0x00, 0x06, 0x0E, 0x00, 0x0F, gainLow, gainHigh)
                            sppOutputStream?.write(gainPacket)
                            sppOutputStream?.flush()
                            Log.d(TAG, "Sent Airoha RACE ANC Gain packet (${level.gainHex})")
                        }
                        AncMode.SPEAKTHROUGH -> {
                            // Turn Ambient / PassThrough ON: SubCmd 10, Filter 4 (PassThrough1), Mode 1
                            // Length = 6 bytes: 0x06, 0x00
                            val ambientPacket = byteArrayOf(0x05, 0x5A, 0x06, 0x00, 0x06, 0x0E, 0x00, 0x0A, 0x04, 0x01)
                            sppOutputStream?.write(ambientPacket)
                            sppOutputStream?.flush()
                            Log.d(TAG, "Sent Airoha RACE Ambient ON packet")

                            // Immediately send Gain packet (SubCmd 0x0F, gain = 0 dB) so DSP doesn't hang
                            delay(100)
                            val gainPacket = byteArrayOf(0x05, 0x5A, 0x06, 0x00, 0x06, 0x0E, 0x00, 0x0F, 0x00, 0x00)
                            sppOutputStream?.write(gainPacket)
                            sppOutputStream?.flush()
                            Log.d(TAG, "Sent Airoha RACE PassThrough Gain packet")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to SPP: ${e.message}")
            }

            // Only attempt Bluetrum BLE fallback if SPP is NOT connected (prevents GATT_CONN_L2C_FAILURE status 147)
            if (sppSocket?.isConnected != true) {
                val g = gatt
                if (g != null) {
                    try {
                        val service = g.getService(BLUETRUM_SERVICE)
                        val writeChar = service?.getCharacteristic(BLUETRUM_WRITE_CHAR)
                        if (writeChar != null) {
                            val bluetrumVal = when (mode) {
                                AncMode.NORMAL -> 0x00.toByte()
                                AncMode.ANC -> 0x01.toByte()
                                AncMode.SPEAKTHROUGH -> 0x02.toByte()
                            }
                            val packet = byteArrayOf(0xAA.toByte(), 0x02, 0x12, bluetrumVal)
                            writeChar.value = packet
                            writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                            g.writeCharacteristic(writeChar)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing to GATT: ${e.message}")
                    }
                }
            }

            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    currentMode = mode,
                    ancLevel = level,
                    statusLog = if (mode == AncMode.ANC) "Switched to ANC (${level.title})" else "Switched mode to ${mode.title}"
                )
            }
        }
    }

    fun disconnect() {
        sppJob?.cancel()
        try {
            sppInputStream?.close()
            sppOutputStream?.close()
            sppSocket?.close()
        } catch (e: Exception) {
            // Ignore
        } finally {
            sppInputStream = null
            sppOutputStream = null
            sppSocket = null
        }

        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: SecurityException) {
            // Ignore
        } finally {
            gatt = null
            _state.value = _state.value.copy(
                isConnected = false,
                isConnecting = false,
                statusLog = "Disconnected"
            )
        }
    }
}
