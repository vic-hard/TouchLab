package com.lime.rawtouchcollector

/**
 * Данные сессии.
 *
 * Календарное время хранится отдельно от uptime и elapsedRealtime и никогда из них
 * не выводится, §7 и критерий 18. [endedAtWallClockMs] равно null только у сессии,
 * которая не была завершена штатно.
 */
public class SessionInfo internal constructor(
    public val sessionId: String,
    public val participantId: String,
    public val startedAtWallClockMs: Long,
    public val endedAtWallClockMs: Long?,
    public val phoneSupportMode: String,
    public val schemaVersion: String,
    public val device: DeviceInfo,
)
