package com.forgequest.app.data

import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.BodyMeasurementEntity
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.TrendPoint
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

enum class MeasurementMetric {
    WAIST, CHEST, ARMS, THIGHS, HIPS;

    companion object {
        fun parse(raw: String): MeasurementMetric? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

object MeasurementMath {
    const val MIN_CM = 10f
    const val MAX_CM = 300f

    fun normalizeCm(valueCm: Float): Float {
        require(valueCm.isFinite() && valueCm in MIN_CM..MAX_CM) {
            "Measurement must be between $MIN_CM and $MAX_CM cm"
        }
        return (valueCm * 10f).roundToInt() / 10f
    }

    fun trend(rows: List<BodyMeasurementEntity>, metric: MeasurementMetric): List<TrendPoint> =
        rows.filter { it.metric == metric.name }
            .sortedWith(compareBy<BodyMeasurementEntity> { it.epochDay }.thenBy { it.loggedAt })
            .map { TrendPoint(it.epochDay, it.valueCm) }

    fun latest(rows: List<BodyMeasurementEntity>, metric: MeasurementMetric): BodyMeasurementEntity? =
        rows.filter { it.metric == metric.name }
            .maxWithOrNull(compareBy<BodyMeasurementEntity> { it.epochDay }.thenBy { it.loggedAt })
}

class MeasurementsRepository(private val db: AppDatabase) {
    fun observeAll(): Flow<List<BodyMeasurementEntity>> = db.bodyMeasurements().observeAll()

    suspend fun all(): List<BodyMeasurementEntity> = db.bodyMeasurements().all()

    suspend fun log(metric: MeasurementMetric, valueCm: Float, epochDay: Long = Dates.today()): Long {
        val clean = MeasurementMath.normalizeCm(valueCm)
        val existing = db.bodyMeasurements().all()
            .firstOrNull { it.epochDay == epochDay && it.metric == metric.name }
        val row = BodyMeasurementEntity(
            id = existing?.id ?: 0L,
            epochDay = epochDay,
            metric = metric.name,
            valueCm = clean,
            loggedAt = System.currentTimeMillis(),
        )
        return db.bodyMeasurements().upsert(row)
    }

    suspend fun delete(id: Long) = db.bodyMeasurements().delete(id)
}
