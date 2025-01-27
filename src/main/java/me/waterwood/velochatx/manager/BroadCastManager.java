package me.waterwood.velochatx.manager;

import me.waterwood.velochatx.VeloChatX;
import me.waterwood.velochatx.methods.Methods;
import me.waterwood.velochatx.utils.BroadCastUtil;
import me.waterwood.velochatx.utils.Channel;
import me.waterwood.velochatx.utils.SubServer;
import org.waterwood.io.FileConfiguration;

import java.util.*;
import java.util.stream.Collectors;

public class BroadCastManager extends Methods {
    public static boolean channelGlobal;
    private static Map<String,Channel> channels;

    protected static FileConfiguration broadCastConfig;
    private static BroadCastUtil globalBCT;
    private static boolean isEnabled = false;
    private static boolean isRandom = false;
    private static boolean isGlobal = false;
    private static boolean isLocal = false;
    private static Set<BroadCastUtil> localBCT;

    private static boolean BROADCAST_ENABLED;
    private static boolean BROADCAST_PROXY_ENABLED;
    private static boolean LOG_ENABLE;
    private static boolean LOG_PROXY_ENABLE;
    private static boolean SEND_TO_ALL;
    private static boolean SEND_TO_ALL_PROXY;
    private static boolean BROADCAST_IN_CHANNEL;

    private static String JOIN_MESSAGE;
    private static String LEAVE_MESSAGE;
    private static String JOIN_PROXY_MESSAGE;
    private static String LEAVE_PROXY_MESSAGE;

    private static String JOIN_PREFIX;
    private static String LEAVE_PREFIX;
    private static String JOIN_PROXY_PREFIX;
    private static String LEAVE_PROXY_PREFIX;

    public static void initialize() {
        loadData();
        broadCastConfig = VeloChatX.getInstance().loadFile("broadcast.yml");
        channelGlobal = getConfigs().getBoolean("channel-global",false);
        if(!channelGlobal) getAndSetChannel();
        if(isGlobal) loadGlobalBCT();
        if(isLocal) loadLocalBCTs();
    }

    /**
     * Add local Broadcast
     * @param name name of the broadcast
     * @param prefix prefix of broadcasting
     * @param welcome welcome message
     * @param firstJoin welcome message display whether first join
     * @param welcomeEnable whether welcome enable
     * @param msgList message list of broadcast
     * @param channelList channel list of broadcast
     */
    public static void addBCT(String name,String prefix,String welcome,boolean firstJoin,
                              boolean welcomeEnable,List<String> msgList,List<String> channelList,List<String> serverList){
        if(localBCT.stream().map(BroadCastUtil::getBroadcastName).toList().contains(name)) {
            return;
        }
        localBCT.add(
                new BroadCastUtil(name,prefix,welcome,firstJoin,welcomeEnable,msgList,channelList.stream()
                        .map(BroadCastManager::getChannel)
                        .filter(Objects::nonNull)
                        .toList(),serverList.stream()
                        .map(Methods::getSubServer)
                        .filter(Objects::nonNull)
                        .toList())
        );
    }

    private static void loadLocalBCTs(){
        Map<String,Object> BCTMap = broadCastConfig.getMap("local.broadcast-list",Object.class);
        if (BCTMap != null) {
            BCTMap.keySet().forEach(
                    key->{
                        String path_prefix = "local.broadcast-list." + key +".";
                        if(broadCastConfig.getBoolean(path_prefix + "enable",false)){
                            addBCT(
                                    key,
                                    broadCastConfig.get(path_prefix + "prefix", "[§6Server§r]"),
                                    broadCastConfig.get(path_prefix + "welcome.message"),
                                    broadCastConfig.get(path_prefix + "welcome.only-first-join"),
                                    broadCastConfig.get(path_prefix + "welcome.enable"),
                                    broadCastConfig.getStringList(path_prefix + "message-list", List.of()),
                                    broadCastConfig.getStringList(path_prefix + "channel-list",List.of()),
                                    broadCastConfig.getStringList(path_prefix + "server-list",List.of())
                            );
                        }
                    }
            );
        }
    }

    public static void loadData(){
        isEnabled = broadCastConfig.getBoolean("enabled",false);
        isRandom = broadCastConfig.getBoolean("random",false);
        isGlobal = broadCastConfig.get("global.enable");
        isLocal = broadCastConfig.get("local.enable");

        BROADCAST_ENABLED = broadCastConfig.getBoolean("join-leave-broadcast.enable",true);
        LOG_ENABLE = broadCastConfig.getBoolean("join-leave-broadcast.log-to-console",true);
        SEND_TO_ALL = broadCastConfig.getBoolean("join-leave-broadcast.send-to-all-subServer",false);
        BROADCAST_PROXY_ENABLED = broadCastConfig.getBoolean("join-leave-proxy-broadcast.enable",true);
        LOG_PROXY_ENABLE = broadCastConfig.getBoolean("join-leave-proxy-broadcast.log-to-console",true);
        SEND_TO_ALL_PROXY = broadCastConfig.getBoolean("join-leave-proxy-broadcast.send-to-all-subServer",true);
        BROADCAST_IN_CHANNEL = broadCastConfig.getBoolean("join-leave-broadcast.broadcast-in-channel",true);

        JOIN_PREFIX = broadCastConfig.getString("join-leave-broadcast.join-prefix","§a(+)§r");
        LEAVE_PREFIX = broadCastConfig.getString("join-leave-broadcast.leave-prefix","§c(-)§r");
        JOIN_PROXY_PREFIX = broadCastConfig.getString("join-leave-proxy-broadcast.join-prefix","§a[+]§r");
        JOIN_PROXY_PREFIX = broadCastConfig.getString("join-leave-proxy-broadcast.leave-prefix","§c[-]§r");

        JOIN_MESSAGE = broadCastConfig.getString("join-leave-broadcast.player-join-message","{Group}{Prefix}{Player}{Suffix} has Joined the {Server}");
        LEAVE_MESSAGE = broadCastConfig.getString("join-leave-broadcast.player-leave-message","{Group}{Prefix}{Player}{Suffix} has left the {Server}");
        JOIN_PROXY_MESSAGE = broadCastConfig.getString("join-leave-proxy-broadcast.player-join-message","{Group}{Prefix}{Player}{Suffix} has Joined the {Server}");
        LEAVE_PROXY_MESSAGE = broadCastConfig.getString("join-leave-proxy-broadcast.player-leave-message","{Group}{Prefix}{Player}{Suffix} has left the {Server}");

    }

    private static void loadGlobalBCT(){
        globalBCT = new BroadCastUtil("global",
                broadCastConfig.get("global.prefix", "[§6Server§r]"),
                broadCastConfig.get("global.welcome.message"),
                broadCastConfig.get("global.welcome.only-first-join"),
                broadCastConfig.get("global.welcome.enable"),
                broadCastConfig.getStringList("global.message-list", List.of()),
                channels.values().stream().toList(),
                serverInfoMap.values().stream().toList());
    }

    // Get and set channels & server's channel
    private static void getAndSetChannel(){
        channels = new HashMap<>();
        Map<String,Object> channelMap = getBroadcastConfigs().getMap("channels.channel-list",Object.class);
        if (channelMap != null) {
            channelMap.forEach( (k,v) ->{
                List<String> serverList = getBroadcastConfigs().getStringList("channels.channel-list." + k + ".server",List.of());
                //add new channel
                Channel serverChannel = new Channel(
                        k, getBroadcastConfigs().get("channels.channel-list." + k + ".display",getMessage("unknown-channel-display"))
                        ,serverList.stream()
                        .filter(serverName-> serverDisplayName.containsKey(serverName))
                        .map(serverInfoMap::get)
                        .collect(Collectors.toSet()));
                channels.put(k,serverChannel);

                // set server's channel
                for(SubServer serverInfo : serverInfoMap.values()){
                    if(serverList.contains(serverInfo.getServerName())){
                        serverInfo.addChannel(serverChannel);
                        // Retrieve source channel name base on server name
                        serverSourceChannelName.put(serverInfo.getServerName(),k);
                    }
                }
            });
        }
    }


    public static boolean isEnabled() {
        return isEnabled;
    }
    public static boolean isRandom() {
        return isRandom;
    }
    public static boolean isGlobal() {
        return isGlobal;
    }
    public static boolean isLocal() {
        return isLocal;
    }
    public static Set<BroadCastUtil> getLocalBCT() {
        return localBCT;
    }
    public static BroadCastUtil getGlobalBCT() {
        return globalBCT;
    }

    public static boolean isBroadcastInChannel() { return BROADCAST_IN_CHANNEL; }
    public static boolean isBroadcastEnabled() { return BROADCAST_ENABLED; }
    public static boolean isBroadcastEnabledProxy() { return BROADCAST_PROXY_ENABLED; }
    public static boolean isLogEnable() { return LOG_ENABLE; }
    public static boolean isLogProxyEnable() { return LOG_PROXY_ENABLE; }
    public static boolean isSendToAll() { return SEND_TO_ALL; }
    public static boolean isSendToAllProxy() { return SEND_TO_ALL_PROXY; }
    public static String getJoinMessage() { return JOIN_MESSAGE; }
    public static String getLeaveMessage() { return LEAVE_MESSAGE; }
    public static String getJoinProxyMessage() { return JOIN_PROXY_MESSAGE; }
    public static String getLeaveProxyMessage() {
        return LEAVE_PROXY_MESSAGE;
    }
    public static String getJoinPrefix() {
        return JOIN_PREFIX;
    }
    public static String getLeavePrefix() {
        return LEAVE_PREFIX;
    }
    public static String getJoinProxyPrefix() {
        return JOIN_PROXY_PREFIX;
    }
    public static String getLeaveProxyPrefix() {
        return LEAVE_PROXY_PREFIX;
    }

    public static String getFullJoinProxyMessage(){
        return getJoinProxyPrefix() + getJoinProxyMessage();
    }
    public static String getFullLeaveProxyMessage(){
        return getLeaveProxyPrefix() + getLeaveProxyMessage();
    }

    public static Channel getChannel(String serverName){ return channels.get(serverName); }

    public static Map<String,Channel> getChannels(){ return channels; }

    public static boolean isChannelGlobal(){
        return channelGlobal;
    }

    public static FileConfiguration getBroadcastConfigs() {
        return broadCastConfig;
    }
}
