package io.github.hustar0311.campusdirect.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkSelectionTest {
    @Test
    fun ignoresVpnThatInheritsWifiTransport() {
        val selected = selectPhysicalWifiCandidate(
            listOf(
                WifiCandidate("vpn", hasWifi = true, hasVpn = true, campusIpv4Count = 0),
                WifiCandidate("wifi", hasWifi = true, hasVpn = false, campusIpv4Count = 1),
            ),
        )

        assertEquals("wifi", selected)
    }

    @Test
    fun prefersPhysicalWifiWithCampusAddress() {
        val selected = selectPhysicalWifiCandidate(
            listOf(
                WifiCandidate("other-wifi", hasWifi = true, hasVpn = false, campusIpv4Count = 0),
                WifiCandidate("campus-wifi", hasWifi = true, hasVpn = false, campusIpv4Count = 1),
            ),
        )

        assertEquals("campus-wifi", selected)
    }

    @Test
    fun keepsPhysicalWifiForUsefulDiagnosticsWhenCidrDoesNotMatch() {
        val selected = selectPhysicalWifiCandidate(
            listOf(WifiCandidate("wifi", hasWifi = true, hasVpn = false, campusIpv4Count = 0)),
        )

        assertEquals("wifi", selected)
    }

    @Test
    fun returnsNullWhenOnlyVpnOrCellularNetworksExist() {
        val selected = selectPhysicalWifiCandidate(
            listOf(
                WifiCandidate("vpn", hasWifi = true, hasVpn = true, campusIpv4Count = 0),
                WifiCandidate("cellular", hasWifi = false, hasVpn = false, campusIpv4Count = 0),
            ),
        )

        assertNull(selected)
    }
}
