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
