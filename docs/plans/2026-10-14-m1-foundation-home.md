# Milestone 1: Foundation and Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An installable Android app where a family member sets up their name and accounts, adds expenses, income and transfers, and sees safe to spend, income, spent, savings and recent transactions on Home.

**Architecture:** Single `app` module with `domain`, `data`, `ui` packages. Every user action becomes an immutable event appended to a SQLite log; a projector derives read tables; screens query read tables through ViewModels exposing `StateFlow<UiState>`. Hilt wires it together.

**Tech Stack:** Kotlin 2.4.21, AGP 8.13.2, Gradle 8.14.6, Jetpack Compose (BOM 2026.09.00, Material 3), Navigation Compose 2.10.2, Hilt 2.60.1 via KSP 2.3.12, SQLDelight 2.4.1, kotlinx-serialization 1.11.0, kotlinx-coroutines 1.11.0, JUnit 4, sqlite-jdbc 3.53.4.0.

**Spec:** `docs/specs/2026-10-14-family-budget-design.md`. Decisions: `docs/decisions/`.

## Global Constraints

- `minSdk 26`, `targetSdk 35`, `compileSdk 35`. JDK 17.
- Package `rs.budget`. Packages `rs.budget.domain`, `rs.budget.data`, `rs.budget.ui`.
- `rs.budget.domain` has no `android.*` or `androidx.*` imports. Enforced by a Gradle task in Task 2.
- All amounts are `Money(BigDecimal, Currency)`. No `Double` or `Float` for money anywhere.
- Every user-facing string lives in `res/values/strings.xml` and `res/values-sr/strings.xml`. No hardcoded UI text.
- Event ids are UUID v7 strings. Timestamps are `kotlinx.datetime`-free: use `java.time.Instant` and `java.time.LocalDate` (desugaring not needed at minSdk 26).
- No new dependency beyond the Tech Stack list without an ADR.
- Commit messages: imperative, no prefix, no AI attribution.
- Validation before every commit: `make check` green.

## Review Focus

1. Amount `0` or empty input on Add: Save must be disabled, never save a zero-amount transaction (Task 10 test).
2. Transfer with same source and destination account: must be rejected (Task 10 test, plus the `Transaction` invariant in Task 3).
3. Transaction dated in a future month: must not count toward this month's income or spent (Task 6 test).
4. Account in EUR with no cached rate: Home shows the amount in EUR with a flag instead of guessing, safe to spend excludes it and is marked partial (Task 6 test).
5. Two events with identical `occurredAt` from different devices: projector order is deterministic by `(occurredAt, deviceId, seq)` (Task 5 test).

---

### Task 1: Project skeleton that builds and installs

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/rs/budget/BudgetApp.kt`, `app/src/main/kotlin/rs/budget/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-sr/strings.xml`, `app/src/main/res/values/themes.xml`
- Create: `.gitignore`, `Makefile`, `.github/workflows/android.yml`

**Interfaces:**
- Produces: a Gradle project where `./gradlew :app:testDebugUnitTest` and `./gradlew :app:installDebug` work. Version catalog aliases used by all later tasks.

- [ ] **Step 1: Generate the Gradle wrapper**

Run from the repo root (system Gradle 8.10.2 is installed):

```bash
gradle wrapper --gradle-version 8.14.6 --distribution-type bin
```

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/` created.

- [ ] **Step 2: Write settings and root build files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "family-budget-tracker"
include(":app")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.sqldelight) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
```

- [ ] **Step 3: Write the version catalog**

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.13.2"
kotlin = "2.4.21"
ksp = "2.3.12"
hilt = "2.60.1"
hiltNavigation = "1.4.0"
sqldelight = "2.4.1"
composeBom = "2026.09.00"
navigation = "2.10.2"
lifecycle = "2.11.0"
activity = "1.13.0"
coreKtx = "1.19.1"
serialization = "1.11.0"
coroutines = "1.11.0"
sqliteJdbc = "3.53.4.0"
junit = "4.13.2"

[libraries]
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigation" }
sqldelight-android = { group = "app.cash.sqldelight", name = "android-driver", version.ref = "sqldelight" }
sqldelight-sqlite = { group = "app.cash.sqldelight", name = "sqlite-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { group = "app.cash.sqldelight", name = "coroutines-extensions", version.ref = "sqldelight" }
serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
sqlite-jdbc = { group = "org.xerial", name = "sqlite-jdbc", version.ref = "sqliteJdbc" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

- [ ] **Step 4: Write the app module build file**

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "rs.budget"
    compileSdk = 35

    defaultConfig {
        applicationId = "rs.budget"
        minSdk = 26
        targetSdk = 35
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

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

sqldelight {
    databases {
        create("BudgetDb") {
            packageName.set("rs.budget.data.db")
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
```

`app/proguard-rules.pro`: empty file with one comment line `# Rules added per library when release builds start.`

- [ ] **Step 5: Manifest, Application, Activity, resources**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".BudgetApp"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/Theme.Budget"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

For `@mipmap/ic_launcher`: create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` as an adaptive icon with `@color/ic_launcher_bg` background and `@drawable/ic_launcher_fg` foreground. Convert `assets/icon.svg` to `app/src/main/res/drawable/ic_launcher_fg.xml` with Android Studio's Vector Asset tool or by hand if the SVG is simple. Add `app/src/main/res/values/colors.xml` with `<color name="ic_launcher_bg">#4A4FE0</color>`.

`app/src/main/kotlin/rs/budget/BudgetApp.kt`:

```kotlin
package rs.budget

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BudgetApp : Application()
```

`app/src/main/kotlin/rs/budget/MainActivity.kt`:

```kotlin
package rs.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Text("Budget") }
    }
}
```

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Family Budget</string>
</resources>
```

`app/src/main/res/values-sr/strings.xml`:

```xml
<resources>
    <string name="app_name">Kućni budžet</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.Budget" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 6: .gitignore, Makefile, CI**

`.gitignore`:

```
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
captures/
*.apk
*.aab
```

`Makefile`:

```makefile
.PHONY: check lint test build run shot emu

check: lint test

lint:
	./gradlew --quiet :app:lintDebug :app:checkDomainPurity

test:
	./gradlew --quiet :app:testDebugUnitTest

build:
	./gradlew --quiet :app:assembleDebug

run: build
	adb install -r app/build/outputs/apk/debug/app-debug.apk
	adb shell am start -n rs.budget/.MainActivity
	sleep 3
	$(MAKE) shot

shot:
	adb exec-out screencap -p > /tmp/budget.png
	@echo "screenshot: /tmp/budget.png"

emu:
	$(HOME)/Android/Sdk/emulator/emulator -avd budget -no-window -no-audio -no-boot-anim -gpu host
```

Note: `checkDomainPurity` is defined in Task 2; until then run `make test` only.

`.github/workflows/android.yml`:

```yaml
name: android
on:
  push:
    branches: [main]
  pull_request:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :app:testDebugUnitTest :app:lintDebug :app:checkDomainPurity :app:assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
      - name: Publish pre-release APK
        if: github.ref == 'refs/heads/main'
        uses: softprops/action-gh-release@v2
        with:
          tag_name: dev-${{ github.run_number }}
          prerelease: true
          files: app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 7: Verify the build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, APK at `app/build/outputs/apk/debug/app-debug.apk`.

Start the emulator (`make emu` in a long-lived shell), wait for `adb shell getprop sys.boot_completed` to print `1`, then `make run`. Expected: screenshot shows the word "Budget".

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Add Android project skeleton with Compose, Hilt and SQLDelight"
```

---

### Task 2: Money and Currency

**Files:**
- Create: `app/src/main/kotlin/rs/budget/domain/Money.kt`
- Modify: `app/build.gradle.kts` (add the `checkDomainPurity` task)
- Test: `app/src/test/kotlin/rs/budget/domain/MoneyTest.kt`

**Interfaces:**
- Produces: `enum class Currency { RSD, EUR, USD }`, `data class Money(val amount: BigDecimal, val currency: Currency)` with `plus`, `minus`, `unaryMinus`, `isZero`, `isNegative`, `Money.of(String, Currency)`, `Money.zero(Currency)`.

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/rs/budget/domain/MoneyTest.kt`:

```kotlin
package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {
    @Test
    fun `adds same currency`() {
        val sum = Money.of("10.50", Currency.RSD) + Money.of("0.50", Currency.RSD)
        assertEquals(Money.of("11.00", Currency.RSD), sum)
    }

    @Test
    fun `refuses to add different currencies`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.of("1", Currency.RSD) + Money.of("1", Currency.EUR)
        }
    }

    @Test
    fun `normalizes scale to two decimals`() {
        assertEquals(BigDecimal("1.00"), Money.of("1", Currency.EUR).amount)
        assertEquals(Money.of("1.00", Currency.EUR), Money.of("1", Currency.EUR))
    }

    @Test
    fun `rounds half up beyond two decimals`() {
        assertEquals(BigDecimal("1.01"), Money.of("1.005", Currency.RSD).amount)
    }

    @Test
    fun `zero and negative checks`() {
        assertTrue(Money.zero(Currency.RSD).isZero)
        assertTrue((-Money.of("5", Currency.RSD)).isNegative)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.domain.MoneyTest"`
Expected: compilation failure, `Money` unresolved.

- [ ] **Step 3: Implement Money**

`app/src/main/kotlin/rs/budget/domain/Money.kt`:

```kotlin
package rs.budget.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class Currency { RSD, EUR, USD }

data class Money(val amount: BigDecimal, val currency: Currency) {
    init {
        require(amount.scale() == SCALE) { "Money amount must have scale $SCALE, use Money.of" }
    }

    val isZero: Boolean get() = amount.signum() == 0
    val isNegative: Boolean get() = amount.signum() < 0

    operator fun plus(other: Money): Money {
        requireSame(other)
        return Money(amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        requireSame(other)
        return Money(amount - other.amount, currency)
    }

    operator fun unaryMinus(): Money = Money(amount.negate(), currency)

    private fun requireSame(other: Money) =
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }

    companion object {
        const val SCALE = 2

        fun of(amount: BigDecimal, currency: Currency): Money =
            Money(amount.setScale(SCALE, RoundingMode.HALF_UP), currency)

        fun of(amount: String, currency: Currency): Money = of(BigDecimal(amount), currency)

        fun zero(currency: Currency): Money = of(BigDecimal.ZERO, currency)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.domain.MoneyTest"`
Expected: 5 tests PASS.

- [ ] **Step 5: Add the domain purity check**

Append to `app/build.gradle.kts`:

```kotlin
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
```

Run: `./gradlew :app:checkDomainPurity`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/rs/budget/domain/Money.kt app/src/test/kotlin/rs/budget/domain/MoneyTest.kt app/build.gradle.kts
git commit -m "Add Money value type and domain purity check"
```

---

### Task 3: Domain entities and events

**Files:**
- Create: `app/src/main/kotlin/rs/budget/domain/Entities.kt`
- Create: `app/src/main/kotlin/rs/budget/domain/Event.kt`
- Create: `app/src/main/kotlin/rs/budget/domain/Ids.kt`
- Test: `app/src/test/kotlin/rs/budget/domain/EventSerializationTest.kt`
- Test: `app/src/test/kotlin/rs/budget/domain/IdsTest.kt`

**Interfaces:**
- Produces: entities `Account`, `Category`, `Member`, `Transaction`, enums `AccountKind`, `TransactionType`, `TransactionSource`; sealed `Event` with subtypes `MemberCreated`, `AccountCreated`, `AccountArchived`, `CategoryCreated`, `TransactionAdded`, `TransactionEdited`, `TransactionDeleted`; `EventRecord(id, deviceId, seq, occurredAt, event)`; `EventJson` (configured `Json`); `newId()` returning a UUID v7 string.

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/rs/budget/domain/EventSerializationTest.kt`:

```kotlin
package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EventSerializationTest {
    @Test
    fun `transaction added round trips through json`() {
        val event: Event = TransactionAdded(
            transaction = Transaction(
                id = "t1",
                type = TransactionType.EXPENSE,
                money = Money.of("4860", Currency.RSD),
                accountId = "a1",
                toAccountId = null,
                categoryId = "c1",
                date = LocalDate.of(2026, 10, 14),
                note = "",
                merchant = "Maxi",
                paidBy = "m1",
                source = TransactionSource.MANUAL,
            )
        )
        val json = EventJson.encodeToString(Event.serializer(), event)
        assertEquals(event, EventJson.decodeFromString(Event.serializer(), json))
    }

    @Test
    fun `money serializes as plain string amount`() {
        val json = EventJson.encodeToString(Money.serializer(), Money.of("12.5", Currency.EUR))
        assertEquals("""{"amount":"12.50","currency":"EUR"}""", json)
    }
}
```

`app/src/test/kotlin/rs/budget/domain/IdsTest.kt`:

```kotlin
package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {
    @Test
    fun `ids are uuid v7 and sort by time`() {
        val first = newId()
        Thread.sleep(2)
        val second = newId()
        assertEquals(36, first.length)
        assertEquals('7', first[14])
        assertTrue(first < second)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.domain.*"`
Expected: compilation failure.

- [ ] **Step 3: Implement ids**

`app/src/main/kotlin/rs/budget/domain/Ids.kt`:

```kotlin
package rs.budget.domain

import java.security.SecureRandom
import java.util.UUID

private val random = SecureRandom()

/** UUID v7: 48-bit unix millis, then random. Sorts by creation time as a string. */
fun newId(now: Long = System.currentTimeMillis()): String {
    val rnd = ByteArray(10).also(random::nextBytes)
    var msb = (now shl 16) or (0x7000L or ((rnd[0].toLong() and 0x0F) shl 8) or (rnd[1].toLong() and 0xFF))
    var lsb = 0L
    for (i in 2 until 10) lsb = (lsb shl 8) or (rnd[i].toLong() and 0xFF)
    lsb = (lsb and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
    return UUID(msb, lsb).toString()
}
```

- [ ] **Step 4: Implement entities**

`app/src/main/kotlin/rs/budget/domain/Entities.kt`:

```kotlin
package rs.budget.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

enum class AccountKind { SPENDING, SAVINGS }
enum class TransactionType { EXPENSE, INCOME, TRANSFER }
enum class TransactionSource { MANUAL, RECEIPT, RECURRING }

@Serializable
data class Member(val id: String, val displayName: String, val deviceId: String)

@Serializable
data class Account(
    val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    val archived: Boolean = false,
)

@Serializable
data class Category(val id: String, val name: String, val color: String, val archived: Boolean = false)

@Serializable
data class Transaction(
    val id: String,
    val type: TransactionType,
    val money: Money,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
    @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
    val note: String,
    val merchant: String,
    val paidBy: String,
    val source: TransactionSource,
) {
    init {
        require(!money.isNegative && !money.isZero) { "Transaction amount must be positive" }
        if (type == TransactionType.TRANSFER) {
            require(toAccountId != null && toAccountId != accountId) { "Transfer needs a different destination account" }
        } else {
            require(toAccountId == null) { "Only transfers have a destination account" }
        }
    }
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
```

Add `@Serializable` to `Money` and `Currency` in `Money.kt`, with a custom serializer for the amount:

```kotlin
@Serializable
data class Money(
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val currency: Currency,
) { /* body unchanged */ }

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): BigDecimal = BigDecimal(decoder.decodeString())
}
```

and mark `enum class Currency` with `@Serializable`.

- [ ] **Step 5: Implement events**

`app/src/main/kotlin/rs/budget/domain/Event.kt`:

```kotlin
package rs.budget.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
sealed interface Event

@Serializable @SerialName("MemberCreated")
data class MemberCreated(val member: Member) : Event

@Serializable @SerialName("AccountCreated")
data class AccountCreated(val account: Account) : Event

@Serializable @SerialName("AccountArchived")
data class AccountArchived(val accountId: String) : Event

@Serializable @SerialName("CategoryCreated")
data class CategoryCreated(val category: Category) : Event

@Serializable @SerialName("TransactionAdded")
data class TransactionAdded(val transaction: Transaction) : Event

@Serializable @SerialName("TransactionEdited")
data class TransactionEdited(val transaction: Transaction) : Event

@Serializable @SerialName("TransactionDeleted")
data class TransactionDeleted(val transactionId: String) : Event

/** One row of the append-only log. `seq` is per device and strictly increasing. */
data class EventRecord(
    val id: String,
    val deviceId: String,
    val seq: Long,
    val occurredAt: Instant,
    val event: Event,
)

val EventJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.domain.*"`
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/rs/budget/domain app/src/test/kotlin/rs/budget/domain
git commit -m "Add domain entities, events and UUID v7 ids"
```

---

### Task 4: SQLDelight schema and JVM test database

**Files:**
- Create: `app/src/main/sqldelight/rs/budget/data/db/Event.sq`
- Create: `app/src/main/sqldelight/rs/budget/data/db/Account.sq`
- Create: `app/src/main/sqldelight/rs/budget/data/db/Category.sq`
- Create: `app/src/main/sqldelight/rs/budget/data/db/Member.sq`
- Create: `app/src/main/sqldelight/rs/budget/data/db/Tx.sq`
- Create: `app/src/test/kotlin/rs/budget/data/TestDb.kt`
- Test: `app/src/test/kotlin/rs/budget/data/SchemaTest.kt`

**Interfaces:**
- Produces: generated `BudgetDb` with queries `eventQueries`, `accountQueries`, `categoryQueries`, `memberQueries`, `txQueries` as listed in the `.sq` files. `testDb(): BudgetDb` helper for JVM tests.

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/rs/budget/data/TestDb.kt`:

```kotlin
package rs.budget.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import rs.budget.data.db.BudgetDb

fun testDb(): BudgetDb {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BudgetDb.Schema.create(driver)
    return BudgetDb(driver)
}
```

`app/src/test/kotlin/rs/budget/data/SchemaTest.kt`:

```kotlin
package rs.budget.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaTest {
    @Test
    fun `fresh database has no events and seq starts at zero`() {
        val db = testDb()
        assertEquals(0L, db.eventQueries.count().executeAsOne())
        assertEquals(0L, db.eventQueries.lastSeq("dev-1").executeAsOne().max_seq ?: 0L)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.data.SchemaTest"`
Expected: compilation failure, `BudgetDb` unresolved.

- [ ] **Step 3: Write the schema files**

`app/src/main/sqldelight/rs/budget/data/db/Event.sq`:

```sql
CREATE TABLE event (
    id TEXT NOT NULL PRIMARY KEY,
    device_id TEXT NOT NULL,
    seq INTEGER NOT NULL,
    occurred_at INTEGER NOT NULL,
    type TEXT NOT NULL,
    payload TEXT NOT NULL,
    UNIQUE(device_id, seq)
);

CREATE INDEX event_order ON event(occurred_at, device_id, seq);

insert:
INSERT INTO event(id, device_id, seq, occurred_at, type, payload)
VALUES (?, ?, ?, ?, ?, ?);

count:
SELECT COUNT(*) FROM event;

lastSeq:
SELECT MAX(seq) AS max_seq FROM event WHERE device_id = ?;

allInOrder:
SELECT * FROM event ORDER BY occurred_at, device_id, seq;

countFor:
SELECT COUNT(*) FROM event WHERE device_id = ? AND seq = ?;
```

`app/src/main/sqldelight/rs/budget/data/db/Account.sq`:

```sql
CREATE TABLE account (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    currency TEXT NOT NULL,
    kind TEXT NOT NULL,
    archived INTEGER NOT NULL DEFAULT 0
);

upsert:
INSERT OR REPLACE INTO account(id, name, currency, kind, archived) VALUES (?, ?, ?, ?, ?);

archive:
UPDATE account SET archived = 1 WHERE id = ?;

active:
SELECT * FROM account WHERE archived = 0 ORDER BY name;

byId:
SELECT * FROM account WHERE id = ?;

deleteAll:
DELETE FROM account;
```

`app/src/main/sqldelight/rs/budget/data/db/Category.sq`:

```sql
CREATE TABLE category (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    color TEXT NOT NULL,
    archived INTEGER NOT NULL DEFAULT 0
);

upsert:
INSERT OR REPLACE INTO category(id, name, color, archived) VALUES (?, ?, ?, ?);

active:
SELECT * FROM category WHERE archived = 0 ORDER BY name;

deleteAll:
DELETE FROM category;
```

`app/src/main/sqldelight/rs/budget/data/db/Member.sq`:

```sql
CREATE TABLE member (
    id TEXT NOT NULL PRIMARY KEY,
    display_name TEXT NOT NULL,
    device_id TEXT NOT NULL
);

upsert:
INSERT OR REPLACE INTO member(id, display_name, device_id) VALUES (?, ?, ?);

all:
SELECT * FROM member ORDER BY display_name;

byDevice:
SELECT * FROM member WHERE device_id = ?;

deleteAll:
DELETE FROM member;
```

`app/src/main/sqldelight/rs/budget/data/db/Tx.sq`:

```sql
CREATE TABLE tx (
    id TEXT NOT NULL PRIMARY KEY,
    type TEXT NOT NULL,
    amount TEXT NOT NULL,
    currency TEXT NOT NULL,
    account_id TEXT NOT NULL,
    to_account_id TEXT,
    category_id TEXT,
    date TEXT NOT NULL,
    note TEXT NOT NULL,
    merchant TEXT NOT NULL,
    paid_by TEXT NOT NULL,
    source TEXT NOT NULL,
    edited_at INTEGER NOT NULL
);

CREATE INDEX tx_date ON tx(date DESC);

upsert:
INSERT OR REPLACE INTO tx(id, type, amount, currency, account_id, to_account_id, category_id, date, note, merchant, paid_by, source, edited_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

editedAt:
SELECT edited_at FROM tx WHERE id = ?;

delete:
DELETE FROM tx WHERE id = ?;

recent:
SELECT tx.*, category.name AS category_name, member.display_name AS paid_by_name
FROM tx
LEFT JOIN category ON category.id = tx.category_id
LEFT JOIN member ON member.id = tx.paid_by
ORDER BY date DESC, edited_at DESC
LIMIT ?;

inMonth:
SELECT * FROM tx WHERE date >= ? AND date < ?;

all:
SELECT * FROM tx;

deleteAll:
DELETE FROM tx;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.data.SchemaTest"`
Expected: PASS. If the generated `lastSeq` result type is not nullable `Long`, adjust the test to `executeAsOneOrNull()` and read the single column.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/sqldelight app/src/test/kotlin/rs/budget/data
git commit -m "Add SQLDelight schema for event log and read tables"
```

---

### Task 5: EventStore and Projector

**Files:**
- Create: `app/src/main/kotlin/rs/budget/data/EventStore.kt`
- Create: `app/src/main/kotlin/rs/budget/data/Projector.kt`
- Create: `app/src/main/kotlin/rs/budget/data/Clock.kt`
- Test: `app/src/test/kotlin/rs/budget/data/EventStoreTest.kt`

**Interfaces:**
- Consumes: `BudgetDb`, `Event`, `EventRecord`, `EventJson`, `newId()`.
- Produces:
  - `interface Clock { fun now(): Instant; fun today(): LocalDate }`, `class SystemClock : Clock`, `class FixedClock(var instant: Instant, zone: ZoneId) : Clock`.
  - `class EventStore(db: BudgetDb, deviceId: String, clock: Clock, projector: Projector)` with `suspend fun append(event: Event): EventRecord`, `fun applyRemote(records: List<EventRecord>)`, `fun rebuild()`.
  - `class Projector(db: BudgetDb)` with `fun apply(record: EventRecord)` and `fun clear()`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/rs/budget/data/EventStoreTest.kt`:

```kotlin
package rs.budget.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.budget.domain.Account
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.EventRecord
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionDeleted
import rs.budget.domain.TransactionEdited
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class EventStoreTest {
    private val db = testDb()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val store = EventStore(db, deviceId = "dev-1", clock = clock, projector = Projector(db))

    private fun tx(id: String, amount: String, type: TransactionType = TransactionType.EXPENSE) = Transaction(
        id = id, type = type, money = Money.of(amount, Currency.RSD), accountId = "a1", toAccountId = null,
        categoryId = null, date = LocalDate.of(2026, 10, 14), note = "", merchant = "", paidBy = "m1",
        source = TransactionSource.MANUAL,
    )

    @Test
    fun `append assigns increasing seq and projects account`() = runTest {
        val r1 = store.append(AccountCreated(Account("a1", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val r2 = store.append(AccountCreated(Account("a2", "Card", Currency.RSD, AccountKind.SPENDING)))
        assertEquals(1L, r1.seq)
        assertEquals(2L, r2.seq)
        assertEquals(listOf("Card", "Cash"), db.accountQueries.active().executeAsList().map { it.name })
    }

    @Test
    fun `edit replaces and delete removes transaction`() = runTest {
        store.append(TransactionAdded(tx("t1", "100")))
        store.append(TransactionEdited(tx("t1", "150")))
        assertEquals("150.00", db.txQueries.all().executeAsList().single().amount)
        store.append(TransactionDeleted("t1"))
        assertEquals(0, db.txQueries.all().executeAsList().size)
    }

    @Test
    fun `older remote edit does not overwrite newer local edit`() = runTest {
        store.append(TransactionAdded(tx("t1", "100")))
        clock.instant = Instant.parse("2026-10-14T12:00:00Z")
        store.append(TransactionEdited(tx("t1", "150")))
        val older = EventRecord(
            id = "remote-1", deviceId = "dev-2", seq = 1,
            occurredAt = Instant.parse("2026-10-14T11:00:00Z"),
            event = TransactionEdited(tx("t1", "120")),
        )
        store.applyRemote(listOf(older))
        assertEquals("150.00", db.txQueries.all().executeAsList().single().amount)
    }

    @Test
    fun `applyRemote skips records already seen`() = runTest {
        val rec = EventRecord("remote-1", "dev-2", 1, clock.now(), AccountCreated(Account("a9", "X", Currency.EUR, AccountKind.SAVINGS)))
        store.applyRemote(listOf(rec))
        store.applyRemote(listOf(rec))
        assertEquals(1L, db.eventQueries.count().executeAsOne())
    }

    @Test
    fun `rebuild reproduces read tables from the log in deterministic order`() = runTest {
        val t = clock.now()
        val fromB = EventRecord("b", "dev-b", 1, t, TransactionEdited(tx("t1", "200")))
        val fromA = EventRecord("a", "dev-a", 1, t, TransactionAdded(tx("t1", "100")))
        store.applyRemote(listOf(fromB, fromA))
        val afterApply = db.txQueries.all().executeAsList().single().amount
        store.rebuild()
        assertEquals(afterApply, db.txQueries.all().executeAsList().single().amount)
        assertEquals("200.00", afterApply)
        assertNull(db.txQueries.editedAt("missing").executeAsOneOrNull())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.data.EventStoreTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement Clock**

`app/src/main/kotlin/rs/budget/data/Clock.kt`:

```kotlin
package rs.budget.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

interface Clock {
    fun now(): Instant
    fun today(): LocalDate
}

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
    override fun today(): LocalDate = LocalDate.now()
}

class FixedClock(var instant: Instant, private val zone: ZoneId) : Clock {
    override fun now(): Instant = instant
    override fun today(): LocalDate = instant.atZone(zone).toLocalDate()
}
```

- [ ] **Step 4: Implement Projector**

`app/src/main/kotlin/rs/budget/data/Projector.kt`:

```kotlin
package rs.budget.data

import rs.budget.data.db.BudgetDb
import rs.budget.domain.AccountArchived
import rs.budget.domain.AccountCreated
import rs.budget.domain.CategoryCreated
import rs.budget.domain.EventRecord
import rs.budget.domain.MemberCreated
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionDeleted
import rs.budget.domain.TransactionEdited

/** Only writer of read tables. Idempotent per record; later `occurredAt` wins for edits. */
class Projector(private val db: BudgetDb) {

    fun apply(record: EventRecord) {
        val at = record.occurredAt.toEpochMilli()
        when (val e = record.event) {
            is MemberCreated -> db.memberQueries.upsert(e.member.id, e.member.displayName, e.member.deviceId)
            is AccountCreated -> db.accountQueries.upsert(
                e.account.id, e.account.name, e.account.currency.name, e.account.kind.name, if (e.account.archived) 1 else 0,
            )
            is AccountArchived -> db.accountQueries.archive(e.accountId)
            is CategoryCreated -> db.categoryQueries.upsert(
                e.category.id, e.category.name, e.category.color, if (e.category.archived) 1 else 0,
            )
            is TransactionAdded -> upsertTx(e.transaction, at)
            is TransactionEdited -> {
                val current = db.txQueries.editedAt(e.transaction.id).executeAsOneOrNull()
                if (current == null || current <= at) upsertTx(e.transaction, at)
            }
            is TransactionDeleted -> db.txQueries.delete(e.transactionId)
        }
    }

    fun clear() {
        db.txQueries.deleteAll()
        db.accountQueries.deleteAll()
        db.categoryQueries.deleteAll()
        db.memberQueries.deleteAll()
    }

    private fun upsertTx(t: Transaction, at: Long) = db.txQueries.upsert(
        id = t.id,
        type = t.type.name,
        amount = t.money.amount.toPlainString(),
        currency = t.money.currency.name,
        account_id = t.accountId,
        to_account_id = t.toAccountId,
        category_id = t.categoryId,
        date = t.date.toString(),
        note = t.note,
        merchant = t.merchant,
        paid_by = t.paidBy,
        source = t.source.name,
        edited_at = at,
    )
}
```

- [ ] **Step 5: Implement EventStore**

`app/src/main/kotlin/rs/budget/data/EventStore.kt`:

```kotlin
package rs.budget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rs.budget.data.db.BudgetDb
import rs.budget.data.db.Event as EventRow
import rs.budget.domain.Event
import rs.budget.domain.EventJson
import rs.budget.domain.EventRecord
import rs.budget.domain.newId
import java.time.Instant

class EventStore(
    private val db: BudgetDb,
    private val deviceId: String,
    private val clock: Clock,
    private val projector: Projector,
) {
    suspend fun append(event: Event): EventRecord = withContext(Dispatchers.IO) {
        db.transactionWithResult {
            val seq = (db.eventQueries.lastSeq(deviceId).executeAsOne().max_seq ?: 0L) + 1
            val record = EventRecord(newId(), deviceId, seq, clock.now(), event)
            insert(record)
            projector.apply(record)
            record
        }
    }

    /** Records from other devices. Unknown `(deviceId, seq)` pairs are stored and projected; the rest are skipped. */
    fun applyRemote(records: List<EventRecord>) = db.transaction {
        records
            .filter { db.eventQueries.countFor(it.deviceId, it.seq).executeAsOne() == 0L }
            .sortedWith(compareBy({ it.occurredAt }, { it.deviceId }, { it.seq }))
            .forEach {
                insert(it)
                projector.apply(it)
            }
    }

    fun rebuild() = db.transaction {
        projector.clear()
        db.eventQueries.allInOrder().executeAsList().forEach { projector.apply(toRecord(it)) }
    }

    private fun insert(r: EventRecord) = db.eventQueries.insert(
        id = r.id,
        device_id = r.deviceId,
        seq = r.seq,
        occurred_at = r.occurredAt.toEpochMilli(),
        type = r.event::class.simpleName!!,
        payload = EventJson.encodeToString(Event.serializer(), r.event),
    )

    private fun toRecord(row: EventRow) = EventRecord(
        id = row.id,
        deviceId = row.device_id,
        seq = row.seq,
        occurredAt = Instant.ofEpochMilli(row.occurred_at),
        event = EventJson.decodeFromString(Event.serializer(), row.payload),
    )
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.data.*"`
Expected: all PASS. SQLDelight generates `LastSeq(val max_seq: Long?)` for the aggregate query; `executeAsOne()` always returns one row.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/rs/budget/data app/src/test/kotlin/rs/budget/data
git commit -m "Add event store and projector"
```

---

### Task 6: Home calculations in the domain

**Files:**
- Create: `app/src/main/kotlin/rs/budget/domain/Rates.kt`
- Create: `app/src/main/kotlin/rs/budget/domain/HomeSummary.kt`
- Test: `app/src/test/kotlin/rs/budget/domain/HomeSummaryTest.kt`

**Interfaces:**
- Consumes: `Money`, `Currency`, `Account`, `Transaction`, `AccountKind`, `TransactionType`.
- Produces:
  - `class Rates(private val toRsd: Map<Currency, BigDecimal>)` with `fun convert(m: Money): Money?` (null when no rate), `Rates.EMPTY`.
  - `data class HomeSummary(safeToSpend: Money, safePerDay: Money, partial: Boolean, daysLeft: Int, income: Money, spent: Money, savingsTotal: Money, savingsByCurrency: Map<Currency, Money>, balances: Map<String, Money>)`.
  - `object HomeCalculator { fun summarize(today: LocalDate, accounts: List<Account>, transactions: List<Transaction>, rates: Rates, committed: Money = Money.zero(Currency.RSD)): HomeSummary }`. `committed` is the hook for bills, goals and budget reserve in later milestones.

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/rs/budget/domain/HomeSummaryTest.kt`:

```kotlin
package rs.budget.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HomeSummaryTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val cash = Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)
    private val card = Account("card", "Card", Currency.RSD, AccountKind.SPENDING)
    private val eurSavings = Account("eur", "EUR savings", Currency.EUR, AccountKind.SAVINGS)
    private val rates = Rates(mapOf(Currency.RSD to BigDecimal.ONE, Currency.EUR to BigDecimal("117.20")))

    private fun tx(id: String, type: TransactionType, amount: String, account: Account, date: LocalDate = today, to: Account? = null, currency: Currency = Currency.RSD) =
        Transaction(id, type, Money.of(amount, currency), account.id, to?.id, null, date, "", "", "m1", TransactionSource.MANUAL)

    @Test
    fun `income minus expenses this month, future and past months excluded`() {
        val txs = listOf(
            tx("1", TransactionType.INCOME, "245000", card),
            tx("2", TransactionType.EXPENSE, "98400", card),
            tx("3", TransactionType.EXPENSE, "5000", card, date = LocalDate.of(2026, 11, 1)),
            tx("4", TransactionType.EXPENSE, "7000", card, date = LocalDate.of(2026, 9, 30)),
        )
        val s = HomeCalculator.summarize(today, listOf(cash, card), txs, rates)
        assertEquals(Money.of("245000", Currency.RSD), s.income)
        assertEquals(Money.of("98400", Currency.RSD), s.spent)
    }

    @Test
    fun `balances include all history and transfers move money`() {
        val txs = listOf(
            tx("1", TransactionType.INCOME, "1000", card, date = LocalDate.of(2025, 1, 1)),
            tx("2", TransactionType.TRANSFER, "400", card, to = cash),
            tx("3", TransactionType.EXPENSE, "100", cash),
        )
        val s = HomeCalculator.summarize(today, listOf(cash, card), txs, rates)
        assertEquals(Money.of("600", Currency.RSD), s.balances["card"])
        assertEquals(Money.of("300", Currency.RSD), s.balances["cash"])
        assertEquals(Money.of("900", Currency.RSD), s.safeToSpend)
        assertEquals(18, s.daysLeft)
        assertEquals(Money.of("50", Currency.RSD), s.safePerDay)
    }

    @Test
    fun `savings accounts excluded from safe to spend but summed in savings`() {
        val txs = listOf(
            tx("1", TransactionType.INCOME, "1000", card),
            tx("2", TransactionType.INCOME, "10", eurSavings, currency = Currency.EUR),
        )
        val s = HomeCalculator.summarize(today, listOf(card, eurSavings), txs, rates)
        assertEquals(Money.of("1000", Currency.RSD), s.safeToSpend)
        assertEquals(Money.of("1172", Currency.RSD), s.savingsTotal)
        assertEquals(Money.of("10", Currency.EUR), s.savingsByCurrency[Currency.EUR])
        assertFalse(s.partial)
    }

    @Test
    fun `missing rate marks summary partial and skips that account`() {
        val eurCash = Account("eurcash", "EUR cash", Currency.EUR, AccountKind.SPENDING)
        val txs = listOf(
            tx("1", TransactionType.INCOME, "1000", card),
            tx("2", TransactionType.INCOME, "10", eurCash, currency = Currency.EUR),
        )
        val s = HomeCalculator.summarize(today, listOf(card, eurCash), txs, Rates.EMPTY.plus(Currency.RSD))
        assertTrue(s.partial)
        assertEquals(Money.of("1000", Currency.RSD), s.safeToSpend)
    }

    @Test
    fun `committed money reduces safe to spend`() {
        val txs = listOf(tx("1", TransactionType.INCOME, "1000", card))
        val s = HomeCalculator.summarize(today, listOf(card), txs, rates, committed = Money.of("250", Currency.RSD))
        assertEquals(Money.of("750", Currency.RSD), s.safeToSpend)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.domain.HomeSummaryTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement Rates**

`app/src/main/kotlin/rs/budget/domain/Rates.kt`:

```kotlin
package rs.budget.domain

import java.math.BigDecimal

/** Middle rates to RSD. RSD itself is always 1. */
class Rates(private val toRsd: Map<Currency, BigDecimal>) {
    fun convert(m: Money): Money? {
        if (m.currency == Currency.RSD) return m
        val rate = toRsd[m.currency] ?: return null
        return Money.of(m.amount * rate, Currency.RSD)
    }

    fun plus(currency: Currency, rate: BigDecimal = BigDecimal.ONE) = Rates(toRsd + (currency to rate))

    companion object {
        val EMPTY = Rates(emptyMap())
    }
}
```

- [ ] **Step 4: Implement HomeCalculator**

`app/src/main/kotlin/rs/budget/domain/HomeSummary.kt`:

```kotlin
package rs.budget.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

data class HomeSummary(
    val safeToSpend: Money,
    val safePerDay: Money,
    val partial: Boolean,
    val daysLeft: Int,
    val income: Money,
    val spent: Money,
    val savingsTotal: Money,
    val savingsByCurrency: Map<Currency, Money>,
    val balances: Map<String, Money>,
)

object HomeCalculator {

    fun summarize(
        today: LocalDate,
        accounts: List<Account>,
        transactions: List<Transaction>,
        rates: Rates,
        committed: Money = Money.zero(Currency.RSD),
    ): HomeSummary {
        val byId = accounts.associateBy { it.id }
        val balances = balances(accounts, transactions)
        var partial = false

        fun rsdOrSkip(m: Money): Money = rates.convert(m) ?: Money.zero(Currency.RSD).also { partial = true }

        val liquid = balances
            .filter { (id, _) -> byId[id]?.kind == AccountKind.SPENDING }
            .values.fold(Money.zero(Currency.RSD)) { acc, m -> acc + rsdOrSkip(m) }

        val savingsByCurrency = balances
            .filter { (id, _) -> byId[id]?.kind == AccountKind.SAVINGS }
            .values.groupBy { it.currency }
            .mapValues { (c, list) -> list.fold(Money.zero(c)) { a, b -> a + b } }
        val savingsTotal = savingsByCurrency.values.fold(Money.zero(Currency.RSD)) { acc, m -> acc + rsdOrSkip(m) }

        val month = YearMonth.from(today)
        val thisMonth = transactions.filter { YearMonth.from(it.date) == month }
        val income = thisMonth.filter { it.type == TransactionType.INCOME }.sumRsd(::rsdOrSkip)
        val spent = thisMonth.filter { it.type == TransactionType.EXPENSE }.sumRsd(::rsdOrSkip)

        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        val safe = liquid - committed
        val perDay = Money.of(safe.amount.divide(BigDecimal(daysLeft), 2, RoundingMode.DOWN), Currency.RSD)

        return HomeSummary(safe, perDay, partial, daysLeft, income, spent, savingsTotal, savingsByCurrency, balances)
    }

    fun balances(accounts: List<Account>, transactions: List<Transaction>): Map<String, Money> {
        val result = accounts.associate { it.id to Money.zero(it.currency) }.toMutableMap()
        fun add(accountId: String, delta: Money) {
            val current = result[accountId] ?: return
            result[accountId] = current + delta
        }
        for (t in transactions) {
            when (t.type) {
                TransactionType.INCOME -> add(t.accountId, t.money)
                TransactionType.EXPENSE -> add(t.accountId, -t.money)
                TransactionType.TRANSFER -> {
                    add(t.accountId, -t.money)
                    add(t.toAccountId!!, t.money)
                }
            }
        }
        return result
    }

    private fun List<Transaction>.sumRsd(convert: (Money) -> Money): Money =
        fold(Money.zero(Currency.RSD)) { acc, t -> acc + convert(t.money) }
}
```

Note on transfers across currencies: the destination gets the same `Money` object, which is in the source currency. Milestone 1 only allows transfers between accounts of the same currency (enforced in the Add screen, Task 9). Cross-currency transfers get an explicit received amount in a later milestone.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.domain.HomeSummaryTest"`
Expected: 5 PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/rs/budget/domain/Rates.kt app/src/main/kotlin/rs/budget/domain/HomeSummary.kt app/src/test/kotlin/rs/budget/domain/HomeSummaryTest.kt
git commit -m "Add home summary calculator with rates and safe to spend"
```

---

### Task 7: Repository, Hilt wiring and starter data

**Files:**
- Create: `app/src/main/kotlin/rs/budget/data/BudgetRepository.kt`
- Create: `app/src/main/kotlin/rs/budget/data/DeviceId.kt`
- Create: `app/src/main/kotlin/rs/budget/data/StarterCategories.kt`
- Create: `app/src/main/kotlin/rs/budget/di/DataModule.kt`
- Test: `app/src/test/kotlin/rs/budget/data/BudgetRepositoryTest.kt`

**Interfaces:**
- Consumes: `EventStore`, `Projector`, `BudgetDb`, `HomeCalculator`, `Rates`.
- Produces: `class BudgetRepository(db, store, clock)` with
  - `val accounts: Flow<List<Account>>`, `val categories: Flow<List<Category>>`, `val members: Flow<List<Member>>`, `val transactions: Flow<List<Transaction>>`
  - `fun recent(limit: Int): Flow<List<RecentTx>>` where `data class RecentTx(val transaction: Transaction, val categoryName: String?, val paidByName: String?)`
  - `val home: Flow<HomeSummary>`
  - `suspend fun setUp(displayName: String, accounts: List<Account>)` which emits `MemberCreated`, starter `CategoryCreated`s, and `AccountCreated`s
  - `suspend fun add(t: Transaction)`, `suspend fun edit(t: Transaction)`, `suspend fun delete(id: String)`
  - `suspend fun currentMember(): Member?`
- `StarterCategories.all: List<Category>` with fixed ids `cat-groceries`, `cat-bills`, `cat-transport`, `cat-eating-out`, `cat-household`, `cat-kids`, `cat-fun`, `cat-health`, `cat-other`.

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/rs/budget/data/BudgetRepositoryTest.kt`:

```kotlin
package rs.budget.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BudgetRepositoryTest {
    private val db = testDb()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val store = EventStore(db, "dev-1", clock, Projector(db))
    private val repo = BudgetRepository(db, store, clock)

    @Test
    fun `setUp creates member, starter categories and accounts`() = runTest {
        repo.setUp("Siniša", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        assertNotNull(repo.currentMember())
        assertEquals(StarterCategories.all.size, repo.categories.first().size)
        assertEquals("Cash", repo.accounts.first().single().name)
    }

    @Test
    fun `add shows up in recent and home`() = runTest {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val member = repo.currentMember()!!
        repo.add(Transaction("t1", TransactionType.INCOME, Money.of("1000", Currency.RSD), "cash", null, null, LocalDate.of(2026, 10, 14), "", "", member.id, TransactionSource.MANUAL))
        repo.add(Transaction("t2", TransactionType.EXPENSE, Money.of("300", Currency.RSD), "cash", null, "cat-groceries", LocalDate.of(2026, 10, 14), "", "Maxi", member.id, TransactionSource.MANUAL))
        val recent = repo.recent(10).first()
        assertEquals("Maxi", recent.first().transaction.merchant)
        assertEquals("Groceries", recent.first().categoryName)
        assertEquals("S", recent.first().paidByName)
        assertEquals(Money.of("700", Currency.RSD), repo.home.first().safeToSpend)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.data.BudgetRepositoryTest"`
Expected: compilation failure.

- [ ] **Step 3: Starter categories and device id**

`app/src/main/kotlin/rs/budget/data/StarterCategories.kt`:

```kotlin
package rs.budget.data

import rs.budget.domain.Category

/** Names are keys resolved through strings.xml by the UI (see CategoryNames in Task 8). Colors follow the mockups. */
object StarterCategories {
    val all = listOf(
        Category("cat-groceries", "Groceries", "#4A4FE0"),
        Category("cat-bills", "Bills", "#D9730D"),
        Category("cat-transport", "Transport", "#0F8A6E"),
        Category("cat-eating-out", "Eating out", "#8B4FD9"),
        Category("cat-household", "Household", "#6B6B6B"),
        Category("cat-kids", "Kids", "#C2366F"),
        Category("cat-fun", "Fun", "#B8860B"),
        Category("cat-health", "Health", "#1E88C7"),
        Category("cat-other", "Other", "#9E9E9E"),
    )
}
```

`app/src/main/kotlin/rs/budget/data/DeviceId.kt`:

```kotlin
package rs.budget.data

import android.content.Context
import rs.budget.domain.newId

/** Stable random id per install. Not the hardware id: it must not identify the phone to anyone reading Drive. */
fun deviceId(context: Context): String {
    val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
    return prefs.getString("id", null) ?: newId().also { prefs.edit().putString("id", it).apply() }
}
```

- [ ] **Step 4: Implement BudgetRepository**

`app/src/main/kotlin/rs/budget/data/BudgetRepository.kt`:

```kotlin
package rs.budget.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import rs.budget.data.db.Account as AccountRow
import rs.budget.data.db.BudgetDb
import rs.budget.data.db.Category as CategoryRow
import rs.budget.data.db.Member as MemberRow
import rs.budget.data.db.Recent
import rs.budget.data.db.Tx as TxRow
import rs.budget.domain.Account
import rs.budget.domain.AccountCreated
import rs.budget.domain.AccountKind
import rs.budget.domain.Category
import rs.budget.domain.CategoryCreated
import rs.budget.domain.Currency
import rs.budget.domain.HomeCalculator
import rs.budget.domain.HomeSummary
import rs.budget.domain.Member
import rs.budget.domain.MemberCreated
import rs.budget.domain.Money
import rs.budget.domain.Rates
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionAdded
import rs.budget.domain.TransactionDeleted
import rs.budget.domain.TransactionEdited
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.domain.newId
import java.math.BigDecimal
import java.time.LocalDate

data class RecentTx(val transaction: Transaction, val categoryName: String?, val paidByName: String?)

class BudgetRepository(
    private val db: BudgetDb,
    private val store: EventStore,
    private val clock: Clock,
    private val deviceId: String = "",
) {
    val accounts: Flow<List<Account>> =
        db.accountQueries.active().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    val categories: Flow<List<Category>> =
        db.categoryQueries.active().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    val members: Flow<List<Member>> =
        db.memberQueries.all().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    val transactions: Flow<List<Transaction>> =
        db.txQueries.all().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toDomain() } }

    fun recent(limit: Int): Flow<List<RecentTx>> =
        db.txQueries.recent(limit.toLong()).asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toRecent() } }

    // ponytail: rates are RSD-only until milestone 2 adds the NBS fetch; EUR/USD accounts show as partial.
    val home: Flow<HomeSummary> = combine(accounts, transactions) { accs, txs ->
        HomeCalculator.summarize(clock.today(), accs, txs, Rates(mapOf(Currency.RSD to BigDecimal.ONE)))
    }

    suspend fun currentMember(): Member? = withContext(Dispatchers.IO) {
        db.memberQueries.byDevice(deviceId).executeAsOneOrNull()?.toDomain()
            ?: db.memberQueries.all().executeAsList().firstOrNull()?.toDomain()
    }

    suspend fun setUp(displayName: String, accounts: List<Account>) {
        store.append(MemberCreated(Member(newId(), displayName, deviceId)))
        StarterCategories.all.forEach { store.append(CategoryCreated(it)) }
        accounts.forEach { store.append(AccountCreated(it)) }
    }

    suspend fun add(t: Transaction) { store.append(TransactionAdded(t)) }
    suspend fun edit(t: Transaction) { store.append(TransactionEdited(t)) }
    suspend fun delete(id: String) { store.append(TransactionDeleted(id)) }

    private fun AccountRow.toDomain() = Account(id, name, Currency.valueOf(currency), AccountKind.valueOf(kind), archived == 1L)
    private fun CategoryRow.toDomain() = Category(id, name, color, archived == 1L)
    private fun MemberRow.toDomain() = Member(id, display_name, device_id)
    private fun TxRow.toDomain() = Transaction(
        id, TransactionType.valueOf(type), Money.of(amount, Currency.valueOf(currency)), account_id, to_account_id,
        category_id, LocalDate.parse(date), note, merchant, paid_by, TransactionSource.valueOf(source),
    )
    private fun Recent.toRecent() = RecentTx(
        Transaction(
            id, TransactionType.valueOf(type), Money.of(amount, Currency.valueOf(currency)), account_id, to_account_id,
            category_id, LocalDate.parse(date), note, merchant, paid_by, TransactionSource.valueOf(source),
        ),
        category_name, paid_by_name,
    )
}
```

If the generated row class names differ (SQLDelight names them after the table: `Event`, `Account`, `Category`, `Member`, `Tx`, and the `recent` query result `Recent`), adjust the imports to the generated names.

- [ ] **Step 5: Hilt module**

`app/src/main/kotlin/rs/budget/di/DataModule.kt`:

```kotlin
package rs.budget.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.data.EventStore
import rs.budget.data.Projector
import rs.budget.data.SystemClock
import rs.budget.data.db.BudgetDb
import rs.budget.data.deviceId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun db(@ApplicationContext context: Context): BudgetDb =
        BudgetDb(AndroidSqliteDriver(BudgetDb.Schema, context, "budget.db"))

    @Provides @Singleton
    fun clock(): Clock = SystemClock()

    @Provides @Singleton
    fun repository(@ApplicationContext context: Context, db: BudgetDb, clock: Clock): BudgetRepository {
        val id = deviceId(context)
        return BudgetRepository(db, EventStore(db, id, clock, Projector(db)), clock, id)
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all PASS. `./gradlew :app:assembleDebug` BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/rs/budget/data app/src/main/kotlin/rs/budget/di app/src/test/kotlin/rs/budget/data
git commit -m "Add budget repository, starter categories and Hilt wiring"
```

---

### Task 8: Theme, navigation shell and strings

**Files:**
- Create: `app/src/main/kotlin/rs/budget/ui/theme/Theme.kt`
- Create: `app/src/main/kotlin/rs/budget/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/rs/budget/ui/Nav.kt`
- Create: `app/src/main/kotlin/rs/budget/ui/Format.kt`
- Create: `app/src/main/kotlin/rs/budget/ui/CategoryNames.kt`
- Modify: `app/src/main/kotlin/rs/budget/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-sr/strings.xml`
- Test: `app/src/test/kotlin/rs/budget/ui/FormatTest.kt`

**Interfaces:**
- Produces: `BudgetTheme { content }`, `enum class Tab(val route: String, val label: Int, val icon: ImageVector)` with HOME, INSIGHTS, SCAN, BUDGETS, GOALS, `Routes.SETUP`, `Routes.ADD`; `fun formatAmount(m: Money, sign: Boolean = false): String` and `fun formatMoneyNumber(amount: BigDecimal): String` producing `"72,510"` style grouping; `fun categoryLabel(id: String, fallback: String): Int?` mapping starter ids to string resources.

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/rs/budget/ui/FormatTest.kt`:

```kotlin
package rs.budget.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.budget.domain.Currency
import rs.budget.domain.Money
import java.util.Locale

class FormatTest {
    @Test
    fun `groups thousands and drops zero cents`() {
        assertEquals("72,510", formatAmount(Money.of("72510", Currency.RSD), locale = Locale.US))
        assertEquals("1,240.50", formatAmount(Money.of("1240.5", Currency.RSD), locale = Locale.US))
    }

    @Test
    fun `signed form`() {
        assertEquals("+245,000", formatAmount(Money.of("245000", Currency.RSD), sign = true, locale = Locale.US))
        assertEquals("-98,400", formatAmount(-Money.of("98400", Currency.RSD), sign = true, locale = Locale.US))
    }

    @Test
    fun `serbian locale uses dot grouping`() {
        assertEquals("72.510", formatAmount(Money.of("72510", Currency.RSD), locale = Locale.forLanguageTag("sr")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.ui.FormatTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement Format**

`app/src/main/kotlin/rs/budget/ui/Format.kt`:

```kotlin
package rs.budget.ui

import rs.budget.domain.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

fun formatAmount(m: Money, sign: Boolean = false, locale: Locale = Locale.getDefault()): String {
    val abs = m.amount.abs()
    val pattern = if (abs.stripTrailingZeros().scale() <= 0) "#,##0" else "#,##0.00"
    val body = DecimalFormat(pattern, DecimalFormatSymbols(locale)).format(abs)
    return when {
        m.isNegative -> "-$body"
        sign && !m.isZero -> "+$body"
        else -> body
    }
}

fun formatMoneyNumber(amount: BigDecimal, locale: Locale = Locale.getDefault()): String =
    DecimalFormat("#,##0.##", DecimalFormatSymbols(locale)).format(amount)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.ui.FormatTest"`
Expected: 3 PASS. If Serbian grouping yields a different separator on the JVM in use, assert with `DecimalFormatSymbols(Locale.forLanguageTag("sr")).groupingSeparator` instead of a literal.

- [ ] **Step 5: Theme and colors**

`app/src/main/kotlin/rs/budget/ui/theme/Color.kt`:

```kotlin
package rs.budget.ui.theme

import androidx.compose.ui.graphics.Color

val Indigo = Color(0xFF4A4FE0)
val IndigoDark = Color(0xFF7B7FF0)
val Green = Color(0xFF0F8A6E)
val Orange = Color(0xFFD9730D)
val LightBg = Color(0xFFFAF9F5)
val LightCard = Color(0xFFF2F1EC)
val LightSurface = Color(0xFFFFFFFF)
val LightText = Color(0xFF1A1A1A)
val LightMuted = Color(0xFF6B6B6B)
val AmoledBg = Color(0xFF000000)
val AmoledCard = Color(0xFF141414)
val AmoledSurface = Color(0xFF0A0A0A)
val AmoledText = Color(0xFFF2F2F2)
val AmoledMuted = Color(0xFF9A9A9A)
```

`app/src/main/kotlin/rs/budget/ui/theme/Theme.kt`:

```kotlin
package rs.budget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Indigo, onPrimary = LightSurface,
    background = LightBg, onBackground = LightText,
    surface = LightSurface, onSurface = LightText,
    surfaceVariant = LightCard, onSurfaceVariant = LightMuted,
    tertiary = Green, error = Orange,
)

private val Amoled = darkColorScheme(
    primary = IndigoDark, onPrimary = AmoledBg,
    background = AmoledBg, onBackground = AmoledText,
    surface = AmoledSurface, onSurface = AmoledText,
    surfaceVariant = AmoledCard, onSurfaceVariant = AmoledMuted,
    tertiary = Green, error = Orange,
)

/** Amounts use monospace so columns of numbers line up, as in the mockups. */
val AmountStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)

private val BudgetTypography = Typography(
    displayLarge = AmountStyle.copy(fontSize = 44.sp),
    headlineMedium = AmountStyle.copy(fontSize = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelSmall = TextStyle(fontSize = 12.sp, letterSpacing = 1.sp),
)

@Composable
fun BudgetTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Amoled else Light, typography = BudgetTypography, content = content)
}
```

- [ ] **Step 6: Navigation shell**

`app/src/main/kotlin/rs/budget/ui/Nav.kt`:

```kotlin
package rs.budget.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector
import rs.budget.R

enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    INSIGHTS("insights", R.string.tab_insights, Icons.Outlined.BarChart),
    SCAN("scan", R.string.tab_scan, Icons.Outlined.QrCodeScanner),
    BUDGETS("budgets", R.string.tab_budgets, Icons.Outlined.AccountBalanceWallet),
    GOALS("goals", R.string.tab_goals, Icons.Outlined.TrackChanges),
}

object Routes {
    const val SETUP = "setup"
    const val ADD = "add"
}
```

`app/src/main/kotlin/rs/budget/ui/CategoryNames.kt`:

```kotlin
package rs.budget.ui

import androidx.annotation.StringRes
import rs.budget.R

/** Starter categories are translated; user-created ones show their stored name. */
@StringRes
fun categoryLabel(id: String): Int? = when (id) {
    "cat-groceries" -> R.string.cat_groceries
    "cat-bills" -> R.string.cat_bills
    "cat-transport" -> R.string.cat_transport
    "cat-eating-out" -> R.string.cat_eating_out
    "cat-household" -> R.string.cat_household
    "cat-kids" -> R.string.cat_kids
    "cat-fun" -> R.string.cat_fun
    "cat-health" -> R.string.cat_health
    "cat-other" -> R.string.cat_other
    else -> null
}
```

- [ ] **Step 7: Strings, both languages**

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Family Budget</string>
    <string name="tab_home">Home</string>
    <string name="tab_insights">Insights</string>
    <string name="tab_scan">Scan</string>
    <string name="tab_budgets">Budgets</string>
    <string name="tab_goals">Goals</string>
    <string name="coming_soon">Coming in a later release</string>

    <string name="setup_title">Welcome</string>
    <string name="setup_name_label">Your name</string>
    <string name="setup_accounts_title">Accounts</string>
    <string name="setup_account_cash">Cash</string>
    <string name="setup_account_card">Card</string>
    <string name="setup_account_savings">Savings</string>
    <string name="setup_add_account">Add account</string>
    <string name="setup_account_name">Account name</string>
    <string name="setup_account_savings_toggle">Savings account</string>
    <string name="setup_done">Start</string>

    <string name="home_safe_to_spend">Safe to spend</string>
    <string name="home_per_day">≈ %1$s per day · %2$d days left</string>
    <string name="home_partial">Some accounts have no exchange rate and are not counted</string>
    <string name="home_income">Income</string>
    <string name="home_spent">Spent</string>
    <string name="home_savings">Savings, all currencies</string>
    <string name="home_recent">Recent</string>
    <string name="home_empty">No transactions yet. Tap + to add one.</string>
    <string name="home_add">Add</string>
    <string name="home_today">Today</string>
    <string name="home_yesterday">Yesterday</string>

    <string name="add_expense">Expense</string>
    <string name="add_income">Income</string>
    <string name="add_transfer">Transfer</string>
    <string name="add_account">Account</string>
    <string name="add_to_account">To account</string>
    <string name="add_date">Date</string>
    <string name="add_merchant">Merchant or note</string>
    <string name="add_save_expense">Save expense</string>
    <string name="add_save_income">Save income</string>
    <string name="add_save_transfer">Save transfer</string>
    <string name="add_close">Close</string>
    <string name="add_backspace">Delete digit</string>
    <string name="add_same_account">Pick a different destination account</string>
    <string name="add_currency_mismatch">Transfers between currencies come later</string>

    <string name="cat_groceries">Groceries</string>
    <string name="cat_bills">Bills</string>
    <string name="cat_transport">Transport</string>
    <string name="cat_eating_out">Eating out</string>
    <string name="cat_household">Household</string>
    <string name="cat_kids">Kids</string>
    <string name="cat_fun">Fun</string>
    <string name="cat_health">Health</string>
    <string name="cat_other">Other</string>

    <string name="source_manual">Manual</string>
    <string name="source_receipt">QR receipt</string>
    <string name="source_recurring">Recurring</string>
    <string name="member_you">You</string>
</resources>
```

`app/src/main/res/values-sr/strings.xml`:

```xml
<resources>
    <string name="app_name">Kućni budžet</string>
    <string name="tab_home">Početna</string>
    <string name="tab_insights">Uvidi</string>
    <string name="tab_scan">Skeniraj</string>
    <string name="tab_budgets">Budžeti</string>
    <string name="tab_goals">Ciljevi</string>
    <string name="coming_soon">Stiže u nekoj od sledećih verzija</string>

    <string name="setup_title">Dobrodošli</string>
    <string name="setup_name_label">Vaše ime</string>
    <string name="setup_accounts_title">Računi</string>
    <string name="setup_account_cash">Keš</string>
    <string name="setup_account_card">Kartica</string>
    <string name="setup_account_savings">Štednja</string>
    <string name="setup_add_account">Dodaj račun</string>
    <string name="setup_account_name">Naziv računa</string>
    <string name="setup_account_savings_toggle">Štedni račun</string>
    <string name="setup_done">Počni</string>

    <string name="home_safe_to_spend">Slobodno za trošenje</string>
    <string name="home_per_day">≈ %1$s dnevno · još %2$d dana</string>
    <string name="home_partial">Neki računi nemaju kurs i nisu uračunati</string>
    <string name="home_income">Prihodi</string>
    <string name="home_spent">Potrošeno</string>
    <string name="home_savings">Štednja, sve valute</string>
    <string name="home_recent">Nedavno</string>
    <string name="home_empty">Još nema transakcija. Dodirni + da dodaš.</string>
    <string name="home_add">Dodaj</string>
    <string name="home_today">Danas</string>
    <string name="home_yesterday">Juče</string>

    <string name="add_expense">Trošak</string>
    <string name="add_income">Prihod</string>
    <string name="add_transfer">Prenos</string>
    <string name="add_account">Račun</string>
    <string name="add_to_account">Na račun</string>
    <string name="add_date">Datum</string>
    <string name="add_merchant">Prodavnica ili beleška</string>
    <string name="add_save_expense">Sačuvaj trošak</string>
    <string name="add_save_income">Sačuvaj prihod</string>
    <string name="add_save_transfer">Sačuvaj prenos</string>
    <string name="add_close">Zatvori</string>
    <string name="add_backspace">Obriši cifru</string>
    <string name="add_same_account">Izaberi drugi odredišni račun</string>
    <string name="add_currency_mismatch">Prenos između valuta stiže kasnije</string>

    <string name="cat_groceries">Namirnice</string>
    <string name="cat_bills">Računi</string>
    <string name="cat_transport">Prevoz</string>
    <string name="cat_eating_out">Restorani</string>
    <string name="cat_household">Domaćinstvo</string>
    <string name="cat_kids">Deca</string>
    <string name="cat_fun">Zabava</string>
    <string name="cat_health">Zdravlje</string>
    <string name="cat_other">Ostalo</string>

    <string name="source_manual">Ručno</string>
    <string name="source_receipt">QR račun</string>
    <string name="source_recurring">Ponavljajuće</string>
    <string name="member_you">Ti</string>
</resources>
```

- [ ] **Step 8: MainActivity with scaffold, stubs for later tabs**

Replace `app/src/main/kotlin/rs/budget/MainActivity.kt`:

```kotlin
package rs.budget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import rs.budget.ui.Routes
import rs.budget.ui.Tab
import rs.budget.ui.theme.BudgetTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BudgetTheme { BudgetApp() } }
    }
}

@Composable
fun BudgetApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    Scaffold(
        bottomBar = {
            if (showBar) NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.HOME.route) { Stub(stringResource(Tab.HOME.label)) }
            composable(Tab.INSIGHTS.route) { Stub(stringResource(R.string.coming_soon)) }
            composable(Tab.SCAN.route) { Stub(stringResource(R.string.coming_soon)) }
            composable(Tab.BUDGETS.route) { Stub(stringResource(R.string.coming_soon)) }
            composable(Tab.GOALS.route) { Stub(stringResource(R.string.coming_soon)) }
            composable(Routes.SETUP) { Stub("setup") }
            composable(Routes.ADD) { Stub("add") }
        }
    }
}

@Composable
private fun Stub(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}
```

- [ ] **Step 9: Build, install, verify**

Run: `make check && make run`
Expected: tests green, screenshot shows five tabs with the Home label centered. Switch the emulator to Serbian (`adb shell "setprop persist.sys.locale sr-RS; setprop ctl.restart zygote"`), reopen, confirm tab labels are in Serbian, then switch back to `en-US`.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "Add theme, navigation shell and Serbian strings"
```

---

### Task 9: Setup screen (first run)

**Files:**
- Create: `app/src/main/kotlin/rs/budget/ui/setup/SetupViewModel.kt`
- Create: `app/src/main/kotlin/rs/budget/ui/setup/SetupScreen.kt`
- Modify: `app/src/main/kotlin/rs/budget/MainActivity.kt` (start destination decided by whether a member exists; SETUP route uses `SetupScreen`)
- Test: `app/src/test/kotlin/rs/budget/ui/setup/SetupViewModelTest.kt`

**Interfaces:**
- Consumes: `BudgetRepository.setUp`, `BudgetRepository.currentMember`.
- Produces: `SetupViewModel` with `val state: StateFlow<SetupState>`, `fun setName(String)`, `fun toggleAccount(index)`, `fun addAccount(name, currency, savings)`, `fun finish()`; `data class SetupState(name: String, accounts: List<SetupAccount>, canFinish: Boolean, done: Boolean)`; `data class SetupAccount(name: String, currency: Currency, kind: AccountKind, enabled: Boolean)`. Default accounts: Cash RSD, Card RSD, Savings RSD (disabled by default).

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/rs/budget/ui/setup/SetupViewModelTest.kt`:

```kotlin
package rs.budget.ui.setup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.testDb
import rs.budget.domain.Currency
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade")), Projector(db)), FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade")), "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `cannot finish without a name, finishing creates enabled accounts`() = runTest(dispatcher) {
        val vm = SetupViewModel(repo)
        assertFalse(vm.state.value.canFinish)
        vm.setName("Ana")
        assertTrue(vm.state.value.canFinish)
        vm.addAccount("EUR cash", Currency.EUR, savings = false)
        vm.finish()
        vm.state.first { it.done }
        assertEquals(listOf("Card", "Cash", "EUR cash"), repo.accounts.first().map { it.name })
        assertEquals("Ana", repo.currentMember()!!.displayName)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.ui.setup.SetupViewModelTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement SetupViewModel**

`app/src/main/kotlin/rs/budget/ui/setup/SetupViewModel.kt`:

```kotlin
package rs.budget.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.budget.data.BudgetRepository
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.newId
import javax.inject.Inject

data class SetupAccount(val name: String, val currency: Currency, val kind: AccountKind, val enabled: Boolean)

data class SetupState(
    val name: String = "",
    val accounts: List<SetupAccount> = listOf(
        SetupAccount("Cash", Currency.RSD, AccountKind.SPENDING, enabled = true),
        SetupAccount("Card", Currency.RSD, AccountKind.SPENDING, enabled = true),
        SetupAccount("Savings", Currency.RSD, AccountKind.SAVINGS, enabled = false),
    ),
    val done: Boolean = false,
) {
    val canFinish: Boolean get() = name.isNotBlank() && accounts.any { it.enabled }
}

@HiltViewModel
class SetupViewModel @Inject constructor(private val repo: BudgetRepository) : ViewModel() {
    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state

    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun toggleAccount(index: Int) = _state.update { s ->
        s.copy(accounts = s.accounts.mapIndexed { i, a -> if (i == index) a.copy(enabled = !a.enabled) else a })
    }

    fun addAccount(name: String, currency: Currency, savings: Boolean) = _state.update { s ->
        if (name.isBlank()) s else s.copy(
            accounts = s.accounts + SetupAccount(name.trim(), currency, if (savings) AccountKind.SAVINGS else AccountKind.SPENDING, enabled = true),
        )
    }

    fun finish() {
        val s = _state.value
        if (!s.canFinish) return
        viewModelScope.launch {
            repo.setUp(
                s.name.trim(),
                s.accounts.filter { it.enabled }.map { Account(newId(), it.name, it.currency, it.kind) },
            )
            _state.update { it.copy(done = true) }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.ui.setup.SetupViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Implement SetupScreen**

`app/src/main/kotlin/rs/budget/ui/setup/SetupScreen.kt`:

```kotlin
package rs.budget.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.Currency

@Composable
fun SetupScreen(onDone: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newCurrency by remember { mutableStateOf(Currency.RSD) }
    var newSavings by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::setName,
            label = { Text(stringResource(R.string.setup_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.setup_accounts_title), style = MaterialTheme.typography.titleMedium)
        state.accounts.forEachIndexed { i, a ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = a.enabled, onCheckedChange = { vm.toggleAccount(i) })
                Text("${a.name} · ${a.currency}", Modifier.weight(1f))
            }
        }
        if (adding) {
            OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.setup_account_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Currency.entries.forEach { c ->
                    FilterChip(selected = newCurrency == c, onClick = { newCurrency = c }, label = { Text(c.name) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = newSavings, onCheckedChange = { newSavings = it })
                Spacer(Modifier.height(0.dp).padding(4.dp))
                Text(stringResource(R.string.setup_account_savings_toggle))
            }
            Button(onClick = { vm.addAccount(newName, newCurrency, newSavings); newName = ""; adding = false }) {
                Text(stringResource(R.string.setup_add_account))
            }
        } else {
            TextButton(onClick = { adding = true }) { Text("+ " + stringResource(R.string.setup_add_account)) }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = vm::finish, enabled = state.canFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_done))
        }
    }
}
```

- [ ] **Step 6: Route to setup on first run**

In `MainActivity.kt`, add a tiny `RootViewModel` in the same file that exposes whether setup is done, and pick the start destination from it:

```kotlin
@HiltViewModel
class RootViewModel @Inject constructor(repo: BudgetRepository) : ViewModel() {
    val hasMember: StateFlow<Boolean?> = repo.members.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
```

In `BudgetApp()`, before the `Scaffold`:

```kotlin
val root: RootViewModel = hiltViewModel()
val hasMember by root.hasMember.collectAsStateWithLifecycle()
if (hasMember == null) return
```

and set `startDestination = if (hasMember == true) Tab.HOME.route else Routes.SETUP`. Replace the SETUP stub with:

```kotlin
composable(Routes.SETUP) {
    SetupScreen(onDone = { nav.navigate(Tab.HOME.route) { popUpTo(Routes.SETUP) { inclusive = true } } })
}
```

Imports needed: `androidx.lifecycle.ViewModel`, `androidx.lifecycle.viewModelScope`, `dagger.hilt.android.lifecycle.HiltViewModel`, `javax.inject.Inject`, `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.flow.map`, `kotlinx.coroutines.flow.stateIn`, `androidx.hilt.navigation.compose.hiltViewModel`, `androidx.lifecycle.compose.collectAsStateWithLifecycle`, `rs.budget.data.BudgetRepository`, `rs.budget.ui.setup.SetupScreen`.

- [ ] **Step 7: Verify on the emulator**

Run: `adb uninstall rs.budget; make check && make run`
Expected: Setup screen on first launch. Type a name, tap Start, Home stub appears. Relaunch the app: Home stub directly, no Setup.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Add first-run setup screen"
```

---

### Task 10: Add transaction screen

**Files:**
- Create: `app/src/main/kotlin/rs/budget/ui/add/AddViewModel.kt`
- Create: `app/src/main/kotlin/rs/budget/ui/add/AddScreen.kt`
- Create: `app/src/main/kotlin/rs/budget/ui/add/AmountInput.kt`
- Modify: `app/src/main/kotlin/rs/budget/MainActivity.kt` (ADD route)
- Test: `app/src/test/kotlin/rs/budget/ui/add/AmountInputTest.kt`
- Test: `app/src/test/kotlin/rs/budget/ui/add/AddViewModelTest.kt`

**Interfaces:**
- Consumes: `BudgetRepository.add`, `.accounts`, `.categories`, `.currentMember`, `Clock.today`.
- Produces:
  - `class AmountInput(val text: String = "")` immutable, with `fun press(key: Char): AmountInput` (digits, `.`, `⌫` as `'<'`), `val value: BigDecimal?` (null when empty or zero).
  - `AddViewModel` with `state: StateFlow<AddState>`, `fun setType(TransactionType)`, `fun press(Char)`, `fun setCurrency(Currency)`, `fun setCategory(String?)`, `fun setAccount(String)`, `fun setToAccount(String)`, `fun setDate(LocalDate)`, `fun setMerchant(String)`, `fun save()`.
  - `data class AddState(type, amount: AmountInput, currency, categoryId, accountId, toAccountId, date, merchant, accounts, categories, error: Int?, saved: Boolean)` with `val canSave: Boolean`.

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/rs/budget/ui/add/AmountInputTest.kt`:

```kotlin
package rs.budget.ui.add

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AmountInputTest {
    private fun type(s: String) = s.fold(AmountInput()) { acc, c -> acc.press(c) }

    @Test fun `digits and one decimal point`() = assertEquals(BigDecimal("1240.5"), type("1240.5").value)
    @Test fun `second point ignored`() = assertEquals("1.5", type("1.5.").text)
    @Test fun `max two decimals`() = assertEquals("1.25", type("1.259").text)
    @Test fun `leading zeros collapse`() = assertEquals("7", type("007").text)
    @Test fun `backspace`() = assertEquals("12", type("123<").text)
    @Test fun `empty and zero are null`() { assertNull(type("").value); assertNull(type("0.00").value) }
    @Test fun `length cap at 12 characters`() = assertEquals(12, type("1234567890123456").text.length)
}
```

`app/src/test/kotlin/rs/budget/ui/add/AddViewModelTest.kt`:

```kotlin
package rs.budget.ui.add

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import rs.budget.R
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.testDb
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private suspend fun seeded(): AddViewModel {
        repo.setUp("S", listOf(
            Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING),
            Account("card", "Card", Currency.RSD, AccountKind.SPENDING),
            Account("eur", "EUR", Currency.EUR, AccountKind.SAVINGS),
        ))
        // Repository flows query on Dispatchers.IO, so wait for real data instead of advancing the virtual clock.
        return AddViewModel(repo, clock).also { vm -> vm.state.first { it.accounts.isNotEmpty() } }
    }

    @Test
    fun `save disabled until amount entered, then saves expense with defaults`() = runTest(dispatcher) {
        val vm = seeded()
        assertFalse(vm.state.value.canSave)
        "1240".forEach(vm::press)
        vm.setCategory("cat-household")
        assertTrue(vm.state.value.canSave)
        vm.save()
        vm.state.first { it.saved }
        val t = repo.transactions.first().single()
        assertEquals(TransactionType.EXPENSE, t.type)
        assertEquals("1240.00", t.money.amount.toPlainString())
        assertEquals("cash", t.accountId)
        assertEquals(clock.today(), t.date)
    }

    @Test
    fun `zero amount cannot be saved`() = runTest(dispatcher) {
        val vm = seeded()
        "0.00".forEach(vm::press)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `transfer to same account is rejected with a message`() = runTest(dispatcher) {
        val vm = seeded()
        vm.setType(TransactionType.TRANSFER)
        "500".forEach(vm::press)
        vm.setAccount("cash")
        vm.setToAccount("cash")
        assertFalse(vm.state.value.canSave)
        assertEquals(R.string.add_same_account, vm.state.value.error)
    }

    @Test
    fun `transfer across currencies is rejected`() = runTest(dispatcher) {
        val vm = seeded()
        vm.setType(TransactionType.TRANSFER)
        "500".forEach(vm::press)
        vm.setAccount("cash")
        vm.setToAccount("eur")
        assertEquals(R.string.add_currency_mismatch, vm.state.value.error)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `choosing an account sets the currency`() = runTest(dispatcher) {
        val vm = seeded()
        vm.setAccount("eur")
        assertEquals(Currency.EUR, vm.state.value.currency)
        assertNotNull(vm.state.value.accounts.firstOrNull { it.id == "eur" })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.ui.add.*"`
Expected: compilation failure.

- [ ] **Step 3: Implement AmountInput**

`app/src/main/kotlin/rs/budget/ui/add/AmountInput.kt`:

```kotlin
package rs.budget.ui.add

import java.math.BigDecimal

/** Keypad state. Keys: '0'..'9', '.', and '<' for backspace. */
data class AmountInput(val text: String = "") {
    val value: BigDecimal?
        get() = text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    fun press(key: Char): AmountInput = when {
        key == '<' -> copy(text = text.dropLast(1))
        text.length >= MAX -> this
        key == '.' -> if ('.' in text) this else copy(text = text.ifEmpty { "0" } + ".")
        key.isDigit() -> {
            val decimals = text.substringAfter('.', "")
            when {
                '.' in text && decimals.length >= 2 -> this
                text == "0" -> copy(text = key.toString())
                else -> copy(text = text + key)
            }
        }
        else -> this
    }

    private companion object { const val MAX = 12 }
}
```

- [ ] **Step 4: Implement AddViewModel**

`app/src/main/kotlin/rs/budget/ui/add/AddViewModel.kt`:

```kotlin
package rs.budget.ui.add

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rs.budget.R
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.domain.Account
import rs.budget.domain.Category
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.domain.newId
import java.time.LocalDate
import javax.inject.Inject

data class AddState(
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: AmountInput = AmountInput(),
    val currency: Currency = Currency.RSD,
    val categoryId: String? = "cat-groceries",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val date: LocalDate,
    val merchant: String = "",
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    @StringRes val error: Int? = null,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = amount.value != null && accountId != null && error == null &&
        (type != TransactionType.TRANSFER || toAccountId != null)
}

@HiltViewModel
class AddViewModel @Inject constructor(
    private val repo: BudgetRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(AddState(date = clock.today()))
    val state: StateFlow<AddState> = _state

    init {
        viewModelScope.launch {
            combine(repo.accounts, repo.categories) { a, c -> a to c }.collect { (accounts, categories) ->
                _state.update { s ->
                    val account = s.accountId ?: accounts.firstOrNull()?.id
                    s.copy(accounts = accounts, categories = categories, accountId = account,
                        currency = accounts.firstOrNull { it.id == account }?.currency ?: s.currency)
                }
            }
        }
    }

    fun setType(type: TransactionType) = _state.update { validate(it.copy(type = type, toAccountId = null)) }
    fun press(key: Char) = _state.update { it.copy(amount = it.amount.press(key)) }
    fun setCurrency(currency: Currency) = _state.update { it.copy(currency = currency) }
    fun setCategory(id: String?) = _state.update { it.copy(categoryId = id) }
    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun setMerchant(text: String) = _state.update { it.copy(merchant = text) }

    fun setAccount(id: String) = _state.update { s ->
        validate(s.copy(accountId = id, currency = s.accounts.firstOrNull { it.id == id }?.currency ?: s.currency))
    }

    fun setToAccount(id: String) = _state.update { validate(it.copy(toAccountId = id)) }

    fun save() {
        val s = _state.value
        val amount = s.amount.value ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            val member = repo.currentMember() ?: return@launch
            repo.add(
                Transaction(
                    id = newId(),
                    type = s.type,
                    money = Money.of(amount, s.currency),
                    accountId = s.accountId!!,
                    toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccountId else null,
                    categoryId = if (s.type == TransactionType.EXPENSE) s.categoryId else null,
                    date = s.date,
                    note = "",
                    merchant = s.merchant.trim(),
                    paidBy = member.id,
                    source = TransactionSource.MANUAL,
                )
            )
            _state.update { it.copy(saved = true) }
        }
    }

    private fun validate(s: AddState): AddState {
        if (s.type != TransactionType.TRANSFER || s.toAccountId == null) return s.copy(error = null)
        val from = s.accounts.firstOrNull { it.id == s.accountId }
        val to = s.accounts.firstOrNull { it.id == s.toAccountId }
        return s.copy(
            error = when {
                s.accountId == s.toAccountId -> R.string.add_same_account
                from != null && to != null && from.currency != to.currency -> R.string.add_currency_mismatch
                else -> null
            }
        )
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.ui.add.*"`
Expected: all PASS. `R.string` constants resolve in unit tests because `unitTests.isReturnDefaultValues = true` and the R class is generated at compile time.

- [ ] **Step 6: Implement AddScreen**

`app/src/main/kotlin/rs/budget/ui/add/AddScreen.kt`:

```kotlin
package rs.budget.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.domain.Currency
import rs.budget.domain.TransactionType
import rs.budget.ui.categoryLabel
import rs.budget.ui.theme.AmountStyle

@Composable
fun AddScreen(onClose: () -> Unit, vm: AddViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.saved) { if (s.saved) onClose() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.add_close)) }
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                TransactionType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = s.type == t,
                        onClick = { vm.setType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, TransactionType.entries.size),
                    ) { Text(stringResource(t.label())) }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(s.amount.text.ifEmpty { "0" }, style = AmountStyle.copy(fontSize = 44.sp))
            Spacer(Modifier.padding(4.dp))
            Text(s.currency.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            Currency.entries.forEach { c ->
                FilterChip(selected = s.currency == c, onClick = { vm.setCurrency(c) }, label = { Text(c.name) })
            }
        }

        if (s.type == TransactionType.EXPENSE) {
            LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(120.dp)) {
                items(s.categories, key = { it.id }) { c ->
                    val label = categoryLabel(c.id)?.let { stringResource(it) } ?: c.name
                    FilterChip(selected = s.categoryId == c.id, onClick = { vm.setCategory(c.id) }, label = { Text(label, maxLines = 1) })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccountPicker(stringResource(R.string.add_account), s.accounts.map { it.id to it.name }, s.accountId, vm::setAccount, Modifier.weight(1f))
            if (s.type == TransactionType.TRANSFER) {
                AccountPicker(stringResource(R.string.add_to_account), s.accounts.map { it.id to it.name }, s.toAccountId, vm::setToAccount, Modifier.weight(1f))
            }
        }
        s.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }

        OutlinedTextField(s.merchant, vm::setMerchant, label = { Text(stringResource(R.string.add_merchant)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.weight(1f))
        Keypad(onKey = vm::press)
        Button(onClick = vm::save, enabled = s.canSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(s.type.saveLabel()))
        }
    }
}

@Composable
private fun AccountPicker(title: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (id, name) ->
                FilterChip(selected = selected == id, onClick = { onSelect(id) }, label = { Text(name) })
            }
        }
    }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "<")
    LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(232.dp)) {
        items(keys) { k ->
            FilledTonalButton(onClick = { onKey(k[0]) }, modifier = Modifier.height(52.dp).fillMaxWidth()) {
                Text(if (k == "<") "⌫" else k, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun TransactionType.label() = when (this) {
    TransactionType.EXPENSE -> R.string.add_expense
    TransactionType.INCOME -> R.string.add_income
    TransactionType.TRANSFER -> R.string.add_transfer
}

private fun TransactionType.saveLabel() = when (this) {
    TransactionType.EXPENSE -> R.string.add_save_expense
    TransactionType.INCOME -> R.string.add_save_income
    TransactionType.TRANSFER -> R.string.add_save_transfer
}
```

Add `import androidx.compose.ui.unit.sp` at the top. Date editing stays at "today" in this milestone; `setDate` exists for the Home edit flow later.

- [ ] **Step 7: Wire the ADD route**

In `MainActivity.kt` replace the ADD stub:

```kotlin
composable(Routes.ADD) { AddScreen(onClose = { nav.popBackStack() }) }
```

with `import rs.budget.ui.add.AddScreen`. Temporarily make the HOME stub a button that navigates to `Routes.ADD` so the screen can be reached before Task 11.

- [ ] **Step 8: Verify on the emulator**

Run: `make check && make run`
Expected: open Add, type 1240, pick Household, Save returns to Home. Open Add, pick Transfer, same account twice shows the error text and Save stays disabled.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "Add transaction entry screen with keypad"
```

---

### Task 11: Home screen

**Files:**
- Create: `app/src/main/kotlin/rs/budget/ui/home/HomeViewModel.kt`
- Create: `app/src/main/kotlin/rs/budget/ui/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/rs/budget/MainActivity.kt` (HOME route uses `HomeScreen`)
- Test: `app/src/test/kotlin/rs/budget/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `BudgetRepository.home`, `.recent(limit)`, `.members`, `.accounts`, `Clock.today`.
- Produces: `HomeViewModel` with `state: StateFlow<HomeState?>`; `data class HomeState(monthLabel: String, summary: HomeSummary, recent: List<RecentTx>, currentMemberId: String?, today: LocalDate)`.

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/rs/budget/ui/home/HomeViewModelTest.kt`:

```kotlin
package rs.budget.ui.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import rs.budget.data.BudgetRepository
import rs.budget.data.EventStore
import rs.budget.data.FixedClock
import rs.budget.data.Projector
import rs.budget.data.testDb
import rs.budget.domain.Account
import rs.budget.domain.AccountKind
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.Transaction
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(Instant.parse("2026-10-14T10:00:00Z"), ZoneId.of("Europe/Belgrade"))
    private val db = testDb()
    private val repo = BudgetRepository(db, EventStore(db, "dev-1", clock, Projector(db)), clock, "dev-1")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state reflects repository`() = runTest(dispatcher) {
        repo.setUp("S", listOf(Account("cash", "Cash", Currency.RSD, AccountKind.SPENDING)))
        val m = repo.currentMember()!!.id
        repo.add(Transaction("t1", TransactionType.INCOME, Money.of("1000", Currency.RSD), "cash", null, null, LocalDate.of(2026, 10, 1), "", "", m, TransactionSource.MANUAL))
        repo.add(Transaction("t2", TransactionType.EXPENSE, Money.of("250", Currency.RSD), "cash", null, "cat-fun", LocalDate.of(2026, 10, 14), "", "Cinema", m, TransactionSource.MANUAL))
        val vm = HomeViewModel(repo, clock)
        val s = vm.state.filterNotNull().first()
        assertEquals(Money.of("750", Currency.RSD), s.summary.safeToSpend)
        assertEquals("Cinema", s.recent.first().transaction.merchant)
        assertEquals(m, s.currentMemberId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.ui.home.HomeViewModelTest"`
Expected: compilation failure.

- [ ] **Step 3: Implement HomeViewModel**

`app/src/main/kotlin/rs/budget/ui/home/HomeViewModel.kt`:

```kotlin
package rs.budget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import rs.budget.data.BudgetRepository
import rs.budget.data.Clock
import rs.budget.data.RecentTx
import rs.budget.domain.HomeSummary
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val summary: HomeSummary,
    val recent: List<RecentTx>,
    val currentMemberId: String?,
    val today: LocalDate,
)

@HiltViewModel
class HomeViewModel @Inject constructor(repo: BudgetRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<HomeState?> = combine(
        repo.home,
        repo.recent(20),
        flow { emit(repo.currentMember()?.id) },
    ) { summary, recent, member ->
        HomeState(summary, recent, member, clock.today())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "rs.budget.ui.home.HomeViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Implement HomeScreen**

`app/src/main/kotlin/rs/budget/ui/home/HomeScreen.kt`:

```kotlin
package rs.budget.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rs.budget.R
import rs.budget.data.RecentTx
import rs.budget.domain.Currency
import rs.budget.domain.Money
import rs.budget.domain.TransactionSource
import rs.budget.domain.TransactionType
import rs.budget.ui.categoryLabel
import rs.budget.ui.formatAmount
import rs.budget.ui.theme.Green
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(onAdd: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state ?: return

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(s.today.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = null), modifier = Modifier.weight(1f))
                FilledIconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add)) }
            }
        }
        item { SafeToSpendCard(s) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.home_income), formatAmount(s.summary.income, sign = true), Green, Modifier.weight(1f))
                StatCard(stringResource(R.string.home_spent), formatAmount(-s.summary.spent, sign = true), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }
        item { SavingsCard(s.summary.savingsTotal, s.summary.savingsByCurrency) }
        item { Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium) }
        if (s.recent.isEmpty()) item { Text(stringResource(R.string.home_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(s.recent, key = { it.transaction.id }) { RecentRow(it, s.currentMemberId, s.today) }
    }
}

@Composable
private fun SafeToSpendCard(s: HomeState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.home_safe_to_spend).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatAmount(s.summary.safeToSpend), style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.size(8.dp))
                Text(Currency.RSD.name, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(stringResource(R.string.home_per_day, formatAmount(s.summary.safePerDay), s.summary.daysLeft), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.summary.partial) Text(stringResource(R.string.home_partial), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        }
    }
}

@Composable
private fun SavingsCard(total: Money, byCurrency: Map<Currency, Money>) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_savings), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatAmount(total), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.size(6.dp))
                Text(Currency.RSD.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                byCurrency.values.forEach { m ->
                    Text(
                        "${formatAmount(m)} ${m.currency}",
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(r: RecentTx, me: String?, today: LocalDate) {
    val t = r.transaction
    val title = t.merchant.ifBlank { r.categoryName ?: t.type.name }
    val category = t.categoryId?.let { id -> categoryLabel(id)?.let { stringResource(it) } ?: r.categoryName }
    val who = if (t.paidBy == me) stringResource(R.string.member_you) else r.paidByName ?: ""
    val source = when (t.source) {
        TransactionSource.MANUAL -> R.string.source_manual
        TransactionSource.RECEIPT -> R.string.source_receipt
        TransactionSource.RECURRING -> R.string.source_recurring
    }
    val signed = when (t.type) {
        TransactionType.EXPENSE -> -t.money
        TransactionType.INCOME -> t.money
        TransactionType.TRANSFER -> t.money
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Text(title.take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(category, who.ifBlank { null }, stringResource(source)).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatAmount(signed, sign = t.type != TransactionType.TRANSFER), style = MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = if (t.type == TransactionType.INCOME) Green else MaterialTheme.colorScheme.onSurface)
            Text(relativeDate(t.date, today), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun relativeDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.home_today)
    today.minusDays(1) -> stringResource(R.string.home_yesterday)
    else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
}
```

- [ ] **Step 6: Wire the HOME route**

In `MainActivity.kt` replace the HOME stub:

```kotlin
composable(Tab.HOME.route) { HomeScreen(onAdd = { nav.navigate(Routes.ADD) }) }
```

with `import rs.budget.ui.home.HomeScreen`. Remove the `Stub("...")` for HOME. The remaining tabs keep `coming_soon` stubs.

- [ ] **Step 7: Verify on the emulator, both themes and both languages**

Run: `adb uninstall rs.budget; make check && make run`
Expected: Setup, then Home with zero amounts and the empty hint. Add an income of 245000 and an expense of 4860 at Maxi. Home shows safe to spend 240,140, income +245,000, spent -4,860, Maxi in Recent with "Groceries · You · Manual" and "Today". Toggle dark mode (`adb shell "cmd uimode night yes"`) and screenshot: black background. Toggle back (`night no`). Switch locale to `sr-RS` and screenshot: Serbian labels and dot grouping. Switch back.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Add home screen with safe to spend, totals and recent transactions"
```

---

### Task 12: Clean-up, README and milestone close

**Files:**
- Modify: `README.md`
- Modify: `docs/plans/roadmap.md` (mark milestone 1 done)

- [ ] **Step 1: README**

Replace `README.md`:

```markdown
<img src="assets/icon.svg" width="64" height="64" alt="">

# Family Budget Tracker

Privacy-first, offline-first family budgeting for Android. Data stays on your phones; Google Drive only ever sees encrypted files.

## Status

Milestone 1 (foundation and Home) done. See `docs/plans/roadmap.md`.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```bash
make check   # JVM tests and lint
make run     # install on the connected device or emulator and screenshot
```

## Docs

- `docs/specs/` product and technical design
- `docs/decisions/` architecture decision records
- `docs/design/` UI mockups
```

- [ ] **Step 2: Roadmap**

In `docs/plans/roadmap.md`, change the milestone 1 row's Delivers cell to start with "Done 2026-MM-DD." using the actual date.

- [ ] **Step 3: Final check**

Run: `make check`
Expected: green. `git status` clean except these two files.

- [ ] **Step 4: Commit**

```bash
git add README.md docs/plans/roadmap.md
git commit -m "Document build and close milestone 1"
```
