package com.example.vibrationbracelet.network

import android.content.Context
import android.util.Log
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MQTTClient(context: Context) {
    private val TAG = "MQTTClient"
    private var mqttClient: MqttAndroidClient
    private val serverUri = "ssl://${YOUR_TENCENT_CLOUD_ENDPOINT}:8883"
    private val clientId = "${YOUR_DEVICE_NAME}"

    // 腾讯云三元组配置（需替换实际值）
    private val productId = "${YOUR_PRODUCT_ID}"
    private val deviceName = "${YOUR_DEVICE_NAME}"
    private val deviceSecret = "${YOUR_DEVICE_SECRET}"

    // 回调接口定义
    interface ParameterCallback {
        fun onParametersReceived(params: ByteArray)
    }

    init {
        mqttClient = MqttAndroidClient(context, serverUri, clientId).apply {
            setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    Log.d(TAG, "MQTT连接成功: $serverURI")
                }

                override fun connectionLost(cause: Throwable) {
                    Log.e(TAG, "MQTT连接丢失: ${cause.message}")
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    Log.d(TAG, "收到参数消息 [${topic}]: ${message.payload.size}字节")
                    parameterCallback?.onParametersReceived(message.payload)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken) {
                    // 消息投递完成处理
                }
            })
        }
    }

    // 云端参数回调
    var parameterCallback: ParameterCallback? = null

    /**
     * 连接到MQTT服务器
     */
    fun connect() {
        try {
            val options = MqttConnectOptions().apply {
                userName = "$productId;$deviceName;$deviceSecret" // 腾讯云鉴权格式
                keepAliveInterval = 60
                isAutomaticReconnect = true
                isCleanSession = false
                mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
            }

            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    subscribeToParameters()
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.e(TAG, "连接失败: ${exception.message}")
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "MQTT异常: ${e.message}")
        }
    }

    /**
     * 订阅云端参数主题
     */
    private fun subscribeToParameters() {
        try {
            mqttClient.subscribe("$productId/$deviceName/control", 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.d(TAG, "参数订阅成功")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.e(TAG, "参数订阅失败: ${exception.message}")
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "订阅异常: ${e.message}")
        }
    }

    /**
     * 发布传感器数据到云端
     * @param data 三轴加速度数据 [x, y, z]
     */
    fun publishData(data: FloatArray) {
        try {
            val payload = """
                {
                    "x": ${data[0]},
                    "y": ${data[1]}, 
                    "z": ${data[2]}
                }
            """.trimIndent()

            val message = MqttMessage(payload.toByteArray()).apply {
                qos = 1
                retained = false
            }

            mqttClient.publish("$productId/$deviceName/data", message)
        } catch (e: MqttException) {
            Log.e(TAG, "数据发布失败: ${e.message}")
        }
    }

    /**
     * 断开MQTT连接
     */
    fun disconnect() {
        try {
            mqttClient.disconnect(null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.d(TAG, "成功断开连接")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.e(TAG, "断开连接失败: ${exception.message}")
                }
            })
        } catch (e: MqttException) {
            Log.e(TAG, "断开连接异常: ${e.message}")
        }
    }
}