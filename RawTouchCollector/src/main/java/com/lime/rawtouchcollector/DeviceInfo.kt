package com.lime.rawtouchcollector

/** Неизменные характеристики устройства и сборки. */
public class DeviceInfo internal constructor(
    public val manufacturer: String,
    public val model: String,
    public val androidVersion: String,
    public val sdkInt: Int,
    public val appVersion: String,
    public val aarVersion: String,
    public val densityDpi: Int,
)
