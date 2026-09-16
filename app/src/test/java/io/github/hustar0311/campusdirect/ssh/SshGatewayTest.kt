package io.github.hustar0311.campusdirect.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SshGatewayTest {
    private val gateway = SshGateway()

    @Test
    fun completePositiveSignalsVerifyOwnership() {
        val result = gateway.parseResult(
            "verify",
            0,
            verificationJson(direct = true, endpointMatches = true),
        )

        assertTrue(result.ok)
        assertTrue(result.verification?.verifiedOwner == true)
        assertEquals(321L, result.verification?.leaseRemainingSeconds)
        assertEquals("active", result.verification?.watchdogStatus)
        assertTrue(result.verification?.stateOwned == true)
        assertTrue(result.verification?.routeExact == true)
    }

    @Test
    fun pingDoesNotOverrideMissingDirectPath() {
        val result = gateway.parseResult(
            "verify",
            0,
            verificationJson(direct = false, endpointMatches = true),
        )

        assertTrue(result.verification?.campusPingReachable == true)
        assertFalse(result.verification?.verifiedOwner ?: true)
    }

    @Test
    fun endpointMismatchFailsOwnership() {
        val result = gateway.parseResult(
            "verify",
            0,
            verificationJson(direct = true, endpointMatches = false),
        )

        assertFalse(result.verification?.verifiedOwner ?: true)
    }

    @Test
    fun legacyReachabilityAloneNeverInfersOwnership() {
        val result = gateway.parseResult("verify", 0, """{"ok":true,"reachable":true}""")

        assertTrue(result.reachable == true)
        assertTrue(result.verification?.campusPingReachable == true)
        assertNull(result.verification?.verifiedOwner)
        assertFalse(result.message.contains("所有权验证通过"))
    }

    @Test
    fun statusContractPreservesWatchdogAndLeaseFields() {
        val result = gateway.parseResult(
            "status",
            0,
            """
                {
                  "ok": true,
                  "action": "status",
                  "watchdog_enabled": true,
                  "watchdog_interval": 300,
                  "stale_timeout": 900,
                  "endpoint_mismatch_confirmations": 3,
                  "last_probe": "probe-time",
                  "last_direct": "direct-time",
                  "failure_count": 2,
                  "mismatch_count": 1,
                  "status": "expired",
                  "lease_deadline": 12345,
                  "lease_remaining_seconds": 0,
                  "lease_fresh": false,
                  "expired": true,
                  "watchdog_managed_peer": "peer-address"
                }
            """.trimIndent(),
        )

        val verification = result.verification
        assertTrue(verification?.watchdogEnabled == true)
        assertEquals(300L, verification?.watchdogIntervalSeconds)
        assertEquals(900L, verification?.staleTimeoutSeconds)
        assertEquals(2L, verification?.failureCount)
        assertEquals("expired", verification?.watchdogStatus)
        assertFalse(verification?.verifiedOwner ?: true)
    }

    private fun verificationJson(direct: Boolean, endpointMatches: Boolean): String = """
        {
          "ok": true,
          "action": "verify",
          "peer": "peer-address",
          "tailnet_reachable": true,
          "direct": $direct,
          "direct_endpoint": "peer-address:port",
          "direct_endpoint_ip": "peer-address",
          "endpoint_matches_peer": $endpointMatches,
          "campus_ping_reachable": true,
          "lease_fresh": true,
          "snat_consistent": true,
          "verified_owner": true,
          "state_owned": true,
          "route_exact": true,
          "status": "active",
          "lease_remaining_seconds": 321
        }
    """.trimIndent()
}
