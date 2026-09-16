package io.github.hustar0311.campusdirect

import io.github.hustar0311.campusdirect.model.RemoteResult

internal data class RouteApplyDecision(
    val remoteResult: RemoteResult,
    val managedPeer: String?,
)

internal suspend fun applyCurrentPeer(
    previousPeer: String?,
    currentPeer: String,
    execute: suspend (operation: String, peer: String) -> RemoteResult,
): RouteApplyDecision {
    val result = execute("apply", currentPeer)
    return RouteApplyDecision(
        remoteResult = result,
        managedPeer = if (result.ok) currentPeer else previousPeer,
    )
}
