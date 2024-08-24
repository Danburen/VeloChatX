package me.waterwood.common;

import java.util.Map;

public interface Basics {
    public static final String BLACK = "\u001B[30m";
    public static final String DARK_RED = "\u001B[31m";
    public static final String DARK_GREEN = "\u001B[32m";
    public static final String GOLD = "\u001B[33m";
    public static final String DARK_BLUE = "\u001B[34m";
    public static final String DARK_PURPLE = "\\u001B[35m";
    public static final String DARK_AQUA = "\u001B[36m";
    public static final String GRAY = "\u001B[37m";
    public static final String DARK_GRAY = "\u001B[90m";
    public static final String RED = "\u001B[91m";
    public static final String GREEN = "\u001B[92m";
    public static final String YELLOW = "\u001B[93m";
    public static final String BLUE = "\u001B[94m";
    public static final String LIGHT_PURPLE = "\u001B[95m";
    public static final String AQUA = "\u001B[96m";
    public static final String WHITE ="\u001B[97m";
    public static final String RESET = "\u001B[0m";

    public static final Map<String,String> color = Map.ofEntries(
            Map.entry("§0",BLACK), Map.entry("§1",DARK_BLUE),
            Map.entry("§2",DARK_GREEN), Map.entry("§3",DARK_AQUA), Map.entry("§4",DARK_RED),
            Map.entry("§5",DARK_PURPLE), Map.entry("§6",GOLD), Map.entry("§7",GRAY),
            Map.entry("§8",DARK_GRAY), Map.entry("§9",BLUE), Map.entry("§a",GREEN),
            Map.entry("§b",AQUA), Map.entry("§c",RED), Map.entry("§d",LIGHT_PURPLE),
            Map.entry("§e",YELLOW), Map.entry("§f",WHITE), Map.entry("§r",RESET));

    /**
     * parse {@link Basics color-code} in order to show in the terminal
     * original text contains code §.
     * @param origin original text
     * @param isEnable whether parse color
     * @return parsed color text(with ANSI)
     */
    public static String parseColor(String origin,boolean isEnable){
        if(isEnable) {
                for (Map.Entry<String, String> entry : color.entrySet()){
                    origin = origin.replace(entry.getKey(), entry.getValue());
                }
                return origin.endsWith(RESET) ? origin : origin.concat(RESET) ;
        }else{
            return origin.replaceAll("§.", "");
        }
    }
    static String parseColor(String origin){
        return parseColor(origin,true);
    }

}
