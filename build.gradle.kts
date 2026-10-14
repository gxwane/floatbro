plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**", "**/third-party/**")
        ktlint("1.5.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint("1.5.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
    source.setFrom(
        files(
            "app/src/main/java",
            "core/common/src/main/java",
            "core/webview/src/main/java",
            "ad-filter/src/main/java",
            "adblock-client/src/main/java",
        ),
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
    }
    exclude("**/build/**", "**/.gradle/**", "**/third-party/**")
}

tasks.register("installGitHooks") {
    group = "quality"
    description = "Installs local Git pre-commit hooks for automated quality gates"
    doLast {
        val hooksDir = rootProject.file(".git/hooks")
        if (hooksDir.exists()) {
            val preCommitFile = file("$hooksDir/pre-commit")
            preCommitFile.writeText(
                """
                #!/bin/sh
                echo "Running pre-commit code style verification..."
                ./gradlew spotlessCheck --daemon
                if [ ${'$'}? -ne 0 ]; then
                    echo "Pre-commit check failed! Run './gradlew spotlessApply' to format code."
                    exit 1
                fi
                """.trimIndent() + "\n",
            )
            preCommitFile.setExecutable(true)
            println("Git pre-commit hook installed successfully at: ${preCommitFile.path}")
        } else {
            println("No .git/hooks directory found; skipped Git hook installation.")
        }
    }
}
