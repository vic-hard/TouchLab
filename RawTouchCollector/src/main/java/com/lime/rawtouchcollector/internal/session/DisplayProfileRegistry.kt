package com.lime.rawtouchcollector.internal.session

import android.os.SystemClock
import com.lime.rawtouchcollector.DisplayProfile
import java.util.UUID

/**
 * Присвоение и переиспользование display_profile_id.
 *
 * Профиль фиксируется при старте сессии и непосредственно перед каждой попыткой.
 * Если все параметры совпали с текущим — возвращается тот же идентификатор, новая
 * запись не создаётся. Изменился хоть один — новый id и новая метка момента фиксации.
 *
 * Дедупликация живёт здесь, а не в приложении: хосту достаточно вызывать
 * updateDisplayProfile перед каждой попыткой, не задумываясь, изменилось ли что-нибудь.
 */
internal class DisplayProfileRegistry {

    private var current: DisplayProfile? = null

    /** true, если профиль обновляли с момента последнего [markConsumed]. */
    var updatedSinceLastTrial: Boolean = false
        private set

    @Suppress("LongParameterList")
    fun update(
        windowWidthPx: Int,
        windowHeightPx: Int,
        displayModeWidthPx: Int,
        displayModeHeightPx: Int,
        displayRefreshRateHz: Float,
        densityDpi: Int,
    ): DisplayProfile {
        updatedSinceLastTrial = true

        val existing = current
        if (existing != null && existing.sameValues(
                windowWidthPx,
                windowHeightPx,
                displayModeWidthPx,
                displayModeHeightPx,
                displayRefreshRateHz,
                densityDpi,
            )
        ) {
            return existing
        }

        val profile = DisplayProfile(
            displayProfileId = UUID.randomUUID().toString(),
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx,
            displayModeWidthPx = displayModeWidthPx,
            displayModeHeightPx = displayModeHeightPx,
            displayRefreshRateHz = displayRefreshRateHz,
            densityDpi = densityDpi,
            capturedAtElapsedNs = SystemClock.elapsedRealtimeNanos(),
        )
        current = profile
        return profile
    }

    fun currentOrNull(): DisplayProfile? = current

    /**
     * Профиль, которым будет помечена начинающаяся попытка.
     *
     * Если хост ни разу не вызвал update, возвращается заглушка с нулями:
     * попытка не остаётся без профиля, а факт отсутствия обновления виден в диагностике.
     */
    fun profileForTrial(): DisplayProfile {
        val existing = current
        if (existing != null) return existing

        val placeholder = DisplayProfile(
            displayProfileId = UUID.randomUUID().toString(),
            windowWidthPx = 0,
            windowHeightPx = 0,
            displayModeWidthPx = 0,
            displayModeHeightPx = 0,
            displayRefreshRateHz = 0f,
            densityDpi = 0,
            capturedAtElapsedNs = SystemClock.elapsedRealtimeNanos(),
        )
        current = placeholder
        return placeholder
    }

    fun markConsumed() {
        updatedSinceLastTrial = false
    }

    fun reset() {
        current = null
        updatedSinceLastTrial = false
    }
}
