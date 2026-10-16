plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21"
    `maven-publish`
}

version = "${property("mod_version")}+${property("minecraft_version")}"
group = property("maven_group")!!
base.archivesName.set("locomotion-fabric")

repositories {
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.terraformersmc.com/releases")
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    compileOnly("dev.isxander:yet-another-config-lib:${property("yacl_version")}")
    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
    if (providers.gradleProperty("configUi").orNull == "true") {
        runtimeOnly("dev.isxander:yet-another-config-lib:${property("yacl_version")}")
        runtimeOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
    }
    val compatMods = providers.gradleProperty("compatMods").orNull.orEmpty().split(",")
    if ("sodium" in compatMods || "iris" in compatMods) {
        runtimeOnly("maven.modrinth:sodium:mc26.3-0.9.2-fabric")
    }
    if ("iris" in compatMods) {
        runtimeOnly("maven.modrinth:iris:1.11.7+26.3-fabric")
    }
    if ("skinlayers" in compatMods) {
        runtimeOnly("maven.modrinth:3dskinlayers:G8BCRxv5")
    }
}

sourceSets.main {
    java.srcDir("fabric/src/main/java")
    resources.srcDir("fabric/src/main/resources")
    resources.exclude("architectury.common.json", "locomotion.accesswidener")
}

loom {
    runs.named("client") {
        programArgs("--username", "Dev")
        runDir("run")
    }
    runs.configureEach {
        if (name == "clientGameTest") {
            val backend = providers.gradleProperty("graphicsBackend").orElse("opengl").get()
            require(backend in listOf("opengl", "vulkan")) { "graphicsBackend must be opengl or vulkan" }
            vmArg("-Dlocomotion.test.backend=$backend")
            programArgs("--graphicsBackend", backend.uppercase())
        }
    }
}

tasks.processResources {
    val properties = mapOf(
        "mod_id" to "locomotion",
        "mod_group" to "com.trainguy9512",
        "mod_version" to project.version,
        "mod_name" to "Locomotion",
        "mod_description" to "An animation overhaul mod",
        "minecraft_version" to project.property("minecraft_version"),
        "fabric_loader_version" to project.property("loader_version"),
        "fabric_api_version" to project.property("fabric_api_version")
    )
    inputs.properties(properties)
    filesMatching(listOf("fabric.mod.json", "locomotion-fabric.mixins.json")) {
        expand(properties)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    from("LICENSE") { rename { "${it}_locomotion" } }
}

// Preserve the familiar build command for existing local workflows.
tasks.register("chiseledBuild") { dependsOn(tasks.build) }

fabricApi {
    configureTests {
        createSourceSet = true
        modId = "locomotion-test"
        enableGameTests = false
        enableClientGameTests = true
    }
}
