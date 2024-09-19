package org.waterwood.common;

import org.waterwood.plugin.WaterPlugin;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Colors {

    protected static final Pattern PATTERN = Pattern.compile("§[0-9a-fA-Fr]");

    protected static final String BLACK = "\u001B[30m";
    protected static final String DARK_RED = "\u001B[31m";
    protected static final String DARK_GREEN = "\u001B[32m";
    protected static final String GOLD = "\u001B[33m";
    protected static final String DARK_BLUE = "\u001B[34m";
    protected static final String DARK_PURPLE = "\u001B[35m";
    protected static final String DARK_AQUA = "\u001B[36m";
    protected static final String GRAY = "\u001B[37m";
    protected static final String DARK_GRAY = "\u001B[90m";
    protected static final String RED = "\u001B[91m";
    protected static final String GREEN = "\u001B[92m";
    protected static final String YELLOW = "\u001B[93m";
    protected static final String BLUE = "\u001B[94m";
    protected static final String LIGHT_PURPLE = "\u001B[95m";
    protected static final String AQUA = "\u001B[96m";
    protected static final String WHITE = "\u001B[97m";
    protected static final String RESET = "\u001B[0m";

    protected static final Map<String, String> COLOR_MAP = Map.ofEntries(
            Map.entry("§0", BLACK), Map.entry("§1", DARK_BLUE),
            Map.entry("§2", DARK_GREEN), Map.entry("§3", DARK_AQUA), Map.entry("§4", DARK_RED),
            Map.entry("§5", DARK_PURPLE), Map.entry("§6", GOLD), Map.entry("§7", GRAY),
            Map.entry("§8", DARK_GRAY), Map.entry("§9", BLUE), Map.entry("§a", GREEN),
            Map.entry("§b", AQUA), Map.entry("§c", RED), Map.entry("§d", LIGHT_PURPLE),
            Map.entry("§e", YELLOW), Map.entry("§f", WHITE), Map.entry("§r", RESET)
    );

    /**
     * Parses {@link Colors color-code} to show in the terminal.
     * Original text contains code §.
     *
     * @param origin   Original text
     * @param isDisable Whether to parse color
     * @return Parsed color text (with ANSI)
     */
    public static String parseColor(String origin, boolean isDisable) {
        if (isDisable) {
            return origin.replaceAll("§[0-9a-fA-Fr]", "");
        }
        StringBuilder sb = new StringBuilder();
        Matcher matcher = PATTERN.matcher(origin);
        int lastIndex = 0;
        while (matcher.find()) {
            sb.append(origin, lastIndex, matcher.start());
            String colorCode = matcher.group();
            String ansiCode = COLOR_MAP.getOrDefault(colorCode, "");
            sb.append(ansiCode);
            lastIndex = matcher.end();
        }
        sb.append(origin, lastIndex, origin.length());
        String result = sb.toString().replaceAll("§.?", "");
        if (!result.endsWith(RESET)) {
            result += RESET;
        }
        return result;
    }

    public static String parseColor(String origin) {
        return parseColor(origin, false);
    }
}