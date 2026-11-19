plugins {
    id("com.android.application") version "8.11.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
}

providers.gradleProperty("buildOutputRoot").orNull?.let { outputRoot ->
    allprojects {
        layout.buildDirectory.set(rootProject.file(outputRoot).resolve(project.name))
    }
}
