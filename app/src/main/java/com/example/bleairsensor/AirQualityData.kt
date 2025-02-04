package com.example.bleairsensor

data class AirQualityData(
    val deviceModel: String,
    val deviceVer: String,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracy: Float,
    val wifiCount: Int,
    val isIndoor: Boolean,
    val velocity: Float,
    val timestamp: Double,
    val theme: String,
    val batteryLevel: String,
    val temperature: Float,
    val humidity: Float,
    val coLevel: Int,
    val co2Level: Int,
    val voc1Level: Int,
    val voc2Level: Int,
    val voc3Level: Int,
    val voc4Level: Int,
    val voc5Level: Int,
    val voc6Level: Int,
    val noxLevel: Int,
    val pm1Level: Int,
    val pm2_5Level: Int,
    val pm10Level: Int,
    val radonLevel: Int,
    val soundLevel: Int,
    val no2Level: Float,
    val nh3Level: Float,
    val so2Level: Float,
    val o3Level: Float,
    val hpaLevel: Float,
    val hchoLevel: Float
)