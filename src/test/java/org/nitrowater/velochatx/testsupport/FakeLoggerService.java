package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.common.AnsiColor;
import org.nitrowater.waterapi.domain.logger.LoggerService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Recording {@link LoggerService}. Captures every level so tests can assert what an admin
 * would have seen without touching a real console.
 */
public final class FakeLoggerService implements LoggerService {

    public final List<String> infoMessages = new CopyOnWriteArrayList<>();
    public final List<String> warningMessages = new CopyOnWriteArrayList<>();
    public final List<String> errorMessages = new CopyOnWriteArrayList<>();
    public final List<String> debugMessages = new CopyOnWriteArrayList<>();
    public final List<String> colorAwareMessages = new CopyOnWriteArrayList<>();

    private volatile Boolean messageColorParseMode;
    private volatile Boolean debugMode;

    public Boolean getMessageColorParseMode() {
        return messageColorParseMode;
    }

    public Boolean getDebugMode() {
        return debugMode;
    }

    public boolean hasWarningContaining(String needle) {
        return warningMessages.stream().anyMatch(message -> message.contains(needle));
    }

    private static String render(String template, Object... args) {
        if (template == null || args == null || args.length == 0) {
            return String.valueOf(template);
        }
        try {
            return String.format(template, args);
        } catch (RuntimeException e) {
            return template;
        }
    }

    @Override
    public Logger getLogger() {
        return Logger.getLogger("VeloChatXTest");
    }

    @Override
    public void info(String message) {
        infoMessages.add(message);
    }

    @Override
    public void warning(String message) {
        warningMessages.add(message);
    }

    @Override
    public void error(String message) {
        errorMessages.add(message);
    }

    @Override
    public void logMessage(String message) {
        colorAwareMessages.add(message);
    }

    @Override
    public void autoLogMessageWithColor(String message, AnsiColor color) {
        colorAwareMessages.add(message);
    }

    @Override
    public void warning(String template, Object... args) {
        warningMessages.add(render(template, args));
    }

    @Override
    public void debug(String template, Object... args) {
        debugMessages.add(render(template, args));
    }

    @Override
    public void info(String template, Object... args) {
        infoMessages.add(render(template, args));
    }

    @Override
    public void debug(String message) {
        debugMessages.add(message);
    }

    @Override
    public void setDebugMode(Boolean debug) {
        this.debugMode = debug;
    }

    @Override
    public void setMessageColorParseMode(Boolean messageColorParseMode) {
        this.messageColorParseMode = messageColorParseMode;
    }
}
