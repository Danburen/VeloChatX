package org.waterwood.common;


import java.util.Arrays;

public abstract class LineFontGenerator {
    public static final String[] FUTURISTIC_BUILD_RAW = {
            "     \n __  \n(__( \n     ", "     \n|__  \n|__) \n     ", "     \n __  \n(___ \n     ",
            "     \n __| \n(__| \n     ", "      \n ___  \n(__/_ \n      ", "  _ \n_|_ \n |  \n    ",
            "     \n __  \n(__| \n __/ ", "     \n|__  \n|  ) \n     ", "  \no \n| \n  ",
            "     \n   | \n(__, \n     ", "     \n|__/ \n|  \\ \n     ", "    \n|   \n|_, \n    ",
            "        \n __ __  \n|  )  ) \n        ", "     \n __  \n|  ) \n     ", "     \n __  \n(__) \n    ",
            "     \n __  \n|__) \n|    ", "     \n __  \n(__| \n   | ", "     \n __  \n|  ' \n     ",
            "     \n  __ \n__)  \n     ", "     \n_|_  \n |_, \n     ", "      \n      \n(__(_ \n      ",
            "     \n     \n(__| \n     ", "        \n        \n(__(__( \n        ", "    \n\\_/ \n/ \\ \n    ",
            "     \n     \n(__| \n  | ", "     \n__   \n (__ \n     ", " __  \n|  | \n|__| \n     ",
            "   \n'| \n | \n   ", " __  \n __) \n(___ \n     ", "___ \n _/ \n__) \n    ",
            "     \n(__| \n   | \n     ", " __  \n(__  \n___) \n     ", "     \n /_  \n(__) \n     ",
            "__  \n  / \n /  \n    ", " __  \n(__) \n(__) \n     ", " __  \n(__) \n  /  \n     "
    };
    // abc/def/ghi/jkl/mno/pqr/stu/vwx/yz0/123/456/789

    public static final String[] FUTURISTIC_BUILD_SPLIT = Arrays.stream(FUTURISTIC_BUILD_RAW).flatMap(
            s -> Arrays.stream(s.split("\n"))).toArray(String[]::new);

    public static String[] parseLineText(String original){
        char[] chars = original.toLowerCase().toCharArray();
        String[] temp = new String[4];
        Arrays.fill(temp, "");
        for(char c : chars){
            int ind = -1;
            if( c >='a' && c <= 'z'){
                ind = c - 'a';
            }else if( c >='0' && c <= '9'){
                ind = c - '0' + 26;
            }
            for(int i = 0 ; i < 4 ; i ++){
                if(ind == -1) {
                    temp[i] = temp[i].concat(String.valueOf(c));
                }else{
                    temp[i] = temp[i].concat(FUTURISTIC_BUILD_SPLIT[ind * 4 + i]);
                }
            }
        }
        return temp;
    }
}