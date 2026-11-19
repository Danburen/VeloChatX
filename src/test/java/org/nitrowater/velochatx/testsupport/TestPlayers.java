package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.spi.PlayerPlatformStrategy;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates {@link UPlayer} instances without a live Velocity proxy.
 * <p>The player's current server is carried by the native holder and resolved by a fake
 * {@link PlayerPlatformStrategy}; UUIDs are deterministic per name so tests can correlate
 * log/DB rows across calls.</p>
 */
public final class TestPlayers {

    public static final PlayerPlatformStrategy STRATEGY = new FakeStrategy();

    private TestPlayers() {
    }

    /** A player online on {@code serverName}, using {@link Locale#ENGLISH}. */
    public static UPlayer online(FakeWProxyServer proxy, String name, String serverName) {
        return online(proxy, name, serverName, Locale.ENGLISH);
    }

    /** A player online on {@code serverName} with an explicit locale. */
    public static UPlayer online(FakeWProxyServer proxy, String name, String serverName, Locale locale) {
        UPlayer player = create(proxy, name, serverName, locale);
        proxy.addPlayer(player);
        return player;
    }

    /** A player not attached to any server (disconnected). */
    public static UPlayer offline(FakeWProxyServer proxy, String name) {
        return create(proxy, name, null, Locale.ENGLISH);
    }

    /** Create a player without adding it to the proxy's online list. */
    public static UPlayer create(FakeWProxyServer proxy, String name, String serverName, Locale locale) {
        NativePlayer nativePlayer = new NativePlayer(uuidOf(name), name, locale, serverName);
        return new UPlayer(nativePlayer, STRATEGY, proxy);
    }

    public static UUID uuidOf(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    /** Native holder consumed by {@link FakeStrategy}. */
    public record NativePlayer(UUID uuid, String name, Locale locale, String serverName) {
    }

    private static final class FakeStrategy implements PlayerPlatformStrategy {

        @Override
        public UUID extractUuid(Object player) {
            return ((NativePlayer) player).uuid();
        }

        @Override
        public String extraLoginName(Object player) {
            return ((NativePlayer) player).name();
        }

        @Override
        public Locale extractLocale(Object nativePlayer) {
            return ((NativePlayer) nativePlayer).locale();
        }

        @Override
        public Long getPlayerPing(Object nativePlayer) {
            return 42L;
        }

        @Override
        public Optional<UServer> getPlayerCurrentServer(Object nativePlayer, WProxyServer proxyServer) {
            String serverName = ((NativePlayer) nativePlayer).serverName();
            if (serverName == null) {
                return Optional.empty();
            }
            // UServer equality is by name. Resolve through the fake proxy so constructing the
            // view never re-enters getServerCurrentUsers() (UServer's constructor reads players).
            if (proxyServer instanceof FakeWProxyServer fake) {
                return Optional.of(fake.serverView(serverName));
            }
            return Optional.of(new UServer(serverName, serverName, proxyServer));
        }
    }
}
