# AI Commit — IntelliJ IDEA 插件设计文档

- 日期：2026-05-21
- 类型：新项目设计 / Spec
- 状态：待评审

## 1. 目标与范围

开发一款类似 `aicommit` 的 IntelliJ IDEA 插件：在 IDEA 内置 Commit 对话框中提供一个按钮，
根据当前 staged diff 调用大模型，**流式生成 commit message 并填入输入框**。

### In scope

- 仅 IntelliJ IDEA（Community + Ultimate），最低 2023.2
- AI provider：以 OpenAI 兼容协议为主轴，覆盖国内主流模型、本地 Ollama / LM Studio、国外第三方
- 多 commit 规范模板（Conventional Commits / Gitmoji / 自定义）
- 多语言生成（中 / 英 / 自定义）
- 上下文：staged diff + 文件路径 + 最近 N 条 commit 历史
- 大 diff：智能截断 + 提示用户
- 全局配置，API Key 存 IDEA `PasswordSafe`
- 流式输出直接填入 message 输入框，支持取消

### Out of scope（不做）

- PR / MR 描述生成
- Code Review / 变更解释
- 自动拆分 commit
- 多候选 message
- 非 IDEA 的其他 JetBrains IDE（后续可基于同一代码低成本扩展，但当前不交付）

## 2. 技术选型

| 项 | 选型 |
|---|---|
| 平台版本 | IntelliJ Platform 2023.2+ |
| 构建 | IntelliJ Platform Gradle Plugin 2.x + Kotlin DSL |
| 语言 | Kotlin 1.9.x |
| 并发 | Kotlin Coroutines |
| HTTP | OkHttp（SSE 流式） |
| 测试 | JUnit5、MockWebServer、`BasePlatformTestCase` |

## 3. 总体架构

```
┌──────────────────────────────────────────────────┐
│         IntelliJ Commit Dialog (内置)            │
│  ┌────────────────────────────────────────────┐  │
│  │ message 输入框                             │  │
│  │ ▼工具栏: [✨ AI Generate]  ← 本插件注入    │  │
│  └────────────────────────────────────────────┘  │
└────────────────┬─────────────────────────────────┘
                 │ click
                 ▼
        ┌────────────────────┐
        │  GenerateAction    │  AnAction
        └─────────┬──────────┘
                  ▼
        ┌────────────────────┐
        │ CommitMsgService   │  @Service(Project)
        └─┬───────┬────────┬─┘
          ▼       ▼        ▼
   DiffCollector  PromptBuilder   LLMClient
   (VCS API)      (模板+截断)      (OkHttp+SSE)
                                      │
                                      ▼
                             OpenAI 兼容端点
                  ┌─────────────────────────────────┐
                  │  Settings (全局, PasswordSafe)  │
                  └─────────────────────────────────┘
```

### 模块边界

| 模块 | 职责 | 主要依赖 |
|---|---|---|
| `action` | 注册 Commit 工具栏按钮、状态判断、触发服务 | platform |
| `service` | 协调主流程、流式回填、取消、错误分发 | 其余全部 |
| `diff` | 从 `ChangeListManager` 收集 staged diff、文件路径、最近 commit | VCS API |
| `prompt` | 加载模板、变量替换、智能截断、脱敏 | - |
| `llm` | OpenAI 兼容 HTTP 客户端、SSE 解析 | OkHttp |
| `settings` | 全局配置 UI、Provider 管理、PasswordSafe 接入 | platform |
| `ui` | 状态栏 widget、Notification 封装 | platform |

## 4. 数据流与流式输出

### 主流程时序

1. 用户在 Commit 对话框点击 ✨ 按钮
2. `GenerateAction` 调 `CommitMsgService.generate(commitWorkflowUi)`
3. `DiffCollector` 拿到 `DiffPayload(diffText, files, recentCommits, branch)`
4. `PromptBuilder.build(payload, settings)` → `PromptMessages(system, user, truncated)`
5. `LLMClient.stream(messages)` 返回 `Flow<String>`
6. service 在 EDT 通过 `CommandProcessor.executeCommand` 把每个 chunk 追加到 message 输入框
7. 完成 / 失败 / 取消 → 通知 + 清理状态

### 关键决策

- **取消机制**：service 持有 `Job` 与 OkHttp `Call`；按钮在生成中变为「停止」；用户再点 → 同时 `job.cancel()` 与 `call.cancel()`。也响应 Commit 对话框关闭。
- **回填策略**：开始前可选清空 message 框（默认清空）；每个 chunk 在 EDT 中以 command 追加，整体作为单个 undo 单元。
- **线程**：网络在 `Dispatchers.IO`，UI 操作切到 `Dispatchers.EDT`。
- **大 diff 截断**：在 `PromptBuilder` 内做，超阈值时按文件保留头尾 N 行 + 文件名，prompt 内附加「diff 已截断」声明，同时弹非阻塞 Notification。
- **可配置项**：是否清空 message 框、最大 diff 字符阈值（默认 12000）、请求超时（默认 60s）。

## 5. Prompt 模板系统

一次生成由两段组成：

```
System Prompt  ← 规范（Conventional Commits / Gitmoji / 自定义）
User Prompt    ← 上下文 + 语言指令
```

### 内置规范

| ID | 说明 |
|---|---|
| `conventional` | Conventional Commits：`type(scope): subject` + body |
| `conventional-emoji` | Conventional + Gitmoji 前缀 |
| `gitmoji` | 仅 Gitmoji |
| `simple` | 1 行 subject + 可选 body |
| `custom` | 用户自填 system prompt |

### User 模板变量（Mustache 风格）

| 变量 | 含义 |
|---|---|
| `{{language}}` | 输出语言 |
| `{{diff}}` | 截断后的 staged diff |
| `{{files}}` | 变更文件路径列表 |
| `{{recent_commits}}` | 最近 N 条 commit message（默认 5） |
| `{{branch}}` | 当前分支名 |
| `{{truncated}}` | bool，diff 是否被截断 |

默认 User 模板：

```
Generate a git commit message in {{language}}.

Recent commit style for reference:
{{recent_commits}}

Changed files:
{{files}}

Diff{{#truncated}} (truncated){{/truncated}}:
{{diff}}
```

### 脱敏

渲染前对 diff 做基础脱敏：剥离 `.env`、`*.pem` 文件内容；
对 `password=` / `token=` / `api_key=` 行的值替换为 `***`。
可在设置中开关，默认开。

## 6. Provider 抽象与设置

### Provider 模型

```kotlin
data class Provider(
    val id: String,            // uuid
    val name: String,
    val baseUrl: String,       // e.g. https://api.deepseek.com/v1
    val model: String,
    val apiKeyRef: String,     // PasswordSafe key，不存明文
    val temperature: Double = 0.3,
    val maxTokens: Int = 512,
    val timeoutSec: Int = 60,
    val extraHeaders: Map<String,String> = emptyMap(),
)
```

所有 provider 走同一个 `OpenAICompatibleClient`。

### 内置预设

OpenAI、DeepSeek、Kimi (Moonshot)、智谱 GLM、通义千问 DashScope（OpenAI 兼容端点）、
Ollama（`http://localhost:11434/v1`）、LM Studio（`http://localhost:1234/v1`）、自定义。

预设只一键填好 baseUrl，model 和 key 仍由用户填写。

### Settings 页面（`Settings → Tools → AI Commit`）

- **Providers**：列表 + 增删 + 复制 + Edit 面板 + [Test connection]
- **Generation**：convention 下拉、language、是否带最近 commit + N 值、是否带文件路径、Max diff chars、是否清空 message、是否脱敏
- **Prompt Templates**：system 模板下拉（含 custom）、user 模板编辑器 + 恢复默认 + Preview（用最近一次 diff 渲染）

### 密钥处理

- 仅通过 `PasswordSafe.instance.set(CredentialAttributes("aicommit:{providerId}"), ...)` 存
- 配置导出 / Settings Sync 只导 `apiKeyRef`，不含明文
- 日志、错误堆栈中 key 一律 mask

### Active provider 切换

状态栏 widget 显示当前 provider 名，点击下拉切换，不必进 Settings。

## 7. 错误处理矩阵

| 场景 | 行为 |
|---|---|
| 未配置 provider | Notification「请先在 Settings 配置」+ 跳转设置页；按钮 disabled |
| API Key 缺失 / 401 | balloon「认证失败，点此打开设置」 |
| 网络超时 / 断连 | Notification「请求超时，已保留原 message」 |
| 模型 400（context too long） | Notification「diff 仍过大，建议拆分提交」 |
| 限流 429 | 提示「触发限流，请稍后重试」 |
| 流式中途断开 | 已生成部分保留，提示「生成中断」 |
| 用户取消 | 静默停止，保留已生成内容 |
| diff 为空 | 按钮 disabled + tooltip「无暂存变更」 |

错误统一走 `NotificationGroupManager`（group: `AI Commit`），不弹模态对话框。

## 8. 可观测性与隐私

- `Logger.getInstance` 输出诊断日志，key/diff 脱敏后再写
- 设置页有「Open log」按钮
- **不上报任何遥测**；README 明示

## 9. 测试策略

| 层 | 工具 | 覆盖 |
|---|---|---|
| 单元 | JUnit5 + Kotlin | `PromptBuilder`（截断、变量替换、脱敏）、`SSEParser`、`Provider` 序列化 |
| 集成 | MockWebServer | `OpenAICompatibleClient` 的流式、错误、取消、超时 |
| 平台 | `BasePlatformTestCase` | `DiffCollector` 真实 `ChangeListManager` fixture、Action `update()` 可用性 |
| 手动 | checklist | 真连 DeepSeek + Ollama，跑通流式、取消、大 diff |

CI：GitHub Actions 跑 unit + 集成；平台测试在 headless IDEA 中跑。

## 10. 项目骨架

```
auto-commit/
├── build.gradle.kts                 # IntelliJ Platform Gradle Plugin 2.x
├── settings.gradle.kts
├── gradle.properties                # platformVersion=2023.2, kotlin=1.9.x
├── src/main/kotlin/io/xxx/aicommit/
│   ├── action/GenerateAction.kt
│   ├── service/CommitMsgService.kt
│   ├── diff/DiffCollector.kt
│   ├── prompt/{PromptBuilder, Templates, Redactor}.kt
│   ├── llm/{OpenAICompatibleClient, SSEParser, LLMException}.kt
│   ├── settings/{AppSettings, SettingsConfigurable, ProviderEditor}.kt
│   ├── ui/{StatusBarWidget, Notifications}.kt
│   └── i18n/messages_{en,zh_CN}.properties
├── src/main/resources/META-INF/
│   ├── plugin.xml
│   └── pluginIcon.svg
└── src/test/kotlin/...
```

### plugin.xml 关键扩展点

- `<actions>` 把 `GenerateAction` 加入 `Vcs.MessageActionGroup`
- `<applicationService>` 注册 `AppSettings`
- `<projectService>` 注册 `CommitMsgService`
- `<applicationConfigurable>` 注册设置页
- `<notificationGroup id="AI Commit">`
- `<statusBarWidgetFactory>` provider 切换

## 11. 里程碑（建议）

1. 骨架 + Settings + PasswordSafe + 单一 OpenAI 兼容 provider 跑通同步生成
2. 流式 + 取消 + EDT 安全的回填
3. 模板系统 + 多规范 + 多语言 + 预览
4. 大 diff 截断 + 脱敏 + 错误矩阵
5. 状态栏 widget + 多 provider 管理 + Test connection
6. 测试补齐 + 文档 + Marketplace 发布准备
