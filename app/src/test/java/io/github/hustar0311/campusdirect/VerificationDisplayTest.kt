package io.github.hustar0311.campusdirect

import io.github.hustar0311.campusdirect.model.VerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationDisplayTest {
    @Test
    fun rendersDistinctConnectivityAndOwnershipRows() {
        val items = verificationDisplayItems(
            VerificationResult(
                direct = false,
                directEndpointIp = "peer-address",
                campusPingReachable = true,
                verifiedOwner = false,
                leaseRemainingSeconds = 12,
            ),
        )

        val ping = items.single { it.label == "校园网 ICMP" }
        val owner = items.single { it.label == "综合所有权验证" }
        val lease = items.single { it.label == "租约剩余" }

        assertTrue(ping.passed == true)
        assertFalse(owner.passed ?: true)
        assertEquals("12 秒", lease.value)
    }
}
