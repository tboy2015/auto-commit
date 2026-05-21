# AI Commit — IntelliJ IDEA Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an IntelliJ IDEA plugin that streams an AI-generated commit message into the built-in Commit dialog's message field, supporting OpenAI-compatible providers (cloud + local), multiple conventions and languages.

**Architecture:** Kotlin plugin on IntelliJ Platform 2023.2+, organized as `action → service → (diff | prompt | llm) → settings`. All providers use one `OpenAICompatibleClient` (OkHttp + SSE). Coroutines for async; PasswordSafe for keys; Notification group for all user-visible errors.

**Tech Stack:** Kotlin 1.9, IntelliJ Platform Gradle Plugin 2.x, OkHttp 4, kotlinx-coroutines, kotlinx-serialization-json, JUnit5, MockWebServer.

Spec: [docs/superpowers/specs/2026-05-21-aicommit-idea-plugin-design.md](../specs/2026-05-21-aicommit-idea-plugin-design.md)

---

## File Structure

```
auto-commit/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
├── src/main/kotlin/io/aicommit/
│   ├── action/GenerateAction.kt          # Commit toolbar button
│   ├── service/CommitMsgService.kt       # Orchestrator, cancel, EDT refill
│   ├── diff/DiffCollector.kt             # staged diff + files + recent commits
│   ├── diff/DiffPayload.kt               # data class
│   ├── prompt/PromptBuilder.kt           # render system+user prompt
│   ├── prompt/Templates.kt               # built-in conventions/templates
│   ├── prompt/Redactor.kt                # secret scrubbing
│   ├── prompt/Truncator.kt               # smart diff truncation
│   ├── llm/OpenAICompatibleClient.kt     # HTTP + SSE
│   ├── llm/SSEParser.kt                  # data: lines → chunks
│   ├── llm/LLMException.kt               # typed errors
│   ├── llm/ChatMessages.kt               # request/response DTOs
│   ├── settings/AppSettings.kt           # PersistentStateComponent
│   ├── settings/Provider.kt              # data class
│   ├── settings/SecretStore.kt           # PasswordSafe wrapper
│   ├── settings/SettingsConfigurable.kt  # Settings page
│   ├── settings/ProviderEditor.kt        # provider edit UI
│   ├── ui/Notifications.kt               # NotificationGroupManager wrapper
│   └── ui/ProviderStatusBarFactory.kt    # status bar widget
├── src/main/resources/
│   ├── META-INF/plugin.xml
│   ├── META-INF/pluginIcon.svg
│   └── messages/AICommitBundle.properties
└── src/test/kotlin/io/aicommit/
    ├── prompt/PromptBuilderTest.kt
    ├── prompt/RedactorTest.kt
    ├── prompt/TruncatorTest.kt
    ├── llm/SSEParserTest.kt
    └── llm/OpenAICompatibleClientTest.kt
```

---

## Phase 0: Repo & Build Skeleton

### Task 0.1: Initialize git repo and `.gitignore`

**Files:**
- Create: `.gitignore`

- [ ] **Step 1: Initialize repo**

```bash
cd /Users/tboy/work/codework/tools/auto-commit
git init -b main
```

- [ ] **Step 2: Write `.gitignore`**

```gitignore
.gradle/
build/
.idea/
*.iml
.DS_Store
local.properties
```

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git commit -m "chore: init repo"
```

### Task 0.2: Gradle build setup

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`

- [ ] **Step 1: `settings.gradle.kts`**

```kotlin
rootProject.name = "ai-commit"
```

- [ ] **Step 2: `gradle.properties`**

```properties
pluginGroup=io.aicommit
pluginName=AI Commit
pluginVersion=0.1.0
pluginSinceBuild=232
pluginUntilBuild=
platformType=IC
platformVersion=2023.2.6
platformPlugins=Git4Idea
kotlinVersion=1.9.25
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
```

- [ ] **Step 3: `build.gradle.kts`**

```kotlin
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
}

kotlin { jvmToolchain(17) }

tasks.test { useJUnitPlatform() }
```

- [ ] **Step 4: Verify build resolves**

```bash
./gradlew --no-daemon help
```
Expected: BUILD SUCCESSFUL (downloads platform on first run).

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties
git commit -m "build: gradle + intellij platform 2.x setup"
```

### Task 0.3: Minimal `plugin.xml` that loads

**Files:**
- Create: `src/main/resources/META-INF/plugin.xml`
- Create: `src/main/resources/messages/AICommitBundle.properties`

- [ ] **Step 1: `plugin.xml`**

```xml
<idea-plugin>
    <id>io.aicommit</id>
    <name>AI Commit</name>
    <vendor>aicommit</vendor>
    <description><![CDATA[Generate git commit messages with AI directly inside IDEA's Commit dialog.]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>Git4Idea</depends>

    <resource-bundle>messages.AICommitBundle</resource-bundle>

    <extensions defaultExtensionNs="com.intellij">
        <notificationGroup id="AI Commit" displayType="BALLOON"/>
    </extensions>
</idea-plugin>
```

- [ ] **Step 2: Bundle file**

```properties
action.aicommit.generate.text=Generate commit message with AI
action.aicommit.generate.description=Use AI to draft a commit message from the staged diff
notification.providerMissing=No AI provider configured. Open settings to add one.
```

- [ ] **Step 3: Verify plugin loads**

```bash
./gradlew runIde
```
Expected: IDEA opens with sandbox; check `Settings → Plugins → Installed` shows "AI Commit". Close.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources
git commit -m "feat: minimal plugin.xml that loads in sandbox IDE"
```

---

## Phase 1: Provider Settings + Secret Store (no UI yet)

### Task 1.1: `Provider` data class with serialization

**Files:**
- Create: `src/main/kotlin/io/aicommit/settings/Provider.kt`
- Test: `src/test/kotlin/io/aicommit/settings/ProviderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.aicommit.settings

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProviderTest {
    @Test
    fun `round-trips through json`() {
        val p = Provider(id = "x", name = "DeepSeek", baseUrl = "https://api.deepseek.com/v1", model = "deepseek-chat")
        val json = Json.encodeToString(Provider.serializer(), p)
        val back = Json.decodeFromString(Provider.serializer(), json)
        assertEquals(p, back)
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

```bash
./gradlew test --tests io.aicommit.settings.ProviderTest
```

- [ ] **Step 3: Implement**

```kotlin
package io.aicommit.settings

import kotlinx.serialization.Serializable

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double = 0.3,
    val maxTokens: Int = 512,
    val timeoutSec: Int = 60,
    val extraHeaders: Map<String, String> = emptyMap(),
)
```

- [ ] **Step 4: Test passes**, commit.

```bash
git add src/main/kotlin/io/aicommit/settings/Provider.kt src/test/kotlin/io/aicommit/settings/ProviderTest.kt
git commit -m "feat(settings): Provider data model"
```

### Task 1.2: `SecretStore` PasswordSafe wrapper

**Files:**
- Create: `src/main/kotlin/io/aicommit/settings/SecretStore.kt`

- [ ] **Step 1: Implement** (no unit test — depends on platform; covered manually)

```kotlin
package io.aicommit.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

object SecretStore {
    private fun attrs(providerId: String) =
        CredentialAttributes("aicommit:$providerId")

    fun set(providerId: String, key: String?) {
        PasswordSafe.instance.set(attrs(providerId), key?.let { Credentials("apikey", it) })
    }

    fun get(providerId: String): String? =
        PasswordSafe.instance.get(attrs(providerId))?.getPasswordAsString()

    fun clear(providerId: String) = set(providerId, null)
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/io/aicommit/settings/SecretStore.kt
git commit -m "feat(settings): PasswordSafe-backed SecretStore"
```

### Task 1.3: `AppSettings` PersistentStateComponent

**Files:**
- Create: `src/main/kotlin/io/aicommit/settings/AppSettings.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Implement state**

```kotlin
package io.aicommit.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "AICommitSettings", storages = [Storage("aicommit.xml")])
@Service(Service.Level.APP)
class AppSettings : PersistentStateComponent<AppSettings.State> {
    data class State(
        var providers: MutableList<Provider> = mutableListOf(),
        var activeProviderId: String? = null,
        var convention: String = "conventional",
        var language: String = "English",
        var includeRecentCommits: Boolean = true,
        var recentCommitCount: Int = 5,
        var includeFilePaths: Boolean = true,
        var maxDiffChars: Int = 12000,
        var clearMessageBeforeGenerate: Boolean = true,
        var redactSecrets: Boolean = true,
        var customSystemPrompt: String? = null,
        var customUserTemplate: String? = null,
    )

    private var state = State()
    override fun getState(): State = state
    override fun loadState(s: State) { XmlSerializerUtil.copyBean(s, state) }

    fun activeProvider(): Provider? =
        state.activeProviderId?.let { id -> state.providers.firstOrNull { it.id == id } }

    companion object {
        fun get(): AppSettings = service()
    }
}
```

- [ ] **Step 2: Register in plugin.xml** — inside `<extensions>` add:

```xml
<applicationService serviceImplementation="io.aicommit.settings.AppSettings"/>
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew compileKotlin
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/aicommit/settings/AppSettings.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat(settings): AppSettings persistent state"
```

---

## Phase 2: LLM Client (TDD against MockWebServer)

### Task 2.1: DTOs

**Files:**
- Create: `src/main/kotlin/io/aicommit/llm/ChatMessages.kt`

- [ ] **Step 1: Implement**

```kotlin
package io.aicommit.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = true,
    val temperature: Double = 0.3,
    @SerialName("max_tokens") val maxTokens: Int = 512,
)

@Serializable
data class StreamDelta(val content: String? = null)

@Serializable
data class StreamChoice(val delta: StreamDelta = StreamDelta(), val index: Int = 0)

@Serializable
data class StreamChunk(val choices: List<StreamChoice> = emptyList())
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/io/aicommit/llm/ChatMessages.kt
git commit -m "feat(llm): OpenAI-compatible chat DTOs"
```

### Task 2.2: `LLMException` typed errors

**Files:**
- Create: `src/main/kotlin/io/aicommit/llm/LLMException.kt`

- [ ] **Step 1: Implement**

```kotlin
package io.aicommit.llm

sealed class LLMException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause) {
    class Auth(msg: String) : LLMException(msg)
    class RateLimited(msg: String) : LLMException(msg)
    class ContextTooLong(msg: String) : LLMException(msg)
    class Network(msg: String, cause: Throwable?) : LLMException(msg, cause)
    class BadResponse(msg: String) : LLMException(msg)
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/io/aicommit/llm/LLMException.kt
git commit -m "feat(llm): typed exceptions"
```

### Task 2.3: `SSEParser` (pure, TDD)

**Files:**
- Create: `src/main/kotlin/io/aicommit/llm/SSEParser.kt`
- Test: `src/test/kotlin/io/aicommit/llm/SSEParserTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package io.aicommit.llm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SSEParserTest {
    @Test
    fun `extracts content deltas and stops on DONE`() {
        val lines = listOf(
            """data: {"choices":[{"delta":{"content":"Hello"}}]}""",
            """data: {"choices":[{"delta":{"content":" world"}}]}""",
            "data: [DONE]",
        )
        val out = mutableListOf<String>()
        SSEParser.parse(lines.asSequence()) { out += it }
        assertEquals(listOf("Hello", " world"), out)
    }

    @Test
    fun `ignores comment and blank lines`() {
        val lines = listOf("", ":heartbeat", """data: {"choices":[{"delta":{"content":"x"}}]}""", "data: [DONE]")
        val out = mutableListOf<String>()
        SSEParser.parse(lines.asSequence()) { out += it }
        assertEquals(listOf("x"), out)
    }
}
```

- [ ] **Step 2: Run, FAIL**

```bash
./gradlew test --tests io.aicommit.llm.SSEParserTest
```

- [ ] **Step 3: Implement**

```kotlin
package io.aicommit.llm

import kotlinx.serialization.json.Json

object SSEParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(lines: Sequence<String>, onChunk: (String) -> Unit) {
        for (raw in lines) {
            val line = raw.trimEnd('\r')
            if (line.isEmpty() || line.startsWith(":")) continue
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") return
            val chunk = runCatching { json.decodeFromString(StreamChunk.serializer(), payload) }.getOrNull() ?: continue
            chunk.choices.firstOrNull()?.delta?.content?.let(onChunk)
        }
    }
}
```

- [ ] **Step 4: Tests pass, commit**

```bash
./gradlew test --tests io.aicommit.llm.SSEParserTest
git add src/main/kotlin/io/aicommit/llm/SSEParser.kt src/test/kotlin/io/aicommit/llm/SSEParserTest.kt
git commit -m "feat(llm): SSE line parser"
```

### Task 2.4: `OpenAICompatibleClient` happy-path test

**Files:**
- Create: `src/main/kotlin/io/aicommit/llm/OpenAICompatibleClient.kt`
- Test: `src/test/kotlin/io/aicommit/llm/OpenAICompatibleClientTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package io.aicommit.llm

import io.aicommit.settings.Provider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OpenAICompatibleClientTest {
    private lateinit var server: MockWebServer

    @BeforeEach fun setUp() { server = MockWebServer(); server.start() }
    @AfterEach fun tearDown() { server.shutdown() }

    private fun provider() = Provider(
        id = "t", name = "t",
        baseUrl = server.url("/v1").toString(),
        model = "m",
    )

    @Test
    fun `streams chunks from sse`() = runTest {
        val body = """
            data: {"choices":[{"delta":{"content":"Hel"}}]}

            data: {"choices":[{"delta":{"content":"lo"}}]}

            data: [DONE]

        """.trimIndent()
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))

        val client = OpenAICompatibleClient(provider(), apiKey = "k")
        val msgs = listOf(ChatMessage("user", "hi"))
        val chunks = client.stream(msgs).toList()
        assertEquals(listOf("Hel", "lo"), chunks)
    }
}
```

- [ ] **Step 2: Run, FAIL**

```bash
./gradlew test --tests io.aicommit.llm.OpenAICompatibleClientTest
```

- [ ] **Step 3: Implement**

```kotlin
package io.aicommit.llm

import io.aicommit.settings.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class OpenAICompatibleClient(
    private val provider: Provider,
    private val apiKey: String?,
    private val httpFactory: () -> OkHttpClient = { defaultHttp(provider) },
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun stream(messages: List<ChatMessage>): Flow<String> = flow {
        val req = ChatRequest(
            model = provider.model,
            messages = messages,
            stream = true,
            temperature = provider.temperature,
            maxTokens = provider.maxTokens,
        )
        val body = json.encodeToString(ChatRequest.serializer(), req)
            .toRequestBody("application/json".toMediaType())

        val builder = Request.Builder()
            .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
            .post(body)
            .header("Accept", "text/event-stream")
        if (!apiKey.isNullOrBlank()) builder.header("Authorization", "Bearer $apiKey")
        provider.extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        val call = httpFactory().newCall(builder.build())
        val response = try { call.execute() } catch (e: IOException) {
            throw LLMException.Network("network error: ${e.message}", e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty()
                throw when (resp.code) {
                    401, 403 -> LLMException.Auth("auth failed: ${resp.code}")
                    429 -> LLMException.RateLimited("rate limited")
                    400 -> if (errBody.contains("context", true) || errBody.contains("length", true))
                        LLMException.ContextTooLong(errBody) else LLMException.BadResponse(errBody)
                    else -> LLMException.BadResponse("http ${resp.code}: ${errBody.take(500)}")
                }
            }
            val source = resp.body?.source() ?: throw LLMException.BadResponse("empty body")
            val seq = sequence {
                while (!source.exhausted()) yield(source.readUtf8Line() ?: break)
            }
            val chunks = mutableListOf<String>()
            SSEParser.parse(seq) { chunks += it }
            for (c in chunks) emit(c)
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        fun defaultHttp(p: Provider): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(p.timeoutSec.toLong(), TimeUnit.SECONDS)
            .writeTimeout(p.timeoutSec.toLong(), TimeUnit.SECONDS)
            .build()
    }
}
```

> Note: the simple `mutableListOf` buffer above keeps the happy-path test deterministic. We refactor to true streaming emission in Task 2.6.

- [ ] **Step 4: Test passes**

```bash
./gradlew test --tests io.aicommit.llm.OpenAICompatibleClientTest
```

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/io/aicommit/llm/OpenAICompatibleClient.kt src/test/kotlin/io/aicommit/llm/OpenAICompatibleClientTest.kt
git commit -m "feat(llm): OpenAI-compatible client with SSE happy path"
```

### Task 2.5: Error mapping tests

**Files:**
- Modify: `src/test/kotlin/io/aicommit/llm/OpenAICompatibleClientTest.kt`

- [ ] **Step 1: Add failing tests**

```kotlin
import org.junit.jupiter.api.assertThrows

@Test
fun `401 maps to Auth`() = runTest {
    server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))
    val client = OpenAICompatibleClient(provider(), apiKey = "bad")
    assertThrows<LLMException.Auth> { client.stream(listOf(ChatMessage("user","x"))).toList() }
}

@Test
fun `429 maps to RateLimited`() = runTest {
    server.enqueue(MockResponse().setResponseCode(429))
    val client = OpenAICompatibleClient(provider(), apiKey = "k")
    assertThrows<LLMException.RateLimited> { client.stream(listOf(ChatMessage("user","x"))).toList() }
}

@Test
fun `400 context length maps to ContextTooLong`() = runTest {
    server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"context length exceeded"}}"""))
    val client = OpenAICompatibleClient(provider(), apiKey = "k")
    assertThrows<LLMException.ContextTooLong> { client.stream(listOf(ChatMessage("user","x"))).toList() }
}
```

- [ ] **Step 2: Run — should already PASS** given Task 2.4 implementation. If any fail, fix the mapping in `OpenAICompatibleClient`.

```bash
./gradlew test --tests io.aicommit.llm.OpenAICompatibleClientTest
```

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/io/aicommit/llm/OpenAICompatibleClientTest.kt
git commit -m "test(llm): error mapping coverage"
```

### Task 2.6: True streaming emission (refactor)

**Files:**
- Modify: `src/main/kotlin/io/aicommit/llm/OpenAICompatibleClient.kt`

- [ ] **Step 1: Replace the buffered loop with per-chunk emission**

Replace the block:

```kotlin
            val chunks = mutableListOf<String>()
            SSEParser.parse(seq) { chunks += it }
            for (c in chunks) emit(c)
```

with:

```kotlin
            for (line in seq) {
                val trimmed = line.trimEnd('\r')
                if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                if (!trimmed.startsWith("data:")) continue
                val payload = trimmed.removePrefix("data:").trim()
                if (payload == "[DONE]") return@use
                val chunk = runCatching { json.decodeFromString(StreamChunk.serializer(), payload) }.getOrNull() ?: continue
                chunk.choices.firstOrNull()?.delta?.content?.let { emit(it) }
            }
```

- [ ] **Step 2: Tests still pass**

```bash
./gradlew test --tests io.aicommit.llm.OpenAICompatibleClientTest
```

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/io/aicommit/llm/OpenAICompatibleClient.kt
git commit -m "refactor(llm): emit SSE chunks as they arrive"
```

---

## Phase 3: Prompt System

### Task 3.1: `Redactor`

**Files:**
- Create: `src/main/kotlin/io/aicommit/prompt/Redactor.kt`
- Test: `src/test/kotlin/io/aicommit/prompt/RedactorTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package io.aicommit.prompt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedactorTest {
    @Test
    fun `masks key=value secrets`() {
        val out = Redactor.scrub("api_key=abcd1234\nfoo=bar")
        assertEquals("api_key=***\nfoo=bar", out)
    }

    @Test
    fun `drops content of dotenv-like hunks by path`() {
        val diff = """
            diff --git a/.env b/.env
            +SECRET=hunter2
            diff --git a/src/Foo.kt b/src/Foo.kt
            +val x = 1
        """.trimIndent()
        val out = Redactor.scrub(diff)
        assertTrue(out.contains("[redacted: .env]"))
        assertTrue(out.contains("val x = 1"))
        assertTrue(!out.contains("hunter2"))
    }
}
```

- [ ] **Step 2: Run, FAIL.**

- [ ] **Step 3: Implement**

```kotlin
package io.aicommit.prompt

object Redactor {
    private val sensitivePathRegex = Regex("""diff --git a/(\S+) b/\1""")
    private val sensitiveFile = Regex(""".*(\.env(\..+)?|\.pem|id_rsa|credentials\.json)$""")
    private val kvRegex = Regex("""(?i)(password|passwd|secret|token|api[_-]?key)\s*=\s*\S+""")

    fun scrub(diff: String): String {
        val out = StringBuilder()
        val sections = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null
        for (line in diff.lines()) {
            if (line.startsWith("diff --git ")) {
                current = mutableListOf(line).also { sections += it }
            } else {
                if (current == null) { current = mutableListOf<String>().also { sections += it } }
                current.add(line)
            }
        }
        for ((i, section) in sections.withIndex()) {
            val header = section.firstOrNull().orEmpty()
            val path = sensitivePathRegex.find(header)?.groupValues?.get(1)
            if (path != null && sensitiveFile.matches(path)) {
                out.append(header).append('\n').append("[redacted: $path]")
            } else {
                section.joinTo(out, separator = "\n") { kvRegex.replace(it) { m -> "${m.groupValues[1]}=***" } }
            }
            if (i < sections.lastIndex) out.append('\n')
        }
        return out.toString()
    }
}
```

- [ ] **Step 4: Tests pass, commit**

```bash
./gradlew test --tests io.aicommit.prompt.RedactorTest
git add src/main/kotlin/io/aicommit/prompt/Redactor.kt src/test/kotlin/io/aicommit/prompt/RedactorTest.kt
git commit -m "feat(prompt): secret redaction"
```

### Task 3.2: `Truncator`

**Files:**
- Create: `src/main/kotlin/io/aicommit/prompt/Truncator.kt`
- Test: `src/test/kotlin/io/aicommit/prompt/TruncatorTest.kt`

- [ ] **Step 1: Failing test**

```kotlin
package io.aicommit.prompt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TruncatorTest {
    @Test
    fun `under limit untouched`() {
        val r = Truncator.truncate("short diff", maxChars = 100)
        assertEquals("short diff", r.text)
        assertEquals(false, r.truncated)
    }

    @Test
    fun `over limit keeps head and tail per file`() {
        val big = buildString {
            append("diff --git a/A.kt b/A.kt\n")
            repeat(200) { append("+line$it\n") }
            append("diff --git a/B.kt b/B.kt\n")
            repeat(200) { append("+lineB$it\n") }
        }
        val r = Truncator.truncate(big, maxChars = 400)
        assertTrue(r.truncated)
        assertTrue(r.text.contains("a/A.kt"))
        assertTrue(r.text.contains("a/B.kt"))
        assertTrue(r.text.contains("... truncated ..."))
        assertTrue(r.text.length <= 600) // allow header overhead
    }
}
```

- [ ] **Step 2: Run, FAIL.**

- [ ] **Step 3: Implement**

```kotlin
package io.aicommit.prompt

data class TruncationResult(val text: String, val truncated: Boolean)

object Truncator {
    fun truncate(diff: String, maxChars: Int): TruncationResult {
        if (diff.length <= maxChars) return TruncationResult(diff, false)

        val files = splitByFile(diff)
        val budgetPerFile = (maxChars / files.size).coerceAtLeast(120)
        val head = budgetPerFile / 2
        val tail = budgetPerFile - head

        val sb = StringBuilder()
        for (f in files) {
            if (f.length <= budgetPerFile) {
                sb.append(f)
            } else {
                sb.append(f.take(head))
                sb.append("\n... truncated ...\n")
                sb.append(f.takeLast(tail))
            }
            sb.append('\n')
        }
        return TruncationResult(sb.toString().trimEnd(), true)
    }

    private fun splitByFile(diff: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        for (line in diff.lines()) {
            if (line.startsWith("diff --git ") && current.isNotEmpty()) {
                parts += current.toString(); current.clear()
            }
            current.appendLine(line)
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }
}
```

- [ ] **Step 4: Tests pass, commit**

```bash
./gradlew test --tests io.aicommit.prompt.TruncatorTest
git add src/main/kotlin/io/aicommit/prompt/Truncator.kt src/test/kotlin/io/aicommit/prompt/TruncatorTest.kt
git commit -m "feat(prompt): diff truncator"
```

### Task 3.3: `Templates` (built-in conventions)

**Files:**
- Create: `src/main/kotlin/io/aicommit/prompt/Templates.kt`

- [ ] **Step 1: Implement**

```kotlin
package io.aicommit.prompt

object Templates {
    data class Convention(val id: String, val displayName: String, val systemPrompt: String)

    val conventions: List<Convention> = listOf(
        Convention("conventional", "Conventional Commits",
            """You write git commit messages strictly following Conventional Commits 1.0.0.
            |Format: `<type>(<scope>): <subject>` then a blank line then optional body.
            |Allowed types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert.
            |Subject: imperative mood, no trailing period, <= 72 chars.
            |Output ONLY the commit message, no markdown fences, no explanations.""".trimMargin()),
        Convention("conventional-emoji", "Conventional + Gitmoji",
            """Same rules as Conventional Commits, but prefix the subject with the matching gitmoji
            |(e.g. ✨ for feat, 🐛 for fix, 📝 for docs, ♻️ for refactor, ⚡ for perf, ✅ for test).
            |Output ONLY the commit message.""".trimMargin()),
        Convention("gitmoji", "Gitmoji only",
            """You write git commit messages prefixed with a gitmoji that matches the change.
            |Single-line subject preferred, body optional. Output ONLY the message.""".trimMargin()),
        Convention("simple", "Simple",
            """Write a clear, concise git commit message: one imperative subject line (<=72 chars),
            |optional body explaining the why. Output ONLY the message.""".trimMargin()),
    )

    const val DEFAULT_USER_TEMPLATE: String = """Generate a git commit message in {{language}}.

Recent commit style for reference:
{{recent_commits}}

Changed files:
{{files}}

Diff{{#truncated}} (truncated){{/truncated}}:
{{diff}}
"""

    fun systemFor(id: String, custom: String?): String =
        if (id == "custom" && !custom.isNullOrBlank()) custom
        else (conventions.firstOrNull { it.id == id } ?: conventions[0]).systemPrompt
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/io/aicommit/prompt/Templates.kt
git commit -m "feat(prompt): built-in convention templates"
```

### Task 3.4: `PromptBuilder`

**Files:**
- Create: `src/main/kotlin/io/aicommit/diff/DiffPayload.kt`
- Create: `src/main/kotlin/io/aicommit/prompt/PromptBuilder.kt`
- Test: `src/test/kotlin/io/aicommit/prompt/PromptBuilderTest.kt`

- [ ] **Step 1: `DiffPayload`**

```kotlin
package io.aicommit.diff

data class DiffPayload(
    val diff: String,
    val files: List<String>,
    val recentCommits: List<String>,
    val branch: String,
)
```

- [ ] **Step 2: Failing test**

```kotlin
package io.aicommit.prompt

import io.aicommit.diff.DiffPayload
import io.aicommit.llm.ChatMessage
import io.aicommit.settings.AppSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {
    private fun settings() = AppSettings.State(
        convention = "simple",
        language = "中文",
        includeRecentCommits = true,
        recentCommitCount = 2,
        includeFilePaths = true,
        maxDiffChars = 1000,
        redactSecrets = true,
    )

    @Test
    fun `renders system + user with variables`() {
        val payload = DiffPayload(
            diff = "diff --git a/A.kt b/A.kt\n+val x = 1\n",
            files = listOf("A.kt"),
            recentCommits = listOf("feat: a", "fix: b"),
            branch = "main",
        )
        val msgs: List<ChatMessage> = PromptBuilder.build(payload, settings(), userTemplate = null)
        assertEquals(2, msgs.size)
        assertEquals("system", msgs[0].role)
        assertEquals("user", msgs[1].role)
        val u = msgs[1].content
        assertTrue(u.contains("中文"))
        assertTrue(u.contains("A.kt"))
        assertTrue(u.contains("feat: a"))
        assertTrue(u.contains("val x = 1"))
    }

    @Test
    fun `marks diff truncated`() {
        val big = "diff --git a/A.kt b/A.kt\n" + "+x\n".repeat(2000)
        val payload = DiffPayload(big, listOf("A.kt"), emptyList(), "main")
        val s = settings().also { it.maxDiffChars = 200 }
        val msgs = PromptBuilder.build(payload, s, userTemplate = null)
        assertTrue(msgs[1].content.contains("(truncated)"))
    }
}
```

- [ ] **Step 3: Run, FAIL.**

- [ ] **Step 4: Implement**

```kotlin
package io.aicommit.prompt

import io.aicommit.diff.DiffPayload
import io.aicommit.llm.ChatMessage
import io.aicommit.settings.AppSettings

object PromptBuilder {
    fun build(
        payload: DiffPayload,
        s: AppSettings.State,
        userTemplate: String?,
    ): List<ChatMessage> {
        val system = Templates.systemFor(s.convention, s.customSystemPrompt)

        val cleaned = if (s.redactSecrets) Redactor.scrub(payload.diff) else payload.diff
        val tr = Truncator.truncate(cleaned, s.maxDiffChars)

        val template = (s.customUserTemplate ?: userTemplate ?: Templates.DEFAULT_USER_TEMPLATE)
        val recent = if (s.includeRecentCommits)
            payload.recentCommits.take(s.recentCommitCount).joinToString("\n") { "- $it" }
        else ""
        val files = if (s.includeFilePaths) payload.files.joinToString("\n") { "- $it" } else ""

        val rendered = template
            .replace("{{language}}", s.language)
            .replace("{{recent_commits}}", recent.ifBlank { "(none)" })
            .replace("{{files}}", files.ifBlank { "(omitted)" })
            .replace("{{diff}}", tr.text)
            .replace("{{branch}}", payload.branch)
            .replace("{{#truncated}}", if (tr.truncated) "" else "<!--")
            .replace("{{/truncated}}", if (tr.truncated) "" else "-->")

        return listOf(ChatMessage("system", system), ChatMessage("user", rendered))
    }
}
```

- [ ] **Step 5: Tests pass, commit**

```bash
./gradlew test --tests io.aicommit.prompt.PromptBuilderTest
git add src/main/kotlin/io/aicommit/diff/DiffPayload.kt src/main/kotlin/io/aicommit/prompt/PromptBuilder.kt src/test/kotlin/io/aicommit/prompt/PromptBuilderTest.kt
git commit -m "feat(prompt): builder with templates+truncation+redaction"
```

---

## Phase 4: Diff Collection (platform integration)

### Task 4.1: `DiffCollector` using Git4Idea

**Files:**
- Create: `src/main/kotlin/io/aicommit/diff/DiffCollector.kt`

- [ ] **Step 1: Implement**

```kotlin
package io.aicommit.diff

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.patch.IdeaTextPatchBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.history.GitHistoryUtils
import java.io.StringWriter

@Service(Service.Level.PROJECT)
class DiffCollector(private val project: Project) {

    fun collect(changes: List<Change>): DiffPayload {
        val diff = renderUnifiedDiff(changes)
        val files = changes.mapNotNull { it.afterRevision?.file?.path ?: it.beforeRevision?.file?.path }
        val branch = currentBranch()
        val recent = recentCommitSubjects(limit = 10)
        return DiffPayload(diff = diff, files = files, recentCommits = recent, branch = branch)
    }

    private fun renderUnifiedDiff(changes: List<Change>): String {
        if (changes.isEmpty()) return ""
        val basePath = project.basePath ?: return ""
        val baseDir: VirtualFile = VcsUtil.getVirtualFile(basePath) ?: return ""
        val patches = IdeaTextPatchBuilder.buildPatch(project, changes, baseDir.toNioPath(), false, false)
        val sw = StringWriter()
        for (p in patches) {
            sw.append("diff --git a/${p.beforeName} b/${p.afterName}\n")
            sw.append("--- a/${p.beforeName}\n+++ b/${p.afterName}\n")
            for (h in p.hunks) {
                sw.append("@@ -${h.startLineBefore},${h.endLineBefore - h.startLineBefore} ")
                  .append("+${h.startLineAfter},${h.endLineAfter - h.startLineAfter} @@\n")
                for (line in h.lines) {
                    val prefix = when (line.type) {
                        com.intellij.diff.util.DiffUtil.Side.LEFT, com.intellij.openapi.diff.impl.patch.PatchLine.Type.REMOVE -> "-"
                        com.intellij.openapi.diff.impl.patch.PatchLine.Type.ADD -> "+"
                        else -> " "
                    }
                    sw.append(prefix).append(line.text).append('\n')
                }
            }
        }
        return sw.toString()
    }

    private fun currentBranch(): String {
        val repo = GitUtil.getRepositoryManager(project).repositories.firstOrNull() ?: return ""
        return repo.currentBranchName.orEmpty()
    }

    private fun recentCommitSubjects(limit: Int): List<String> {
        val repo = GitUtil.getRepositoryManager(project).repositories.firstOrNull() ?: return emptyList()
        return try {
            GitHistoryUtils.history(project, repo.root, "-n", limit.toString())
                .map { it.fullMessage.lineSequence().first() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
```

> **Note:** the unified-diff rendering above may need adjustment to current Platform 2023.2 API. If `PatchLine` import paths differ, look up the actual class with `gradle idea` then in IDE search "PatchLine". The function is a pure helper — fix imports until it compiles.

- [ ] **Step 2: Register service in `plugin.xml`** — add inside `<extensions>`:

```xml
<projectService serviceImplementation="io.aicommit.diff.DiffCollector"/>
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew compileKotlin
```

If imports fail, simplify by using `IdeaTextPatchBuilder.buildPatch(...)` + `UnifiedDiffWriter.write(...)` from the platform API instead. (Search platform sources for `UnifiedDiffWriter`.)

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/aicommit/diff/DiffCollector.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat(diff): collect staged diff + files + recent commits"
```

---

## Phase 5: Service, Action, Notifications

### Task 5.1: `Notifications` helper

**Files:**
- Create: `src/main/kotlin/io/aicommit/ui/Notifications.kt`

- [ ] **Step 1: Implement**

```kotlin
package io.aicommit.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object Notifications {
    private fun group() = NotificationGroupManager.getInstance().getNotificationGroup("AI Commit")
    fun info(project: Project?, content: String) =
        group().createNotification(content, NotificationType.INFORMATION).notify(project)
    fun warn(project: Project?, content: String) =
        group().createNotification(content, NotificationType.WARNING).notify(project)
    fun error(project: Project?, content: String) =
        group().createNotification(content, NotificationType.ERROR).notify(project)
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/io/aicommit/ui/Notifications.kt
git commit -m "feat(ui): Notifications wrapper"
```

### Task 5.2: `CommitMsgService`

**Files:**
- Create: `src/main/kotlin/io/aicommit/service/CommitMsgService.kt`
- Modify: `src/main/resources/META-INF/plugin.xml` (register `projectService`)

- [ ] **Step 1: Implement**

```kotlin
package io.aicommit.service

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ui.CommitMessage
import io.aicommit.diff.DiffCollector
import io.aicommit.llm.LLMException
import io.aicommit.llm.OpenAICompatibleClient
import io.aicommit.prompt.PromptBuilder
import io.aicommit.settings.AppSettings
import io.aicommit.settings.SecretStore
import io.aicommit.ui.Notifications
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

@Service(Service.Level.PROJECT)
class CommitMsgService(private val project: Project) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var current: Job? = null

    val isGenerating: Boolean get() = current?.isActive == true

    fun cancel() { current?.cancel() }

    fun generate(messageUi: CommitMessage, changes: List<Change>) {
        if (isGenerating) { cancel(); return }
        val settings = AppSettings.get()
        val provider = settings.activeProvider()
        if (provider == null) {
            Notifications.warn(project, "No AI provider configured. Open Settings → Tools → AI Commit.")
            return
        }
        if (changes.isEmpty()) {
            Notifications.warn(project, "No staged changes."); return
        }

        current = scope.launch {
            try {
                val payload = project.service<DiffCollector>().collect(changes)
                val msgs = PromptBuilder.build(payload, settings.state, userTemplate = null)
                val key = SecretStore.get(provider.id)
                val client = OpenAICompatibleClient(provider, key)

                val truncated = msgs[1].content.contains("(truncated)")
                if (truncated) withContext(Dispatchers.EDT) {
                    Notifications.info(project, "Diff was truncated to fit model context.")
                }

                withContext(Dispatchers.EDT) {
                    if (settings.state.clearMessageBeforeGenerate) replaceMessage(messageUi, "")
                }

                client.stream(msgs).collect { chunk ->
                    withContext(Dispatchers.EDT) { appendMessage(messageUi, chunk) }
                }
            } catch (_: CancellationException) {
                // user-initiated, silent
            } catch (e: LLMException.Auth) {
                Notifications.error(project, "Auth failed: check API key.")
            } catch (e: LLMException.RateLimited) {
                Notifications.warn(project, "Rate limited, please retry shortly.")
            } catch (e: LLMException.ContextTooLong) {
                Notifications.warn(project, "Diff is still too long for the model. Consider splitting your commit.")
            } catch (e: LLMException.Network) {
                Notifications.error(project, "Network error: ${e.message}")
            } catch (e: LLMException.BadResponse) {
                Notifications.error(project, "Model error: ${e.message?.take(200)}")
            } catch (e: Throwable) {
                Notifications.error(project, "Unexpected: ${e.message}")
            }
        }
    }

    private fun appendMessage(ui: CommitMessage, chunk: String) {
        CommandProcessor.getInstance().executeCommand(project, {
            ui.setCommitMessage(ui.comment + chunk)
        }, "AI Commit Append", "ai-commit")
    }

    private fun replaceMessage(ui: CommitMessage, text: String) {
        CommandProcessor.getInstance().executeCommand(project, {
            ui.setCommitMessage(text)
        }, "AI Commit Reset", "ai-commit")
    }
}
```

- [ ] **Step 2: Register**

In `plugin.xml` `<extensions>` add:

```xml
<projectService serviceImplementation="io.aicommit.service.CommitMsgService"/>
```

- [ ] **Step 3: Compile**

```bash
./gradlew compileKotlin
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/aicommit/service/CommitMsgService.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat(service): orchestrator with streaming refill and cancel"
```

### Task 5.3: `GenerateAction` registered on Commit toolbar

**Files:**
- Create: `src/main/kotlin/io/aicommit/action/GenerateAction.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Implement**

```kotlin
package io.aicommit.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vcs.ui.Refreshable
import io.aicommit.service.CommitMsgService
import io.aicommit.settings.AppSettings

class GenerateAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val ui = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage
        val panel = e.getData(Refreshable.PANEL_KEY)
        val hasChanges = (panel as? com.intellij.openapi.vcs.changes.CommitContext) != null ||
                e.getData(VcsDataKeys.CHANGES)?.isNotEmpty() == true
        val hasProvider = project != null && AppSettings.get().activeProvider() != null
        e.presentation.isEnabled = project != null && ui != null && hasChanges && hasProvider
        e.presentation.text = if (project != null && project.service<CommitMsgService>().isGenerating)
            "Stop generating" else "Generate commit message with AI"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ui = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage ?: return
        val changes = e.getData(VcsDataKeys.CHANGES)?.toList().orEmpty()
        project.service<CommitMsgService>().generate(ui, changes)
    }
}
```

- [ ] **Step 2: Register action on commit toolbar**

In `plugin.xml`, add an `<actions>` block:

```xml
<actions>
    <action id="AICommit.Generate"
            class="io.aicommit.action.GenerateAction"
            icon="AllIcons.Actions.Lightning">
        <add-to-group group-id="Vcs.MessageActionGroup" anchor="first"/>
    </action>
</actions>
```

- [ ] **Step 3: Manual smoke test**

```bash
./gradlew runIde
```
In sandbox: open any git project, stage a file, open Commit panel. The lightning button should appear in the message toolbar. Without a provider configured, it should be disabled.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/aicommit/action/GenerateAction.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat(action): commit-toolbar Generate button"
```

---

## Phase 6: Settings UI

### Task 6.1: `ProviderEditor` form

**Files:**
- Create: `src/main/kotlin/io/aicommit/settings/ProviderEditor.kt`

- [ ] **Step 1: Implement**

```kotlin
package io.aicommit.settings

import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class ProviderEditor(private var provider: Provider) {
    private val name = JBTextField(provider.name)
    private val baseUrl = JBTextField(provider.baseUrl)
    private val model = JBTextField(provider.model)
    private val apiKey = JBPasswordField().apply { text = SecretStore.get(provider.id).orEmpty() }
    private val temperature = JBTextField(provider.temperature.toString())
    private val maxTokens = JBTextField(provider.maxTokens.toString())
    private val timeout = JBTextField(provider.timeoutSec.toString())

    val component: JComponent = panel {
        row("Name:") { cell(name).resizableColumn() }
        row("Base URL:") { cell(baseUrl).resizableColumn() }
        row("Model:") { cell(model).resizableColumn() }
        row("API Key:") { cell(apiKey).resizableColumn() }
        row("Temperature:") { cell(temperature) }
        row("Max tokens:") { cell(maxTokens) }
        row("Timeout (sec):") { cell(timeout) }
    }

    fun apply(): Provider {
        provider = provider.copy(
            name = name.text.trim(),
            baseUrl = baseUrl.text.trim(),
            model = model.text.trim(),
            temperature = temperature.text.toDoubleOrNull() ?: 0.3,
            maxTokens = maxTokens.text.toIntOrNull() ?: 512,
            timeoutSec = timeout.text.toIntOrNull() ?: 60,
        )
        SecretStore.set(provider.id, String(apiKey.password).takeIf { it.isNotBlank() })
        return provider
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/io/aicommit/settings/ProviderEditor.kt
git commit -m "feat(settings): provider editor form"
```

### Task 6.2: `SettingsConfigurable`

**Files:**
- Create: `src/main/kotlin/io/aicommit/settings/SettingsConfigurable.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Implement**

```kotlin
package io.aicommit.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import io.aicommit.prompt.Templates
import java.util.UUID
import javax.swing.JComponent
import javax.swing.JPanel

class SettingsConfigurable : Configurable {

    private val settings = AppSettings.get()
    private val model = CollectionListModel(settings.state.providers.toMutableList())
    private val list = JBList(model)
    private var editor: ProviderEditor? = null
    private val editorHolder = JPanel().apply { border = JBUI.Borders.empty(8) }

    private val convention = ComboBox(Templates.conventions.map { it.id }.toTypedArray()).apply {
        selectedItem = settings.state.convention
    }
    private val language = com.intellij.ui.components.JBTextField(settings.state.language)
    private val recentCount = com.intellij.ui.components.JBTextField(settings.state.recentCommitCount.toString())
    private val maxChars = com.intellij.ui.components.JBTextField(settings.state.maxDiffChars.toString())

    override fun getDisplayName() = "AI Commit"

    override fun createComponent(): JComponent {
        list.addListSelectionListener {
            val sel = list.selectedValue ?: return@addListSelectionListener
            editor = ProviderEditor(sel)
            editorHolder.removeAll()
            editorHolder.add(editor!!.component)
            editorHolder.revalidate(); editorHolder.repaint()
        }

        val providersUi = ToolbarDecorator.createDecorator(list)
            .setAddAction {
                val p = Provider(id = UUID.randomUUID().toString(), name = "New", baseUrl = "https://api.openai.com/v1", model = "gpt-4o-mini")
                model.add(p); list.selectedIndex = model.size - 1
            }
            .setRemoveAction {
                val idx = list.selectedIndex
                if (idx >= 0) { SecretStore.clear(model.getElementAt(idx).id); model.remove(idx) }
            }
            .createPanel()

        return panel {
            group("Providers") {
                row { cell(providersUi).resizableColumn() }
                row { cell(editorHolder).resizableColumn() }
            }
            group("Generation") {
                row("Convention:") { cell(convention) }
                row("Language:") { cell(language) }
                row("Recent commits N:") { cell(recentCount) }
                row("Max diff chars:") { cell(maxChars) }
            }
        }
    }

    override fun isModified(): Boolean = true

    override fun apply() {
        editor?.let { ed ->
            val updated = ed.apply()
            val idx = list.selectedIndex
            if (idx >= 0) model.setElementAt(updated, idx)
        }
        settings.state.providers = model.items.toMutableList()
        if (settings.state.activeProviderId == null && model.items.isNotEmpty()) {
            settings.state.activeProviderId = model.items.first().id
        }
        settings.state.convention = convention.item ?: "conventional"
        settings.state.language = language.text.trim().ifBlank { "English" }
        settings.state.recentCommitCount = recentCount.text.toIntOrNull() ?: 5
        settings.state.maxDiffChars = maxChars.text.toIntOrNull() ?: 12000
    }

    override fun reset() {
        model.replaceAll(settings.state.providers)
    }
}
```

- [ ] **Step 2: Register in `plugin.xml`**

```xml
<applicationConfigurable parentId="tools"
                          instance="io.aicommit.settings.SettingsConfigurable"
                          id="io.aicommit.settings"
                          displayName="AI Commit"/>
```

- [ ] **Step 3: Smoke test**

```bash
./gradlew runIde
```
Open `Settings → Tools → AI Commit`. Add a provider, fill in DeepSeek/Ollama, Apply. Verify it persists across restart.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/io/aicommit/settings/SettingsConfigurable.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat(settings): settings page with provider list + generation prefs"
```

---

## Phase 7: End-to-End Manual Test

### Task 7.1: Manual smoke checklist

- [ ] **Step 1: Run sandbox**

```bash
./gradlew runIde
```

- [ ] **Step 2: Configure provider**
  - Open `Settings → Tools → AI Commit`
  - Add provider: Ollama
    - baseUrl: `http://localhost:11434/v1`
    - model: any local model you have (`llama3`, etc.)
    - API key: blank
  - Apply

- [ ] **Step 3: Stage a small change** in any project, open Commit panel.

- [ ] **Step 4: Click ✨ button**
  - Expect: message box clears, text streams in
  - Click again mid-stream → should stop, keep partial text

- [ ] **Step 5: Try a large diff**
  - Stage a generated 5000-line file
  - Expect: notification "Diff was truncated …", generation still completes

- [ ] **Step 6: Try with no provider**
  - Remove all providers in settings
  - Button should be disabled, tooltip explains

- [ ] **Step 7: If all pass, tag**

```bash
git tag v0.1.0
```

---

## Phase 8: Polish (optional, may be split into separate plans)

These were called out in the spec but are not blocking for v0.1:

- Status-bar widget for switching active provider (spec §6)
- `Test connection` button in provider editor
- Prompt preview using last diff
- Custom system/user template editors with reset-to-default
- `messages_zh_CN.properties` localization
- GitHub Actions CI for unit + headless platform tests
- Marketplace metadata polish (icon SVG, change-notes, screenshots)

Each of these can be added independently once the v0.1 loop is solid.

---

## Self-Review

**Spec coverage:**
- §1 goals → Phases 0–7 ✓
- §2 tech selection → Task 0.2 ✓
- §3 architecture (action/service/diff/prompt/llm/settings) → mapped to files ✓
- §4 data flow + streaming + cancel + truncation → Tasks 2.6, 3.2, 5.2 ✓
- §5 prompt templates incl. system/user/variables/redaction → Tasks 3.1–3.4 ✓
- §6 provider model + PasswordSafe + settings UI → Tasks 1.1, 1.2, 6.1, 6.2 ✓
  - Status-bar widget, Test connection, Preview, custom templates → deferred to Phase 8 (called out)
- §7 error matrix → mapped in CommitMsgService (Task 5.2) ✓
- §8 observability/privacy → no telemetry; Redactor + masked key ✓
- §9 test strategy → unit (PromptBuilder, Redactor, Truncator, SSEParser), integration (OpenAICompatibleClient) ✓; platform tests deferred to Phase 8 (CI)
- §10 project skeleton → matches File Structure ✓

**Placeholder scan:** Task 4.1 contains a "Note" about possibly adjusting platform API imports — kept because Platform diff-API names occasionally shift between versions; the engineer is told exactly which symbols to look up. No "TBD" / "implement later" elsewhere.

**Type consistency:** `Provider`, `DiffPayload`, `ChatMessage`, `AppSettings.State`, `CommitMsgService.generate(ui, changes)` referenced consistently across tasks.
