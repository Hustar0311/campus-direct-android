package io.github.hustar0311.campusdirect.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import io.github.hustar0311.campusdirect.model.AppConfig
import io.github.hustar0311.campusdirect.model.CheckResult
import io.github.hustar0311.campusdirect.model.NetworkSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

class NetworkInspector(context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    @SuppressLint("MissingPermission")
    suspend fun inspect(config: AppConfig): NetworkSnapshot = withContext(Dispatchers.IO) {
        val cidr = Ipv4Cidr.parse(config.campusCidr)
        val gatewayExpected = Ipv4Value.parse(config.expectedGateway)
        val wifiNetwork = selectPhysicalWifiCandidate(
            connectivityManager.allNetworks.map { network ->
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                val networkProperties = connectivityManager.getLinkProperties(network)
                val campusIpv4Count = networkProperties?.linkAddresses.orEmpty().count { link ->
                    link.address is Inet4Address && runCatching {
                        cidr.contains(Ipv4Value.parse(link.address.hostAddress.orEmpty()))
                    }.getOrDefault(false)
                }
                WifiCandidate(
                    value = network,
                    hasWifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                    hasVpn = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
                    campusIpv4Count = campusIpv4Count,
                )
            },
        )
        val capabilities = wifiNetwork?.let(connectivityManager::getNetworkCapabilities)
        val properties = wifiNetwork?.let(connectivityManager::getLinkProperties)
        val isWifi = wifiNetwork != null
        val wifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities?.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }
        val ssid = wifiInfo?.ssid
            ?.takeUnless { it.isBlank() || it.contains("unknown", ignoreCase = true) }

        val allIpv4 = properties?.linkAddresses.orEmpty()
            .filter { it.address is Inet4Address }
        val candidates = allIpv4.filter { link ->
            runCatching { cidr.contains(Ipv4Value.parse(link.address.hostAddress.orEmpty())) }.getOrDefault(false)
        }
        val candidate = candidates.singleOrNull()
        val candidateValue = candidate?.address?.hostAddress?.let { Ipv4Value.parse(it) }
        val gateway = properties?.routes.orEmpty()
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val gatewayValue = gateway?.let { runCatching { Ipv4Value.parse(it) }.getOrNull() }
        val sshReachable = runCatching { canConnect(config.sshHost, config.sshPort) }.getOrDefault(false)

        val checks = buildList {
            add(result("底层网络", isWifi, if (isWifi) "物理 Wi-Fi" else "未找到物理 Wi-Fi"))
            add(result("校园网 IPv4", candidate != null, if (candidate != null) "已找到唯一候选" else "候选地址必须恰好一个"))
            add(result(
                "IPv4 地址范围",
                candidateValue != null && !cidr.isNetworkOrBroadcast(candidateValue) && candidateValue != gatewayExpected,
                if (candidateValue == null) "无法检查" else "地址不是网络、广播或网关地址",
            ))
            val prefixOk = candidate?.prefixLength == cidr.prefixLength || candidate?.prefixLength == 32
            add(result("网络前缀", prefixOk, candidate?.prefixLength?.toString() ?: "未知"))
            add(result("默认网关", gatewayValue == gatewayExpected, if (gateway == null) "未找到 IPv4 默认网关" else "与配置比较完成"))
            add(result("R5C SSH 端口", sshReachable, if (sshReachable) "TCP 可连接" else "TCP 不可连接"))
        }

        NetworkSnapshot(
            isWifi = isWifi,
            ssid = ssid,
            ipv4 = candidate?.address?.hostAddress,
            prefixLength = candidate?.prefixLength,
            gateway = gateway,
            dnsServers = properties?.dnsServers.orEmpty().filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
            sshPortReachable = sshReachable,
            checks = checks,
        )
    }

    private fun canConnect(host: String, port: Int): Boolean = Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        true
    }

    private fun result(label: String, passed: Boolean, detail: String) = CheckResult(label, passed, detail)
    private fun failed(label: String, detail: String) = CheckResult(label, false, detail)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 4_000
    }
}

internal data class WifiCandidate<T>(
    val value: T,
    val hasWifi: Boolean,
    val hasVpn: Boolean,
    val campusIpv4Count: Int,
)

internal fun <T> selectPhysicalWifiCandidate(candidates: List<WifiCandidate<T>>): T? {
    val physicalWifi = candidates.filter { it.hasWifi && !it.hasVpn }
    return physicalWifi.firstOrNull { it.campusIpv4Count > 0 }?.value
        ?: physicalWifi.firstOrNull()?.value
}
