package me.waterwood.config;

public interface ConfigKeys {
    FileConfiguration config = null;

    void load();
    void reload();

}
