package org.waterwood.api;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;

public abstract class LuckPermsAPI implements ApiBase<LuckPerms>{
    private static boolean hasLuckPerms = false;
    private static LuckPerms api = null;
    public static void checkApi(){
        hasLuckPerms = hasLuckPerm();
    }
    @Override
    public Object getAPI(){
        return api;
    }

    public static boolean hasLuckPerm(){
        try {
            api = LuckPermsProvider.get();
        }catch(NoClassDefFoundError e){
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

    public static String getPlayerGroupDisplay(String playerName){
        if(hasLuckPerms) {
            User user = api.getUserManager().getUser(playerName);
            Group group = api.getGroupManager().getGroup(user.getPrimaryGroup());
            if (group == null) {
                return null;
            }
            return group.getDisplayName();
        }else{
            return null;
        }
    }
}
