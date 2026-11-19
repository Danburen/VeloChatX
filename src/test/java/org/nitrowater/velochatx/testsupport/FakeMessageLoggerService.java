package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.kernel.services.PluginMessageService;
import org.nitrowater.waterapi.domain.logger.LoggerService;
import org.nitrowater.waterapi.domain.logger.MessageLoggerService;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.shared.common.MessageTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Recording {@link MessageLoggerService}.
 * <p>Template calls are captured by key; {@code auto(...)} captures the resolved text because
 * that is what the console would print.</p>
 */
public final class FakeMessageLoggerService implements MessageLoggerService {

    private final LoggerService loggerService;
    private final PluginMessageService pluginMessageService;
    private final PlaceholderService placeholderService;

    public final List<String> infos = new CopyOnWriteArrayList<>();
    public final List<String> warnings = new CopyOnWriteArrayList<>();
    public final List<String> errors = new CopyOnWriteArrayList<>();
    public final List<String> successes = new CopyOnWriteArrayList<>();
    public final List<String> autos = new CopyOnWriteArrayList<>();

    public FakeMessageLoggerService(LoggerService loggerService,
                                    PluginMessageService pluginMessageService,
                                    PlaceholderService placeholderService) {
        this.loggerService = loggerService;
        this.pluginMessageService = pluginMessageService;
        this.placeholderService = placeholderService;
    }

    @Override
    public LoggerService getLoggerService() {
        return loggerService;
    }

    @Override
    public void info(MessageTemplate template, Object... args) {
        infos.add(template.getKey());
        loggerService.info(template.getKey());
    }

    @Override
    public void warn(MessageTemplate template, Object... args) {
        warnings.add(template.getKey());
        loggerService.warning(template.getKey());
    }

    @Override
    public void error(MessageTemplate template, Object... args) {
        errors.add(template.getKey());
        loggerService.error(template.getKey());
    }

    @Override
    public void success(MessageTemplate template, Object... args) {
        successes.add(template.getKey());
        loggerService.info(template.getKey());
    }

    @Override
    public void info(String template, PlaceholderContext context) {
        infos.add(placeholderService.resolve(template, context));
    }

    @Override
    public void info(String template, PlaceholderContext context, Map<String, String> args) {
        infos.add(placeholderService.resolve(template, context, args));
    }

    @Override
    public void warn(String template, PlaceholderContext context) {
        warnings.add(placeholderService.resolve(template, context));
    }

    @Override
    public void error(String template, PlaceholderContext context) {
        errors.add(placeholderService.resolve(template, context));
    }

    @Override
    public void auto(String template, PlaceholderContext context) {
        String resolved = placeholderService.resolve(template, context);
        autos.add(resolved);
        loggerService.logMessage(resolved);
    }

    @Override
    public void auto(String template, PlaceholderContext context, Map<String, String> args) {
        String resolved = placeholderService.resolve(template, context, args);
        autos.add(resolved);
        loggerService.logMessage(resolved);
    }

    @Override
    public void auto(MessageTemplate messageTemplate) {
        autos.add(messageTemplate.getKey());
        loggerService.logMessage(messageTemplate.getKey());
    }

    @Override
    public void auto(MessageTemplate messageTemplate, Object... args) {
        autos.add(messageTemplate.getKey());
        loggerService.logMessage(messageTemplate.getKey());
    }

    @Override
    public PluginMessageService getPluginMessageService() {
        return pluginMessageService;
    }

    @Override
    public PlaceholderService getPlaceholderService() {
        return placeholderService;
    }
}
