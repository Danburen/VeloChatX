package org.waterwood.common;

import java.util.Map;

public interface Colors {
    String BLACK = "\u001B[30m";
    String DARK_RED = "\u001B[31m";
    String DARK_GREEN = "\u001B[32m";
    String GOLD = "\u001B[33m";
    String DARK_BLUE = "\u001B[34m";
    String DARK_PURPLE = "\u001B[35m";
    String DARK_AQUA = "\u001B[36m";
    String GRAY = "\u001B[37m";
    String DARK_GRAY = "\u001B[90m";
    String RED = "\u001B[91m";
    String GREEN = "\u001B[92m";
    String YELLOW = "\u001B[93m";
    String BLUE = "\u001B[94m";
    String LIGHT_PURPLE = "\u001B[95m";
    String AQUA = "\u001B[96m";
    String WHITE = "\u001B[97m";
    String RESET = "\u001B[0m";

    Map<String, String> color = Map.ofEntries(
            Map.entry("§0", BLACK), Map.entry("§1", DARK_BLUE),
            Map.entry("§2", DARK_GREEN), Map.entry("§3", DARK_AQUA), Map.entry("§4", DARK_RED),
            Map.entry("§5", DARK_PURPLE), Map.entry("§6", GOLD), Map.entry("§7", GRAY),
            Map.entry("§8", DARK_GRAY), Map.entry("§9", BLUE), Map.entry("§a", GREEN),
            Map.entry("§b", AQUA), Map.entry("§c", RED), Map.entry("§d", LIGHT_PURPLE),
            Map.entry("§e", YELLOW), Map.entry("§f", WHITE), Map.entry("§r", RESET));

    /**
     * parse {@link Colors color-code} in order to show in the terminal
     * original text contains code §.
     *
     * @param origin   original text
     * @param isDisable whether parse color
     * @return parsed color text(with ANSI)
     */
    static String parseColor(String origin, boolean isDisable) {
        if (isDisable) {
            return origin.replaceAll("§[0-9a-fA-F]", "");
        }
        StringBuilder sb = new StringBuilder(origin);
        for (Map.Entry<String, String> entry : color.entrySet()) {
            int index = sb.indexOf(entry.getKey());
            while (index != -1) {
                sb.replace(index, index + entry.getKey().length(), entry.getValue());
                index = sb.indexOf(entry.getKey(), index + entry.getValue().length());
            }
        }
        String result = sb.toString();
        return result.endsWith(RESET) ? result : result + RESET;
    }

    static String parseColor(String origin) {
        return parseColor(origin, false);
    }
}
