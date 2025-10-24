plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.associate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.associate"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures{
        viewBinding=true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    ///sdp
    implementation ("com.intuit.sdp:sdp-android:1.1.1")

    /// round image
    implementation ("de.hdodenhof:circleimageview:3.1.0")

    ///auth
    implementation("androidx.credentials:credentials:1.2.0-alpha01")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.0-alpha01")
    implementation ("com.google.android.gms:play-services-safetynet:18.1.0")


    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))

    implementation ("com.google.firebase:firebase-auth:22.3.1")

    //lottie animation
    implementation ("com.airbnb.android:lottie:6.1.0")

    implementation("com.google.code.gson:gson:2.10.1")


    implementation("com.google.firebase:firebase-messaging:23.4.1")

    ///Glide  for image loading
    implementation("com.github.bumptech.glide:glide:5.0.4")



}