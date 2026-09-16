package io.github.hustar0311.campusdirect.model

import io.github.hustar0311.campusdirect.network.Ipv4Cidr
import io.github.hustar0311.campusdirect.network.Ipv4Value

enum class AuthMode {
    PUBLIC_KEY,
    TAILSCALE_SSH,
}

data class AppConfig(
    val campusCidr: String = "",
    val expectedGateway: String = "",
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "",
    val helperPath: String = "",
    val hostKeyFingerprint: String = "",
    val authMode: AuthMode = AuthMode.PUBLIC_KEY,
) {
    fun validationErrors(hasImportedKey: Boolean): List<String> = buildList {
        if (runCatching { Ipv4Cidr.parse(campusCidr) }.isFailure) add("校园网 CIDR 无效")
        if (runCatching { Ipv4Value.parse(expectedGateway) }.isFailure) add("校园网网关无效")
        if (runCatching { Ipv4Value.parse(sshHost) }.isFailure) add("SSH 主机地址无效")
        if (sshPort !in 1..65535) add("SSH 端口无效")
        if (!sshUser.matches(Regex("[A-Za-z0-9_.-]{1,32}"))) add("SSH 用户名无效")
        if (!helperPath.matches(Regex("/[A-Za-z0-9_./-]{1,160}")) || ".." in helperPath) {
            add("helper 路径无效")
        }
        if (!hostKeyFingerprint.matches(Regex("SHA256:[A-Za-z0-9+/]{43}"))) {
            add("SSH 主机指纹必须是 SHA256 格式")
        }
        if (authMode == AuthMode.PUBLIC_KEY && !hasImportedKey) add("尚未导入 SSH 私钥")
    }
}

data class CheckResult(
    val label: String,
    val passed: Boolean,
    val detail: String,
)

data class NetworkSnapshot(
    val isWifi: Boolean = false,
    val ssid: String? = null,
    val ipv4: String? = null,
    val prefixLength: Int? = null,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val sshPortReachable: Boolean = false,
    val checks: List<CheckResult> = emptyList(),
) {
    val readyForRemoteChange: Boolean
        get() = checks.isNotEmpty() && checks.all { it.passed }
}

data class RemoteResult(
    val ok: Boolean,
    val operation: String,
    val changed: Boolean = false,
    val reachable: Boolean? = null,
    val verification: VerificationResult? = null,
    val message: String,
)

data class VerificationResult(
    val tailnetReachable: Boolean? = null,
    val direct: Boolean? = null,
    val directEndpointIp: String? = null,
    val endpointMatchesPeer: Boolean? = null,
    val campusPingReachable: Boolean? = null,
    val leaseFresh: Boolean? = null,
    val snatConsistent: Boolean? = null,
    val verifiedOwner: Boolean? = null,
    val watchdogActive: Boolean? = null,
    val watchdogStatus: String? = null,
    val leaseRemainingSeconds: Long? = null,
)
