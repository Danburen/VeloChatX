package me.waterwood.common;

public abstract class  Color {
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String RESET = "\u001B[0m";

    public static String paint(String color,String message){
        return getColor(color) + message + RESET;
    }

    public static final String getColor(String color){
        switch (color.toUpperCase()){
            case "BLACK" : return BLACK;
            case "RED"   : return RED;
            case "GREEN" : return GREEN;
            case "YELLOW": return YELLOW;
            case "BLUE"  : return BLUE;
            case "MAGENTA": return MAGENTA;
            case "CYAN"   : return CYAN;
            case "WHITE"  : return WHITE;
            default: return RESET;
        }
    }
}
