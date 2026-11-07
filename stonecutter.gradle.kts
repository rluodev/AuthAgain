import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.17.491" apply false
    id("com.gradleup.shadow") version "9.5.1" apply false
}
stonecutter active "1.20.1" /* [SC] DO NOT EDIT */

stonecutter parameters {
    val loader = branch.id
    properties {
        tags(current.version, loader)
    }
    constants {
        match(loader, "forge", "neoforge")
    }

    project.afterEvaluate {
        val build = extensions.getByType<StonecutterBuildExtension>()
        extensions.getByType<SourceSetContainer>().forEach { sourceSet ->
            val sharedSource = rootProject.file("src/${sourceSet.name}")
            val prepare = build.tasks.prepare.getValue(sourceSet.name)
            prepare.configure {
                root.set(sharedSource)
                source.setFrom(fileTree(sharedSource).matching(build.filters))
            }
            build.tasks.generate.getValue(sourceSet.name).configure {
                from(sharedSource) {
                    exclude {
                        !it.isDirectory && it.relativePath.getFile(prepare.get().destination.get().asFile).isFile
                    }
                }
            }
            if (build.current.isActive && !build.branch.location.resolve("src").toFile().isDirectory) {
                sourceSet.java.srcDir(sharedSource.resolve("java"))
                sourceSet.resources.srcDir(sharedSource.resolve("resources"))
            }
        }
    }
}

// Builds every loader/version node and gathers the jars under build/libs/<version>/<loader>.
tasks.register("chiseledBuildAndCollect") {
    group = "build"
    description = "Builds all loader/version variants and collects their jars."
    dependsOn(stonecutter.tasks.named("buildAndCollect"))
}

// Runs the test suite for every loader/version node.
tasks.register("chiseledTest") {
    group = "verification"
    description = "Runs the tests for all loader/version variants."
    dependsOn(stonecutter.tasks.named("test"))
}
