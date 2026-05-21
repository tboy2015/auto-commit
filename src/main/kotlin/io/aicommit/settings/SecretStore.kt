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
