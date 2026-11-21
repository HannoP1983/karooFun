import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

/**
 * Release signing key, never part of the repo. On GitHub it comes from the secrets as environment variables,
 * locally from the signing.properties file that local.properties points to. Without either, release builds
 * fall back to the debug key: fine for trying things out, but such an APK can't update a published one.
 */
val releaseSigning: Properties? = System.getenv("KAROO_FUN_KEYSTORE")?.let { keystore ->
    val password = System.getenv("KAROO_FUN_KEYSTORE_PASSWORD").orEmpty()
    Properties().apply {
        setProperty("storeFile", keystore)
        setProperty("storePassword", password)
        setProperty("keyAlias", "karoo-fun")
        setProperty("keyPassword", password)
    }
} ?: rootProject.file("local.properties").takeIf { it.exists() }
    ?.let { local -> Properties().apply { local.inputStream().use { load(it) } }.getProperty("signing.properties") }
    ?.let { path -> Properties().apply { file(path).inputStream().use { load(it) } } }

android {
    namespace = "de.karoofun.karoo"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "de.karoofun.karoo"
        minSdk = 23
        targetSdk = 34
        versionCode = 6
        versionName = "1.5"
    }

    signingConfigs {
        releaseSigning?.let { key ->
            create("release") {
                storeFile = file(key.getProperty("storeFile"))
                storePassword = key.getProperty("storePassword")
                keyAlias = key.getProperty("keyAlias")
                keyPassword = key.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Same key for test builds, so they install over a published release on the Karoo and back
        debug {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.karoo.ext)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
