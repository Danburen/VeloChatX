package org.nitrowater.velochatx.testsupport;

import net.kyori.adventure.text.Component;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.spi.TabListProvider;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Recording {@link TabListProvider}. */
public final class RecordingTabListProvider implements TabListProvider {

    public record HeaderFooter(UPlayer player, Component header, Component footer) {
    }

    public record EntryChange(UPlayer source, UPlayer target) {
    }

    public record RemovedEntry(UUID sourceUuid, UPlayer target) {
    }

    public final List<HeaderFooter> headerFooters = new CopyOnWriteArrayList<>();
    public final List<HeaderFooter> displayNameUpdates = new CopyOnWriteArrayList<>();
    public final List<EntryChange> addedEntries = new CopyOnWriteArrayList<>();
    public final List<RemovedEntry> removedEntries = new CopyOnWriteArrayList<>();

    @Override
    public void sendHeaderAndFooter(UPlayer player, Component header, Component footer) {
        headerFooters.add(new HeaderFooter(player, header, footer));
    }

    @Override
    public void addEntry(UPlayer source, UPlayer target, Component displayName) {
        addedEntries.add(new EntryChange(source, target));
    }

    @Override
    public void removeEntry(UUID sourceUuid, UPlayer target) {
        removedEntries.add(new RemovedEntry(sourceUuid, target));
    }

    @Override
    public void updateDisplayName(UPlayer player, Component displayName) {
        displayNameUpdates.add(new HeaderFooter(player, displayName, null));
    }
}
