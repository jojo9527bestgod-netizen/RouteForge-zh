plugins {
    id("routeforge.android.application")
    id("routeforge.compose")
    id("routeforge.koin")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.routeforge.app"
    defaultConfig {
        applicationId = "com.routeforge.zh"
        versionCode = 8
        versionName = "1.2.4-zh"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.material.icons.extended)
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:design-system"))
    implementation(project(":feature:addresssearch:domain"))
    implementation(project(":feature:addresssearch:data"))
    implementation(project(":feature:addresssearch:presentation"))
    implementation(project(":feature:mocklocationsetup:data"))
    implementation(project(":feature:mocklocationsetup:domain"))
    implementation(project(":feature:mocklocationsetup:presentation"))
    implementation(project(":feature:routing:domain"))
    implementation(project(":feature:routing:data"))
    implementation(project(":feature:routing:presentation"))
    implementation(project(":feature:simulation:data"))
    implementation(project(":feature:simulation:presentation"))
}
