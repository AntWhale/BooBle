package com.thisandroid.booble

import android.app.ListActivity
import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.content.Intent
import android.os.*
import android.provider.ContactsContract.Intents.Insert.ACTION
import android.system.Os.close
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.thisandroid.booble.databinding.ActivityDeviceScanBinding
import java.lang.IllegalArgumentException
import java.util.*

class BluetoothLeService(private var bluetoothGatt : BluetoothGatt?) : Service() {

    private val TAG = "로그"

    companion object {
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2
        const val ACTION_GATT_CONNECTED = "com.thisandroid.booble.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.thisandroid.booble.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.thisandroid.booble.le.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.thisandroid.booble.le.ACTION_DATA_AVAILABLE"
        const val EXTRA_DATA = "com.thisandroid.booble.le.EXTRA_DATA"
    }
    val UUID_HEART_RATE_MEASUREMENT = UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT)

    private var device: BluetoothDevice? = null
    private var connectionState = STATE_DISCONNECTED

    private var bluetoothAdapter: BluetoothAdapter ?= null

    fun initialize() : Boolean {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    private val gattCallback : BluetoothGattCallback = object : BluetoothGattCallback(){
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val intentAction : String
            when(newState){
                BluetoothProfile.STATE_CONNECTED ->{
                    intentAction = ACTION_GATT_CONNECTED
                    connectionState = STATE_CONNECTED
                    broadcastUpdate(intentAction)
                    Log.d(TAG, "GATT server와 연결됨")
                    Log.d(TAG, "서비스 찾기 시작: " +
                            bluetoothGatt?.discoverServices())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT server와 연결안됨 GATT연결 종료")
                    intentAction = ACTION_GATT_DISCONNECTED
                    connectionState = STATE_DISCONNECTED
                    broadcastUpdate(intentAction)
                    bluetoothGatt?.disconnect()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }

            }
        }
        //디바이스가 가능한 서비스를 알려줄때 호출되는 함수
        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            when(status){
                BluetoothGatt.GATT_SUCCESS -> broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
                else -> Log.d(TAG, "onServicesDiscovered 상태결과: $status")
            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)

            when(status){
                BluetoothGatt.GATT_SUCCESS -> {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                }
            }
        }
    }

    //작업 전달
    private fun broadcastUpdate(action: String){
        val intent = Intent(action)
        sendBroadcast(intent)
    }
    //characteristic도 보내고 싶은데 이거 맞나??
    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic?){
        val intent = Intent(action)

        // This is special handling for the Heart Rate Measurement profile. Data
        // parsing is carried out as per profile specifications.
        when(characteristic?.uuid) {
            UUID_HEART_RATE_MEASUREMENT -> {
                val flag = characteristic?.properties
                val format = when (flag?.and(0x01)) {
                    0x01 -> {
                        Log.d(TAG, "Heart rate format UINT16.")
                        BluetoothGattCharacteristic.FORMAT_UINT16
                    }
                    else -> {
                        Log.d(TAG, "Heart rate format UINT8")
                        BluetoothGattCharacteristic.FORMAT_UINT8
                    }
                }
                val heartRate = characteristic?.getIntValue(format, 1)
                Log.d(TAG, String.format("Received heart rate: %d", heartRate))
                intent.putExtra(EXTRA_DATA, (heartRate).toString())
                }
            else -> {
                // For all other profiles, writes the data formatted in HEX.
                val data: ByteArray? = characteristic?.value
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    intent.putExtra(EXTRA_DATA, "$data\n$hexString")
                }
            }
        }
        sendBroadcast(intent)
    }

    private var mScanning: Boolean = false

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    private fun close(){
        bluetoothGatt?.let {
            gatt ->
            gatt.close()
            bluetoothGatt = null
        }
    }

    inner class LocalBinder: Binder() {
        fun getService() : BluetoothLeService {
            return this@BluetoothLeService
        }
    }

    //블루투스 장비와 연결하기
    fun connect(address: String): Boolean {
        bluetoothAdapter?.let{ adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
                return true
            } catch (exception: IllegalArgumentException) {
                Log.d(TAG, "Device not found with provided address.")
                return false
            }
            //connect to the GATT server on the device
        } ?: run{
            Log.d(TAG, "Bluetoothadapter 초기화 안됨")
            return false
        }
    }

    //서비스가 발견되면 서비스는 getServices 호출가능
    fun getSupportedGattServices() : List<BluetoothGattService?>? {
        return bluetoothGatt?.services
    }
}