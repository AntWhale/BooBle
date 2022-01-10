package com.thisandroid.booble

import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.recyclerview.widget.LinearLayoutManager
import com.thisandroid.booble.databinding.ActivityMainBinding
import java.lang.IllegalArgumentException
import java.util.jar.Manifest

class MainActivity : BaseActivity() {
    val TAG = "로그"
    val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    var recyclerAdapter:BLERecyclerViewAdapter? = null
    //권한
    private val PERMISSIONS = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    val REQ_LOC_PERM = 99

    val REQUEST_ENABLE_BT = 40
    private var devicesArr = ArrayList<BluetoothDevice>()
    var bluetoothHeadset: BluetoothHeadset? = null
    var bluetoothHealth: BluetoothHealth? = null

    //Get the default adapter
    val bluetoothAdapter: BluetoothAdapter? by lazy (LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothService : BluetoothLeService? = null

    // Code to manage Service lifecycle.
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService?.let{ bluetoothLeService ->
                if(bluetoothAdapter == null){
                    Log.e(TAG, "블루투스 어댑터 널임. Unable to initialize BluetoothLeService")
                    finish()
                }
                //장비와 연결
                bluetoothService?.connect(devicesArr.get(0).address)
                Log.d(TAG, "블루투스 서비스 연결됨")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            bluetoothService = null
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if(profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset

            }
            if(profile == BluetoothProfile.HEALTH)
                bluetoothHealth = proxy as BluetoothHealth
            }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
            }
        }

    }

    //블루투스 활성화 되었는지
    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    override fun onResume() {
        super.onResume()
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        recyclerAdapter = BLERecyclerViewAdapter()
        recyclerAdapter?.listDevice = devicesArr

        binding.recyclerView.adapter = recyclerAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        // Establish connection to the proxy.
        bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)

        bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        binding.btnScan.setOnClickListener {
            requirePermissions(PERMISSIONS, REQ_LOC_PERM)

        }

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)


        // Close proxy connection after use.
        //bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)

    }

    //제한 시간에 스캔하기
    //private val bluetoothLeScanner = BluetoothAdapter.bluetoothLeScanner
    private var scanning = false
    private val handler = Handler()
    private val SCAN_PERIOD: Long = 5000
    private val leScanCallback = object : ScanCallback(){
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d(TAG, "스캔에 실패했습니다" + errorCode)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.let{
                Log.d(TAG, "실행되나?.")
                for(result in it){
                    if(!devicesArr.contains(result.device) && result.device.name!=null) recyclerAdapter.addDevice(result.device)
                }
                Log.d(TAG, "스캔한 장치 ${devicesArr}")
            } ?: Log.d(TAG, "스캔 가능한 장치가 없습니다.")
        }
    }

    fun scanLeDevice(){
        if(!scanning) {
            //제한된 시간만 스캔한다
            handler.postDelayed({
                scanning = false
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
                    Log.d(TAG, "스캔 완료")

            }, SCAN_PERIOD)
            Log.d(TAG, "스캔 시작")
            scanning = true
            bluetoothAdapter?.bluetoothLeScanner?.startScan(leScanCallback)
        }else{
            Log.d(TAG, "스캔 중지")
            scanning = false
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    private var connected: Boolean = false
    //GATT가 연결되었는지 broadcast를 받으려면 브로드캐스트 수신자 구현해야함
    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action){
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    connected = true
                    displayToast("GATT 연결 성공")
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    displayToast("GATT 연결 성공")
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    //Show all the supported services and characteristics on the user interface
                   displayGattServices(bluetoothService?.getSupportedGattServices())
                }
            }
        }
    }


    displayGattServices(gattServices: List<BluetoothGattService?>?)
    {
        if (gattServices == null) return
        var uuid: String?
        val unknownServiceString: String = resources.getString(R.string.unknown_service)
        val unknowncharaString: String = resources.getString(R.string.unknown_characteristic)
        val gattServiceData: MutableList<HashMap<String, String>> = mutableListOf()
        val gattCharacteristicData: MutableList<ArrayList<HashMap<String, String>>> = mutableListOf()
        var mGattCharacteristics = mutableListOf()

        // Loops through available GATT Services.
        gattServices.forEach { gattService ->
            val currentServiceData = HashMap<String, String>()
            uuid = gattService.uuid.toString()
            currentServiceData[LIST_NAME] = SampleGattAttributes.lookup(uuid, unknownServiceString)
            currentServiceData[LIST_UUID] = uuid
            gattServiceData += currentServiceData

            val gattCharacteristicGroupData: ArrayList<HashMap<String, String>> = arrayListOf()
            val gattCharacteristics = gattService.characteristics
            val charas: MutableList<BluetoothGattCharacteristic> = mutableListOf()

            // Loops through available Characteristics.
            gattCharacteristics.forEach { gattCharacteristic ->
                charas += gattCharacteristic
                val currentCharaData: HashMap<String, String> = hashMapOf()
                uuid = gattCharacteristic.uuid.toString()
                currentCharaData[LIST_NAME] = SampleGattAttributes.lookup(uuid, unknownCharaString)
                currentCharaData[LIST_UUID] = uuid
                gattCharacteristicGroupData += currentCharaData
            }
            mGattCharacteristics += charas
            gattCharacteristicData += gattCharacteristicGroupData
        }
    }

    fun displayToast(string :String){
        Toast.makeText(this, string, Toast.LENGTH_LONG).show()
    }

    override fun permissionDenied(requestCode: Int) {
        when(requestCode){
            REQ_LOC_PERM -> {
                Toast.makeText(this, "ACCESS_FINE_LOCATION 허용불가",Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun permissionGranted(requestCode: Int) {
        when(requestCode){
            REQ_LOC_PERM -> {
                Toast.makeText(this, "ACCESS_FINE_LOCATION 허용",Toast.LENGTH_LONG).show()
                //스캔시작
                scanLeDevice()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {   //블루투스 사용 허가 다이얼로그 결과
        super.onActivityResult(requestCode, resultCode, data)
        if(resultCode == RESULT_OK){

        }else{
            if(requestCode == REQUEST_ENABLE_BT){
                Toast.makeText(this, "블루투스를 사용해야만 가능합니다", Toast.LENGTH_LONG).show()
                Log.d(TAG, "블루투스 사용 금지됨")
                finish()
            }
        }

    }
}