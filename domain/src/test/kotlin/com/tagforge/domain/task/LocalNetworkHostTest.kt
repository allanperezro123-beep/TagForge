package com.tagforge.domain.task

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure string classification of "does this URL authority point inside the user's own network". No DNS anywhere. */
class LocalNetworkHostTest {

    private fun assertLocal(vararg hosts: String) = hosts.forEach { assertTrue(LocalNetworkHost.isLocal(it), "expected local: $it") }

    private fun assertPublic(vararg hosts: String) = hosts.forEach { assertFalse(LocalNetworkHost.isLocal(it), "expected public: $it") }

    // ---- canonical IPv4 -----------------------------------------------------------------------------------------

    @Test
    fun privateLoopbackLinkLocalCgnatAndThisNetworkRangesAreLocal() {
        assertLocal(
            "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.254", "192.168.0.1", "192.168.1.1",
            "127.0.0.1", "127.255.0.3", "169.254.169.254", "100.64.0.1", "100.127.255.255", "0.0.0.0", "0.1.2.3",
            "255.255.255.255",
        )
    }

    @Test
    fun addressesJustOutsideThoseRangesArePublic() {
        assertPublic(
            "8.8.8.8", "1.1.1.1", "11.0.0.1", "9.255.255.255", "172.15.255.255", "172.32.0.1", "192.167.1.1", "192.169.1.1",
            "126.255.255.255", "128.0.0.1", "169.253.1.1", "169.255.0.1", "100.63.255.255", "100.128.0.1", "255.255.255.254",
        )
    }

    // ---- spelling variations, case, dot and port --------------------------------------------------------------------

    @Test
    fun aPortAUserInfoFreeAuthorityAndATrailingDotAreIgnored() {
        assertLocal("192.168.1.1:8080", "192.168.1.1.", "192.168.1.1.:443", "ROUTER:80", "Localhost:3000", "LOCALHOST.", "NAS.LOCAL:8443")
        assertPublic("8.8.8.8:53", "8.8.8.8.", "EXAMPLE.COM", "Example.Com:8443", "example.com.")
    }

    @Test
    fun numericSpellingsThatHttpClientsAcceptAreTreatedAsLocal() {
        assertLocal(
            "2130706433",      // 127.0.0.1 as one decimal number
            "3232235777",      // 192.168.1.1
            "0x7f.0.0.1", "0X7F000001", "0xc0.0xa8.0x01.0x01",
            "0177.0.0.1", "0300.0250.01.01",
            "127.1", "10.1", "192.168.257", "127.0.1",
            "0x7f.1",
        )
    }

    @Test
    fun aCanonicalPublicDottedQuadStaysPublic() {
        assertPublic("8.8.8.8", "93.184.216.34", "203.0.113.9", "172.217.14.206")
    }

    // ---- IPv6 --------------------------------------------------------------------------------------------------

    @Test
    fun localIpv6LiteralsAreLocal() {
        assertLocal(
            "[::1]", "[::1]:8080", "[::]", "[fc00::1]", "[FD12:3456:789a::1]", "[fe80::1]", "[fe80::1%25eth0]", "[febf::1]",
            "[::ffff:192.168.1.1]", "[::ffff:c0a8:0101]", "[::ffff:7f00:1]", "[::ffff:10.0.0.1]:80", "[::127.0.0.1]", "[::c0a8:101]",
            "[0:0:0:0:0:0:0:1]", "[0000:0000:0000:0000:0000:ffff:0a00:0001]",
        )
    }

    @Test
    fun publicIpv6LiteralsStayPublic() {
        assertPublic(
            "[2606:4700:4700::1111]", "[2606:4700:4700::1111]:443", "[2001:db8::1]", "[fe00::1]", "[::ffff:8.8.8.8]",
            "[::ffff:808:808]", "[fb00::1]", "[fe::1]",
        )
    }

    // ---- names -------------------------------------------------------------------------------------------------

    @Test
    fun reservedLocalNamesAndSingleLabelHostsAreLocal() {
        assertLocal(
            "localhost", "LocalHost", "app.localhost", "printer.local", "nas.lan", "tv.home", "gateway.internal", "wiki.intranet",
            "router.home.arpa", "home.arpa", "router", "nas", "a", "my-plug",
        )
    }

    @Test
    fun ordinaryPublicNamesAreNotLocal() {
        assertPublic(
            "example.com", "www.example.com", "hooks.zapier.com", "api.example.co.uk", "local.example.com", "internal.example.com",
            "mylocal.com", "home.com", "lan.io", "localhost.example.com", "192.168.1.1.example.com", "10.example.com",
        )
    }
}
