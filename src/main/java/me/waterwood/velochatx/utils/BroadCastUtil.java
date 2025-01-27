package me.waterwood.velochatx.utils;

import java.util.List;

/**
 * A class served as util to holder broadcast
 * @since 1.4.0
 * @author Danburen
 * @version 1.0.0
 */
public class BroadCastUtil {
    private String broadcastName;
    private String prefix;
    private String welcomeMessage;
    private boolean welcomeFirstJoin;
    private boolean welcomeEnable;
    private List<String> messageList;
    private List<Channel> channelList;
    private List<SubServer> serverList;

    public BroadCastUtil(String broadcastName, String prefix, String welcomeMessage, boolean welcomeFirstJoin,
                         boolean welcomeEnable, List<String> messageList, List<Channel> channelList,List<SubServer> serverList) {
        this.broadcastName = broadcastName;
        this.prefix = prefix;
        this.welcomeMessage = welcomeMessage;
        this.welcomeFirstJoin = welcomeFirstJoin;
        this.welcomeEnable = welcomeEnable;
        this.messageList = messageList;
        this.channelList = channelList;
        this.serverList = serverList;
    }

    public void addMessage(String message) {
        messageList.add(message);
    }

    public void removeMessage(String message) {
        messageList.remove(message);
    }
    public String getBroadcastName() {
        return broadcastName;
    }

    public void setBroadcastName(String broadcastName) {
        this.broadcastName = broadcastName;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public boolean isWelcomeFirstJoin() {
        return welcomeFirstJoin;
    }

    public void setWelcomeFirstJoin(boolean welcomeFirstJoin) {
        this.welcomeFirstJoin = welcomeFirstJoin;
    }

    public boolean isWelcomeEnable() {
        return welcomeEnable;
    }

    public void setWelcomeEnable(boolean welcomeEnable) {
        this.welcomeEnable = welcomeEnable;
    }

    public List<String> getMessageList() {
        return messageList;
    }

    public void setMessageList(List<String> messageList) {
        this.messageList = messageList;
    }

    public List<Channel> getChannelList() {
        return channelList;
    }
    public List<SubServer> getServerList() {
        return serverList;
    }

    public void setChannelList(List<Channel> channelList) {
        this.channelList = channelList;
    }
}
