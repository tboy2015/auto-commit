import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.7.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(kotlin("test"))

    intellijPlatform {
        // 本地有 IDEA 安装路径就用本地（开发快）；否则从 JetBrains 仓库下载（CI 用）。
        val localPath = providers.gradleProperty("localIdePath").orNull?.takeIf { it.isNotBlank() }
        if (localPath != null) {
            local(file(localPath))
        } else {
            create(
                providers.gradleProperty("platformType").get(),
                providers.gradleProperty("platformVersion").get(),
            )
        }
        bundledPlugin("Git4Idea")
        bundledModule("intellij.platform.vcs.impl")
        bundledModule("intellij.platform.vcs.dvcs.impl")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    instrumentCode = false
    buildSearchableOptions = false

    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    // ---- 签名（仅当三个 env 都存在时生效，本地构建无害） ----
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // ---- Marketplace 发布（依赖 PUBLISH_TOKEN env） ----
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // 渠道：default = stable；可加 "beta"/"eap"
        channels = providers.gradleProperty("pluginChannel").map { listOf(it) }
            .orElse(listOf("default"))
    }

    // ---- 兼容性验证：扫描代码对各 IDE 版本是否有缺失符号 ----
    pluginVerification {
        ides {
            // 验证：本地装的 IDEA Ultimate 2026.1 + 平台 plugin 推荐的版本集合
            recommended()
        }
        freeArgs = listOf(
            "-mute", "TemplateWordInPluginName,ForbiddenPluginIdPrefix"
        )
    }
}

kotlin { jvmToolchain(21) }

tasks.test { useJUnitPlatform() }

tasks.named<JavaExec>("runIde") {
    // 本地 IDEA 启动需要把 nio-fs.jar 注入 boot classpath
    providers.gradleProperty("localIdePath").orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { jvmArgs("-Xbootclasspath/a:$it/lib/nio-fs.jar") }
}
