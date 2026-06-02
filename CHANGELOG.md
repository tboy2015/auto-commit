# Changelog

## [0.1.6] - 2026-06-02

### Improved
- 修正 reasoning-only 流式响应的失败提示，不再误导用户切换到已在使用的 flash 模型。
- 失败提示现在会建议调大最大输出 token、减少本次 diff 范围，或关闭服务端 Thinking。

## [0.1.5] - 2026-05-30

### Added
- Provider 列表增加状态小圆点，直接显示未配置、未验证、验证成功和验证失败状态。
- 模型表增加标签列，标记推荐、快速、高质量、推理/思考和本地模型。
- 增加极简中文、开源项目英文风格和团队自定义提交风格预设。
- 生成失败通知增加快捷动作：打开设置、刷新模型、切换推荐模型和获取 API Key。

### Improved
- 刷新模型后会缓存模型列表，下次打开设置页或状态栏切换模型时可直接使用。
- 当前模型不存在时会自动选择推荐模型或第一个可用模型。
- 更新 DeepSeek 和 Anthropic 默认模型，迁移旧模型名并优化推理模型提示。
- API Key 验证结果会记忆并在配置变更后提示重新验证。

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
