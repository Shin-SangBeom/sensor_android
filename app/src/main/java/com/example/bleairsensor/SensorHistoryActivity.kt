package com.example.bleairsensor

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SensorHistoryActivity : AppCompatActivity() {

    private lateinit var sensorName: String
    private lateinit var sensorHistoryRecyclerView: RecyclerView
    private lateinit var adapter: SensorHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_history)

        sensorName = intent.getStringExtra("sensor_name") ?: ""
        val sensorNameTextView: TextView = findViewById(R.id.sensor_name_text_view)
        sensorNameTextView.text = sensorName

        sensorHistoryRecyclerView = findViewById(R.id.sensor_history_recycler_view)
        sensorHistoryRecyclerView.layoutManager = LinearLayoutManager(this)

        loadSensorHistoryData()
    }

    private fun loadSensorHistoryData() {
        val dbHelper = AirQualityDatabaseHelper(this)
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            AirQualityDatabaseHelper.TABLE_NAME,
            arrayOf("timestamp", sensorName),
            null, null, null, null, "timestamp DESC", "220"
        )

        val sensorHistoryList = mutableListOf<SensorHistory>()

        if (cursor.moveToFirst()) {
            do {
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                val value = cursor.getFloat(cursor.getColumnIndexOrThrow(sensorName))

                sensorHistoryList.add(SensorHistory(timestamp, value))
            } while (cursor.moveToNext())
        }

        cursor.close()

        adapter = SensorHistoryAdapter(sensorHistoryList)
        sensorHistoryRecyclerView.adapter = adapter
    }
}