package com.example.vibrationbracelet.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.vibrationbracelet.R
import com.example.vibrationbracelet.network.MQTTClient
import org.eclipse.paho.client.mqttv3.MqttException

class SensorService : Service() {

    private lateinit var mqttClient: MQTTClient
    private var bluetoothGatt: BluetoothGatt? = null
    private val notificationId = 101
    private val channelId = "sensor_service_channel"

    // BLE回调处理
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    reconnectBluetooth()
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value
            // 处理接收到的蓝牙数据
            processSensorData(data)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeConnections()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(notificationId, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Sensor Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service for sensor data collection"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sensor Data Collection")
            .setContentText("Collecting and transmitting data in background")
            .setSmallIcon(R.drawable.ic_stat_sensor)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initializeConnections() {
        // 初始化MQTT连接
        mqttClient = MQTTClient(applicationContext)
        try {
            mqttClient.connect()
            setupCloudListener()
        } catch (e: MqttException) {
            e.printStackTrace()
        }

        // 初始化BLE连接（需从已配对设备获取）
        // 实际实现需根据具体设备连接逻辑调整
        // bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private fun setupCloudListener() {
        mqttClient.subscribe("parameters") { params ->
            params?.let {
                sendParametersToDevice(it)
            }
        }
    }

    private fun processSensorData(data: ByteArray) {
        // 解析加速度数据并发布到云端
        val accData = parseAccelerationData(data)
        mqttClient.publishData(accData)
    }

    private fun parseAccelerationData(data: ByteArray): FloatArray {
        // 示例解析逻辑（需根据实际数据格式调整）
        return floatArrayOf(
            data[0].toFloat(),
            data[1].toFloat(),
            data[2].toFloat()
        )
    }

    private fun sendParametersToDevice(params: ByteArray) {
        // 通过蓝牙发送参数到设备
        val characteristic = bluetoothGatt?.getService(UUID.fromString("AE25A5C4-4601-143C-12BB-8BC45A18749C"))
            ?.getCharacteristic(UUID.fromString("AE25A5C5-4601-143C-12BB-8BC45A18749C"))

        characteristic?.value = params
        bluetoothGatt?.writeCharacteristic(characteristic)
    }

    private fun reconnectBluetooth() {
        // 实现蓝牙重连逻辑
        // bluetoothGatt?.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseResources()
    }

    private fun releaseResources() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        try {
            mqttClient.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }
}