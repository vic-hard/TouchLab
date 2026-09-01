package com.lime.touchlab.session

import com.lime.rawtouchcollector.TrialSnapshot

/**
 * Диапазон значений одного сырого канала внутри попытки.
 *
 * Число различных значений считается наравне с минимумом и максимумом: постоянный
 * канал - не ошибка, но оператор обязан видеть, что он постоянен,
 * прямо во время прогона, а не при разборе архива.
 */
class ChannelRange(
    val min: Float,
    val max: Float,
    val distinct: Int,
)

/**
 * Техническая сводка последней попытки.
 *
 * Считается из [TrialSnapshot] за один проход по примитивным массивам, на worker-потоке.
 */
class TrialSummary(
    val trialIndex: Int,
    val completionStatus: String,
    val durationMs: Double,
    val currentSampleCount: Int,
    val historicalSampleCount: Int,
    val touchMajor: ChannelRange,
    val touchMinor: ChannelRange,
    val size: ChannelRange,
    val pressure: ChannelRange,
    val timestampPrecision: String,
    val appReceiptUptimePrecision: String,
    val syncMethod: String,
    val samplingUncertaintyNs: Long,
    val quantizationUncertaintyNs: Long,
) {

    companion object {

        fun from(trial: TrialSnapshot): TrialSummary {
            val s = trial.samples
            val count = s.count

            var majorMin = Float.MAX_VALUE
            var majorMax = -Float.MAX_VALUE
            var minorMin = Float.MAX_VALUE
            var minorMax = -Float.MAX_VALUE
            var sizeMin = Float.MAX_VALUE
            var sizeMax = -Float.MAX_VALUE
            var pressureMin = Float.MAX_VALUE
            var pressureMax = -Float.MAX_VALUE

            val majorSeen = HashSet<Float>()
            val minorSeen = HashSet<Float>()
            val sizeSeen = HashSet<Float>()
            val pressureSeen = HashSet<Float>()

            for (i in 0 until count) {
                val major = s.touchMajor(i)
                val minor = s.touchMinor(i)
                val size = s.size(i)
                val pressure = s.pressure(i)

                if (major < majorMin) majorMin = major
                if (major > majorMax) majorMax = major
                if (minor < minorMin) minorMin = minor
                if (minor > minorMax) minorMax = minor
                if (size < sizeMin) sizeMin = size
                if (size > sizeMax) sizeMax = size
                if (pressure < pressureMin) pressureMin = pressure
                if (pressure > pressureMax) pressureMax = pressure

                majorSeen.add(major)
                minorSeen.add(minor)
                sizeSeen.add(size)
                pressureSeen.add(pressure)
            }

            if (count == 0) {
                majorMin = 0f; majorMax = 0f
                minorMin = 0f; minorMax = 0f
                sizeMin = 0f; sizeMax = 0f
                pressureMin = 0f; pressureMax = 0f
            }

            return TrialSummary(
                trialIndex = trial.trialIndex,
                completionStatus = trial.completionStatus,
                // Длительность контакта считается по времени события,
                // а не по времени обработки callback.
                durationMs = trial.contactDurationNs / 1_000_000.0,
                currentSampleCount = trial.currentSampleCount,
                historicalSampleCount = trial.historicalSampleCount,
                touchMajor = ChannelRange(majorMin, majorMax, majorSeen.size),
                touchMinor = ChannelRange(minorMin, minorMax, minorSeen.size),
                size = ChannelRange(sizeMin, sizeMax, sizeSeen.size),
                pressure = ChannelRange(pressureMin, pressureMax, pressureSeen.size),
                timestampPrecision = trial.timestampPrecision,
                appReceiptUptimePrecision = trial.appReceiptUptimePrecision,
                syncMethod = trial.clockSync.syncMethod,
                samplingUncertaintyNs = trial.clockSync.samplingUncertaintyNs,
                quantizationUncertaintyNs = trial.clockSync.quantizationUncertaintyNs,
            )
        }
    }
}
