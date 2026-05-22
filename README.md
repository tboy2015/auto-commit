# Auto Commit — IntelliJ IDEA Plugin

在 IDEA 的 Commit 对话框里用 AI 一键生成 commit message。

![status](https://img.shields.io/badge/status-alpha-orange)
![ide](https://img.shields.io/badge/IDEA-2023.2%2B-blue)
![license](https://img.shields.io/badge/license-MIT-green)

## 特性

- **OpenAI 兼容协议**：内置 OpenAI / Anthropic Claude / DeepSeek / Kimi / 智谱 GLM / 通义 / SiliconFlow / OpenRouter / Ollama / LM Studio 预设；也支持任意自定义端点
- **流式输出**直接填入 message 框；中途可停止
- **多生成模式**（右键 ⚡）：仅 staged diff / + 文件路径 / + 最近 commit 历史
- **多 commit 规范**：Conventional Commits / Conventional + Gitmoji / Gitmoji / Simple，或自填 system prompt
- **多语言**：中 / 英 / 日 / 韩 / 自定义
- **大 diff 智能截断 + 脱敏**（剥离 `.env`、`*.pem`、`api_key=` 等敏感行）
- **API Key 存系统 Keychain**（PasswordSafe），不写入配置文件
- **推理模型保护**：识别 `reasoning_content`-only 输出并提示更换模型
- **快捷键**：`⌘⇧G` / `Ctrl+Shift+G`
- **状态栏 widget**：右下角点击快速切换 provider / 模型
- **不上报任何遥测**

## 安装

### 方式 A：从源码构建（推荐用于开发）

```bash
git clone <repo-url>
cd auto-commit
./gradlew buildPlugin
```

产物：`build/distributions/ai-commit-<version>.zip`

在 IDEA：**Settings → Plugins → ⚙ → Install Plugin from Disk…** 选这个 zip → 重启。

### 方式 B：JetBrains Marketplace（待上架）

`Settings → Plugins → Marketplace`，搜 "Auto Commit"。

## 使用

1. **配置 Provider**：`Settings → Tools → Auto Commit`
   - 选预设 tab（DeepSeek / Ollama / …）或点 `+ Custom`
   - 填 API Key，点"验证"测试连接，点"刷新模型"拉模型列表 → 勾选要启用的
   - 在表格上方"当前模型"下拉里选默认模型
   - 勾"设为当前服务" → Apply
2. **生成**：在任意 git 项目里改文件 → 打开 Commit 面板 → 勾选要提交的文件 → 点 ⚡ 或 `⌘⇧G`
3. **切换 provider / 模型**：右下角状态栏点 "AI: …" 文字 → 弹出列表

## 开发

```bash
# 跑沙盒 IDE 测试
./gradlew runIde

# 跑单元测试
./gradlew test

# 打 zip
./gradlew buildPlugin
```

### 本地 IDEA 加速

`gradle.properties` 里把 `localIdePath` 指向你本机已装的 IDEA，避免每次下载 1GB 平台：

```properties
localIdePath=/Applications/IntelliJ IDEA.app/Contents
```

## 发布到 Marketplace

详见 [PUBLISHING.md](./PUBLISHING.md)。简版三步：

1. 生成签名密钥
   ```bash
   ./scripts/generate-signing-keys.sh
   ```
2. 在 [JetBrains Marketplace](https://plugins.jetbrains.com/author/me/tokens) 创建 publish token
3. 设环境变量后：
   ```bash
   export CERTIFICATE_CHAIN="$(cat secrets/chain.crt)"
   export PRIVATE_KEY="$(cat secrets/private.pem)"
   export PRIVATE_KEY_PASSWORD="..."
   export PUBLISH_TOKEN="..."
   ./gradlew publishPlugin
   ```

## 许可证

[MIT](./LICENSE)
