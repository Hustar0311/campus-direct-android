package io.github.hustar0311.campusdirect.data

import android.content.Context
import io.github.hustar0311.campusdirect.model.AppConfig
import io.github.hustar0311.campusdirect.model.AuthMode

class ConfigRepository(context: Context) {
    private val preferences = context.getSharedPreferences("campus_direct_config", Context.MODE_PRIVATE)

    fun load(): AppConfig = AppConfig(
        campusCidr = preferences.getString("campus_cidr", "").orEmpty(),
        expectedGateway = preferences.getString("expected_gateway", "").orEmpty(),
        sshHost = preferences.getString("ssh_host", "").orEmpty(),
        sshPort = preferences.getInt("ssh_port", 22),
        sshUser = preferences.getString("ssh_user", "").orEmpty(),
        helperPath = preferences.getString("helper_path", "").orEmpty(),
        hostKeyFingerprint = preferences.getString("host_key_fingerprint", "").orEmpty(),
        authMode = runCatching {
            AuthMode.valueOf(preferences.getString("auth_mode", null) ?: AuthMode.PUBLIC_KEY.name)
        }.getOrDefault(AuthMode.PUBLIC_KEY),
    )

    fun save(config: AppConfig) {
        preferences.edit()
            .putString("campus_cidr", config.campusCidr.trim())
            .putString("expected_gateway", config.expectedGateway.trim())
            .putString("ssh_host", config.sshHost.trim())
            .putInt("ssh_port", config.sshPort)
            .putString("ssh_user", config.sshUser.trim())
            .putString("helper_path", config.helperPath.trim())
            .putString("host_key_fingerprint", config.hostKeyFingerprint.trim())
            .putString("auth_mode", config.authMode.name)
            .apply()
    }

    fun managedPeer(): String? = preferences.getString("managed_peer", null)

    fun setManagedPeer(peer: String?) {
        preferences.edit().apply {
            if (peer == null) remove("managed_peer") else putString("managed_peer", peer)
        }.apply()
    }
}
