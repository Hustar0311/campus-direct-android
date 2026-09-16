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

    private fun verificationJson(direct: Boolean, endpointMatches: Boolean): String = """
        {
          "ok": true,
          "verification": {
            "tailnet_reachable": true,
            "direct": $direct,
            "direct_endpoint_ip": "peer-address",
            "endpoint_matches_peer": $endpointMatches,
            "campus_ping_reachable": true,
            "lease_fresh": true,
            "snat_consistent": true,
            "watchdog_active": true,
            "watchdog_status": "active",
            "lease_remaining_seconds": 321
          }
        }
    """.trimIndent()
}
