package me.waterwood.velochatx.methods;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.velochatx.utils.Channel;
import me.waterwood.velochatx.utils.SubServer;

import java.util.*;

/**
 * A class extends methods used to Control Player's chat
 * @since 1.4.0
 * @author Danburen(Waterwood)
 */
public class ChatControl extends Methods{

    private static boolean isBanWorldEnable;

    public static void initialize() {
        isBanWorldEnable = getConfigs().getBoolean("ban-words.enable",false);
    }
    /**
     * Judge whether two server can communicate
     * <p>
     * get two sub server's channel and get their intersections
     * if the intersections is empty , this indicates they can communicate
     * <b>otherwise</b> they cannot
     * @param source source of server
     * @param target target of server
     * @return whether they can communicate
     */
    public static boolean canCommunicate(SubServer source, SubServer target) {
        if(source == null || target == null) {
            return true;
        }
        Set<Channel> commonChannel= new HashSet<>(source.getChannels());
        commonChannel.retainAll(target.getChannels()); // intersections
        return !commonChannel.isEmpty(); // intersection is not null
    }

    /**
     * @see ChatControl#canCommunicate(SubServer, SubServer)
     * @param source source of server
     * @param target target of server
     * @return whether they can communicate
     */
    public static boolean canCommunicate(String source,String target){
        return canCommunicate(
                serverInfoMap.getOrDefault(source,null),
                serverInfoMap.getOrDefault(target,null)
        );
    }

    public static boolean canCommunicate(RegisteredServer source, RegisteredServer target){
        return canCommunicate(
                source.getServerInfo().getName(), target.getServerInfo().getName()
        );
    }

    public static boolean isBanWorldEnable() {
        return isBanWorldEnable;
    }
}
