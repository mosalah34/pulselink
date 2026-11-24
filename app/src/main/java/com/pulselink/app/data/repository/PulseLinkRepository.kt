package com.pulselink.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.pulselink.app.data.local.AlertEventDao
import com.pulselink.app.data.local.AlertEventEntity
import com.pulselink.app.data.local.VitalReadingDao
import com.pulselink.app.data.local.VitalReadingEntity
import com.pulselink.app.data.model.AlarmToneType
import com.pulselink.app.data.model.AlertEvent
import com.pulselink.app.data.model.AlertType
import com.pulselink.app.data.model.ThresholdSettings
import com.pulselink.app.data.model.VitalReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PulseLinkRepository(
    private val context: Context,
    private val vitalReadingDao: VitalReadingDao,
    private val alertEventDao: AlertEventDao
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pulselink_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings = _settings.asStateFlow()

    private fun loadSettings(): ThresholdSettings {
        return ThresholdSettings(
            hrMinBpm = prefs.getFloat(KEY_HR_MIN, 50.0f),
            hrMaxBpm = prefs.getFloat(KEY_HR_MAX, 100.0f),
            tempThresholdCelsius = prefs.getFloat(KEY_TEMP_THRESHOLD, 35.0f),
            alarmSoundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true),
            alarmTone = try {
                AlarmToneType.valueOf(
                    prefs.getString(KEY_ALARM_TONE, AlarmToneType.MEDICAL_BUZZER.name)
                        ?: AlarmToneType.MEDICAL_BUZZER.name
                )
            } catch (e: Exception) {
                AlarmToneType.MEDICAL_BUZZER
            },
            autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, true),
            staleTimeoutMillis = prefs.getLong(KEY_STALE_TIMEOUT, 5000L)
        )
    }

    fun updateSettings(newSettings: ThresholdSettings) {
        prefs.edit().apply {
            putFloat(KEY_HR_MIN, newSettings.hrMinBpm)
            putFloat(KEY_HR_MAX, newSettings.hrMaxBpm)
            putFloat(KEY_TEMP_THRESHOLD, newSettings.tempThresholdCelsius)
            putBoolean(KEY_SOUND_ENABLED, newSettings.alarmSoundEnabled)
            putBoolean(KEY_VIBRATION_ENABLED, newSettings.vibrationEnabled)
            putString(KEY_ALARM_TONE, newSettings.alarmTone.name)
            putBoolean(KEY_AUTO_RECONNECT, newSettings.autoReconnect)
            putLong(KEY_STALE_TIMEOUT, newSettings.staleTimeoutMillis)
            apply()
        }
        _settings.value = newSettings
    }

    suspend fun saveReading(reading: VitalReading): Long = withContext(Dispatchers.IO) {
        vitalReadingDao.insert(VitalReadingEntity.fromModel(reading))
    }

    suspend fun saveReadingsBatch(readings: List<VitalReading>): List<Long> = withContext(Dispatchers.IO) {
        if (readings.isEmpty()) return@withContext emptyList()
        try {
            vitalReadingDao.insertAll(readings.map { VitalReadingEntity.fromModel(it) })
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getRecentReadings(limit: Int = 100): Flow<List<VitalReading>> {
        return vitalReadingDao.getRecentReadings(limit).map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun getReadingsInRange(startTime: Long, endTime: Long): Flow<List<VitalReading>> {
        return vitalReadingDao.getReadingsInRange(startTime, endTime).map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun saveAlert(alert: AlertEvent): Long = withContext(Dispatchers.IO) {
        alertEventDao.insert(AlertEventEntity.fromModel(alert))
    }

    suspend fun resolveActiveAlerts(alertType: AlertType) = withContext(Dispatchers.IO) {
        alertEventDao.resolveActiveAlerts(alertType.name, System.currentTimeMillis())
    }

    suspend fun findActiveAlertByType(alertType: AlertType): AlertEvent? = withContext(Dispatchers.IO) {
        alertEventDao.findActiveAlertByType(alertType.name)?.toModel()
    }

    suspend fun acknowledgeAlert(alertId: Long) = withContext(Dispatchers.IO) {
        alertEventDao.acknowledgeAlert(alertId, System.currentTimeMillis())
    }

    fun getActiveAlerts(): Flow<List<AlertEvent>> {
        return alertEventDao.getActiveAlerts().map { entities ->
            entities.map { it.toModel() }
                .sortedWith(compareByDescending<AlertEvent> { it.alertType.priority }
                    .thenByDescending { it.timestamp })
        }
    }

    fun getResolvedAlerts(): Flow<List<AlertEvent>> {
        return alertEventDao.getResolvedAlerts().map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun getAllAlerts(): Flow<List<AlertEvent>> {
        return alertEventDao.getAllAlerts().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        vitalReadingDao.deleteAll()
        alertEventDao.deleteAll()
    }

    suspend fun exportHistoryToCsvFile(): File = withContext(Dispatchers.IO) {
        val readings = vitalReadingDao.getAllReadingsList()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "pulselink_records_${System.currentTimeMillis()}.csv")

        file.bufferedWriter().use { writer ->
            writer.write("Timestamp,Formatted Date,Raw BPM,Adjusted BPM,Temperature (C),Stale Flag\n")
            for (r in readings) {
                val formatted = sdf.format(Date(r.timestamp))
                writer.write("${r.timestamp},\"$formatted\",${r.rawBpm},${r.adjustedBpm},${r.temperatureCelsius},${r.isStale}\n")
            }
        }
        file
    }

    companion object {
        private const val KEY_HR_MIN = "setting_hr_min"
        private const val KEY_HR_MAX = "setting_hr_max"
        private const val KEY_TEMP_THRESHOLD = "setting_temp_threshold"
        private const val KEY_SOUND_ENABLED = "setting_sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "setting_vibration_enabled"
        private const val KEY_ALARM_TONE = "setting_alarm_tone"
        private const val KEY_AUTO_RECONNECT = "setting_auto_reconnect"
        private const val KEY_STALE_TIMEOUT = "setting_stale_timeout"
    }
}

