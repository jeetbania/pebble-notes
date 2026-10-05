plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.leafnotes"
    compileSdk = 35
    defaultConfig { testInstrumentationRunner = "dev.leafnotes.LeafSmoke"; applicationId = "dev.leafnotes"; minSdk = 29; targetSdk = 35; versionCode = 24; versionName = "0.15.1"; ndk { abiFilters += "arm64-v8a" } }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs { getByName("debug") { storeFile = rootProject.file("../private/leaf-development.keystore"); storePassword = "leaf-development"; keyAlias = "leaf"; keyPassword = "leaf-development" } }
}
dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.foundation:foundation:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("com.google.android.gms:play-services-auth:22.0.0")
}

val releaseSource = rootProject.projectDir.parentFile.resolve("releases.json")
val releaseAsset = projectDir.resolve("src/main/assets/releases.json")
val syncReleaseNotes = tasks.register("syncReleaseNotes") {
    inputs.file(releaseSource); inputs.property("releaseVersion", android.defaultConfig.versionName ?: "")
    outputs.file(releaseAsset)
    doLast {
        val releases = groovy.json.JsonSlurper().parse(releaseSource) as List<*>
        val latest = releases.firstOrNull() as? Map<*, *>
        require(latest?.get("version") == android.defaultConfig.versionName) { "Add the current release to releases.json before building" }
        releaseAsset.writeText(releaseSource.readText())
    }
}
tasks.named("preBuild") { dependsOn(syncReleaseNotes) }
