import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.kurdo.assistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kurdo.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

tasks.register("decodeKurdoAvatar") {
    val encoded = file("src/main/assets/kurdo_avatar_base64.txt")
    val decoded = file("src/main/res/drawable/kurdo_avatar.png")
    inputs.file(encoded)
    outputs.file(decoded)
    doLast {
        decoded.parentFile.mkdirs()
        decoded.writeBytes(Base64.getDecoder().decode(encoded.readText().trim()))
    }
}
tasks.named("preBuild").configure { dependsOn("decodeKurdoAvatar") }
