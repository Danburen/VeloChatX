package org.waterwood.plugin;

import org.waterwood.common.Colors;

import org.slf4j.Logger;


public interface Plugin extends Colors {

    Logger logger = null;

    static Logger getLogger() {
        return null;
    }

    String getDefaultSourcePath(String source, String extension, String lang);

    void loadConfig();

    void loadDefaultSource(String lang);
    void reloadConfig();

    void checkUpdate(boolean download, String author, String repositories);
}
