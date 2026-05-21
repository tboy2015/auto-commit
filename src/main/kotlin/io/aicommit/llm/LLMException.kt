package io.aicommit.llm

sealed class LLMException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause) {
    class Auth(msg: String) : LLMException(msg)
    class RateLimited(msg: String) : LLMException(msg)
    class ContextTooLong(msg: String) : LLMException(msg)
    class Network(msg: String, cause: Throwable?) : LLMException(msg, cause)
    class BadResponse(msg: String) : LLMException(msg)
}
