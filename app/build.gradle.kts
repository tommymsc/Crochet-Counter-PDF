plugins { id("com.android.application") }

android {
    namespace = "com.lauraapps.crochetcounter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lauraapps.crochetcounter"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled = false } }
}
