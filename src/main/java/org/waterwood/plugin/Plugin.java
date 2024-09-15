package org.waterwood.plugin;

import org.waterwood.common.Colors;

import java.util.logging.Logger;


public interface Plugin extends Colors {

    static Logger getLogger() {
        return null;
    }

    String getDefaultSourcePath(String source, String extension, String lang);

    void loadConfig();

    void loadDefaultSource(String lang);

    void loadConfig(boolean loadMessage);

    void reloadConfig();
    void checkUpdate(String owner, String repositories);
}
