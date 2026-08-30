plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

android {
    namespace = "hu.konyvtar.tts"
    compileSdk = 35

    defaultConfig {
        applicationId = "hu.konyvtar.tts"
        minSdk = 30
        targetSdk = 35
        versionCode = 6
        versionName = "3.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Debug kulccsal írjuk alá, hogy a release APK azonnal telepíthető legyen.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media:media:1.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // PDF szövegkinyerés (nincs képfeldolgozás, csak text layer)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Egységtesztek: a parserek és a szövegkezelés Android nélkül futnak,
    // ezért sima JUnit elég — nem kell emulátor. A kxml2 az XML-olvasó
    // megvalósítása a JVM-en; Androidon a rendszer sajátja használódik.
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}
