package me.waterwood.velochatx.mapper;

import me.waterwood.velochatx.VeloChatX;
import org.waterwood.io.DataBase.H2DataBase;
import org.waterwood.plugin.WaterPlugin;

public abstract class MapperBase extends H2DataBase {
    private static final String dbFilePath = VeloChatX.getInstance().getDefaultFilePath("data").replace("/","\\");

    public MapperBase() {
        super(dbFilePath);
        if(connection == null) {
            VeloChatX.getLogger().warning(WaterPlugin.getPluginMessage("error-connect-database-message").formatted(dbFilePath));
        }else{
            VeloChatX.getLogger().info(WaterPlugin.getPluginMessage("success-connect-database-message"));
            createTable();
        }
    }

    protected abstract void createTable();
}
