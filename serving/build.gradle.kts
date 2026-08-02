plugins {
    application
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// FFM - restricted API: с JDK 24 вызов без этого флага печатает предупреждение,
// в будущих выпусках станет ошибкой. Нужен всюду, где грузится lib_lightgbm,
// включая форки JMH.
val nativeAccess = "--enable-native-access=ALL-UNNAMED"

val jmh = sourceSets.create("jmh")

dependencies {
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    "jmhImplementation"(sourceSets.main.get().output)
    "jmhImplementation"(libs.jmh.core)
    "jmhAnnotationProcessor"(libs.jmh.generator)
}

application {
    mainClass = "io.github.danilkiff.lgbmserving.cli.ScorerMain"
    applicationDefaultJvmArgs = listOf(nativeAccess)
}

// Рабочий каталог - корень репозитория: пути к native/ и refs/ разрешаются
// подъёмом вверх от него, как ${SRCDIR} в Go-варианте.
val repoRoot = layout.projectDirectory.dir("..")

tasks.withType<JavaExec>().configureEach {
    workingDir = repoRoot.asFile
}

tasks.test {
    useJUnitPlatform()
    workingDir = repoRoot.asFile
    jvmArgs(nativeAccess)
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register<JavaExec>("jmh") {
    group = "verification"
    description = "бенчмарки JMH"
    classpath = jmh.runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    args("-jvmArgsAppend", nativeAccess)
    args(providers.gradleProperty("jmhArgs").orNull?.split(" ") ?: listOf<String>())
}

tasks.register<JavaExec>("dump") {
    group = "application"
    description = "дамп предсказаний в CSV (аргументы через -PdumpArgs=\"...\")"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.danilkiff.lgbmserving.cli.DumpMain"
    jvmArgs(nativeAccess)
    args(providers.gradleProperty("dumpArgs").orNull?.split(" ") ?: listOf<String>())
}
