import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.ksp)
	alias(libs.plugins.hilt)
}

// Signing credentials live outside the repository, in a `keystore.properties` the .gitignore already
// excludes along with the `*.jks` it points at. Same arrangement as the FitnessTracker app.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
	if (keystorePropertiesFile.exists()) {
		load(keystorePropertiesFile.inputStream())
	}
}
// A checkout without the keystore still has to configure and build debug, so the release signing config
// only exists when the credentials do. Without this guard every clone fails at configuration time.
val hasReleaseKeystore = keystorePropertiesFile.exists()

android {
	namespace = "net.luis.sudoku"
	// 37 is required by the Compose BOM's lifecycle artifacts; it is independent of targetSdk/minSdk.
	compileSdk {
		version = release(37)
	}
	
	defaultConfig {
		applicationId = "net.luis.sudoku"
		minSdk = 33
		targetSdk = 36
		versionCode = 1
		versionName = "1.0"
		
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}
	
	signingConfigs {
		if (hasReleaseKeystore) {
			create("release") {
				// Relative to this module, so `storeFile=release-keystore.jks` means `app/release-keystore.jks`.
				storeFile = file(keystoreProperties.getProperty("storeFile", ""))
				storePassword = keystoreProperties.getProperty("storePassword", "")
				keyAlias = keystoreProperties.getProperty("keyAlias", "")
				keyPassword = keystoreProperties.getProperty("keyPassword", "")
			}
		}
	}
	buildTypes {
		release {
			// R8 stays off. Hilt, Room, Ktor and kotlinx.serialization all rely on reflection or generated
			// code that shrinking would have to be told about rule by rule, and none of that is worth
			// debugging on a release build that is installed by hand.
			optimization {
				enable = false
			}
			// Unsigned without this, which is to say not installable. Left unset when there are no
			// credentials, so the build still runs and says so instead of failing while configuring.
			if (hasReleaseKeystore) {
				signingConfig = signingConfigs.getByName("release")
			}
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	buildFeatures {
		compose = true
	}
	testOptions {
		unitTests {
			isIncludeAndroidResources = true
		}
	}
}

ksp {
	// Exported schemas make Room migrations reviewable in diffs and testable with MigrationTestHelper.
	arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
	implementation(libs.sudoku.lib)

	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.material.icons.core)
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.navigation.compose)

	// DI
	implementation(libs.hilt.android)
	implementation(libs.androidx.hilt.navigation.compose)
	ksp(libs.hilt.compiler)

	// Persistence
	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.room.ktx)
	ksp(libs.androidx.room.compiler)
	implementation(libs.androidx.datastore.preferences)

	// Networking - no single-player path may touch any of this (feature-spec 9.1)
	implementation(libs.ktor.client.core)
	implementation(libs.ktor.client.okhttp)
	implementation(libs.ktor.client.content.negotiation)
	implementation(libs.ktor.client.websockets)
	implementation(libs.ktor.client.logging)
	implementation(libs.ktor.serialization.kotlinx.json)

	// Daily reminder
	implementation(libs.androidx.work.runtime.ktx)
	implementation(libs.androidx.hilt.work)
	ksp(libs.androidx.hilt.compiler)

	testImplementation(libs.junit)
	testImplementation(libs.ktor.client.mock)
	testImplementation(libs.androidx.room.testing)
	testImplementation(libs.robolectric)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(libs.androidx.junit)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	debugImplementation(libs.androidx.compose.ui.tooling)
}
