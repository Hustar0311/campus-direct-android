package io.github.hustar0311.campusdirect

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.hustar0311.campusdirect.model.AppConfig
import io.github.hustar0311.campusdirect.model.AuthMode
import io.github.hustar0311.campusdirect.model.NetworkSnapshot
import io.github.hustar0311.campusdirect.ui.CampusDirectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CampusDirectTheme {
                val vm: MainViewModel = viewModel()
                CampusDirectApp(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampusDirectApp(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.inspectNetwork() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园网直连助手") },
                actions = {
                    TextButton(onClick = { vm.setSettingsVisible(true) }) { Text("设置") }
                },
            )
        },
    ) { padding ->
        if (state.showSettings) {
            SettingsScreen(state, vm, padding)
        } else {
            HomeScreen(
                state = state,
                padding = padding,
                onInspect = {
                    val permission = if (Build.VERSION.SDK_INT >= 33) {
                        Manifest.permission.NEARBY_WIFI_DEVICES
                    } else {
                        Manifest.permission.ACCESS_FINE_LOCATION
                    }
                    permissionLauncher.launch(arrayOf(permission))
                },
                onStatus = vm::remoteStatus,
                onApply = vm::applyRoute,
                onVerify = vm::verifyRoute,
                onCleanup = vm::cleanupRoute,
                onOpenWifi = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            )
        }
    }
}

@Composable
private fun SettingsScreen(state: UiState, vm: MainViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val keyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) vm.importPrivateKey(bytes)
        }
    }
    val config = state.config
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("部署配置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("所有值只保存在本机，不会进入 GitHub Actions。")
        ConfigField("校园网 CIDR", config.campusCidr) { vm.editConfig { c -> c.copy(campusCidr = it) } }
        ConfigField("校园网网关", config.expectedGateway) { vm.editConfig { c -> c.copy(expectedGateway = it) } }
        ConfigField("R5C 的 Tailscale 地址", config.sshHost) { vm.editConfig { c -> c.copy(sshHost = it) } }
        OutlinedTextField(
            value = config.sshPort.toString(),
            onValueChange = { value -> vm.editConfig { c -> c.copy(sshPort = value.toIntOrNull() ?: 0) } },
            label = { Text("SSH 端口") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        ConfigField("SSH 用户名", config.sshUser) { vm.editConfig { c -> c.copy(sshUser = it) } }
        ConfigField("helper 绝对路径", config.helperPath) { vm.editConfig { c -> c.copy(helperPath = it) } }
        ConfigField("SSH 主机 SHA-256 指纹", config.hostKeyFingerprint) {
            vm.editConfig { c -> c.copy(hostKeyFingerprint = it) }
        }

        Text("SSH 认证模式", fontWeight = FontWeight.SemiBold)
        AuthModeRow(
            selected = config.authMode == AuthMode.PUBLIC_KEY,
            title = "Dropbear/OpenSSH 公钥 over Tailscale",
            detail = "当前推荐；需要导入受限账号私钥",
        ) { vm.editConfig { c -> c.copy(authMode = AuthMode.PUBLIC_KEY) } }
        AuthModeRow(
            selected = config.authMode == AuthMode.TAILSCALE_SSH,
            title = "Tailscale 内置 SSH",
            detail = "仅在 R5C 开启内置 SSH 后使用",
        ) { vm.editConfig { c -> c.copy(authMode = AuthMode.TAILSCALE_SSH) } }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { keyLauncher.launch(arrayOf("*/*")) }) {
                Text(if (state.hasImportedKey) "重新导入私钥" else "导入私钥")
            }
            if (state.hasImportedKey) {
                OutlinedButton(onClick = vm::deletePrivateKey) { Text("删除私钥") }
            }
        }
        if (state.configErrors.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("尚需处理", fontWeight = FontWeight.Bold)
                    state.configErrors.forEach { Text("• $it") }
                }
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::saveConfig, enabled = !state.busy) { Text("保存") }
            OutlinedButton(onClick = { vm.setSettingsVisible(false) }) { Text("返回") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConfigField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun AuthModeRow(selected: Boolean, title: String, detail: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    padding: PaddingValues,
    onInspect: () -> Unit,
    onStatus: () -> Unit,
    onApply: () -> Unit,
    onVerify: () -> Unit,
    onCleanup: () -> Unit,
    onOpenWifi: () -> Unit,
) {
    var confirmAction by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.busy) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text("正在执行，请勿切换网络…")
            }
        }
        Button(onClick = onInspect, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text("读取并检查当前 Wi-Fi")
        }
        state.network?.let { NetworkCard(it) }
        state.message?.let {
            Card(Modifier.fillMaxWidth()) { Text(it, Modifier.padding(12.dp)) }
        }

        Text("R5C 操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onStatus, enabled = !state.busy && state.configErrors.isEmpty()) { Text("读取状态") }
            Button(
                onClick = { confirmAction = "apply" },
                enabled = !state.busy && state.network?.readyForRemoteChange == true,
            ) { Text("添加路由") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onVerify, enabled = !state.busy && state.managedPeer != null) { Text("重新验证") }
            OutlinedButton(onClick = { confirmAction = "cleanup" }, enabled = !state.busy && state.managedPeer != null) {
                Text("回退")
            }
        }

        state.network?.takeIf { it.ipv4 != null }?.let { StaticSettingsCard(it, onOpenWifi) }
        Text(
            "应用只能验证路由和连通性。是否已经变为 Direct，请在 Tailscale 应用或 R5C 端确认。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmAction != null) {
        val applying = confirmAction == "apply"
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(if (applying) "确认添加远端路由" else "确认删除受管路由") },
            text = {
                Text(
                    if (applying) "应用将调用受限 helper 添加当前手机地址的临时路由。网络变化后会重新读取实际状态。"
                    else "仅删除本应用记录、且经 helper 验证归属的路由。完成后仍需手动把 Wi-Fi 改回 DHCP。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAction = null
                    if (applying) onApply() else onCleanup()
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NetworkCard(network: NetworkSnapshot) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("网络信息", fontWeight = FontWeight.Bold)
            InfoLine("SSID", network.ssid ?: "未知或未授权")
            InfoLine("IPv4", network.ipv4 ?: "未取得")
            InfoLine("前缀", network.prefixLength?.toString() ?: "未知")
            InfoLine("网关", network.gateway ?: "未取得")
            InfoLine("DNS", network.dnsServers.joinToString().ifBlank { "未取得" })
            Spacer(Modifier.height(4.dp))
            network.checks.forEach { check ->
                Text("${if (check.passed) "✓" else "✗"} ${check.label}：${check.detail}")
            }
        }
    }
}

@Composable
private fun StaticSettingsCard(network: NetworkSnapshot, onOpenWifi: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("手动静态配置", fontWeight = FontWeight.Bold)
            CopyLine(context, "IP 地址", network.ipv4.orEmpty())
            CopyLine(context, "网关", network.gateway.orEmpty())
            CopyLine(context, "网络前缀长度", "32")
            network.dnsServers.take(2).forEachIndexed { index, dns -> CopyLine(context, "DNS ${index + 1}", dns) }
            Text("保存前记录原 DHCP/DNS。保持 IP 不变，只将前缀改为 32。")
            OutlinedButton(onClick = onOpenWifi) { Text("打开 Wi-Fi 设置") }
        }
    }
}

@Composable
private fun CopyLine(context: Context, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label：$value")
        TextButton(onClick = {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        }) { Text("复制") }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Text("$label：$value")
}
