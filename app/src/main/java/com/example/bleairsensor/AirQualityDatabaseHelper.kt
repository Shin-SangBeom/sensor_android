package com.example.bleairsensor
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AirQualityDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "air_quality.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "AirQualityData"
        const val COLUMN_ID = "id"
        const val COLUMN_DEVICE_MODEL = "device_model"
        const val COLUMN_DEVICE_VER = "device_ver"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_HORIZONTAL_ACCURACY = "horizontal_accuracy"
        const val COLUMN_WIFI_COUNT = "wifi_count"
        const val COLUMN_IS_INDOOR = "is_indoor"
        const val COLUMN_VELOCITY = "velocity"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_THEME = "theme"
        const val COLUMN_BATTERY_LEVEL = "battery_level"
        const val COLUMN_TEMPERATURE = "temperature"
        const val COLUMN_HUMIDITY = "humidity"
        const val COLUMN_CO_LEVEL = "co_level"
        const val COLUMN_CO2_LEVEL = "co2_level"
        const val COLUMN_VOC1_LEVEL = "voc1_level"
        const val COLUMN_VOC2_LEVEL = "voc2_level"
        const val COLUMN_VOC3_LEVEL = "voc3_level"
        const val COLUMN_VOC4_LEVEL = "voc4_level"
        const val COLUMN_VOC5_LEVEL = "voc5_level"
        const val COLUMN_VOC6_LEVEL = "voc6_level"
        const val COLUMN_NOX_LEVEL = "nox_level"
        const val COLUMN_PM1_LEVEL = "pm1_level"
        const val COLUMN_PM2_5_LEVEL = "pm2_5_level"
        const val COLUMN_PM10_LEVEL = "pm10_level"
        const val COLUMN_RADON_LEVEL = "radon_level"
        const val COLUMN_SOUND_LEVEL = "sound_level"
        const val COLUMN_NO2_LEVEL = "no2_level"
        const val COLUMN_NH3_LEVEL = "nh3_level"
        const val COLUMN_SO2_LEVEL = "so2_level"
        const val COLUMN_O3_LEVEL = "o3_level"
        const val COLUMN_HPA_LEVEL = "hpa_level"
        const val COLUMN_HCHO_LEVEL = "hcho_level"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DEVICE_MODEL TEXT,
                $COLUMN_DEVICE_VER TEXT,
                $COLUMN_LATITUDE DOUBLE,
                $COLUMN_LONGITUDE DOUBLE,
                $COLUMN_HORIZONTAL_ACCURACY FLOAT,
                $COLUMN_WIFI_COUNT INTEGER,
                $COLUMN_IS_INDOOR INTEGER,
                $COLUMN_VELOCITY FLOAT,
                $COLUMN_TIMESTAMP TEXT,
                $COLUMN_THEME TEXT,
                $COLUMN_BATTERY_LEVEL TEXT,
                $COLUMN_TEMPERATURE FLOAT,
                $COLUMN_HUMIDITY FLOAT,
                $COLUMN_CO_LEVEL INTEGER,
                $COLUMN_CO2_LEVEL INTEGER,
                $COLUMN_VOC1_LEVEL INTEGER,
                $COLUMN_VOC2_LEVEL INTEGER,
                $COLUMN_VOC3_LEVEL INTEGER,
                $COLUMN_VOC4_LEVEL INTEGER,
                $COLUMN_VOC5_LEVEL INTEGER,
                $COLUMN_VOC6_LEVEL INTEGER,
                $COLUMN_NOX_LEVEL FLOAT,
                $COLUMN_PM1_LEVEL INTEGER,
                $COLUMN_PM2_5_LEVEL INTEGER,
                $COLUMN_PM10_LEVEL INTEGER,
                $COLUMN_RADON_LEVEL INTEGER,
                $COLUMN_SOUND_LEVEL INTEGER,
                $COLUMN_NO2_LEVEL FLOAT,
                $COLUMN_NH3_LEVEL FLOAT,
                $COLUMN_SO2_LEVEL FLOAT,
                $COLUMN_O3_LEVEL FLOAT,
                $COLUMN_HPA_LEVEL FLOAT,
                $COLUMN_HCHO_LEVEL FLOAT
            )
        """.trimIndent()
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }
}