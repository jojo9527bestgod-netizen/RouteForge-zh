package com.routeforge.buildlogic.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("routeforge.ktlint")

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.intVersion("androidCompileSdk")
                compileSdkMinor = 0

                defaultConfig {
                    applicationId = "com.routeforge.app"
                    minSdk = libs.intVersion("androidMinSdk")
                    targetSdk = libs.intVersion("androidTargetSdk")
                    versionCode = 1
                    versionName = "1.0"
                }

                compileOptions {
                    sourceCompatibility = jdkVersion
                    targetCompatibility = jdkVersion
                }
            }
        }
    }
}
