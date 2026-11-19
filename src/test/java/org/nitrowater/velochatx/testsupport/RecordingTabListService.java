package org.nitrowater.velochatx.testsupport;

import org.nitrowater.velochatx.domain.tablist.TabListService;
import org.nitrowater.velochatx.domain.tablist.TabListUpdateTask;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.shared.config.Config;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Recording {@link TabListService} so event-handler tests can assert queued update tasks. */
public final class RecordingTabListService implements TabListService {

    private final Config config;

    public final List<TabListUpdateTask> queued = new CopyOnWriteArrayList<>();
    public int startCount;
    public int stopCount;
    public int refreshCount;

    public RecordingTabListService(Config config) {
        this.config = config;
    }

    public RecordingTabListService() {
        this(TestConfigs.config());
    }

    public List<TabListUpdateTask> queuedOfType(TabListUpdateTask.UpdateType type) {
        return queued.stream().filter(task -> task.getType() == type).toList();
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public void onEvent(KernelConfigUpdateEvent event) {
        // no-op
    }

    @Override
    public void updateHeaderAndFooter(UPlayer player) {
        // no-op
    }

    @Override
    public void updateTabListEntry(UPlayer player) {
        // no-op
    }

    @Override
    public void addPlayerToTabList(UPlayer sourcePlayer, UPlayer targetPlayer) {
        // no-op
    }

    @Override
    public void removePlayerFromTabList(UUID sourceUuid, UPlayer targetPlayer) {
        // no-op
    }

    @Override
    public void refreshAllTabLists() {
        refreshCount++;
    }

    @Override
    public void queueUpdate(TabListUpdateTask task) {
        queued.add(task);
    }

    @Override
    public void startConsumer() {
        startCount++;
    }

    @Override
    public void stopConsumer() {
        stopCount++;
    }
}
