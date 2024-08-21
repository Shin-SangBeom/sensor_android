package com.example.bleairsensor

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BLEService : Service() {

    private lateinit var bluetoothManager: BluetoothManager

    // BLE 관련 변수들 선언
    private var bluetoothGatt: BluetoothGatt? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        startForegroundService()
//        restoreConnection()
    }

    private fun startForegroundService() {
        val channelId = "BLEServiceChannel"
        val channelName = "BLE Service Channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BLE Service")
            .setContentText("Receiving BLE data")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

//    @SuppressLint("MissingPermission")
//    private fun restoreConnection() {
//        // 이미 연결된 장치가 있는지 확인
//        val connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)
//        for (device in connectedDevices) {
//            if (device.name == "BLE_Server") {
//                bluetoothGatt = device.connectGatt(this, true, gattCallback)
//                break
//            }
//        }
//    }
}