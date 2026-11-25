plugins { id("com.android.application") }

android {
    namespace = "com.lauraapps.crochetcounter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lauraapps.crochetcounter"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "1.3.0"
        buildConfigField("boolean", "LIVE_ADS", (project.findProperty("liveAds") == "true").toString())
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled = false } }
}

dependencies {
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
