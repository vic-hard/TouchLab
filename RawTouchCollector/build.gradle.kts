plugins {
    alias(libs.plugins.android.library)
}

private val aarVersion = "1.1.0"

android {
    namespace = "com.lime.rawtouchcollector"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 31

        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "AAR_VERSION", "\"$aarVersion\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

base {
    archivesName = "rawtouchcollector-$aarVersion"
}

kotlin {
    explicitApi()
}

// Библиотека собрана на framework API: MotionEvent, SystemClock, android.util.JsonWriter.
// Единственная зависимость — аннотации nullability ради читаемых Java-сигнатур.
// Ничего, что тянуло бы транзитивные зависимости в потребителя AAR, здесь быть не должно.
dependencies {
    implementation(libs.androidx.annotation)
}
