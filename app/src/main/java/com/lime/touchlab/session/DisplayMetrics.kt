package com.lime.touchlab.session

/**
 * Параметры окна и активного режима дисплея.
 *
 * Их умеет читать только визуальный контекст, поэтому источник живёт в Activity и
 * подставляется сюда. Библиотека от Activity не зависит,
 * контроллер — тем более: он переживает пересоздание экрана.
 */
class DisplayMetrics(
    val windowWidthPx: Int,
    val windowHeightPx: Int,
    val modeWidthPx: Int,
    val modeHeightPx: Int,
    val refreshRateHz: Float,
    val densityDpi: Int,
)

/** Поставщик [DisplayMetrics]: его подставляет Activity, пока она жива. */
fun interface DisplaySource {
    fun read(): DisplayMetrics
}
