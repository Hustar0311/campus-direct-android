package io.github.hustar0311.campusdirect.ssh

import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import io.github.hustar0311.campusdirect.model.AppConfig
import io.github.hustar0311.campusdirect.model.AuthMode
import io.github.hustar0311.campusdirect.model.RemoteResult
import io.github.hustar0311.campusdirect.model.VerificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class SshGateway {
    suspend fun execute(
        config: AppConfig,
        operation: String,
        peer: String? = null,
        privateKey: ByteArray? = null,
    ): RemoteResult = withContext(Dispatchers.IO) {
        require(operation in setOf("status", "inspect", "apply", "verify", "cleanup"))
        val command = buildString {
            append(config.helperPath)
            append(' ')
            append(operation)
            if (peer != null) {
                append(' ')
                append(peer)
            }
        }
        val jsch = JSch().apply {
            hostKeyRepository = FingerprintRepository(config.hostKeyFingerprint)
            if (config.authMode == AuthMode.PUBLIC_KEY) {
                requireNotNull(privateKey) { "未读取到 SSH 私钥" }
                addIdentity("campus-direct-imported", privateKey, null, null)
            }
        }
        val session = jsch.getSession(config.sshUser, config.sshHost, config.sshPort).apply {
            setConfig("StrictHostKeyChecking", "yes")
            setConfig(
                "PreferredAuthentications",
                if (config.authMode == AuthMode.PUBLIC_KEY) "publickey" else "none,password",
            )
            if (config.authMode == AuthMode.TAILSCALE_SSH) {
                setPassword("unused".toByteArray())
                userInfo = NonInteractiveUserInfo
            }
            timeout = SOCKET_TIMEOUT_MS
        }

        try {
            session.connect(CONNECT_TIMEOUT_MS)
            val channel = session.openChannel("exec") as ChannelExec
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            channel.setCommand(command)
            channel.setPty(false)
            channel.outputStream = stdout
            channel.setErrStream(stderr)
            channel.connect(CONNECT_TIMEOUT_MS)
            withTimeout(COMMAND_TIMEOUT_MS.toLong()) {
                while (!channel.isClosed) delay(50)
            }
            val exitStatus = channel.exitStatus
            channel.disconnect()
            val body = stdout.toString(Charsets.UTF_8.name()).trim()
            if (body.length > MAX_RESPONSE_CHARS) error("远端响应过长")
            parseResult(operation, exitStatus, body)
        } finally {
            privateKey?.fill(0)
            session.disconnect()
        }
    }

    internal fun parseResult(operation: String, exitStatus: Int, body: String): RemoteResult {
        if (body.isBlank()) {
            return RemoteResult(false, operation, message = "远端没有返回结构化结果（退出码 $exitStatus）")
        }
        return runCatching {
            val json = JSONObject(body)
            val ok = json.optBoolean("ok", false) && exitStatus == 0
            val action = json.optString("action", operation)
            val changed = action in setOf("applied", "removed") || json.optBoolean("changed", false)
            val reachable = if (json.has("reachable")) json.getBoolean("reachable") else null
            val verification = parseVerification(json, operation)
            val message = when {
                ok && verification?.verifiedOwner == true -> "综合所有权验证通过"
                ok && verification?.verifiedOwner == false -> "验证完成，但综合所有权验证未通过"
                ok && operation == "verify" && verification != null -> "验证完成，综合所有权状态未知"
                ok && reachable == true -> "远端验证可达（仅连通性，不代表所有权）"
                ok && reachable == false -> "路由存在，但目标暂不可达"
                ok -> "操作完成：$action"
                else -> "远端拒绝：${json.optString("error", "unknown_error")}"
            }
            RemoteResult(
                ok = ok,
                operation = operation,
                changed = changed,
                reachable = reachable,
                verification = verification,
                message = message,
            )
        }.getOrElse {
            RemoteResult(false, operation, message = "远端响应不是有效 JSON（退出码 $exitStatus）")
        }
    }

    private fun parseVerification(json: JSONObject, operation: String): VerificationResult? {
        val details = json.optJSONObject("verification") ?: json
        val tailnetReachable = details.firstBoolean("tailnet_reachable", "tailnet_online")
        val direct = details.firstBoolean("direct", "tailscale_direct")
        val directEndpoint = details.firstString("direct_endpoint")
        val directEndpointIp = details.firstString("direct_endpoint_ip", "endpoint_ip")
        val endpointMatchesPeer = details.firstBoolean("endpoint_matches_peer", "endpoint_matches_campus_ip")
        val campusPingReachable = details.firstBoolean("campus_ping_reachable", "reachable")
            ?: json.firstBoolean("campus_ping_reachable", "reachable")
        val leaseFresh = details.firstBoolean("lease_fresh")
        val snatConsistent = details.firstBoolean("snat_consistent")
        val explicitVerifiedOwner = details.firstBoolean("verified_owner")
        val watchdogActive = details.firstBoolean("watchdog_active", "watchdog_running")
            ?: json.firstBoolean("watchdog_active", "watchdog_running")
        val watchdogStatus = details.firstString("watchdog_status", "status")
            ?: json.firstString("watchdog_status", "status")
        val leaseRemainingSeconds = details.firstLong("lease_remaining_seconds", "lease_remaining")
            ?: json.firstLong("lease_remaining_seconds", "lease_remaining")
        val stateOwned = details.firstBoolean("state_owned")
        val routeExact = details.firstBoolean("route_exact")
        val watchdogEnabled = details.firstBoolean("watchdog_enabled")
        val watchdogIntervalSeconds = details.firstLong("watchdog_interval")
        val staleTimeoutSeconds = details.firstLong("stale_timeout")
        val endpointMismatchConfirmations = details.firstLong("endpoint_mismatch_confirmations")
        val lastProbe = details.firstString("last_probe")
        val lastDirect = details.firstString("last_direct")
        val failureCount = details.firstLong("failure_count")
        val mismatchCount = details.firstLong("mismatch_count")
        val leaseDeadline = details.firstLong("lease_deadline")
        val expired = details.firstBoolean("expired")
        val watchdogManagedPeer = details.firstString("watchdog_managed_peer")
        val inferredOwnershipSignals = listOf(
            tailnetReachable,
            direct,
            endpointMatchesPeer,
            leaseFresh,
            snatConsistent,
            stateOwned,
            routeExact,
        )
        val ownershipSafetySignals = inferredOwnershipSignals + expired?.not()
        val inferredVerifiedOwner = inferredOwnershipSignals
            .takeIf { signals -> signals.all { it != null } }
            ?.all { it == true }
        val verifiedOwner = when {
            ownershipSafetySignals.any { it == false } -> false
            explicitVerifiedOwner != null -> explicitVerifiedOwner
            else -> inferredVerifiedOwner
        }
        val hasVerificationData = operation == "verify" || listOf(
            tailnetReachable,
            direct,
            directEndpoint,
            directEndpointIp,
            endpointMatchesPeer,
            campusPingReachable,
            leaseFresh,
            snatConsistent,
            explicitVerifiedOwner,
            watchdogActive,
            watchdogStatus,
            leaseRemainingSeconds,
            stateOwned,
            routeExact,
            watchdogEnabled,
            watchdogIntervalSeconds,
            staleTimeoutSeconds,
            endpointMismatchConfirmations,
            lastProbe,
            lastDirect,
            failureCount,
            mismatchCount,
            leaseDeadline,
            expired,
            watchdogManagedPeer,
        ).any { it != null }
        if (!hasVerificationData) return null

        return VerificationResult(
            tailnetReachable = tailnetReachable,
            direct = direct,
            directEndpoint = directEndpoint,
            directEndpointIp = directEndpointIp,
            endpointMatchesPeer = endpointMatchesPeer,
            campusPingReachable = campusPingReachable,
            leaseFresh = leaseFresh,
            snatConsistent = snatConsistent,
            verifiedOwner = verifiedOwner,
            watchdogActive = watchdogActive,
            watchdogStatus = watchdogStatus,
            leaseRemainingSeconds = leaseRemainingSeconds,
            stateOwned = stateOwned,
            routeExact = routeExact,
            watchdogEnabled = watchdogEnabled,
            watchdogIntervalSeconds = watchdogIntervalSeconds,
            staleTimeoutSeconds = staleTimeoutSeconds,
            endpointMismatchConfirmations = endpointMismatchConfirmations,
            lastProbe = lastProbe,
            lastDirect = lastDirect,
            failureCount = failureCount,
            mismatchCount = mismatchCount,
            leaseDeadline = leaseDeadline,
            expired = expired,
            watchdogManagedPeer = watchdogManagedPeer,
        )
    }

    private fun JSONObject.firstBoolean(vararg names: String): Boolean? {
        for (name in names) {
            if (has(name) && !isNull(name)) return optBoolean(name)
        }
        return null
    }

    private fun JSONObject.firstString(vararg names: String): String? {
        for (name in names) {
            if (has(name) && !isNull(name)) return optString(name).takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun JSONObject.firstLong(vararg names: String): Long? {
        for (name in names) {
            if (has(name) && !isNull(name)) return runCatching { getLong(name) }.getOrNull()
        }
        return null
    }

    private class FingerprintRepository(expected: String) : HostKeyRepository {
        private val expectedFingerprint = expected.trim()

        override fun check(host: String?, key: ByteArray?): Int {
            if (key == null) return HostKeyRepository.NOT_INCLUDED
            val digest = MessageDigest.getInstance("SHA-256").digest(key)
            val actual = "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
            return if (actual == expectedFingerprint) HostKeyRepository.OK else HostKeyRepository.CHANGED
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "Pinned SHA-256 fingerprint"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    private object NonInteractiveUserInfo : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String = "unused"
        override fun promptPassword(message: String?): Boolean = true
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptYesNo(message: String?): Boolean = false
        override fun showMessage(message: String?) = Unit
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val SOCKET_TIMEOUT_MS = 12_000
        const val COMMAND_TIMEOUT_MS = 20_000
        const val MAX_RESPONSE_CHARS = 16_384
    }
}
