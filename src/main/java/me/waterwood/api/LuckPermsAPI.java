package me.waterwood.api;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;

public abstract class LuckPermsAPI implements ApiBase<LuckPerms>{
    private static boolean hasLuckPerms = true;
    private static LuckPerms api = null;
    public LuckPermsAPI(){

    }
    @Override
    public Object getAPI(){
        return api;
    }

    public static boolean hasLuckPerm(){
        try {
            api = LuckPermsProvider.get();
        }catch(Throwable t){
            hasLuckPerms = false;
            return false;
        }
        return true;
    }
    public static String getPlayerPrefix(String playerName){
        if(hasLuckPerms){
            User user = api.getUserManager().getUser(playerName);
            if (user == null){ return null; }
            return user.getCachedData().getMetaData().getPrefix();
        }else{
            return null;
        }
    }
    public static String getPlayersuffix(String playerName){
        if(hasLuckPerms){
            User user = api.getUserManager().getUser(playerName);
            if (user == null){
                return null;
            }
            return user.getCachedData().getMetaData().getSuffix();
        }else{
            return null;
        }
    }

    public static Group getPlayerGroup(String playerName){
        return getPlayerGroup(api.getUserManager().getUser(playerName));
    }
    public static Group getPlayerGroup(User user){
        Group group = api.getGroupManager().getGroup(user.getPrimaryGroup());
        if(group == null){
            return null;
        }
        return group;
    }
}
