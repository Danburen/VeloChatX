package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.spi.CommandSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** Recording {@link CommandSource} for {@code CommandHelper} tests. */
public final class FakeCommandSource implements CommandSource {

    private final String name;
    private final UUID uuid;
    private final Locale locale;
    private final boolean player;
    private final Set<String> permissions = ConcurrentHashMap.newKeySet();

    public final List<String> messages = new CopyOnWriteArrayList<>();
    public final List<Object> components = new CopyOnWriteArrayList<>();

    private FakeCommandSource(String name, UUID uuid, Locale locale, boolean player) {
        this.name = name;
        this.uuid = uuid;
        this.locale = locale;
        this.player = player;
    }

    public static FakeCommandSource console() {
        return new FakeCommandSource("CONSOLE", null, null, false);
    }

    public static FakeCommandSource player(String name, UUID uuid, Locale locale, String... permissions) {
        FakeCommandSource source = new FakeCommandSource(name, uuid, locale, true);
        for (String permission : permissions) {
            source.permissions.add(permission);
        }
        return source;
    }

    public FakeCommandSource grant(String permission) {
        permissions.add(permission);
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Optional<UUID> getUniqueId() {
        return Optional.ofNullable(uuid);
    }

    @Override
    public boolean isPlayer() {
        return player;
    }

    @Override
    public Optional<Locale> getLocale() {
        return Optional.ofNullable(locale);
    }

    @Override
    public void sendMessage(String message) {
        messages.add(message);
    }

    @Override
    public void sendComponent(Object component) {
        components.add(component);
    }

    @Override
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    @Override
    public <T> T toNative() {
        @SuppressWarnings("unchecked")
        T self = (T) this;
        return self;
    }
}
