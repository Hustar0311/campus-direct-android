package io.github.hustar0311.campusdirect.network

@JvmInline
value class Ipv4Value private constructor(val bits: Long) {
    override fun toString(): String = listOf(24, 16, 8, 0)
        .joinToString(".") { shift -> ((bits shr shift) and 255).toString() }

    companion object {
        fun parse(text: String): Ipv4Value {
            val parts = text.trim().split('.')
            require(parts.size == 4) { "IPv4 must contain four octets" }
            var value = 0L
            parts.forEach { part ->
                require(part.isNotEmpty() && part.all(Char::isDigit)) { "Invalid IPv4 octet" }
                require(part.length == 1 || part.first() != '0') { "Leading zero is not allowed" }
                val octet = part.toIntOrNull() ?: error("Invalid IPv4 octet")
                require(octet in 0..255) { "IPv4 octet out of range" }
                value = (value shl 8) or octet.toLong()
            }
            return Ipv4Value(value)
        }
    }
}

data class Ipv4Cidr private constructor(
    val network: Ipv4Value,
    val prefixLength: Int,
) {
    private val mask: Long = if (prefixLength == 0) 0 else (0xffffffffL shl (32 - prefixLength)) and 0xffffffffL
    private val broadcastBits: Long = network.bits or (mask xor 0xffffffffL)

    fun contains(value: Ipv4Value): Boolean = (value.bits and mask) == network.bits
    fun isNetworkOrBroadcast(value: Ipv4Value): Boolean = value.bits == network.bits || value.bits == broadcastBits

    companion object {
        fun parse(text: String): Ipv4Cidr {
            val parts = text.trim().split('/')
            require(parts.size == 2) { "CIDR must contain a prefix" }
            val address = Ipv4Value.parse(parts[0])
            val prefix = parts[1].toIntOrNull() ?: error("Invalid prefix")
            require(prefix in 0..32) { "Prefix out of range" }
            val mask = if (prefix == 0) 0 else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
            require((address.bits and mask) == address.bits) { "CIDR address is not the network address" }
            return Ipv4Cidr(address, prefix)
        }
    }
}
