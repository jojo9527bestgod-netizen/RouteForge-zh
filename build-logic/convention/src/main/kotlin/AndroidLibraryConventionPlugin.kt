package com.routeforge.buildlogic.convention

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("routeforge.ktlint")

            extensions.configure<LibraryExtension> {
                compileSdk = libs.intVersion("androidCompileSdk")
                compileSdkMinor = 0

                defaultConfig {
                    minSdk = libs.intVersion("androidMinSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                compileOptions {
                    sourceCompatibility = jdkVersion
                    targetCompatibility = jdkVersion
                }

                testOptions {
                    unitTests.isReturnDefaultValues = true
                }
            }

            dependencies.add("testImplementation", libs.findLibrary("junit-jupiter").get())
            dependencies.add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }
        }
    }
}
