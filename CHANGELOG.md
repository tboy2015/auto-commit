# Changelog

## [0.1.4] - 2026-05-29

### Added
- 在 API Key 输入框旁添加“获取 API Key”链接，直达各 Provider 的密钥页面。
- 支持为单个 Provider 配置独立代理端口，并默认跟随 IDE 全局代理。

### Improved
- 网络异常提示现在会包含代理路径和更明确的排查建议。
- AI 生成的 commit message 会在流式输出完成后自动规整 header、bullet 和空行。

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
