package io.github.hustar0311.campusdirect.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4Test {
    @Test
    fun parsesAndFormatsCanonicalAddress() {
        val address = ip(192, 0, 2, 7)
        assertEquals(address, Ipv4Value.parse(address).toString())
    }

    @Test
    fun rejectsLeadingZero() {
        val invalid = listOf("01", "2", "3", "4").joinToString(".")
        assertThrows(IllegalArgumentException::class.java) { Ipv4Value.parse(invalid) }
    }

    @Test
    fun cidrContainsOnlyMatchingAddresses() {
        val cidr = Ipv4Cidr.parse(cidr(198, 51, 100, 0, 24))
        assertTrue(cidr.contains(Ipv4Value.parse(ip(198, 51, 100, 17))))
        assertFalse(cidr.contains(Ipv4Value.parse(ip(203, 0, 113, 17))))
    }

    @Test
    fun detectsNetworkAndBroadcastAddresses() {
        val cidr = Ipv4Cidr.parse(cidr(203, 0, 113, 0, 24))
        assertTrue(cidr.isNetworkOrBroadcast(Ipv4Value.parse(ip(203, 0, 113, 0))))
        assertTrue(cidr.isNetworkOrBroadcast(Ipv4Value.parse(ip(203, 0, 113, 255))))
        assertFalse(cidr.isNetworkOrBroadcast(Ipv4Value.parse(ip(203, 0, 113, 1))))
    }

    @Test
    fun rejectsCidrWithHostBits() {
        assertThrows(IllegalArgumentException::class.java) {
            Ipv4Cidr.parse(cidr(192, 0, 2, 1, 24))
        }
    }

    private fun ip(a: Int, b: Int, c: Int, d: Int) = listOf(a, b, c, d).joinToString(".")
    private fun cidr(a: Int, b: Int, c: Int, d: Int, prefix: Int) = "${ip(a, b, c, d)}/$prefix"
}
