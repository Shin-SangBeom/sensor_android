package com.example.bleairsensor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SensorHistory(val timestamp: Long, val value: Float)

class SensorHistoryAdapter(private val sensorHistoryList: List<SensorHistory>) :
    RecyclerView.Adapter<SensorHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timestampTextView: TextView = view.findViewById(R.id.timestamp_text_view)
        val valueTextView: TextView = view.findViewById(R.id.value_text_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.sensor_history_item, parent, false)
        return ViewHolder(view)
    }

    fun convertTimestampToFormattedDate(timestamp: Long): String {
        // Timestamp는 밀리초 단위이므로, 이를 기준으로 Date 객체를 생성합니다.
        val date = Date(timestamp)

        // SimpleDateFormat 객체를 사용하여 원하는 형식으로 날짜를 변환합니다.
        val format = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

        // 변환된 날짜 문자열을 반환합니다.
        return format.format(date)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sensorHistory = sensorHistoryList[position]

//        holder.timestampTextView.text = sensorHistory.timestamp.toString()
        holder.timestampTextView.text = convertTimestampToFormattedDate(sensorHistory.timestamp*1000)
//        holder.timestampTextView.text = String.format("%s (%s)", convertTimestampToFormattedDate(sensorHistory.timestamp), sensorHistory.timestamp.toString())
        holder.valueTextView.text = sensorHistory.value.toString()
    }

    override fun getItemCount() = sensorHistoryList.size
}