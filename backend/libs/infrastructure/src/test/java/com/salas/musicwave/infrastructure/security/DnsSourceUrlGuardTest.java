package com.salas.musicwave.infrastructure.security;

import com.salas.musicwave.application.security.UnsafeSourceUrlException;
import com.salas.musicwave.domain.download.SourceUrl;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DnsSourceUrlGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0", "127.0.0.1", "10.0.0.1", "169.254.1.1", "224.0.0.1",
            "100.64.0.1", "198.51.100.1", "::1", "fe80::1", "fc00::1",
            "ff02::1", "2001:db8::1"
    })
    void rejectsNonPublicAddressClasses(String address) throws Exception {
        DnsSourceUrlGuard guard = new DnsSourceUrlGuard(host -> new InetAddress[] {
                InetAddress.getByName(address)
        });

        assertEquals("URL_DESTINATION_NOT_ALLOWED", assertThrows(
                UnsafeSourceUrlException.class,
                () -> guard.assertAllowed(SourceUrl.parse("https://example.com/audio"))).reason());
    }

    @Test
    void acceptsOnlyWhenEveryResolvedAddressIsPublic() throws Exception {
        DnsSourceUrlGuard guard = new DnsSourceUrlGuard(host -> new InetAddress[] {
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("1.1.1.1")
        });

        assertDoesNotThrow(() -> guard.assertAllowed(SourceUrl.parse("https://example.com/audio")));
    }

    @Test
    void rejectsPrivateLoopbackAndMixedDnsAnswers() throws Exception {
        DnsSourceUrlGuard guard = new DnsSourceUrlGuard(host -> new InetAddress[] {
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("10.0.0.7")
        });

        UnsafeSourceUrlException exception = assertThrows(
                UnsafeSourceUrlException.class,
                () -> guard.assertAllowed(SourceUrl.parse("https://example.com/audio")));

        assertEquals("URL_DESTINATION_NOT_ALLOWED", exception.reason());
    }

    @Test
    void rejectsLocalNamesWithoutResolvingThem() {
        DnsSourceUrlGuard guard = new DnsSourceUrlGuard(host -> {
            throw new AssertionError("resolver must not be called");
        });

        assertEquals("URL_DESTINATION_NOT_ALLOWED", assertThrows(
                UnsafeSourceUrlException.class,
                () -> guard.assertAllowed(SourceUrl.parse("http://service.local/file"))).reason());
    }

    @Test
    void rejectsUnresolvedHostsWithStableReason() {
        DnsSourceUrlGuard guard = new DnsSourceUrlGuard(host -> {
            throw new UnknownHostException(host);
        });

        assertEquals("URL_HOST_UNRESOLVED", assertThrows(
                UnsafeSourceUrlException.class,
                () -> guard.assertAllowed(SourceUrl.parse("https://missing.example/file"))).reason());
    }
}
