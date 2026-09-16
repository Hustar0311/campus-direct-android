package io.github.hustar0311.campusdirect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hustar0311.campusdirect.data.ConfigRepository
import io.github.hustar0311.campusdirect.data.PrivateKeyStore
import io.github.hustar0311.campusdirect.model.AppConfig
import io.github.hustar0311.campusdirect.model.AuthMode
import io.github.hustar0311.campusdirect.model.NetworkSnapshot
import io.github.hustar0311.campusdirect.model.RemoteResult
import io.github.hustar0311.campusdirect.network.NetworkInspector
import io.github.hustar0311.campusdirect.ssh.SshGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val config: AppConfig,
    val hasImportedKey: Boolean,
    val managedPeer: String?,
    val network: NetworkSnapshot? = null,
    val busy: Boolean = false,
    val showSettings: Boolean = false,
    val message: String? = null,
    val lastRemoteResult: RemoteResult? = null,
) {
    val configErrors: List<String>
        get() = config.validationErrors(hasImportedKey)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val configRepository = ConfigRepository(application)
    private val privateKeyStore = PrivateKeyStore(application)
    private val networkInspector = NetworkInspector(application)
    private val sshGateway = SshGateway()

    private val _state = MutableStateFlow(
        UiState(
            config = configRepository.load(),
            hasImportedKey = privateKeyStore.exists(),
            managedPeer = configRepository.managedPeer(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (_state.value.config.validationErrors(_state.value.hasImportedKey).isNotEmpty()) {
            _state.update { it.copy(showSettings = true) }
        }
    }

    fun editConfig(transform: (AppConfig) -> AppConfig) {
        _state.update { it.copy(config = transform(it.config), message = null) }
    }

    fun setSettingsVisible(visible: Boolean) {
        _state.update { it.copy(showSettings = visible, message = null) }
    }

    fun saveConfig() {
        val snapshot = _state.value
        val errors = snapshot.configErrors
        if (errors.isNotEmpty()) {
            _state.update { it.copy(message = errors.joinToString("；")) }
            return
        }
        configRepository.save(snapshot.config)
        _state.update { it.copy(showSettings = false, network = null, message = "配置已保存在本机") }
    }

    fun importPrivateKey(bytes: ByteArray) {
        runCatching { privateKeyStore.import(bytes) }
            .onSuccess { _state.update { it.copy(hasImportedKey = true, message = "私钥已加密导入") } }
            .onFailure { _state.update { it.copy(message = "私钥导入失败：${it.message ?: "未知错误"}") } }
    }

    fun deletePrivateKey() {
        privateKeyStore.delete()
        _state.update { it.copy(hasImportedKey = false, message = "已删除应用内私钥副本") }
    }

    fun inspectNetwork() = launchBusy {
        requireValidConfig()
        val network = networkInspector.inspect(_state.value.config)
        _state.update {
            it.copy(
                network = network,
                message = if (network.readyForRemoteChange) "本机检查通过" else "存在未通过的检查项",
            )
        }
    }

    fun remoteStatus() = runRemote("status", null)

    fun applyRoute() = launchBusy {
        requireValidConfig()
        val peer = requireReadyPeer()
        val previous = _state.value.managedPeer
        val decision = applyCurrentPeer(previous, peer) { operation, target ->
            executeRemote(operation, target)
        }
        val result = decision.remoteResult
        if (result.ok && decision.managedPeer == peer) {
            configRepository.setManagedPeer(peer)
            _state.update {
                it.copy(managedPeer = peer, message = result.message, lastRemoteResult = result)
            }
        } else {
            val retained = previous?.let { "旧受管地址 $it 已保留" } ?: "本地未记录新的受管地址"
            _state.update {
                it.copy(
                    managedPeer = decision.managedPeer,
                    message = "Apply 失败，$retained：${result.message}",
                    lastRemoteResult = result,
                )
            }
        }
    }

    fun verifyRoute() = launchBusy {
        requireValidConfig()
        val peer = _state.value.network?.ipv4 ?: _state.value.managedPeer
            ?: error("没有可验证的手机地址")
        val result = executeRemote("verify", peer)
        _state.update { it.copy(message = result.message, lastRemoteResult = result) }
    }

    fun cleanupRoute() = launchBusy {
        requireValidConfig()
        val peer = _state.value.managedPeer ?: error("没有本应用记录的受管路由")
        val result = executeRemote("cleanup", peer)
        if (result.ok) {
            configRepository.setManagedPeer(null)
            _state.update {
                it.copy(
                    managedPeer = null,
                    message = "远端路由已清理，请将 Wi-Fi 恢复为 DHCP",
                    lastRemoteResult = result,
                )
            }
        } else {
            _state.update { it.copy(message = result.message, lastRemoteResult = result) }
        }
    }

    private fun runRemote(operation: String, peer: String?) = launchBusy {
        requireValidConfig()
        val result = executeRemote(operation, peer)
        _state.update { it.copy(message = result.message, lastRemoteResult = result) }
    }

    private suspend fun executeRemote(operation: String, peer: String?) = sshGateway.execute(
        config = _state.value.config,
        operation = operation,
        peer = peer,
        privateKey = if (_state.value.config.authMode == AuthMode.PUBLIC_KEY) privateKeyStore.read() else null,
    )

    private fun requireValidConfig() {
        val errors = _state.value.configErrors
        check(errors.isEmpty()) { errors.joinToString("；") }
    }

    private fun requireReadyPeer(): String {
        val network = _state.value.network ?: error("请先检查本机网络")
        check(network.readyForRemoteChange) { "本机网络检查未全部通过" }
        return network.ipv4 ?: error("未取得手机 IPv4")
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            runCatching { block() }
                .onFailure { error ->
                    _state.update { it.copy(message = "操作失败：${error.message ?: "未知错误"}") }
                }
            _state.update { it.copy(busy = false) }
        }
    }
}
