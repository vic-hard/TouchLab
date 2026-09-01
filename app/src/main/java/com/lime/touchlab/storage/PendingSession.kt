package com.lime.touchlab.storage

import com.lime.rawtouchcollector.ClockSyncPoint
import com.lime.rawtouchcollector.SessionInfo

internal class PendingSession(
    val info: SessionInfo,
    val clockSync: ClockSyncPoint?,
    val deviceId: String,
)