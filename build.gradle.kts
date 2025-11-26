// Top-level build file where you can add configuration options common to all sub-project
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // A linha d
    id("com.google.gms.google-services") version "4.4.2" apply false
}
