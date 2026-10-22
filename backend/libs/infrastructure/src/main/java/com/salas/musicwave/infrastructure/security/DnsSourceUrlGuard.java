package com.salas.musicwave.infrastructure.security;

import com.salas.musicwave.application.security.SourceUrlGuard;
import com.salas.musicwave.application.security.UnsafeSourceUrlException;
import com.salas.musicwave.domain.download.SourceUrl;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;

public final class DnsSourceUrlGuard implements SourceUrlGuard {

    @FunctionalInterface
    public interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final AddressResolver resolver;

    public DnsSourceUrlGuard() {
        this(InetAddress::getAllByName);
    }

    DnsSourceUrlGuard(AddressResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public void assertAllowed(SourceUrl sourceUrl) {
        String host = URI.create(sourceUrl.value()).getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal")) {
            throw new UnsafeSourceUrlException("URL_DESTINATION_NOT_ALLOWED");
        }
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses.length == 0) {
                throw new UnsafeSourceUrlException("URL_HOST_UNRESOLVED");
            }
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw new UnsafeSourceUrlException("URL_DESTINATION_NOT_ALLOWED");
                }
            }
        } catch (UnknownHostException exception) {
            throw new UnsafeSourceUrlException("URL_HOST_UNRESOLVED");
        }
    }

    private boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            return first != 0 && first != 10 && first != 127
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && second == 0)
                    && !(first == 192 && second == 168)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51)
                    && !(first == 203 && second == 0)
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) != 0xfc
                    && !(first == 0x20 && Byte.toUnsignedInt(bytes[1]) == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d
                    && Byte.toUnsignedInt(bytes[3]) == 0xb8);
        }
        return false;
    }
}
