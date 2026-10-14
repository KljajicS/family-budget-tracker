plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "rs.budget"
    compileSdk = 37

    defaultConfig {
        applicationId = "rs.budget"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

sqldelight {
    databases {
        create("BudgetDb") {
            packageName.set("rs.budget.data.db")
            // Migrations live next to the .sq files; the version is derived from the highest N.sqm plus one.
            verifyMigrations.set(true)
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.sqldelight.android)
    implementation(libs.sqldelight.coroutines)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.sqldelight.sqlite)
    testImplementation(libs.sqlite.jdbc)
    testImplementation(libs.coroutines.test)
}

tasks.register("checkDomainPurity") {
    description = "Fails if rs.budget.domain imports Android classes"
    val domainDir = layout.projectDirectory.dir("src/main/kotlin/rs/budget/domain")
    inputs.dir(domainDir)
    doLast {
        val offenders = domainDir.asFileTree.files.filter { file ->
            file.readLines().any { it.trim().startsWith("import android") || it.trim().startsWith("import androidx") }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException("Android imports in domain: ${offenders.joinToString { it.name }}")
        }
    }
}
tasks.named("check") { dependsOn("checkDomainPurity") }
