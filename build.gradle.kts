plugins {
    kotlin("jvm") version "2.0.0"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "me.albert"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.2-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}


tasks {
    // Shadow JAR 配置
    shadowJar {
        // 最小化 JAR，只包含必要的类
        minimize()
        // 重定位 Kotlin 包，避免与其他插件冲突
        relocate("kotlin", "me.albert.network_hijack.libs.kotlin")
        // 可选：重定位其他依赖（如果需要）
        // relocate("org.jetbrains", "com.example.myplugin.jetbrains")
        // 确保 plugin.yml 等资源文件包含在 JAR 中
        archiveClassifier.set("") // 移除 -all 后缀
        destinationDirectory.set(file("$buildDir/libs")) // 输出目录
    }
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.20")
    }
}

val targetJavaVersion = 17
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
