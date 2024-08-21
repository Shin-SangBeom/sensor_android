package com.example.bleairsensor

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bleairsensor.ui.theme.BleAirSensorTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.*

class MainActivity : ComponentActivity() {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private val serviceUUID: UUID = UUID.fromString("000000FF-0000-1000-8000-00805F9B34FB")
    private val service_readUUID: UUID = UUID.fromString("000000AA-0000-1000-8000-00805F9B34FB")
    private val service_writeUUID: UUID = UUID.fromString("000000BB-0000-1000-8000-00805F9B34FB")
    private lateinit var textState: MutableState<String>
    private lateinit var dataState: MutableState<String>
    private lateinit var locationState: MutableState<String>
    private val deviceList = mutableStateListOf<BluetoothDevice>()
    private var alertDialog: AlertDialog? = null
    private lateinit var writeCharacteristc: BluetoothGattCharacteristic

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var lati: Double = 0.0
    private var longi: Double = 0.0
    private var speed: Float = 0.0f
    private var is_indoor: Boolean = false;
    private var horizontalAccuracy: Float = 0.0f
    private var isConnected: Boolean = false;
//    val androidId: String = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

    private val client = OkHttpClient()

    private lateinit var sharedPreferences: SharedPreferences
    var uuid_str: String = ""

    private fun getOrGenerateUUID(): String {
        val storedUUID = sharedPreferences.getString("UUID", null)
        return if (storedUUID != null) {
            storedUUID
        } else {
            val newUUID = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("UUID", newUUID).apply()
            newUUID
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            val bluetoothScanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
            val bluetoothConnectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
//            val postNotificationsGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            val postNotificationsGranted = true

                if (fineLocationGranted && coarseLocationGranted &&
                (bluetoothScanGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) &&
                (bluetoothConnectGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) &&
                (postNotificationsGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)) {
                startBleScan()
            } else {
                Log.e("ble server", "Build.VERSION.SDK_INT : "+Build.VERSION.SDK_INT)
                Toast.makeText(this, "필수 권한이 허용되지 않았습니다.", Toast.LENGTH_SHORT).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        setContent {
            BleAirSensorTheme {
                textState = remember { mutableStateOf("미연결 상태") }
                dataState = remember { mutableStateOf("데이터 없음") }
                locationState = remember { mutableStateOf("위치 없음") } // 위치 데이터 상태 추가
                BLEContent(textState, dataState, locationState)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            } else {
                startBleScan()
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.create().apply {
            interval = 10000 // 10초마다 위치 업데이트
            fastestInterval = 5000 // 최소 5초마다 업데이트
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

//        locationCallback = object : LocationCallback() {
//            override fun onLocationResult(locationResult: LocationResult?) {
//                locationResult ?: return
//                for (location in locationResult.locations) {
//                    // 위치 정보를 이용하여 작업을 수행
//                    Log.d("LocationUpdate", "Location: ${location.latitude}, ${location.longitude}")
//                }
//            }
//        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    // 위치 정보를 이용하여 작업을 수행
                    Log.d("LocationUpdate", "Location: ${location.latitude}, ${location.longitude} & Speed: ${location.speed}")
                    lati = location.latitude
                    longi = location.longitude
                    speed = location.speed*3600 / 1000; // m/s를 km/h로 변환
                    horizontalAccuracy = location.accuracy
                    if (horizontalAccuracy < 15) { // 분명 밖인데
                        if (is_indoor == true) { //기존에 안이었다...
                            is_indoor = false; // 바깥으로 바꿔주자
                        }
                    }else if (horizontalAccuracy >= 20){ //분명 안인데
                        if (is_indoor == false) { //기존에 밖이었다.
                            if (speed < 0) {
                                is_indoor = true;
                            }
                        }
                    }
                    // 위치 데이터를 업데이트
                    runOnUiThread {
                        locationState.value = "위도: $lati, 경도: $longi, 속도: $speed m/h, 정확도: $horizontalAccuracy m"
                    }
                }
            }
        }
        sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        uuid_str = getOrGenerateUUID()
        Log.d("UUID", "UUID: $uuid_str")
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()
//        val serviceIntent = Intent(this, BLEService::class.java)
//        stopService(serviceIntent)
        if (bluetoothGatt == null) {
            Log.e("onResume", "bluetoothGatt == null")
            restoreConnection()
            Handler(Looper.getMainLooper()).postDelayed({
                if (bluetoothGatt == null) {  // 만약 연결된 장치가 없으면 스캔 시작
                    startBleScan()
                    showDeviceListAlert()
                }
            }, 2000) // 2초 대기
        }else {
            Log.e("onResume", "bluetoothGatt != null")
            writeDataToCharacteristic("foreground")
        }
        if (checkPermissions()) {
            startLocationUpdates()
        } else {
            requestPermissions()
        }
    }
    override fun onPause() {
        super.onPause()
        writeDataToCharacteristic("background")
//        val serviceIntent = Intent(this, BLEService::class.java)
//        ContextCompat.startForegroundService(this, serviceIntent)
//        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(this, BLEService::class.java)
        stopService(intent)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }


    @SuppressLint("MissingPermission")
    private fun restoreConnection() {
        Log.e("resotre connection", "start")
        // 이미 연결된 장치가 있는지 확인
//        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val connectedDevices = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).getConnectedDevices(BluetoothProfile.GATT)
        for (device in connectedDevices) {
            Log.e("resotre connection", "" + device.name)
            if (device.name == "BLE_Server") {
                bluetoothGatt = device.connectGatt(this, true, gattCallback)
                break
            }
        }
        if (bluetoothGatt == null) {
            Log.e("restoreConnection", "No connected devices found or connection failed")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        requestPermissionLauncher.launch(permissions)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startBleScan() {
        Log.e("startBleScan", "scan start")
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(this, "Bluetooth LE Scanner is not available", Toast.LENGTH_SHORT).show()
            return
        }


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            val filters = listOf(
                ScanFilter.Builder().setDeviceName("BLE-Server").build()
            )
            val settings = ScanSettings.Builder().build()

            scanner.startScan(filters, settings, object : ScanCallback() {
                @SuppressLint("MissingPermission")
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
//                    result?.device?.let { device ->
//                        if (device.name == "BLE-Server") {
//                            Toast.makeText(this@MainActivity, "BLE-Server에 연결합니다", Toast.LENGTH_SHORT).show()
//                            connectToDevice(device)
//                            scanner.stopScan(this)
//                        }
//                    }
                    result?.let {
                        if (!isConnected) {
                            val device: BluetoothDevice = it.device
                            val name = device.name
                            val address = device.address



                            val rssi = it.rssi
                            var isContained: Boolean = false;

                            for (result in deviceList) {
                                if (result.address == address) {
                                    isContained = true
                                }
                            }
                            if (isContained) return
                            Log.e("onScanResult", "onScanResult - "+name+"("+address+")")

                            if (device.name == "BLE-Server" && !isContained) {
                                Log.d("BLE", "Device found: ${name} - RSSI: $rssi")
                                deviceList.add(device)
                                showDeviceListAlert()  // 알럿을 업데이트합니다.
                            }
                        }else {
                            scanner.stopScan(this)
                        }
                    }

                }
            })
        } else {
            requestPermissions()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDeviceListAlert() {
        val deviceNames = deviceList.map { "${it.name} - ${it.address} - RSSI: " }.toTypedArray()
        runOnUiThread {
            alertDialog?.dismiss()
            alertDialog = AlertDialog.Builder(this)
                .setTitle("BLE 장비 리스트")
                .setItems(deviceNames) { dialog, which ->
                    val selectedDevice = deviceList[which]
                    connectToDevice(selectedDevice)
                    deviceList.clear()
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }

    fun insertAirQualityData(context: Context, airQualityData: AirQualityData) {
        val dbHelper = AirQualityDatabaseHelper(context)
        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put(AirQualityDatabaseHelper.COLUMN_DEVICE_MODEL, airQualityData.deviceModel)
            put(AirQualityDatabaseHelper.COLUMN_DEVICE_VER, airQualityData.deviceVer)
            put(AirQualityDatabaseHelper.COLUMN_LATITUDE, airQualityData.latitude)
            put(AirQualityDatabaseHelper.COLUMN_LONGITUDE, airQualityData.longitude)
            put(AirQualityDatabaseHelper.COLUMN_HORIZONTAL_ACCURACY, airQualityData.horizontalAccuracy)
            put(AirQualityDatabaseHelper.COLUMN_WIFI_COUNT, airQualityData.wifiCount)
            put(AirQualityDatabaseHelper.COLUMN_IS_INDOOR, if (airQualityData.isIndoor) 1 else 0)
            put(AirQualityDatabaseHelper.COLUMN_VELOCITY, airQualityData.velocity)
            put(AirQualityDatabaseHelper.COLUMN_TIMESTAMP, airQualityData.timestamp)
            put(AirQualityDatabaseHelper.COLUMN_THEME, airQualityData.theme)
            put(AirQualityDatabaseHelper.COLUMN_BATTERY_LEVEL, airQualityData.batteryLevel)
            put(AirQualityDatabaseHelper.COLUMN_TEMPERATURE, airQualityData.temperature)
            put(AirQualityDatabaseHelper.COLUMN_HUMIDITY, airQualityData.humidity)
            put(AirQualityDatabaseHelper.COLUMN_CO_LEVEL, airQualityData.coLevel)
            put(AirQualityDatabaseHelper.COLUMN_CO2_LEVEL, airQualityData.co2Level)
            put(AirQualityDatabaseHelper.COLUMN_VOC1_LEVEL, airQualityData.voc1Level)
            put(AirQualityDatabaseHelper.COLUMN_VOC2_LEVEL, airQualityData.voc2Level)
            put(AirQualityDatabaseHelper.COLUMN_VOC3_LEVEL, airQualityData.voc3Level)
            put(AirQualityDatabaseHelper.COLUMN_VOC4_LEVEL, airQualityData.voc4Level)
            put(AirQualityDatabaseHelper.COLUMN_VOC5_LEVEL, airQualityData.voc5Level)
            put(AirQualityDatabaseHelper.COLUMN_VOC6_LEVEL, airQualityData.voc6Level)
            put(AirQualityDatabaseHelper.COLUMN_NOX_LEVEL, airQualityData.noxLevel)
            put(AirQualityDatabaseHelper.COLUMN_PM1_LEVEL, airQualityData.pm1Level)
            put(AirQualityDatabaseHelper.COLUMN_PM2_5_LEVEL, airQualityData.pm2_5Level)
            put(AirQualityDatabaseHelper.COLUMN_PM10_LEVEL, airQualityData.pm10Level)
            put(AirQualityDatabaseHelper.COLUMN_RADON_LEVEL, airQualityData.radonLevel)
            put(AirQualityDatabaseHelper.COLUMN_SOUND_LEVEL, airQualityData.soundLevel)
            put(AirQualityDatabaseHelper.COLUMN_NO2_LEVEL, airQualityData.no2Level)
            put(AirQualityDatabaseHelper.COLUMN_NH3_LEVEL, airQualityData.nh3Level)
            put(AirQualityDatabaseHelper.COLUMN_SO2_LEVEL, airQualityData.so2Level)
            put(AirQualityDatabaseHelper.COLUMN_O3_LEVEL, airQualityData.o3Level)
            put(AirQualityDatabaseHelper.COLUMN_HPA_LEVEL, airQualityData.hpaLevel)
            put(AirQualityDatabaseHelper.COLUMN_HCHO_LEVEL, airQualityData.hchoLevel)
        }

        db.insert(AirQualityDatabaseHelper.TABLE_NAME, null, values)
        db.close()
    }

    private fun sendSensorDataToServer(sensorData: String) {
        // 데이터를 " / "로 분할하여 JSON 객체로 변환
        //NOX / CO / CO2 / NH3 / NO2 / Temp / hPa / Humi / PM1 / PM2.5 / PM10
        val modifiedSensorData = sensorData.replace("VOC(ENS160):", "voc4_level:")
            .replace("VOC(AGS10):", "voc2_level:")
            .replace("VOC_RAW:", "voc1_level:")
            .replace("GAS:", "voc3_level:")
            .replace("ETHANOL:", "voc5_level:")
            .replace("NOX_RAW:", "nox_level:")
            .replace("CO:", "co_level:")
            .replace("CO2:", "co2_level:")
            .replace("NH3:", "nh3_level:")
            .replace("NO2:", "no2_level:")
            .replace("Temp:", "temperature:")
            .replace("hPa:", "hpa_level:")
            .replace("Humi:", "humidity:")
            .replace("PM1:", "pm1_level:")
            .replace("PM2.5:", "pm2_5_level:")
            .replace("PM10:", "pm10_level:")

        //VOC_AQI(160) / VOC(ENS160) / VOC(AGS10) / VOC_RAW / GAS / ETHANOL / NOX / CO / CO2 / NH3 / NO2 / Temp / hPa / Humi / PM1 / PM2.5 / PM10 / SMELL_V / SMELL_N
        val sensorDataMap = modifiedSensorData.split(" / ").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) {
                val title = parts[0].trim()
                val value = parts[1].trim()
                if (title.equals("VOC_AQI(160)")) {
                    null
                }else if (title.equals("SMELL_V")) {
                    null
                }else if (title.equals("SMELL_N")) {
                    null
                }else if (title.equals("voc4_level")) {
                    title to value.toInt()
                }else if (title.equals("voc2_level")) {
                    title to value.toInt()
                }else if (title.equals("voc1_level")) {
                    title to value.toInt()
                }else if (title.equals("voc3_level")) {
                    title to value.toInt()
                }else if (title.equals("voc5_level")) {
                    title to value.toInt()
                }else if (title.equals("nox_level")) {
                    title to value.toInt().toInt()
                }else if (title.equals("co_level")) {
                    title to value.toFloat().toInt()
                }else if (title.equals("co2_level")) {
                    title to value.toFloat().toInt()
                }else if (title.equals("nh3_level")) {
                    title to value.toFloat()
                }else if (title.equals("no2_level")) {
                    title to value.toFloat()
                }else if (title.equals("temperature")) {
                    title to value.toFloat()
                }else if (title.equals("hpa_level")) {
                    title to value.toFloat()
                }else if (title.equals("humidity")) {
                    title to value.toFloat()
                }else if (title.equals("pm1_level")) {
                    title to value.toInt()
                }else if (title.equals("pm2_5_level")) {
                    title to value.toInt()
                }else if (title.equals("pm10_level")) {
                    title to value.toInt()
                }else{
                    title to value
                }
//                parts[0].trim() to parts[1].trim()
            } else {
                null
            }
        }.toMap()

        val deviceModel = android.os.Build.MODEL
        val osVersion = android.os.Build.VERSION.RELEASE
        val appVersion = try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }

        val json = JSONObject(sensorDataMap)
        // 추가적으로 필요한 데이터들
        json.put("idfv", uuid_str)
        json.put("member_id", "0")
        json.put("device_model", "prototype")
        json.put("device_ver", "0.1")
        json.put("app_os_type", "android")
        json.put("app_device_model", deviceModel)
        json.put("app_os_ver", osVersion)
        json.put("app_version", appVersion)
        json.put("latitude", lati)
        json.put("longitude", longi)
        json.put("horizontal_accuracy", horizontalAccuracy)
        json.put("wifi_count", 0)
        json.put("is_indoor", is_indoor)
        json.put("velocity", speed)
        json.put("timestamp", System.currentTimeMillis() / 1000.0)
        json.put("theme", "[]")
        json.put("battery_level", "0")
        json.put("so2_level", 0.0f)
        json.put("o3_level", 0.0f)
        json.put("radon_level", 0)
        json.put("hcho_level", 0.0f)
        json.put("sound_level", 0)

        val airQualityData = AirQualityData(
            deviceModel = "prototype1",
            deviceVer = "0.1",
            latitude = lati,
            longitude = longi,
            horizontalAccuracy = horizontalAccuracy,
            wifiCount = 0,
            isIndoor = is_indoor,
            velocity = speed,
            timestamp = System.currentTimeMillis() / 1000.0,
            theme = "[]",
            batteryLevel = "0",
            temperature = json.getString("temperature").toFloat(),
            humidity = json.getString("humidity").toFloat(),
            coLevel = json.getString("co_level").toInt(),
            co2Level = json.getString("co2_level").toInt(),
            voc1Level = json.getString("voc1_level").toInt(),
            voc2Level = json.getString("voc2_level").toInt(),
            voc3Level = json.getString("voc3_level").toInt(),
            voc4Level = json.getString("voc4_level").toInt(),
            voc5Level = json.getString("voc5_level").toInt(),
//            voc6Level = json.getString("voc6_level").toInt(),
            voc6Level = 0,
            noxLevel = json.getString("nox_level").toInt(),
            pm1Level = json.getString("pm1_level").toInt(),
            pm2_5Level = json.getString("pm2_5_level").toInt(),
            pm10Level = json.getString("pm10_level").toInt(),
            radonLevel = 0,
            soundLevel = 0,
            no2Level = json.getString("pm10_level").toFloat(),
            nh3Level = json.getString("pm10_level").toFloat(),
            so2Level = 0f,
            o3Level = 0f,
            hpaLevel = json.getString("hpa_level").toFloat(),
            hchoLevel = 0.0f
        )
        insertAirQualityData(this, airQualityData);

        // :1004.96 / :57.65 / :1 / :3 / :3 / :6438 / :204 / :30550 / :9614
        Log.e("json", "json body : " + json.toString())

        val jsonString = json.toString()
//        val requestBody = RequestBody.create(MediaType.get("application/json; charset=utf-8"), jsonString)
//        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaType(), jsonString)
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonString.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("http://3.36.57.133:5000/sensor-data")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object :Callback{
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e("Server Error", "Failed to send data to server: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    Log.d("Server Response", responseData ?: "No Response")
                } else {
                    throw IOException("Unexpected code $response")
                }
            }

        })

    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                isConnected = true;
                runOnUiThread {
                    textState.value = "연결 완료"
                    alertDialog?.dismiss()
                }
                gatt?.requestMtu(512)  // MTU를 512로 설정합니다.
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                isConnected = false;
                runOnUiThread {
                    textState.value = "미연결 상태"
                    Toast.makeText(this@MainActivity, "BLE-Server와 연결이 해제되었습니다.", Toast.LENGTH_SHORT).show()
                    sendNotification("BLE disconnected")
                    startBleScan()
                    showDeviceListAlert()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.discoverServices()  // MTU가 성공적으로 변경된 후 서비스 검색을 시작합니다.
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.e("onServicesDiscovered", "onServicesDiscovered")
            val service = gatt?.getService(serviceUUID)
            if (service != null) {
                runOnUiThread {
                    textState.value = "연결 완료"
                }
                for (characteristic in service.characteristics) {
                    Log.e("onServicesDiscovered", "uuid : "+characteristic.uuid.toString())

                    if (characteristic.uuid == service_readUUID) {
                        gatt.readCharacteristic(characteristic)
                        gatt.setCharacteristicNotification(characteristic, true)
                    }
                    if (characteristic.uuid == service_writeUUID) {
                        Log.e("onServicesDiscovered", "writeCharacteristc = characteristic")
                        writeCharacteristc = characteristic
//                        writeDataToCharacteristic("foreground")
                        // 데이터를 보내기 전에 MTU가 설정되었는지 확인
                        Handler(Looper.getMainLooper()).postDelayed({
                            writeDataToCharacteristic("foreground")
                        }, 1000) // 1초 대
                    }
                }
            }
        }

//        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
//            characteristic?.value?.let { value ->
//                runOnUiThread {
//                    Log.e("BLE Server", String(value));
//                    dataState.value = String(value) //.replace(" / ", "\n")
//                    sendSensorDataToServer(String(value))
//                }
//            }
//        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            runOnUiThread {
                Log.e("BLE Server", String(value));
                dataState.value = String(value) //.replace(" / ", "\n")
                sendSensorDataToServer(String(value))
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.e("onCharacteristicWrite", "onCharacteristicWrite")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private fun writeDataToCharacteristic(data: String) {
//        val service = bluetoothGatt?.getService(serviceUUID)
//        val characteristic = service?.getCharacteristic(serviceUUID)
        if (bluetoothGatt == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Log.e("write data to char", ""+data);
//                        bluetoothGatt?.writeCharacteristic(it, data.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                try {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        Log.e("write data to char", data)
                        // API 33 이상에서는 새로운 메서드 사용
//                            bluetoothGatt?.writeCharacteristic(it, data.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE, 0, data.toByteArray().size)
                        //public int writeCharacteristic(@NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int writeType)
                        //- (void)writeValue:(NSData *)data forCharacteristic:(CBCharacteristic *)characteristic type:(CBCharacteristicWriteType)type;
                        bluetoothGatt?.writeCharacteristic(writeCharacteristc, data.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    } else {
                        // 권한이 없을 경우 권한 요청 로직 추가
                        Toast.makeText(this, "BLUETOOTH_CONNECT 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Permission denied for writeCharacteristic", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 권한이 없을 경우 권한 요청 로직 추가
                Toast.makeText(this, "BLUETOOTH_CONNECT 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }

        } else {
            Log.e("write esp", "else")
            writeCharacteristc.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            writeCharacteristc.value = data.toByteArray()
//                characteristic.value = URLEncoder.encode(data, "utf-8"))
            bluetoothGatt?.writeCharacteristic(writeCharacteristc)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "BLE Disconnection"
            val descriptionText = "Notifications for BLE disconnection"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("BLE_DISCONNECT_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(message: String) {
        val builder = NotificationCompat.Builder(this, "BLE_DISCONNECT_CHANNEL")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BLE Status")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notify(1, builder.build())
            }
        }
    }
}

@Composable
fun BLEContent(textState: MutableState<String>, dataState: MutableState<String>, locationState: MutableState<String>) {
    val context = LocalContext.current
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = textState.value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 위치 데이터를 표시하는 Text 추가
                Text(
                    text = locationState.value,
                    fontSize = 18.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val sensorDataString = dataState.value
            if (sensorDataString.length > 50) {
                val sensorData = sensorDataString.split(" / ").mapNotNull {
                    if (it.isBlank()) {
                        null
                    } else {
                        val parts = it.split(":")
                        if (parts.size < 2) {
                            null
                        } else {
                            val (name, value) = parts
                            name.trim() to value.trim()
                        }
                    }
                }
                items(sensorData.chunked(3)) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for ((name, value) in rowItems) {
                            Column(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .weight(1f)
                                    .border(1.dp, Color.Black)
                                    .padding(8.dp)
                                    .clickable {
                                        var column_name:String = ""
                                        if (name.equals("VOC(AGS10)")) column_name = "voc2_level"
                                        else if (name.equals("VOC_AQI(160)")) column_name = "voc4_level"
                                        else if (name.equals("VOC_RAW")) column_name = "voc1_level"
                                        else if (name.equals("GAS")) column_name = "voc3_level"
                                        else if (name.equals("ETHANOL")) column_name = "voc5_level"
                                        else if (name.equals("NOX_RAW")) column_name = "nox_level"
                                        else if (name.equals("CO")) column_name = "co_level"
                                        else if (name.equals("CO2")) column_name = "co2_level"
                                        else if (name.equals("NH3")) column_name = "nh3_level"
                                        else if (name.equals("NO2")) column_name = "no2_level"
                                        else if (name.equals("Temp")) column_name = "temperature"
                                        else if (name.equals("hPa")) column_name = "hpa_level"
                                        else if (name.equals("Humi")) column_name = "humidity"
                                        else if (name.equals("PM1")) column_name = "pm1_level"
                                        else if (name.equals("PM2.5")) column_name = "pm2_5_level"
                                        else if (name.equals("PM10")) column_name = "pm10_level"
                                        val intent = Intent(context, SensorHistoryActivity::class.java)
                                        intent.putExtra("sensor_name", column_name)
                                        context.startActivity(intent)
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = value,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = name,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "데이터 없음",
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}