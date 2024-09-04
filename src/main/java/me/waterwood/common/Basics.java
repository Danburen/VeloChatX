package me.waterwood.common;

public interface Basics {
    static double parseVersion(String dotStr){
        int dotInd = dotStr.indexOf(".");
        String out = dotStr;
        if(dotInd != -1){
            out = dotStr.substring(0,dotInd + 1) + dotStr.substring(dotInd + 1).replaceAll("\\.","");
            double num = Double.parseDouble(out);
            return num;
        }else{
            return 0.0f;
        }
    }
}
