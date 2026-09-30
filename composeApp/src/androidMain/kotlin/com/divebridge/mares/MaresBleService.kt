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

    var onStateChanged: ((Boolean) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    fun start(dive: Dive) {
        protocol = MaresProtocol(dive)

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
        notifyChar!!.addDescriptor(cccd)
        service.addCharacteristic(notifyChar!!)

        // Additional characteristic (as seen on real Mares devices)
        val extraChar = BluetoothGattCharacteristic(
            EXTRA_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        service.addCharacteristic(extraChar)

        gattServer?.addService(service)

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
        modelChar.value = "Puck 4".toByteArray()
        deviceInfoService.addCharacteristic(modelChar)

        val serialChar = BluetoothGattCharacteristic(
            SERIAL_NUMBER_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        serialChar.value = "000001".toByteArray()
        deviceInfoService.addCharacteristic(serialChar)

        gattServer?.addService(deviceInfoService)

        log("GATT server started")
    }

    private fun startAdvertising(adapter: BluetoothAdapter) {
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

    private fun sendResponse(data: ByteArray) {
        val device = connectedDevice ?: return
        val char = notifyChar ?: return

        // Frame: [ACK] [data] [END]
        val framed = ByteArray(data.size + 2)
        framed[0] = MaresProtocol.ACK
        data.copyInto(framed, 1)
        framed[framed.size - 1] = MaresProtocol.END

        char.value = framed
        gattServer?.notifyCharacteristicChanged(device, char, false)
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
                connectedDevice = device
                log("Device connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null
                log("Device disconnected")
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

            // CCCD write to enable notifications
            if (descriptor.uuid == CCCD_UUID) {
                log("Notifications enabled")
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

            if (characteristic.uuid == WRITE_CHAR_UUID && value.size >= 2) {
                val cmd = value[0]
                val check = value[1]
                if ((cmd.toInt() xor 0xA5).toByte() != check) {
                    log("  -> invalid check byte, expected ${"%02X".format((cmd.toInt() xor 0xA5) and 0xFF)}")
                    return
                }

                val payload = if (value.size > 2) value.copyOfRange(2, value.size) else ByteArray(0)
                log("  -> CMD 0x${"%02X".format(cmd)} payload=${payload.size}b")

                val proto = protocol ?: return
                val response = proto.handleCommand(cmd, payload)
                if (response.isNotEmpty()) {
                    log("  <- RSP ${response.size}b")
                    sendResponse(response)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            log("READ [${characteristic.uuid.toString().take(8)}] offset=$offset")
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                characteristic.value ?: ByteArray(0))
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            log("Service added: status=$status uuid=${service?.uuid?.toString()?.take(8)}")
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            log("MTU changed: $mtu")
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