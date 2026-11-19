package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.shared.common.WColor;
import org.nitrowater.waterapi.domain.spi.ColoredMessageSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Recording {@link ColoredMessageSender}. Captures player-targeted messages, server
 * broadcasts, proxy broadcasts and component sends in arrival order.
 */
public final class FakeColoredMessageSender implements ColoredMessageSender {

    /** A message sent to one player. {@code color} is {@code null} in auto mode. */
    public record PlayerMessage(UPlayer player, String message, WColor color) {
    }

    /** A message broadcast to one backend. */
    public record ServerBroadcast(String serverName, String message, WColor color) {
    }

    public record ComponentSend(UPlayer player, Object component) {
    }

    public record TitleSend(UPlayer player, String main, String sub, int fadeIn, int stay, int fadeOut) {
    }

    public final List<PlayerMessage> playerMessages = new CopyOnWriteArrayList<>();
    public final List<ComponentSend> componentSends = new CopyOnWriteArrayList<>();
    public final List<ServerBroadcast> serverBroadcasts = new CopyOnWriteArrayList<>();
    public final List<String> allBroadcasts = new CopyOnWriteArrayList<>();
    public final List<TitleSend> titles = new CopyOnWriteArrayList<>();
    public final List<String> sounds = new CopyOnWriteArrayList<>();

    public List<String> playerMessageTexts(UPlayer player) {
        return playerMessages.stream()
                .filter(message -> message.player().getUuid().equals(player.getUuid()))
                .map(PlayerMessage::message)
                .toList();
    }

    public List<String> serverBroadcastTexts(String serverName) {
        return serverBroadcasts.stream()
                .filter(broadcast -> broadcast.serverName().equals(serverName))
                .map(ServerBroadcast::message)
                .toList();
    }

    @Override
    public void sendMessage(UPlayer player, String message) {
        playerMessages.add(new PlayerMessage(player, message, null));
    }

    @Override
    public void broadcastToServer(UServer uServer, String message) {
        serverBroadcasts.add(new ServerBroadcast(uServer.getName(), message, null));
    }

    @Override
    public void broadcastToAll(String message) {
        allBroadcasts.add(message);
    }

    @Override
    public void sendMessage(UPlayer player, String message, WColor color) {
        playerMessages.add(new PlayerMessage(player, message, color));
    }

    @Override
    public void broadcastToServer(String serverName, String message, WColor color) {
        serverBroadcasts.add(new ServerBroadcast(serverName, message, color));
    }

    @Override
    public void broadcastToAll(String message, WColor color) {
        allBroadcasts.add(message);
    }

    @Override
    public void sendComponent(UPlayer player, Object component) {
        componentSends.add(new ComponentSend(player, component));
    }

    @Override
    public void showTitle(UPlayer player, String main, String sub, int fadeIn, int stay, int fadeOut) {
        titles.add(new TitleSend(player, main, sub, fadeIn, stay, fadeOut));
    }

    @Override
    public void playSound(UPlayer player, String soundKey) {
        sounds.add(soundKey);
    }

    @Override
    public <T> T toNative() {
        @SuppressWarnings("unchecked")
        T self = (T) this;
        return self;
    }
}
