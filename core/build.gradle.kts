import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Module Kotlin Multiplatform : toute la logique métier (sans dépendance Android ni iOS),
// partagée entre l'app Android (cible jvm) et l'app iOS (cibles ios*).
// Tests JVM sur n'importe quelle machine : ./gradlew -p core jvmTest
// Compilation iOS (Mac, ou klib seulement sous Linux) : ./gradlew -p core compileKotlinIosSimulatorArm64
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "fr.mescourses"
version = "0.1.0"

// Les fichiers JSON de src/commonMain/resources sont recopiés en constantes Kotlin :
// iOS n'a pas de « classpath », et ce texte embarqué marche pareil sur toutes les cibles.
val embedResources by tasks.registering {
    val dir = layout.projectDirectory.dir("src/commonMain/resources/fr/mescourses/core")
    val out = layout.buildDirectory.dir("generated/embedded/kotlin")
    inputs.dir(dir)
    outputs.dir(out)
    doLast {
        val files = dir.asFileTree.matching { include("**/*.json") }.files.sortedBy { it.path }
        val body = files.joinToString("\n") { f ->
            val key = f.relativeTo(dir.asFile).invariantSeparatorsPath
            val text = f.readText()
                .replace("\\", "\\\\").replace("\"", "\\\"").replace("\$", "\\\$")
                .replace("\r", "").replace("\n", "\\n")
            // Découpe en morceaux : une constante de chaîne JVM est limitée à 65 535 octets.
            val parts = text.chunked(8000).joinToString(",\n            ") { "\"$it\"" }
            "        \"$key\" to listOf(\n            $parts,\n        ).joinToString(\"\"),"
        }
        val target = out.get().file("fr/mescourses/core/EmbeddedResources.kt").asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            |// Fichier généré par la tâche embedResources de core/build.gradle.kts : ne pas modifier.
            |package fr.mescourses.core
            |
            |internal object EmbeddedResources {
            |    private val texts: Map<String, String> by lazy {
            |        mapOf(
            |$body
            |        )
            |    }
            |
            |    /** Texte du fichier `src/commonMain/resources/fr/mescourses/core/<path>`, ou null s'il n'existe pas. */
            |    fun text(path: String): String? = texts[path]
            |}
            |""".trimMargin()
        )
    }
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            // Tests lents (performance) : ./gradlew -p core jvmTest -Dtests.lents=true
            systemProperty("tests.lents", providers.systemProperty("tests.lents").getOrElse("false"))
            testLogging { showStandardStreams = providers.systemProperty("tests.lents").getOrElse("false") == "true" }
        }
    }
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(embedResources)
            dependencies {
                api(libs.coroutines.core)
                api(libs.serialization.json)
                api(libs.ktor.client.core)
                api(libs.kotlinx.datetime)
                implementation(libs.ksoup)
                implementation(libs.atomicfu)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
    }
}

// Ancien nom de la tâche de tests (CI, habitudes) : ./gradlew -p core test
tasks.register("test") {
    group = "verification"
    description = "Lance les tests JVM du module core (alias de jvmTest)."
    dependsOn("jvmTest")
}
