package com.example.vibrationbracelet

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import com.example.vibrationbracelet.databinding.ActivityMainBinding
import com.example.vibrationbracelet.network.MQTTClient
import com.example.vibrationbracelet.service.SensorService
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mqttClient: MQTTClient

    // 蓝牙相关
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private val deviceName = "振动手环治疗震颤"
    private val serviceUUID = UUID.fromString("AE25A5C4-4601-143C-12BB-8BC45A18749C")
    private val characteristicUUID = UUID.fromString("AE25A5C6-4601-143C-12BB-8BC45A18749C")

    // 图表数据
    private val entriesX = LinkedList<Entry>()
    private val entriesY = LinkedList<Entry>()
    private val entriesZ = LinkedList<Entry>()
    private var dataIndex = 0f

    // 权限请求码
    private val PERMISSION_REQUEST_CODE = 100

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化组件
        initCharts()
        checkPermissions()
        initBluetooth()
        initMQTT()
        setupUIListeners()
    }

    /* -------------------------- 权限管理 -------------------------- */
    private fun checkPermissions() {
        val requiredPermissions = mutableListOf<String>().apply {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        val ungrantedPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungrantedPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                ungrantedPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initBluetooth()
            } else {
                showPermissionWarning()
            }
        }
    }

    private fun showPermissionWarning() {
        AlertDialog.Builder(this)
            .setTitle("权限不足")
            .setMessage("需要蓝牙和位置权限才能正常使用设备连接功能")
            .setPositiveButton("去设置") { _, _ ->
                // 跳转到应用设置页面
            }
            .setCancelable(false)
            .show()
    }

    /* -------------------------- 蓝牙管理 -------------------------- */
    private fun initBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (!bluetoothAdapter.isEnabled) {
            // 实际应用中应请求用户启用蓝牙
            return
        }

        startDeviceScan()
    }

    private fun startDeviceScan() {
        binding.statusText.text = "正在搜索设备..."
        bluetoothAdapter.bluetoothLeScanner.startScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (result.device.name == deviceName) {
                bluetoothAdapter.bluetoothLeScanner.stopScan(this)
                connectToDevice(result.device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        binding.statusText.text = "正在连接..."
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    runOnUiThread { binding.statusText.text = "已连接" }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    runOnUiThread { binding.statusText.text = "连接断开" }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(serviceUUID)
            val characteristic = service?.getCharacteristic(characteristicUUID)
            characteristic?.let {
                gatt.setCharacteristicNotification(it, true)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            processSensorData(characteristic.value)
        }
    }

    /* -------------------------- 数据处理 -------------------------- */
    private fun processSensorData(data: ByteArray) {
        val accData = parseAccelerationData(data)
        updateCharts(accData)
        mqttClient.publishData(accData)
    }

    private fun parseAccelerationData(data: ByteArray): FloatArray {
        // 根据实际数据格式解析，示例为简单转换
        return floatArrayOf(
            data[0].toFloat(),
            data[1].toFloat(),
            data[2].toFloat()
        )
    }

    private fun updateCharts(data: FloatArray) {
        dataIndex += 1f

        entriesX.add(Entry(dataIndex, data[0]))
        entriesY.add(Entry(dataIndex, data[1]))
        entriesZ.add(Entry(dataIndex, data[2]))

        // 保持最多100个数据点
        if (entriesX.size > 100) entriesX.removeFirst()
        if (entriesY.size > 100) entriesY.removeFirst()
        if (entriesZ.size > 100) entriesZ.removeFirst()

        listOf(binding.lineChartX, binding.lineChartY, binding.lineChartZ).forEach {
            it.data.notifyDataChanged()
            it.notifyDataSetChanged()
            it.invalidate()
        }
    }

    /* -------------------------- 图表初始化 -------------------------- */
    private fun initCharts() {
        listOf(binding.lineChartX, binding.lineChartY, binding.lineChartZ).forEach { chart ->
            with(chart) {
                description.isEnabled = false
                setTouchEnabled(false)
                setDrawGridBackground(false)
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                axisRight.isEnabled = false

                val dataSet = LineDataSet(null, "加速度").apply {
                    color = ColorTemplate.getHoloBlue()
                    valueTextColor = Color.WHITE
                    setDrawCircles(false)
                    lineWidth = 1.5f
                }

                data = LineData(dataSet)
            }
        }
    }

    /* -------------------------- MQTT初始化 -------------------------- */
    private fun initMQTT() {
        mqttClient = MQTTClient(applicationContext).apply {
            connect()
        }
    }

    /* -------------------------- UI事件监听 -------------------------- */
    private fun setupUIListeners() {
        binding.btnStartService.setOnClickListener {
            startService(Intent(this, SensorService::class.java))
        }

        binding.btnStopService.setOnClickListener {
            stopService(Intent(this, SensorService::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.disconnect()
        mqttClient.disconnect()
    }
}