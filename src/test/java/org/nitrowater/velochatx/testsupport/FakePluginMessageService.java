package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.kernel.services.PluginMessageService;
import org.nitrowater.waterapi.domain.shared.common.MessageTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal {@link PluginMessageService} used by the event-handler tests. */
public final class FakePluginMessageService implements PluginMessageService {

    public final List<String> loadedLocales = new CopyOnWriteArrayList<>();
    private final Map<String, String> messages = new HashMap<>();
    private volatile boolean failLoadLocale;

    public FakePluginMessageService put(String key, String value) {
        messages.put(key, value);
        return this;
    }

    public void failLoadLocale(boolean fail) {
        this.failLoadLocale = fail;
    }

    @Override
    public String getPluginMessage(String key) {
        return messages.getOrDefault(key, key);
    }

    @Override
    public String getPluginMessage(String key, Object... args) {
        return format(getPluginMessage(key), args);
    }

    @Override
    public void loadLocale(String lang) throws IOException {
        if (failLoadLocale) {
            throw new IOException("simulated locale failure");
        }
        loadedLocales.add(lang);
    }

    @Override
    public String getPluginMessage(MessageTemplate templateKey) {
        return getPluginMessage(templateKey.getKey());
    }

    @Override
    public String getPluginMessage(MessageTemplate messageTemplate, Object... args) {
        return format(getPluginMessage(messageTemplate.getKey()), args);
    }

    private static String format(String template, Object... args) {
        if (template == null || args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(template, args);
        } catch (RuntimeException e) {
            return template;
        }
    }

    @Override
    public void reload() {
        // no-op
    }
}
