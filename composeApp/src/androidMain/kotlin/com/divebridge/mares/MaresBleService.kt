package com.divebridge.mares

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.divebridge.dive.Dive
import java.util.UUID

/**
 * BLE peripheral that emulates a Mares Puck 4 dive computer.
 * Advertises with the Mares service UUID and handles the Icon HD protocol
 * to serve dive data to the MySSI app.
 */
@SuppressLint("MissingPermission")
class MaresBleService(
    private val context: Context,
) {
    companion object {
        private const val TAG = "MaresBLE"

        val SERVICE_UUID: UUID = UUID.fromString("544e326b-5b72-c6b0-1c46-41c1bc448118")
        val WRITE_CHAR_UUID: UUID = UUID.fromString("99a91ebd-b21f-1689-bb43-681f1f55e966")
        val NOTIFY_CHAR_UUID: UUID = UUID.fromString("1d1aae28-d2a8-91a1-1242-9d2973fbe571")
        val EXTRA_CHAR_UUID: UUID = UUID.fromString("d8b3ab7c-4101-ec80-c441-9b0914f6ebc3")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Standard BLE services
        val DEVICE_INFO_SERVICE_UUID: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_UUID: UUID = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_UUID: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
        val SERIAL_NUMBER_UUID: UUID = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
    }

    private var protocol: MaresProtocol? = null
    private var gattServer: BluetoothGattServer? = null
    private var connectedDevice: BluetoothDevice? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var isAdvertising = false
    private var pendingServices = mutableListOf<BluetoothGattService>()
    private var pendingCmd: Byte? = null // buffered command waiting for payload
    private var savedAdapterName: String? = null
    private var currentMtu: Int = 23 // BLE default; updated by onMtuChanged

    private val ioThread = java.util.concurrent.Executors.newSingleThreadExecutor()

    var onStateChanged: ((Boolean) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    fun start(dive: Dive) {
        protocol = MaresProtocol(dive)
        pendingCmd = null

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth not available or not enabled")
            return
        }

        if (!adapter.isMultipleAdvertisementSupported) {
            log("BLE peripheral mode not supported on this device")
            return
        }

        startGattServer(bluetoothManager)
        startAdvertising(adapter)
    }

    fun stop() {
        stopAdvertising()
        gattServer?.close()
        gattServer = null
        protocol = null
        connectedDevice = null
        isAdvertising = false
        // Restore original adapter name
        savedAdapterName?.let {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bm.adapter?.name = it
            savedAdapterName = null
        }
        onStateChanged?.invoke(false)
        log("Stopped")
    }

    val isRunning: Boolean get() = isAdvertising

    private fun startGattServer(bluetoothManager: BluetoothManager) {
        gattServer = bluetoothManager.openGattServer(context, gattCallback)

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Write characteristic (WriteWithoutResponse)
        val writeChar = BluetoothGattCharacteristic(
            WRITE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(writeChar)

        // Notify characteristic (Read + Notify)
        notifyChar = BluetoothGattCharacteristic(
            NOTIFY_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
        )
        cccd.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        notifyChar!!.addDescriptor(cccd)
        service.addCharacteristic(notifyChar!!)

        // Additional characteristic (as seen on real Mares devices)
        val extraChar = BluetoothGattCharacteristic(
            EXTRA_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(extraChar)

        // Device Information service (standard BLE)
        val deviceInfoService = BluetoothGattService(DEVICE_INFO_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val manufacturerChar = BluetoothGattCharacteristic(
            MANUFACTURER_NAME_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        manufacturerChar.value = "Mares".toByteArray()
        deviceInfoService.addCharacteristic(manufacturerChar)

        val modelChar = BluetoothGattCharacteristic(
            MODEL_NUMBER_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        modelChar.value = "Puck4".toByteArray()
        deviceInfoService.addCharacteristic(modelChar)

        val serialChar = BluetoothGattCharacteristic(
            SERIAL_NUMBER_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        serialChar.value = "000001".toByteArray()
        deviceInfoService.addCharacteristic(serialChar)

        // Queue services -- must add sequentially (wait for onServiceAdded)
        pendingServices.clear()
        pendingServices.add(deviceInfoService)
        gattServer?.addService(service)
        log("GATT server started, adding services...")
    }

    private fun startAdvertising(adapter: BluetoothAdapter) {
        savedAdapterName = adapter.name
        adapter.name = "Puck4"

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0) // advertise indefinitely
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        adapter.bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertising() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
    }

    private var transferredBytes = 0
    private var totalTransferBytes = 0

    private fun cmdName(cmd: Byte): String = when (cmd) {
        MaresProtocol.CMD_VERSION -> "VERSION"
        MaresProtocol.CMD_OBJ_INIT -> "OBJ_INIT"
        MaresProtocol.CMD_OBJ_EVEN -> "OBJ_EVEN"
        MaresProtocol.CMD_OBJ_ODD -> "OBJ_ODD"
        MaresProtocol.CMD_READ -> "READ"
        MaresProtocol.CMD_SET_TIME -> "SET_TIME"
        else -> "0x${"%02X".format(cmd)}"
    }

    private fun handleProtocolWrite(value: ByteArray) {
        val proto = protocol ?: return

        // Check if this is a payload for a previously received command
        if (pendingCmd != null) {
            val cmd = pendingCmd!!
            pendingCmd = null

            // Decode OBJ_INIT payload for logging
            if (cmd == MaresProtocol.CMD_OBJ_INIT && value.size >= 4) {
                val idx = (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
                val sub = value[3].toInt() and 0xFF
                val objName = when {
                    idx == 0x2000 && sub == 0x02 -> "model"
                    idx == 0x2000 && sub == 0x04 -> "serial"
                    idx == 0x2008 && sub == 0x01 -> "dive count"
                    idx >= 0x3000 && sub == 0x02 -> "dive header #${idx - 0x3000}"
                    idx >= 0x3000 && sub == 0x03 -> "dive profile #${idx - 0x3000}"
                    else -> "obj 0x${"%04X".format(idx)}/0x${"%02X".format(sub)}"
                }
                log("OBJ_INIT -> $objName")
            }

            val response = proto.handleCommand(cmd, value)
            if (response.isNotEmpty()) {
                // Check if this starts a large transfer
                if (cmd == MaresProtocol.CMD_OBJ_INIT && response.size == 16 && response[0] == 0x41.toByte()) {
                    totalTransferBytes = (response[4].toInt() and 0xFF) or
                            ((response[5].toInt() and 0xFF) shl 8) or
                            ((response[6].toInt() and 0xFF) shl 16) or
                            ((response[7].toInt() and 0xFF) shl 24)
                    transferredBytes = 0
                    log("  Starting transfer: $totalTransferBytes bytes")
                }
                val framed = ByteArray(response.size + 1)
                response.copyInto(framed, 0)
                framed[framed.size - 1] = MaresProtocol.END
                sendRaw(framed)
            }
            return
        }

        // Must be a command (2 bytes: cmd, cmd^0xA5)
        if (value.size >= 2) {
            val cmd = value[0]
            val check = value[1]
            if ((cmd.toInt() xor 0xA5).toByte() != check) {
                log("Unknown write: ${value.joinToString(" ") { "%02X".format(it) }}")
                return
            }

            // Commands that expect a follow-up payload
            val needsPayload = cmd == MaresProtocol.CMD_OBJ_INIT ||
                    cmd == MaresProtocol.CMD_READ ||
                    cmd == MaresProtocol.CMD_SET_TIME

            if (needsPayload && value.size == 2) {
                pendingCmd = cmd
                sendAck()
                return
            }

            val payload = if (value.size > 2) value.copyOfRange(2, value.size) else ByteArray(0)

            val response = proto.handleCommand(cmd, payload)
            if (response.isNotEmpty()) {
                // Track transfer progress
                if (cmd == MaresProtocol.CMD_OBJ_EVEN || cmd == MaresProtocol.CMD_OBJ_ODD) {
                    transferredBytes += response.size - 1 // minus toggle byte
                    if (totalTransferBytes > 0) {
                        val pct = (transferredBytes * 100) / totalTransferBytes
                        log("${cmdName(cmd)} -> ${response.size}b ($pct% of $totalTransferBytes)")
                    }
                    if (!proto.hasPendingData()) {
                        log("Transfer complete: $transferredBytes bytes sent")
                    }
                } else {
                    log("${cmdName(cmd)} -> ${response.size}b response")
                }
                sendResponse(response)
            }
        }
    }

    private fun sendAck() {
        sendRaw(byteArrayOf(MaresProtocol.ACK))
    }

    private fun sendResponse(data: ByteArray) {
        val device = connectedDevice ?: return
        val char = notifyChar ?: return

        // Frame: [ACK] [data] [END]
        val framed = ByteArray(data.size + 2)
        framed[0] = MaresProtocol.ACK
        data.copyInto(framed, 1)
        framed[framed.size - 1] = MaresProtocol.END

        sendRaw(framed)
    }

    private fun sendRaw(data: ByteArray) {
        val device = connectedDevice ?: return
        val char = notifyChar ?: return
        val maxChunk = (currentMtu - 3).coerceIn(20, 512) // ATT payload = MTU - 3
        var offset = 0
        while (offset < data.size) {
            val end = (offset + maxChunk).coerceAtMost(data.size)
            val chunk = data.copyOfRange(offset, end)
            char.value = chunk
            val sent = gattServer?.notifyCharacteristicChanged(device, char, false) ?: false
            if (!sent) {
                log("notifyCharacteristicChanged failed at offset $offset")
                return
            }
            offset = end
            // Pace notifications: wait for BLE stack to process
            if (offset < data.size) {
                Thread.sleep(10)
            }
        }
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        onLog?.invoke(msg)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            onStateChanged?.invoke(true)
            log("Advertising as Puck4")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            log("Advertising failed: error $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Device connected: ${device.address}")
                // Don't set connectedDevice yet -- wait for CCCD write to identify
                // the real dive computer client (not random BLE scanners)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (device.address == connectedDevice?.address) {
                    connectedDevice = null
                    pendingCmd = null
                    currentMtu = 23
                    log("Device disconnected, restarting advertising")
                    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                    bm.adapter?.let { startAdvertising(it) }
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor, preparedWrite: Boolean,
            responseNeeded: Boolean, offset: Int, value: ByteArray,
        ) {
            val hex = value.joinToString(" ") { "%02X".format(it) }
            log("DESC WRITE [${descriptor.uuid.toString().take(8)}] ${value.size}b: $hex")

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            }

            // CCCD write to enable notifications -- this identifies the real client
            if (descriptor.uuid == CCCD_UUID) {
                connectedDevice = device
                val name = device.name ?: "unknown"
                val type = when (device.type) {
                    BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                    BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                    BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                    else -> "Unknown"
                }
                log("Client accepted: $name (${device.address}) type=$type MTU=$currentMtu")
                stopAdvertising()
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray,
        ) {
            val hex = value.joinToString(" ") { "%02X".format(it) }
            log("WRITE [${characteristic.uuid.toString().take(8)}] ${value.size}b: $hex")

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            if (characteristic.uuid == WRITE_CHAR_UUID) {
                val valueCopy = value.copyOf()
                ioThread.execute { handleProtocolWrite(valueCopy) }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            log("READ [${characteristic.uuid.toString().take(8)}] offset=$offset")
            val value = when (characteristic.uuid) {
                EXTRA_CHAR_UUID -> {
                    // "UART version" -- return a version byte that indicates ready
                    byteArrayOf(0x01)
                }
                NOTIFY_CHAR_UUID -> ByteArray(1)
                else -> characteristic.value ?: ByteArray(0)
            }
            val responseValue = if (offset < value.size) value.copyOfRange(offset, value.size) else ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, responseValue)
            log("  <- READ RSP ${responseValue.size}b: ${responseValue.joinToString(" ") { "%02X".format(it) }}")
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            log("Service added: status=$status uuid=${service?.uuid?.toString()?.take(8)}")
            // Add next pending service if any
            if (pendingServices.isNotEmpty()) {
                val next = pendingServices.removeAt(0)
                gattServer?.addService(next)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            if (connectedDevice == null || device?.address == connectedDevice?.address) {
                currentMtu = mtu
                // Match chunk size to MTU so each response fits in one notification
                val chunkSize = (mtu - 7).coerceIn(20, 511)
                protocol?.maxDataChunkSize = chunkSize
                log("MTU changed: $mtu (chunk size: $chunkSize)")
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            log("DESC READ [${descriptor.uuid.toString().take(8)}]")
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                descriptor.value ?: ByteArray(0))
        }
    }
}