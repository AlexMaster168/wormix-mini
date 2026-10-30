plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "com.riftartillery.game"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.riftartillery.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.1"
        testInstrumentationRunner = "com.riftartillery.game.SmokeInstrumentation"
    }
    val signingFile=System.getenv("RIFT_SIGNING_FILE")
    if(!signingFile.isNullOrBlank()) signingConfigs {
        create("ownerRelease") {
            storeFile=file(signingFile)
            storeType=System.getenv("RIFT_SIGNING_TYPE") ?: "PKCS12"
            storePassword=System.getenv("RIFT_SIGNING_PASSWORD")
            keyAlias="rift-upload"
            keyPassword=System.getenv("RIFT_SIGNING_PASSWORD")
        }
    }
    buildTypes { release {
        isMinifyEnabled=true
        isShrinkResources=true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        if(!signingFile.isNullOrBlank()) signingConfig=signingConfigs.getByName("ownerRelease")
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "ogg" }
}
dependencies { implementation(project(":core")) }
