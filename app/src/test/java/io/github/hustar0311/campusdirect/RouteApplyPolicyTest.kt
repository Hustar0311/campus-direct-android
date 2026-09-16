package io.github.hustar0311.campusdirect

import io.github.hustar0311.campusdirect.model.RemoteResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteApplyPolicyTest {
    @Test
    fun samePeerStillCallsApplyExactlyOnce() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()

        val decision = applyCurrentPeer("same-peer", "same-peer") { operation, peer ->
            calls += operation to peer
            RemoteResult(true, operation, message = "ok")
        }

        assertEquals(listOf("apply" to "same-peer"), calls)
        assertEquals("same-peer", decision.managedPeer)
        assertTrue(decision.remoteResult.ok)
    }

    @Test
    fun changedPeerCallsApplyDirectlyWithoutCleanup() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()

        val decision = applyCurrentPeer("old-peer", "new-peer") { operation, peer ->
            calls += operation to peer
            RemoteResult(true, operation, message = "ok")
        }

        assertEquals(listOf("apply" to "new-peer"), calls)
        assertEquals("new-peer", decision.managedPeer)
    }

    @Test
    fun failedApplyRetainsPreviousManagedPeer() = runBlocking {
        val decision = applyCurrentPeer("old-peer", "new-peer") { operation, _ ->
            RemoteResult(false, operation, message = "rejected")
        }

        assertFalse(decision.remoteResult.ok)
        assertEquals("old-peer", decision.managedPeer)
    }
}
