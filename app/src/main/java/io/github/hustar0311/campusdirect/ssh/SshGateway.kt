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
            val message = when {
                ok && reachable == true -> "远端验证可达"
                ok && reachable == false -> "路由存在，但目标暂不可达"
                ok -> "操作完成：$action"
                else -> "远端拒绝：${json.optString("error", "unknown_error")}"
            }
            RemoteResult(ok, operation, changed, reachable, message)
        }.getOrElse {
            RemoteResult(false, operation, message = "远端响应不是有效 JSON（退出码 $exitStatus）")
        }
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
