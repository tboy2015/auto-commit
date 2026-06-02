package io.aicommit.llm

object LLMErrorMessages {
    fun userMessage(error: Throwable): String = when (error) {
        is LLMException.Auth ->
            "API Key 错误或没有权限。请检查 API Key 是否填写正确，必要时重新获取并保存。"
        is LLMException.RateLimited ->
            "额度不足或触发限流。请检查账户余额、套餐额度，或稍后重试。"
        is LLMException.ContextTooLong ->
            "本次 diff 太长，已超出模型上下文。请减少本次提交范围，或调低最大 diff 字符数后重试。"
        is LLMException.Network ->
            "网络连接失败。请检查代理、网络和 Base URL。\n${error.message.orEmpty()}"
        is LLMException.BadResponse ->
            badResponseMessage(error.message.orEmpty())
        else ->
            "${error.javaClass.simpleName}: ${error.message.orEmpty()}"
    }

    private fun badResponseMessage(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("model") &&
                (lower.contains("not found") ||
                    lower.contains("does not exist") ||
                    lower.contains("not exist") ||
                    lower.contains("invalid") ||
                    message.contains("模型不存在")) ->
                "当前模型不存在或你的账号无权使用。请点击“刷新模型”后重新选择可用模型。"
            lower.contains("insufficient") || message.contains("余额") || message.contains("额度") ->
                "账户额度不足或余额不可用。请检查服务商控制台的余额/额度后重试。"
            lower.contains("unauthorized") || lower.contains("forbidden") ->
                "服务端拒绝访问。请检查 API Key 权限、模型权限和 Base URL。"
            lower.contains("reasoning_content") || message.contains("推理内容") ->
                "模型没有返回可写入 commit message 的正文，只返回了推理内容。请调大最大输出 token、减少本次 diff 范围；如果服务端开启了 Thinking，请关闭后重试。"
            message.isBlank() ->
                "模型返回异常。请点击“刷新模型”重新选择可用模型后重试。"
            else ->
                "模型返回异常：${message.take(300)}"
        }
    }
}
