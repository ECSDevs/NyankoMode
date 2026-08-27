import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val signingProperties = Properties().apply {
    val signingFile = rootProject.file("signing.properties")
    if (signingFile.isFile) signingFile.inputStream().use(::load)
}

fun requiredSigningValue(name: String): String =
    signingProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("signing.$name").orNull?.takeIf { it.isNotBlank() }
        ?: error(
            "Missing signing.$name. Configure root signing.properties " +
                "or pass -Psigning.$name=<value>."
        )

val signingStoreFile = rootProject.file(requiredSigningValue("storeFile"))
val signingStorePassword = requiredSigningValue("storePassword")
val signingKeyAlias = requiredSigningValue("keyAlias")
val signingKeyPassword = requiredSigningValue("keyPassword")

check(signingStoreFile.isFile) {
    "Signing keystore does not exist: ${signingStoreFile.absolutePath}"
}

android {
    namespace = "cc.ptoe.nyankomode"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cc.ptoe.nyankomode"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("nyanko") {
            storeFile = signingStoreFile
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("nyanko")
        }
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("nyanko")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

configurations.configureEach {
    resolutionStrategy.force(
        "androidx.core:core:1.16.0",
        "androidx.core:core-ktx:1.16.0",
    )
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}