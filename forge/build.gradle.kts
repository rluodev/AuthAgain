@file:Suppress("UnstableApiUsage")

import java.nio.file.FileSystems
import java.nio.file.Files

plugins {
    id("dev.architectury.loom")
    id("com.gradleup.shadow")
}

val p = stonecutter.properties
val minecraft: String = stonecutter.current.version
val modVersion: String = (findProperty("mod_version") as String?) ?: p.get<String>("mod.version")

group = p.get<String>("mod.group")
version = "$minecraft-$modVersion"
base {
    archivesName.set("${p.get<String>("mod.id")}-forge")
}

// Auth library is bundled and relocated into the jar.
val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

repositories {
    maven("https://maven.minecraftforge.net")
    maven("https://maven.lenni0451.net/releases")
    maven("https://repo.viaversion.com/")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    mappings(loom.officialMojangMappings())
    "forge"("net.minecraftforge:forge:$minecraft-${p.get<String>("deps.loader")}")

    // Supplies net.fabricmc.api.EnvType that is used by loom's Minecraft jar.
    compileOnly("net.fabricmc:fabric-loader:${p.get<String>("deps.fabric_loader")}")
    // javax.annotation.Nullable.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    implementation("net.raphimc:MinecraftAuth:${p.get<String>("deps.minecraftauth")}")
    shadowBundle("net.raphimc:MinecraftAuth:${p.get<String>("deps.minecraftauth")}") { isTransitive = true }

    testImplementation("org.junit.jupiter:junit-jupiter:${p.get<String>("deps.junit")}")
    testImplementation("org.assertj:assertj-core:${p.get<String>("deps.assertj")}")
    testImplementation("org.mockito:mockito-core:${p.get<String>("deps.mockito")}")
    testImplementation("org.mockito:mockito-junit-jupiter:${p.get<String>("deps.mockito")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    accessWidenerPath = rootProject.file("src/main/resources/authagain.accesswidener")
    forge.convertAccessWideners = true
}

java {
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveClassifier = "dev"
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
    relocate("net.raphimc.minecraftauth", "dev.rluo.authagain.shaded.minecraftauth")
    relocate("net.lenni0451.commons", "dev.rluo.authagain.shaded.lenni0451.commons")
    relocate("com.google.gson", "dev.rluo.authagain.shaded.gson")
    dependencies {
        exclude(dependency("com.google.errorprone:error_prone_annotations"))
    }
    exclude("module-info.class")
    // Drop relocated deps' versioned module descriptors; they name packages that no longer exist.
    exclude("META-INF/versions/**")
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
    exclude("fabric.mod.json", "architectury.common.json")
    exclude("META-INF/neoforge.mods.toml")
}

tasks.remapJar {
    injectAccessWidener = true
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
    // Shadow flags the jar Multi-Release from the bundled deps so we strip that
    doLast { stripMultiRelease(archiveFile.get().asFile) }
}

tasks.processResources {
    exclude("META-INF/neoforge.mods.toml")
    properties(
        listOf("META-INF/mods.toml", "pack.mcmeta"),
        "id" to p.get<String>("mod.id"),
        "name" to p.get<String>("mod.name"),
        "version" to p.get<String>("mod.version"),
        "license" to p.get<String>("mod.license"),
        "authors" to p.get<String>("mod.authors"),
        "description" to p.get<String>("mod.description"),
        "loader_version_range" to p.get<String>("mod.loader_range"),
        "forge_version_range" to p.get<String>("mod.deps_range"),
        "minecraft_version_range" to p.get<String>("mod.mc_dep"),
        "pack_format" to p.get<String>("mod.pack_format"),
    )
}

tasks.register<Copy>("buildAndCollect") {
    group = "versioned"
    from(tasks.remapJar.get().archiveFile)
    into(rootProject.layout.buildDirectory.file("libs/$modVersion/forge"))
    dependsOn("build")
}

// Removes the Multi-Release manifest attribute that shadow adds from the bundled deps.
fun stripMultiRelease(jar: java.io.File) {
    val env = mapOf<String, String>()
    FileSystems.newFileSystem(jar.toPath(), env).use { fs ->
        val manifest = fs.getPath("META-INF/MANIFEST.MF")
        if (Files.exists(manifest)) {
            val kept = Files.readAllLines(manifest)
                .filterNot { it.startsWith("Multi-Release", ignoreCase = true) }
            Files.write(manifest, (kept.joinToString("\r\n") + "\r\n").toByteArray())
        }
    }
}
