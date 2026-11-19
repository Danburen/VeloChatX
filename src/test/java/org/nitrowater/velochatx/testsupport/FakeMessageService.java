package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.message.MessageService;
import org.nitrowater.waterapi.domain.shared.common.MessageTemplate;
import org.nitrowater.waterapi.domain.shared.config.ConfigData;
import org.nitrowater.waterapi.domain.shared.config.FileConfigData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link MessageService}. Supports per-language overrides via
 * {@code put(lang, key, value)}; unknown keys return {@code MISSING:<key>} so assertions fail
 * loudly instead of silently rendering an empty string.
 */
public final class FakeMessageService implements MessageService {

    private final Map<String, String> messages = new ConcurrentHashMap<>();
    private final Set<String> languages = ConcurrentHashMap.newKeySet();

    public FakeMessageService put(String key, String value) {
        messages.put(key, value);
        return this;
    }

    public FakeMessageService put(String lang, String key, String value) {
        languages.add(lang);
        messages.put(lang + "|" + key, value);
        return this;
    }

    @Override
    public String getMessage(String key) {
        return messages.getOrDefault(key, "MISSING:" + key);
    }

    @Override
    public String getMessage(String key, String lang) {
        return messages.getOrDefault(lang + "|" + key, getMessage(key));
    }

    @Override
    public boolean isLangExist(String lang) {
        return languages.contains(lang);
    }

    @Override
    public String getMessage(MessageTemplate template, String lang) {
        return getMessage(template.getKey(), lang);
    }

    @Override
    public String getSystemMessage(String key) {
        return getMessage(key);
    }

    @Override
    public String getSystemMessage(String key, String lang) {
        return getMessage(key, lang);
    }

    @Override
    public ConfigData getConfigData() {
        return new FileConfigData(new HashMap<>());
    }

    @Override
    public String getConfigBaseName() {
        return "message";
    }

    @Override
    public void reload() {
        // no-op
    }
}
