package io.aicommit.settings

object SecretStore {
    fun set(providerId: String, key: String?) {
        AppSettings.get().setApiKey(providerId, key)
    }

    fun get(providerId: String): String? =
        AppSettings.get().getApiKey(providerId)

    fun clear(providerId: String) = set(providerId, null)
}
