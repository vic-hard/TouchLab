package com.lime.rawtouchcollector

/**
 * Профиль дисплея и окна, действовавший непосредственно перед попыткой.
 *
 * Частота обновления здесь — параметр вывода изображения. Она не является частотой
 * тач-контроллера и не должна так трактоваться, §5.2 и критерий 22.
 */
public class DisplayProfile internal constructor(
    public val displayProfileId: String,
    public val windowWidthPx: Int,
    public val windowHeightPx: Int,
    public val displayModeWidthPx: Int,
    public val displayModeHeightPx: Int,
    public val displayRefreshRateHz: Float,
    public val densityDpi: Int,
    /** Момент фиксации профиля в монотонной шкале elapsedRealtimeNanos. */
    public val capturedAtElapsedNs: Long,
) {
    internal fun sameValues(
        windowWidthPx: Int,
        windowHeightPx: Int,
        displayModeWidthPx: Int,
        displayModeHeightPx: Int,
        displayRefreshRateHz: Float,
        densityDpi: Int,
    ): Boolean =
        this.windowWidthPx == windowWidthPx &&
            this.windowHeightPx == windowHeightPx &&
            this.displayModeWidthPx == displayModeWidthPx &&
            this.displayModeHeightPx == displayModeHeightPx &&
            this.displayRefreshRateHz == displayRefreshRateHz &&
            this.densityDpi == densityDpi
}
