package me.waterwood.config;


public interface FileConfigBase {
    static Object get(String path) {
        return null;
    }
    void set(String path, Object val);
    void saveConfig();
}
