package io.github.hustar0311.campusdirect

import io.github.hustar0311.campusdirect.model.VerificationResult

internal data class VerificationDisplayItem(
    val label: String,
    val value: String,
    val passed: Boolean?,
)

internal fun verificationDisplayItems(result: VerificationResult): List<VerificationDisplayItem> = listOf(
    VerificationDisplayItem("Tailnet 在线", result.tailnetReachable.asText(), result.tailnetReachable),
    VerificationDisplayItem("Tailscale Direct", result.direct.asText(), result.direct),
    VerificationDisplayItem("Direct 端点", result.directEndpoint ?: result.directEndpointIp ?: "未取得", null),
    VerificationDisplayItem("端点匹配校园网 IP", result.endpointMatchesPeer.asText(), result.endpointMatchesPeer),
    VerificationDisplayItem("校园网 ICMP", result.campusPingReachable.asText(), result.campusPingReachable),
    VerificationDisplayItem("R5C 租约有效", result.leaseFresh.asText(), result.leaseFresh),
    VerificationDisplayItem("SNAT 一致", result.snatConsistent.asText(), result.snatConsistent),
    VerificationDisplayItem("helper 状态归属", result.stateOwned.asText(), result.stateOwned),
    VerificationDisplayItem("路由精确匹配", result.routeExact.asText(), result.routeExact),
    VerificationDisplayItem(
        "租约剩余",
        result.leaseRemainingSeconds?.let { "${it.coerceAtLeast(0)} 秒" } ?: "未知",
        result.leaseRemainingSeconds?.let { it > 0 },
    ),
    VerificationDisplayItem(
        "Watchdog",
        result.watchdogStatus ?: result.watchdogEnabled.asText(),
        result.watchdogActive ?: result.watchdogEnabled,
    ),
    VerificationDisplayItem("综合所有权验证", result.verifiedOwner.asText(), result.verifiedOwner),
)

private fun Boolean?.asText(): String = when (this) {
    true -> "通过"
    false -> "未通过"
    null -> "未知"
}
