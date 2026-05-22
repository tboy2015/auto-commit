# Changelog

## [0.1.0] - 2026-05-22

### Added
- Commit 工具栏的 ⚡ 按钮，调用 OpenAI 兼容 API 流式生成 commit message
- Provider 多 tab 配置：OpenAI / Anthropic / DeepSeek / Kimi / GLM / Qwen / SiliconFlow / OpenRouter / Ollama / LM Studio + Custom
- 模型管理：拉取 / 搜索 / 启用表格 / 当前模型下拉
- 生成模式：仅 staged diff / + 文件路径 / + 最近 N 条 commit 历史（右键 ⚡ 切换）
- 多 commit 规范：Conventional / Conventional + Gitmoji / Gitmoji / Simple
- 多语言生成：中 / 英 / 日 / 韩 / 自定义
- 大 diff 自动截断 + 脱敏（.env / API key）
- API Key 存系统 Keychain（PasswordSafe）
- 推理模型保护：`reasoning_content` 不污染输出，并提示更换模型
- 快捷键：`⌘⇧G` (macOS) / `Ctrl+Shift+G` (Win/Linux)
- 灰色 ⚡ 状态下左键直达 Settings
